package com.tau.nexusnote.codex.graph.fcose

import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.collections.ArrayDeque
import kotlin.math.max
import kotlin.math.min

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

// Phase 3: Multik Imports
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.api.linalg.norm
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*

/**
 * Robust Spectral Layout Implementation (Phase 1).
 * Refactored to implement LayoutPhase interface.
 *
 * Phase 3: Linear Algebra Refactor (Kotlin Multik).
 * Replaces manual matrix math with Multik ndarrays and operations.
 *
 * Pipeline:
 * 1. Graph Unification (Connect disconnected components)
 * 2. Compound Simplification (Collapse compound nodes to representatives)
 * 3. PivotMDS (High-dimensional embedding + PCA projection)
 *
 * Phase 4 Optimization: Adaptive Pivot Selection.
 */
class SpectralLayout : LayoutPhase {

    // Track dummy elements locally to clean up after run
    private val dummyEdges = mutableListOf<FcEdge>()

    override suspend fun run(graph: FcGraph, config: LayoutConfig) = coroutineScope {
        println("[Spectral] Starting robust layout...")

        // Clear local state
        dummyEdges.clear()

        // Step 1.1: Preprocessing (Graph Unification)
        unifyGraph(graph)

        // Step 1.2: Compound-to-Simple Conversion
        val (calcNodes, calcEdges) = buildCalculationGraph(graph)
        val n = calcNodes.size

        if (n < 2) {
            println("[Spectral] Graph too small, skipping.")
            // Cleanup on Main thread as it touches the graph structure
            withContext(Dispatchers.Main) {
                cleanup(graph)
            }
            return@coroutineScope
        }

        // Map node ID to index 0..n-1 for matrix operations
        val nodeToIndex = calcNodes.mapIndexed { index, node -> node.id to index }.toMap()

        // Step 1.3: PivotMDS Implementation with MaxMin Selection
        // Heavy calculation runs on default dispatcher (Background)
        val numPivots = calculateAdaptivePivots(n)
        val (pivots, distanceMatrix) = selectPivotsMaxMin(calcNodes, calcEdges, numPivots, nodeToIndex)

        println("[Spectral] Selected ${pivots.size} pivots using MaxMin strategy for $n nodes (Target: $numPivots).")

        // C. Dimensionality Reduction (PCA on Distance Vectors using Multik)
        val coordinates = computePCA(distanceMatrix, n, numPivots, config.eigenVectorIterations)

        // D. Apply Coordinates & Cleanup
        // Must run on Main thread to update UI-visible nodes safely
        withContext(Dispatchers.Main) {
            applyCoordinates(calcNodes, coordinates, config.spectralScalingFactor)

            // Step 4: Cleanup & Expansion
            cleanup(graph)
            graph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
        }

        println("[Spectral] Finished.")
    }

    /**
     * Phase 4: Adaptive Pivot Calculation.
     */
    private fun calculateAdaptivePivots(n: Int): Int {
        if (n <= 50) return n
        val scalingTarget = sqrt(n.toDouble()).toInt()
        return min(n, max(50, scalingTarget))
    }

    // --- Step 1.1: Graph Unification ---

    private fun unifyGraph(graph: FcGraph) {
        val allNodes = graph.nodes.toSet()
        val visited = mutableSetOf<FcNode>()
        val components = mutableListOf<List<FcNode>>()

        // Identify Components
        for (node in allNodes) {
            if (node !in visited) {
                val component = bfsComponent(node)
                visited.addAll(component)
                components.add(component)
            }
        }

        if (components.size <= 1) return

        println("[Spectral] Unifying ${components.size} disconnected components.")

        components.sortByDescending { it.size }
        val mainComponent = components[0]
        val mainCenter = findCenterNode(mainComponent)

        // Connect other components to the main component
        for (i in 1 until components.size) {
            val comp = components[i]
            val compCenter = findCenterNode(comp)

            val edge = graph.addEdge(mainCenter.id, compCenter.id, isDummy = true)
            if (edge != null) dummyEdges.add(edge)
        }
    }

