package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge

/**
 * Standard implementation using Barnes-Hut QuadTree optimization.
 * Uses standard Euler integration with constant damping.
 * Good balance of performance and stability.
 */
class BarnesHutSolver : ForceSolver {

    override fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        // 1. Copy nodes for immutability
        val newNodes = nodes.mapValues { (_, node) -> node.copyNode() }

        // 2. Initialize Forces
        val forces = mutableMapOf<Long, Offset>()
        newNodes.keys.forEach { forces[it] = Offset.Zero }

        // 3. Build QuadTree
        val bounds = calculateBounds(newNodes.values)
        val quadTree = QuadTree(bounds)
        newNodes.values.forEach { quadTree.insert(it) }

        // 4. Calculate Forces
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) continue

            // Gravity
            val gravityForce = -node.pos * options.gravity * node.mass

            // Repulsion (Barnes-Hut)
            val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)

            forces[node.id] = forces[node.id]!! + gravityForce + repulsionForce
        }

        // Springs
        applySprings(newNodes, edges, options, forces)

        // 5. Integrate (Standard Euler)
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id] ?: Offset.Zero
            val acceleration = force / node.mass

            node.vel = (node.vel + (acceleration * dt)) * options.damping
            node.pos += node.vel * dt
        }

        return newNodes
    }

    private fun applySprings(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        forces: MutableMap<Long, Offset>
    ) {
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

                if (!nodeA.isFixed && !nodeA.isLocked) forces[nodeA.id] = forces[nodeA.id]!! + springForce
                if (!nodeB.isFixed && !nodeB.isLocked) forces[nodeB.id] = forces[nodeB.id]!! - springForce
            }
        }
    }

    private fun calculateBounds(nodes: Collection<GraphNode>): Rect {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY

        for (node in nodes) {
            if (node.pos.x < minX) minX = node.pos.x
            if (node.pos.x > maxX) maxX = node.pos.x
            if (node.pos.y < minY) minY = node.pos.y
            if (node.pos.y > maxY) maxY = node.pos.y
        }

        // Pad bounds to ensure nodes don't drift out immediately
        val width = (maxX - minX) * 1.2f + 100f
        val height = (maxY - minY) * 1.2f + 100f
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        return Rect(centerX - width/2, centerY - height/2, centerX + width/2, centerY + height/2)
    }

    private fun Offset.normalized(): Offset {
        val mag = this.getDistance()
        return if (mag == 0f) Offset.Zero else this / mag
    }
}