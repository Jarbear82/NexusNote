package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge
import kotlin.math.abs

/**
 * A specialized solver that enforces a strict "Swimlane" tree structure.
 * - Y-Axis: Heavily constrained to maintain rank.
 * - X-Axis: Simulated using Repulsion (Rectangular Aware) and Springs.
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
        val nodeList = newNodes.values.toList()
        for (i in nodeList.indices) {
            val nodeA = nodeList[i]
            for (j in (i + 1) until nodeList.size) {
                val nodeB = nodeList[j]

                // Only interact if they are vertically close (same level band)
                if (abs(nodeA.pos.y - nodeB.pos.y) < 200f) {
                    val dx = nodeA.pos.x - nodeB.pos.x
                    val absDx = abs(dx)

                    // --- Dimension Aware Repulsion ---
                    val halfWA = nodeA.width / 2f
                    val halfWB = nodeB.width / 2f
                    // Minimum distance required between centers to NOT overlap
                    val minSep = halfWA + halfWB + (options.minDistance * 2)

                    if (absDx < minSep) {
                        // Hard overlap or very close
                        val overlap = minSep - absDx
                        val sign = if (dx > 0) 1f else -1f
                        // Strong force to separate
                        val force = options.repulsion * 5f * overlap * sign

                        if (!nodeA.isFixed && !nodeA.isLocked) forcesX[nodeA.id] = forcesX[nodeA.id]!! + force
                        if (!nodeB.isFixed && !nodeB.isLocked) forcesX[nodeB.id] = forcesX[nodeB.id]!! - force
                    } else {
                        // Standard Repulsion (decaying)
                        val dist = absDx
                        val forceVal = (options.repulsion * 10f) / dist
                        val sign = if (dx > 0) 1f else -1f

                        if (!nodeA.isFixed && !nodeA.isLocked) forcesX[nodeA.id] = forcesX[nodeA.id]!! + (forceVal * sign)
                        if (!nodeB.isFixed && !nodeB.isLocked) forcesX[nodeB.id] = forcesX[nodeB.id]!! - (forceVal * sign)
                    }
                }
            }
        }

        // Springs: Pull connected nodes horizontally towards each other (Alignment)
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
            // Dampen Y velocity aggressively to prevent drifting out of rank
            val newVelY = node.vel.y * 0.05f
            val newPosY = node.pos.y + newVelY * dt

            node.vel = Offset(newVelX, newVelY)
            node.pos = Offset(newPosX, newPosY)
        }

        return newNodes
    }
}