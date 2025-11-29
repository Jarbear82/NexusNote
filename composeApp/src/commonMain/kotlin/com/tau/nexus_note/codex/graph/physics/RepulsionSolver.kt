package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge

/**
 * Simple O(N^2) Solver.
 * Iterates every pair of nodes to calculate repulsion.
 * Accurate but slow. Best for graphs < 100 nodes.
 */
class RepulsionSolver : ForceSolver {

    override fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        val newNodes = nodes.mapValues { (_, node) -> node.copyNode() }
        val forces = mutableMapOf<Long, Offset>()
        newNodes.keys.forEach { forces[it] = Offset.Zero }
        val nodeList = newNodes.values.toList()

        // 1. All-Pairs Repulsion (O(N^2))
        for (i in nodeList.indices) {
            val nodeA = nodeList[i]
            for (j in (i + 1) until nodeList.size) {
                val nodeB = nodeList[j]

                val delta = nodeA.pos - nodeB.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue // Ignore overlap for simple logic

                // Force = k * m1 * m2 / dist
                val repulsion = delta.normalized() * (options.repulsion * nodeA.mass * nodeB.mass / dist)

                if (!nodeA.isFixed && !nodeA.isLocked) forces[nodeA.id] = forces[nodeA.id]!! + repulsion
                if (!nodeB.isFixed && !nodeB.isLocked) forces[nodeB.id] = forces[nodeB.id]!! - repulsion
            }
        }

        // 2. Springs
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]
            if (nodeA != null && nodeB != null) {
                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 2)

                val displacement = dist - idealLength
                val springForce = delta.normalized() * displacement * options.spring * edge.strength

                if (!nodeA.isFixed && !nodeA.isLocked) forces[nodeA.id] = forces[nodeA.id]!! + springForce
                if (!nodeB.isFixed && !nodeB.isLocked) forces[nodeB.id] = forces[nodeB.id]!! - springForce
            }
        }

        // 3. Central Gravity
        for (node in newNodes.values) {
            if (!node.isFixed && !node.isLocked) {
                val gravity = -node.pos * options.gravity * node.mass
                forces[node.id] = forces[node.id]!! + gravity
            }
        }

        // 4. Integration
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) {
                node.vel = Offset.Zero
                continue
            }
            val force = forces[node.id] ?: Offset.Zero
            val acc = force / node.mass
            node.vel = (node.vel + acc * dt) * options.damping
            node.pos += node.vel * dt
        }

        return newNodes
    }

    private fun Offset.normalized(): Offset {
        val mag = this.getDistance()
        return if (mag == 0f) Offset.Zero else this / mag
    }
}