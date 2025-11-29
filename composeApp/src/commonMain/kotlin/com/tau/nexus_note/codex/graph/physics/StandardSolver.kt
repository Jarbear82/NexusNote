package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge

/**
 * The default implementation of the physics logic.
 * Uses:
 * 1. Central Gravity
 * 2. Barnes-Hut QuadTree for Repulsion (O(n log n))
 * 3. Hooke's Law for Springs
 */
class StandardSolver : ForceSolver {

    override fun applyForces(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions
    ): Map<Long, Offset> {
        val forces = mutableMapOf<Long, Offset>()

        // Initialize forces
        for (nodeId in nodes.keys) {
            forces[nodeId] = Offset.Zero
        }

        // --- 1. Apply Gravity (pull to center 0,0) ---
        for (node in nodes.values) {
            if (node.isFixed || node.isLocked) continue
            val gravityForce = -node.pos * options.gravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // --- 2. Apply Repulsion (Barnes-Hut) ---
        // Calculate bounds for QuadTree
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY

        for (node in nodes.values) {
            if (node.pos.x < minX) minX = node.pos.x
            if (node.pos.x > maxX) maxX = node.pos.x
            if (node.pos.y < minY) minY = node.pos.y
            if (node.pos.y > maxY) maxY = node.pos.y
        }

        val width = (maxX - minX) * 1.2f + 100f
        val height = (maxY - minY) * 1.2f + 100f
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val boundary = Rect(centerX - width/2, centerY - height/2, centerX + width/2, centerY + height/2)

        val quadTree = QuadTree(boundary)
        for (node in nodes.values) {
            quadTree.insert(node)
        }

        for (node in nodes.values) {
            if (node.isFixed || node.isLocked) continue
            val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)
            forces[node.id] = forces[node.id]!! + repulsionForce
        }

        // --- 3. Apply Springs (Edges) ---
        for (edge in edges) {
            val nodeA = nodes[edge.sourceId]
            val nodeB = nodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue

                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)
                val displacement = dist - idealLength
                val springForce = delta.normalized() * displacement * options.spring * edge.strength

                if (!nodeA.isFixed && !nodeA.isLocked) {
                    forces[nodeA.id] = forces[nodeA.id]!! + springForce
                }
                if (!nodeB.isFixed && !nodeB.isLocked) {
                    forces[nodeB.id] = forces[nodeB.id]!! - springForce
                }
            }
        }

        return forces
    }

    private fun Offset.normalized(): Offset {
        val mag = this.getDistance()
        return if (mag == 0f) Offset.Zero else this / mag
    }
}