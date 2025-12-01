package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.settings.LayoutDirection
import kotlin.math.max

object HierarchicalLayoutEngine {

    private const val MIN_NODE_SEPARATION = 50f  // Gap between nodes horizontally
    private const val MIN_LAYER_SEPARATION = 100f // Gap between layers vertically

    /**
     * Calculates positions for a Generic Hierarchical Layout (Sugiyama Framework).
     * Now Dimension-Aware: Respects node width and height.
     */
    fun arrange(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        direction: LayoutDirection
    ): Map<Long, Offset> {
        if (nodes.isEmpty()) return emptyMap()

        // 1. Build Adjacency List & Remove Cycles
        // We treat the graph as a DAG by ignoring back-edges during rank assignment.
        val nodeIds = nodes.keys.toList()
        val forwardEdges = removeCycles(nodeIds, edges)

        // 2. Layering (Rank Assignment)
        // Assign each node a Y-rank (0, 1, 2...)
        val ranks = assignRanks(nodeIds, forwardEdges)
        val maxRank = ranks.values.maxOrNull() ?: 0

        // Group nodes by Rank
        val layers = MutableList(maxRank + 1) { mutableListOf<Long>() }
        ranks.forEach { (id, rank) -> layers[rank].add(id) }

        // 3. Ordering (Crossing Minimization)
        // Sort nodes within each layer to minimize edge crossings
        minimizeCrossings(layers, forwardEdges)

        // 4. Coordinate Assignment (X-Axis)
        // Assign X coordinates based on width + "Block Shifting" heuristic
        val xPositions = assignXCoordinates(layers, forwardEdges, nodes)

        // 5. Layer Height Assignment (Y-Axis)
        // Calculate Y positions based on the tallest node in each layer
        val positions = assignYCoordinates(layers, xPositions, nodes)

        // 6. Transform based on Direction
        return applyDirectionTransform(positions, direction)
    }

    // --- Step 1: Cycle Removal (DFS) ---
    private fun removeCycles(nodes: List<Long>, edges: List<GraphEdge>): Map<Long, List<Long>> {
        val adj = edges.groupBy { it.sourceId }
            .mapValues { (_, v) -> v.map { it.targetId } }

        val visited = mutableSetOf<Long>()
        val recursionStack = mutableSetOf<Long>()
        val dagAdj = mutableMapOf<Long, MutableList<Long>>()

        fun dfs(u: Long) {
            visited.add(u)
            recursionStack.add(u)

            adj[u]?.forEach { v ->
                if (!visited.contains(v)) {
                    // Forward edge
                    dagAdj.getOrPut(u) { mutableListOf() }.add(v)
                    dfs(v)
                } else if (!recursionStack.contains(v)) {
                    // Cross edge or Forward edge to already visited (but not ancestor)
                    dagAdj.getOrPut(u) { mutableListOf() }.add(v)
                }
                // Else: Back edge (v is in recursionStack), ignore it for DAG structure
            }

            recursionStack.remove(u)
        }

        nodes.forEach { if (!visited.contains(it)) dfs(it) }
        return dagAdj
    }

    // --- Step 2: Rank Assignment (Longest Path) ---
    private fun assignRanks(nodes: List<Long>, adj: Map<Long, List<Long>>): Map<Long, Int> {
        val ranks = mutableMapOf<Long, Int>()
        val inDegree = mutableMapOf<Long, Int>().withDefault { 0 }

        // Calculate in-degrees based on DAG edges
        adj.forEach { (_, neighbors) ->
            neighbors.forEach { v -> inDegree[v] = inDegree.getValue(v) + 1 }
        }

        // Initialize Queue with roots (in-degree 0)
        val queue = ArrayDeque<Long>()
        nodes.forEach { if (inDegree.getValue(it) == 0) {
            queue.add(it)
            ranks[it] = 0
        }}

        // Process (Khan's Algorithm style)
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val uRank = ranks[u] ?: 0

            adj[u]?.forEach { v ->
                // Longest Path: Rank is max(parents) + 1
                val currentVRank = ranks[v] ?: 0
                ranks[v] = max(currentVRank, uRank + 1)

                inDegree[v] = inDegree.getValue(v) - 1
                if (inDegree.getValue(v) == 0) {
                    queue.add(v)
                }
            }
        }

        // Fallback for any disconnected components or complex cycles missed
        nodes.forEach { if (!ranks.containsKey(it)) ranks[it] = 0 }

