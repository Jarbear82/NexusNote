package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.codex.graph.HeadingGraphNode
import com.tau.nexus_note.datamodels.GraphEdge
import kotlin.math.abs

/**
 * A specialized solver that enforces a strict "Swimlane" tree structure.
 * - Y-Axis: Fixed (or constrained) based on node Hierarchy/Level.
 * - X-Axis: Simulated using Repulsion and Springs to space out siblings.
 */
class HierarchicalRepulsionSolver : ForceSolver {

    override fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        val newNodes = nodes.mapValues { (_, node) -> node.copyNode() }
        val forcesX = mutableMapOf<Long, Float>()
        newNodes.keys.forEach { forcesX[it] = 0f }

        // 1. Calculate Forces (Horizontal Only)
        // Repulsion: Only push away neighbors on the same level (or close Y)
        val nodeList = newNodes.values.toList()
        for (i in nodeList.indices) {
            val nodeA = nodeList[i]
            for (j in (i + 1) until nodeList.size) {
                val nodeB = nodeList[j]

                // Only interact if they are vertically close (same level band)
                if (abs(nodeA.pos.y - nodeB.pos.y) < 200f) {
                    val dx = nodeA.pos.x - nodeB.pos.x
                    // Avoid division by zero
                    val dist = if (abs(dx) < 1f) 1f else abs(dx)

                    val force = (options.repulsion * 50f) / dist // Simplified 1D repulsion
                    val sign = if (dx > 0) 1f else -1f

                    if (!nodeA.isFixed && !nodeA.isLocked) forcesX[nodeA.id] = forcesX[nodeA.id]!! + (force * sign)
                    if (!nodeB.isFixed && !nodeB.isLocked) forcesX[nodeB.id] = forcesX[nodeB.id]!! - (force * sign)
                }
            }
        }

        // Springs: Pull connected nodes horizontally towards each other
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]
            if (nodeA != null && nodeB != null) {
                val dx = nodeB.pos.x - nodeA.pos.x
                // We want them to align vertically (x-diff = 0) or have small offset
                val springForce = dx * options.spring

                if (!nodeA.isFixed && !nodeA.isLocked) forcesX[nodeA.id] = forcesX[nodeA.id]!! + springForce
                if (!nodeB.isFixed && !nodeB.isLocked) forcesX[nodeB.id] = forcesX[nodeB.id]!! - springForce
            }
        }

        // Center Gravity (Horizontal only)
        for (node in newNodes.values) {
            if (!node.isFixed && !node.isLocked) {
                forcesX[node.id] = forcesX[node.id]!! - (node.pos.x * options.gravity * 0.5f)
            }
        }

        // 2. Integration
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) {
                node.vel = Offset.Zero
                continue
            }

            // X-Axis: Standard physics
            val accX = forcesX[node.id]!! / node.mass
            val newVelX = (node.vel.x + accX * dt) * options.damping
            val newPosX = node.pos.x + newVelX * dt

            // Y-Axis: Strict Constraint
            // If the node has a defined level (HeadingGraphNode), snap to it.
            // Otherwise, maintain current Y (assume initial layout placed it correctly).
            var targetY = node.pos.y
            if (node is HeadingGraphNode) {
                // Example: Level 1 at -300, Level 2 at 0, etc.
                // Or just trust the initial Y
            }

            // Dampen Y velocity aggressively (force alignment)
            val newVelY = node.vel.y * 0.1f
            val newPosY = targetY + newVelY * dt

            node.vel = Offset(newVelX, newVelY)
            node.pos = Offset(newPosX, newPosY)
        }

        return newNodes
    }
}