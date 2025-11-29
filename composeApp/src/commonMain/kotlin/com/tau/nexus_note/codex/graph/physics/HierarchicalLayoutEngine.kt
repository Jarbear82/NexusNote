package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.settings.LayoutDirection
import kotlin.math.max

object HierarchicalLayout {

    private const val LAYER_SEPARATION = 300f
    private const val NODE_SEPARATION = 250f

    /**
     * Calculates positions for a Generic Hierarchical Layout (Sugiyama Framework).
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

        // 4. Coordinate Assignment
        // Assign X coordinates based on order + "Block Shifting" heuristic
        val positions = assignCoordinates(layers, forwardEdges)

        // 5. Transform based on Direction
        return applyDirectionTransform(positions, direction)
    }

    // --- Step 1: Cycle Removal (DFS) ---
    // Returns an adjacency map representing the DAG
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
        // Build reverse adj for parent lookups
        val reverseAdj = mutableMapOf<Long, MutableList<Long>>()
        adj.forEach { (u, neighbors) ->
            neighbors.forEach { v -> reverseAdj.getOrPut(v) { mutableListOf() }.add(u) }
        }

        // Iterative Sweeps (Down-Up-Down)
        val iterations = 4
        for (iter in 0 until iterations) {
            // Down sweep (Rank 1 -> Max)
            for (i in 1 until layers.size) {
                val layer = layers[i]
                val prevLayer = layers[i - 1]

                // Sort by average position of parents
                layer.sortBy { node ->
                    val parents = reverseAdj[node] ?: emptyList()
                    if (parents.isEmpty()) {
                        // Keep relative order or use current index
                        layer.indexOf(node).toFloat()
                    } else {
                        parents.map { prevLayer.indexOf(it) }.average().toFloat()
                    }
                }
            }
            // (Optional) Up sweep could be added here for better quality
        }
    }

    // --- Step 4: Coordinate Assignment (Block Shifting Simplified) ---
    private fun assignCoordinates(layers: List<List<Long>>, adj: Map<Long, List<Long>>): Map<Long, Offset> {
        val positions = mutableMapOf<Long, Offset>()

        // Build reverse adj for parent lookups
        val reverseAdj = mutableMapOf<Long, MutableList<Long>>()
        adj.forEach { (u, neighbors) ->
            neighbors.forEach { v -> reverseAdj.getOrPut(v) { mutableListOf() }.add(u) }
        }

        // 1. Initial Packing: Center the layers
        layers.forEachIndexed { rank, nodes ->
            var currentWidth = 0f
            nodes.forEach { _ -> currentWidth += NODE_SEPARATION }

            var startX = -(currentWidth / 2f) + (NODE_SEPARATION / 2f)

            nodes.forEach { nodeId ->
                positions[nodeId] = Offset(startX, rank * LAYER_SEPARATION)
                startX += NODE_SEPARATION
            }
        }

        // 2. Block Shifting Heuristic
        // Shift nodes towards average parent X, but respect neighbor boundaries
        for (i in 1 until layers.size) {
            val layer = layers[i]

            // Try to move each node to its barycenter
            layer.forEach { node ->
                val parents = reverseAdj[node]
                if (!parents.isNullOrEmpty()) {
                    val avgParentX = parents.mapNotNull { positions[it]?.x }.average().toFloat()
                    val currentPos = positions[node]!!

                    // Simple easing towards parent center
                    val newX = (currentPos.x + avgParentX) / 2f
                    positions[node] = currentPos.copy(x = newX)
                }
            }

            // 3. Resolve Overlaps (Compact)
            // After shifting, ensure nodes satisfy minimum separation
            layer.sortedBy { positions[it]?.x }.forEachIndexed { idx, node ->
                if (idx > 0) {
                    val prevNode = layer[idx-1] // Actually need to access sorted list by index logic
                    // Since we are iterating the *sorted* list, let's just grab the previous item in this iteration
                    // Wait, we need the ID of the previous item in the *sorted* order.
                    // Let's rely on the fact that sort order doesn't flip drastically with slight nudges.

                    // Correct approach: Re-sort based on new X, then enforce spacing
                }
            }

            // Re-sort layer by new X to enforce spacing left-to-right
            val sortedLayer = layer.sortedBy { positions[it]?.x }

            // Left-to-right pass
            for (j in 1 until sortedLayer.size) {
                val leftNode = sortedLayer[j-1]
                val rightNode = sortedLayer[j]
                val leftX = positions[leftNode]!!.x
                val rightX = positions[rightNode]!!.x

                if (rightX < leftX + NODE_SEPARATION) {
                    val newRightX = leftX + NODE_SEPARATION
                    positions[rightNode] = positions[rightNode]!!.copy(x = newRightX)
                }
            }
        }

        return positions
    }

    // --- Step 5: Direction Transformation ---
    private fun applyDirectionTransform(
        original: Map<Long, Offset>,
        direction: LayoutDirection
    ): Map<Long, Offset> {
        return original.mapValues { (_, pos) ->
            when (direction) {
                LayoutDirection.TOP_BOTTOM -> pos // Default (x, y)
                LayoutDirection.BOTTOM_TOP -> Offset(pos.x, -pos.y) // Flip Y
                LayoutDirection.LEFT_RIGHT -> Offset(pos.y, pos.x) // Swap X/Y
                LayoutDirection.RIGHT_LEFT -> Offset(-pos.y, pos.x) // Swap X/Y and flip new X
            }
        }
    }
}