        return ranks
    }

    // --- Step 3: Crossing Minimization (Barycenter Method) ---
    private fun minimizeCrossings(layers: List<MutableList<Long>>, adj: Map<Long, List<Long>>) {
        val reverseAdj = mutableMapOf<Long, MutableList<Long>>()
        adj.forEach { (u, neighbors) ->
            neighbors.forEach { v -> reverseAdj.getOrPut(v) { mutableListOf() }.add(u) }
        }

        val iterations = 4
        for (iter in 0 until iterations) {
            // Down sweep (Rank 1 -> Max)
            for (i in 1 until layers.size) {
                val layer = layers[i]
                val prevLayer = layers[i - 1]

                layer.sortBy { node ->
                    val parents = reverseAdj[node] ?: emptyList()
                    if (parents.isEmpty()) {
                        layer.indexOf(node).toFloat()
                    } else {
                        parents.map { prevLayer.indexOf(it) }.average().toFloat()
                    }
                }
            }
        }
    }

    // --- Step 4: Coordinate Assignment (Block Shifting with Dimensions) ---
    private fun assignXCoordinates(
        layers: List<List<Long>>,
        adj: Map<Long, List<Long>>,
        nodeData: Map<Long, GraphNode>
    ): MutableMap<Long, Float> {
        val xPositions = mutableMapOf<Long, Float>()

        // Build reverse adj for parent lookups
        val reverseAdj = mutableMapOf<Long, MutableList<Long>>()
        adj.forEach { (u, neighbors) ->
            neighbors.forEach { v -> reverseAdj.getOrPut(v) { mutableListOf() }.add(u) }
        }

        // 1. Initial Packing: Center nodes based on Widths
        layers.forEach { nodes ->
            var currentWidth = 0f
            // Calculate total width of the layer
            nodes.forEach { id ->
                val w = nodeData[id]?.width ?: 200f
                currentWidth += w + MIN_NODE_SEPARATION
            }
            if (nodes.isNotEmpty()) currentWidth -= MIN_NODE_SEPARATION

            var currentX = -(currentWidth / 2f)

            nodes.forEach { nodeId ->
                val w = nodeData[nodeId]?.width ?: 200f
                xPositions[nodeId] = currentX + (w / 2f) // Store Center X
                currentX += w + MIN_NODE_SEPARATION
            }
        }

        // 2. Block Shifting Heuristic
        // Shift nodes towards average parent X, but respect neighbor boundaries (Width Aware)
        for (i in 1 until layers.size) {
            val layer = layers[i]

            // A. Try to move each node to its barycenter
            layer.forEach { node ->
                val parents = reverseAdj[node]
                if (!parents.isNullOrEmpty()) {
                    val avgParentX = parents.mapNotNull { xPositions[it] }.average().toFloat()
                    val currentX = xPositions[node]!!

                    // Simple easing towards parent center
                    val newX = (currentX + avgParentX) / 2f
                    xPositions[node] = newX
                }
            }

            // B. Resolve Overlaps (Left-to-Right Sweep)
            // Re-sort layer based on new conceptual X to identify order
            val sortedLayer = layer.sortedBy { xPositions[it] }

            for (j in 1 until sortedLayer.size) {
                val leftNodeId = sortedLayer[j-1]
                val rightNodeId = sortedLayer[j]

                val leftNode = nodeData[leftNodeId]
                val rightNode = nodeData[rightNodeId]

                val leftX = xPositions[leftNodeId]!!
                val rightX = xPositions[rightNodeId]!!

                val leftHalfW = (leftNode?.width ?: 200f) / 2f
                val rightHalfW = (rightNode?.width ?: 200f) / 2f

                // Minimum distance between centers to avoid overlap
                val minSeparation = leftHalfW + rightHalfW + MIN_NODE_SEPARATION

                if (rightX < leftX + minSeparation) {
                    // Push right node
                    val newRightX = leftX + minSeparation
                    xPositions[rightNodeId] = newRightX
                }
            }
        }

        return xPositions
    }

    // --- Step 5: Assign Y based on Max Height per Layer ---
    private fun assignYCoordinates(
        layers: List<List<Long>>,
        xPositions: Map<Long, Float>,
        nodeData: Map<Long, GraphNode>
    ): Map<Long, Offset> {
        val positions = mutableMapOf<Long, Offset>()
        var currentY = 0f

        layers.forEach { layerNodes ->
            // Find max height in this rank to determine layer height
            val maxH = layerNodes.maxOfOrNull { nodeData[it]?.height ?: 100f } ?: 100f

            layerNodes.forEach { id ->
                val x = xPositions[id] ?: 0f
                // Center node vertically within the layer band?
                // Or Top align? Let's use Center for graph aesthetics.
                // Center of this layer band is currentY + maxH/2
                positions[id] = Offset(x, currentY + (maxH / 2f))
            }

            // Move cursor for next layer
            currentY += maxH + MIN_LAYER_SEPARATION
        }

        return positions
    }

    // --- Step 6: Direction Transformation ---
    private fun applyDirectionTransform(
        original: Map<Long, Offset>,
        direction: LayoutDirection
    ): Map<Long, Offset> {
        return original.mapValues { (_, pos) ->
            when (direction) {
                LayoutDirection.TOP_BOTTOM -> pos
                LayoutDirection.BOTTOM_TOP -> Offset(pos.x, -pos.y)
                LayoutDirection.LEFT_RIGHT -> Offset(pos.y, pos.x)
                LayoutDirection.RIGHT_LEFT -> Offset(-pos.y, pos.x)
            }
        }
    }
}