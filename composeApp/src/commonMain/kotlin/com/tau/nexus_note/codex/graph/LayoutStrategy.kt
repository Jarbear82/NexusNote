package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.doc_parser.StandardSchemas

object LayoutStrategy {

    private const val LAYER_HEIGHT = 300f
    private const val NODE_SPACING_X = 400f // Increased from 350f to accommodate wider nodes
    private const val RIB_SPACING = 300f // Increased from 200f to prevent overlap in orphan grid
    private const val START_X = 0f
    private const val START_Y = 0f

    /**
     * Calculates the initial position for all nodes based on a Spine (Tree) layout.
     * Documents are roots. Sections/Blocks are children. Ribs are placed below/around.
     */
    fun calculateInitialPositions(
        nodes: List<NodeDisplayItem>,
        edges: List<EdgeDisplayItem>
    ): Map<Long, Offset> {
        val positions = mutableMapOf<Long, Offset>()
        val visited = mutableSetOf<Long>()

        // 1. Build Adjacency for the Spine only
        val spineChildren = mutableMapOf<Long, MutableList<Long>>()
        val childNodeIds = mutableSetOf<Long>()

        edges.filter { it.label == StandardSchemas.EDGE_CONTAINS }.forEach { edge ->
            // Sort later by 'order' property if available (requires EdgeDisplayItem to have props, currently implied by list order)
            spineChildren.getOrPut(edge.src.id) { mutableListOf() }.add(edge.dst.id)
            childNodeIds.add(edge.dst.id)
        }

        // 2. Identify Topological Roots (Tree Roots)
        // Any node that has children (is a parent) BUT is not a child itself (in-degree 0 for CONTAINS).
        // This ensures we capture Headings, Groups, etc., as valid roots even if they aren't labeled "Title".
        val treeRoots = nodes.filter { node ->
            !childNodeIds.contains(node.id) && spineChildren.containsKey(node.id)
        }

        var currentXCursor = START_X

        // 3. Process each Document Tree
        treeRoots.forEach { root ->
            val subtreeWidth = layoutTree(
                nodeId = root.id,
                x = currentXCursor,
                y = START_Y,
                spineChildren = spineChildren,
                positions = positions,
                visited = visited
            )
            currentXCursor += subtreeWidth + NODE_SPACING_X
        }

        // 4. Place remaining nodes (Singletons, Tags, Attachments, Orphans)
        // Group them by type for cleaner layout
        var ribX = START_X
        var ribY = currentXCursor + 500f // Place far below or to the side

        nodes.filter { !visited.contains(it.id) }.forEachIndexed { index, node ->
            val col = index % 10
            val row = index / 10
            positions[node.id] = Offset(
                START_X + (col * RIB_SPACING),
                ribY + (row * RIB_SPACING)
            )
        }

        return positions
    }

    /**
     * Recursive function to layout tree. Returns total width of the subtree.
     */
    private fun layoutTree(
        nodeId: Long,
        x: Float,
        y: Float,
        spineChildren: Map<Long, List<Long>>,
        positions: MutableMap<Long, Offset>,
        visited: MutableSet<Long>
    ): Float {
        positions[nodeId] = Offset(x, y)
        visited.add(nodeId)

        val children = spineChildren[nodeId] ?: emptyList()
        if (children.isEmpty()) {
            return NODE_SPACING_X // Base width of a leaf node
        }

        var totalWidth = 0f
        var childX = x

        children.forEach { childId ->
            // Cycle Guard: Only layout child if not already visited
            if (!visited.contains(childId)) {
                val childWidth = layoutTree(
                    nodeId = childId,
                    x = childX,
                    y = y + LAYER_HEIGHT,
                    spineChildren = spineChildren,
                    positions = positions,
                    visited = visited
                )
                childX += childWidth
                totalWidth += childWidth
            }
        }

        // Center parent over children?
        // For this design (left-aligned indentation flow), parent at X, children at X is fine,
        // but typically parents are centered. Let's keep it simple:
        // Parent aligns with first child to create that "document outline" look.

        // Ensure parent has at least some width if children were all visited (cycles)
        return if (totalWidth == 0f) NODE_SPACING_X else totalWidth
    }
}