    private fun bfsComponent(start: FcNode): List<FcNode> {
        val component = mutableListOf<FcNode>()
        val queue = ArrayDeque<FcNode>()
        val seen = mutableSetOf<FcNode>()

        queue.add(start)
        seen.add(start)

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            component.add(u)

            u.edges.forEach { edge ->
                val v = if (edge.source == u) edge.target else edge.source
                if (v !in seen) {
                    seen.add(v)
                    queue.add(v)
                }
            }
        }
        return component
    }

    private fun findCenterNode(nodes: List<FcNode>): FcNode {
        return nodes.maxByOrNull { it.getDegree() } ?: nodes[0]
    }

    // --- Step 1.2: Compound Simplification ---

    private data class CalculationData(val nodes: List<FcNode>, val edges: List<SimpleEdge>)
    private data class SimpleEdge(val source: FcNode, val target: FcNode)

    private fun buildCalculationGraph(graph: FcGraph): CalculationData {
        val leafNodes = mutableListOf<FcNode>()
        val nodeMapping = mutableMapOf<FcNode, FcNode>()

        graph.nodes.forEach { node ->
            if (node.isLeaf()) {
                nodeMapping[node] = node
                leafNodes.add(node)
            }
        }

        val compounds = graph.nodes.filter { it.isCompound() }
        compounds.forEach { comp ->
            val rep = findRepresentative(comp)
            if (rep != null) {
                nodeMapping[comp] = rep
            }
        }

        val activeEdges = mutableListOf<SimpleEdge>()
        graph.edges.forEach { edge ->
            val u = nodeMapping[edge.source]
            val v = nodeMapping[edge.target]

            if (u != null && v != null && u != v) {
                activeEdges.add(SimpleEdge(u, v))
            }
        }

        return CalculationData(leafNodes, activeEdges)
    }

    private fun findRepresentative(node: FcNode): FcNode? {
        if (node.isLeaf()) return node
        node.children.forEach { child ->
            val leaf = findRepresentative(child)
            if (leaf != null) return leaf
        }
        return null
    }

    // --- Step 1.3: PivotMDS Helpers (MaxMin Selection) ---

    private fun selectPivotsMaxMin(
        nodes: List<FcNode>,
        edges: List<SimpleEdge>,
        k: Int,
        nodeToIndex: Map<String, Int>
    ): Pair<List<FcNode>, Array<DoubleArray>> {
        val n = nodes.size
        val selectedPivots = mutableListOf<FcNode>()
        val distanceMatrix = Array(k) { DoubleArray(n) }

        val adj = Array(n) { mutableListOf<Int>() }
        edges.forEach { edge ->
            val u = nodeToIndex[edge.source.id]
            val v = nodeToIndex[edge.target.id]
            if (u != null && v != null) {
                adj[u].add(v)
                adj[v].add(u)
            }
        }

        val minDists = DoubleArray(n) { Double.MAX_VALUE }

        val firstPivotIndex = Random(123).nextInt(n)
        val firstPivot = nodes[firstPivotIndex]
        selectedPivots.add(firstPivot)

        val dists0 = bfs(firstPivotIndex, n, adj)
        distanceMatrix[0] = dists0

        for (i in 0 until n) {
            minDists[i] = dists0[i]
        }

        for (i in 1 until k) {
            var maxVal = -1.0
            var candidateIndex = -1

            for (j in 0 until n) {
                if (minDists[j] > maxVal) {
                    maxVal = minDists[j]
                    candidateIndex = j
                }
            }

            if (maxVal == 0.0) {
                val usedIndices = selectedPivots.map { nodeToIndex[it.id]!! }.toSet()
                candidateIndex = (0 until n).firstOrNull { it !in usedIndices } ?: break
            }

            val nextPivot = nodes[candidateIndex]
            selectedPivots.add(nextPivot)

            val dists = bfs(candidateIndex, n, adj)
            distanceMatrix[i] = dists

            for (j in 0 until n) {
                if (dists[j] < minDists[j]) {
                    minDists[j] = dists[j]
                }
            }
        }

        return Pair(selectedPivots, distanceMatrix)
    }

    private fun bfs(
        startIndex: Int,
        n: Int,
        adj: Array<MutableList<Int>>
    ): DoubleArray {
        val dists = DoubleArray(n) { Double.MAX_VALUE }
        dists[startIndex] = 0.0

        val queue = ArrayDeque<Int>()
        queue.add(startIndex)

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val d = dists[u]

            adj[u].forEach { v ->
                if (dists[v] == Double.MAX_VALUE) {
                    dists[v] = d + 1.0
                    queue.add(v)
                }
            }
        }

        var localMax = 0.0
        for (d in dists) {
            if (d != Double.MAX_VALUE && d > localMax) localMax = d
        }
        val penalty = if (localMax > 0) localMax * 1.5 else 1.0

        for (i in dists.indices) {
            if (dists[i] == Double.MAX_VALUE) dists[i] = penalty
        }

        return dists
    }

    // --- Linear Algebra Refactor (Multik Integration) ---

    private fun computePCA(
        data: Array<DoubleArray>,
        n: Int,
        k: Int,
        iterations: Int
    ): List<Pair<Double, Double>> {
        // 1. Data Conversion: Array<DoubleArray> -> D2Array
        val flattenedData = data.flatMap { it.toList() }
        val matrix = mk.ndarray(flattenedData, k, n) // k rows (pivots), n columns (nodes)

        // 2. Centering
        // Calculate mean of each row (axis 1) manually to be safe across Multik versions
        // and create centered matrix
        val centeredData = DoubleArray(k * n)
        for (i in 0 until k) {
            // Extract row i manually or via slicing
            var sum = 0.0
            for (j in 0 until n) sum += matrix[i, j]
            val mean = sum / n

            for (j in 0 until n) {
                centeredData[i * n + j] = matrix[i, j] - mean
            }
        }
        val centered = mk.ndarray(centeredData, k, n)

        // 3. Covariance: (centered dot centered.transpose()) / n
        // Multik 'dot' handles matrix multiplication efficiently.
        // centered (k x n) dot centered.transpose (n x k) -> (k x k)
        val cov = (centered dot centered.transpose()) / n.toDouble()

        // 4. Eigen Decomposition (Refactored Power Iteration using Multik)
        // We need the top 2 eigenvectors (v1, v2).

        // Calculate v1
        val v1 = powerIterationMultik(cov, k, iterations)

        // Calculate Lambda1 (Rayleigh Quotient)
        // lambda1 = (v1 . (cov . v1)) / (v1 . v1) -> v1 is normalized, so denom is 1
        val covV1 = cov dot v1
        val lambda1 = v1 dot covV1 // scalar result

        // Deflate Covariance Matrix: cov2 = cov - lambda1 * (v1 * v1.T)
        // Outer product: v1 (k x 1) dot v1.T (1 x k) -> k x k
        val v1Col = v1.reshape(k, 1)
        val v1Row = v1.reshape(1, k)
        val outer = v1Col dot v1Row
        val cov2 = cov - (outer * lambda1)

        // Calculate v2
        val v2 = powerIterationMultik(cov2, k, iterations)

        // 5. Projection / Coordinates
        // Project data onto eigenvectors: Coords = EigenVector dot CenteredData
        // v1 (size k) needs to be treated as row vector (1 x k) to dot with centered (k x n)
        val xRow = v1.reshape(1, k) dot centered // Result: 1 x n
        val yRow = v2.reshape(1, k) dot centered // Result: 1 x n

        val coords = mutableListOf<Pair<Double, Double>>()
        // Accessing D2Array elements: [row, col]
        for (j in 0 until n) {
            coords.add(xRow[0, j] to yRow[0, j])
        }

        return coords
    }

    /**
     * Refactored Power Iteration using Multik's vector operations.
     * Replaces manual loops with optimized dot products and norms.
     */
    private fun powerIterationMultik(matrix: D2Array<Double>, size: Int, iter: Int): D1Array<Double> {
        // Initialize random vector
        val randData = DoubleArray(size) { Random.nextDouble() }
        var v = mk.ndarray(randData)

        // Normalize
        // FIX: mk.linalg.norm(v) is not supported for D1Array in this version.
        // Use manual Euclidean norm: sqrt(v dot v)
        var norm = sqrt(v dot v)
        if (norm > 0) v = v / norm

        repeat(iter) {
            // Matrix-Vector multiplication
            val next = matrix dot v

            // FIX: Manual norm calculation for the next vector as well
            norm = sqrt(next dot next)
            if (norm > 1e-9) {
                v = next / norm
            }
        }
        return v
    }

    private fun applyCoordinates(nodes: List<FcNode>, coords: List<Pair<Double, Double>>, scale: Double) {
        nodes.forEachIndexed { index, node ->
            if (!node.isFixed) {
                val (x, y) = coords[index]
                // Add random noise
                val jitterX = (Math.random() - 0.5) * 50.0
                val jitterY = (Math.random() - 0.5) * 50.0

                node.setCenter((x * scale) + jitterX, (y * scale) + jitterY)
            }
        }
    }
    private fun cleanup(graph: FcGraph) {
        dummyEdges.forEach { graph.removeEdge(it) }
        dummyEdges.clear()
    }
}
