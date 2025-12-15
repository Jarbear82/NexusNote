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
 * Robust Spectral Layout Implementation.
 * * Fixes:
 * - Unify Graph: Uses peripheral connections to prevent "hairball" clustering.
 */
class SpectralLayout : LayoutPhase {

    // Track dummy elements locally to clean up after run
    private val dummyEdges = mutableListOf<FcEdge>()
    private val dummyNodes = mutableListOf<FcNode>()

    override suspend fun run(graph: FcGraph, config: LayoutConfig) = coroutineScope {
        println("[Spectral] Starting robust layout...")

        // Clear local state
        dummyEdges.clear()
        dummyNodes.clear()

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

    private fun calculateAdaptivePivots(n: Int): Int {
        if (n <= 50) return n
        val scalingTarget = sqrt(n.toDouble()).toInt()
        return min(n, max(50, scalingTarget))
    }

    // --- Step 1.1: Graph Unification (Anti-Hairball Fix) ---

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

        // FIX: Create a central dummy "Hub" node.
        // Connect the "Peripheral" (lowest degree) node of each component to this hub.
        // This spreads components out rather than dragging their centers together.

        val hub = graph.addNode(id = "dummy_hub_${Random.nextInt()}", isDummy = true)
        dummyNodes.add(hub)

        components.forEach { comp ->
            val peripheralNode = findPeripheralNode(comp)

            // Long edge to separate components
            val edge = graph.addEdge(hub.id, peripheralNode.id, isDummy = true)
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

    private fun findPeripheralNode(nodes: List<FcNode>): FcNode {
        // Find node with minimum degree (closest to being a leaf)
        // If multiple, pick one far from the "center" if possible, but random/first is fine.
        return nodes.minByOrNull { it.getDegree() } ?: nodes[0]
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

    // --- Linear Algebra (Multik Integration) ---

    private fun computePCA(
        data: Array<DoubleArray>,
        n: Int,
        k: Int,
        iterations: Int
    ): List<Pair<Double, Double>> {
        val flattenedData = data.flatMap { it.toList() }
        val matrix = mk.ndarray(flattenedData, k, n)

        val centeredData = DoubleArray(k * n)
        for (i in 0 until k) {
            var sum = 0.0
            for (j in 0 until n) sum += matrix[i, j]
            val mean = sum / n

            for (j in 0 until n) {
                centeredData[i * n + j] = matrix[i, j] - mean
            }
        }
        val centered = mk.ndarray(centeredData, k, n)

        val cov = (centered dot centered.transpose()) / n.toDouble()

        val v1 = powerIterationMultik(cov, k, iterations)
        val covV1 = cov dot v1
        val lambda1 = v1 dot covV1

        val v1Col = v1.reshape(k, 1)
        val v1Row = v1.reshape(1, k)
        val outer = v1Col dot v1Row
        val cov2 = cov - (outer * lambda1)

        val v2 = powerIterationMultik(cov2, k, iterations)

        val xRow = v1.reshape(1, k) dot centered
        val yRow = v2.reshape(1, k) dot centered

        val coords = mutableListOf<Pair<Double, Double>>()
        for (j in 0 until n) {
            coords.add(xRow[0, j] to yRow[0, j])
        }

        return coords
    }

    private fun powerIterationMultik(matrix: D2Array<Double>, size: Int, iter: Int): D1Array<Double> {
        val randData = DoubleArray(size) { Random.nextDouble() }
        var v = mk.ndarray(randData)

        var norm = sqrt(v dot v)
        if (norm > 0) v = v / norm

        repeat(iter) {
            val next = matrix dot v
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
                val jitterX = (Math.random() - 0.5) * 50.0
                val jitterY = (Math.random() - 0.5) * 50.0

                node.setCenter((x * scale) + jitterX, (y * scale) + jitterY)
            }
        }
    }

    private fun cleanup(graph: FcGraph) {
        dummyEdges.forEach { graph.removeEdge(it) }
        dummyNodes.forEach { graph.removeNode(it) }
        dummyEdges.clear()
        dummyNodes.clear()
    }
}