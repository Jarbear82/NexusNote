package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge
import kotlin.math.sqrt

/**
 * Implementation of ForceAtlas2 Logic.
 * Features:
 * - Adaptive Speed (Swinging/Traction) for faster convergence.
 * - Barnes-Hut Repulsion.
 * - Stronger Gravity logic.
 */
class ForceAtlas2Solver : ForceSolver {

    override fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        val newNodes = nodes.mapValues { (_, node) -> node.copyNode() }
        val forces = mutableMapOf<Long, Offset>()
        newNodes.keys.forEach { forces[it] = Offset.Zero }

        // --- 1. Forces Calculation ---

        // QuadTree for Repulsion
        val bounds = calculateBounds(newNodes.values)
        val quadTree = QuadTree(bounds)
        newNodes.values.forEach { quadTree.insert(it) }

        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) continue

            // Gravity (ForceAtlas2 style: Linear attraction to center)
            // F = -g * m * pos
            val gravityForce = -node.pos * options.gravity * node.mass

            // Repulsion
            val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)

            forces[node.id] = forces[node.id]!! + gravityForce + repulsionForce
        }

        // Springs
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]
            if (nodeA != null && nodeB != null) {
                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist > 0f) {
                    // FA2 often uses Logarithmic attraction, but we'll stick to
                    // Hooke's law with distance offset to maintain style consistency
                    val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)
                    val displacement = dist - idealLength
                    val springForce = delta.normalized() * displacement * options.spring * edge.strength

                    if (!nodeA.isFixed && !nodeA.isLocked) forces[nodeA.id] = forces[nodeA.id]!! + springForce
                    if (!nodeB.isFixed && !nodeB.isLocked) forces[nodeB.id] = forces[nodeB.id]!! - springForce
                }
            }
        }

        // --- 2. Adaptive Speed & Integration ---

        var globalSwinging = 0f
        var globalTraction = 0f

        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) continue

            val currentForce = forces[node.id] ?: Offset.Zero

            // Swinging: How much the force vector changed direction
            val swingingVector = currentForce - node.oldForce
            node.swinging = swingingVector.getDistance()

            // Traction: How much the force vector persists
            val tractionVector = currentForce + node.oldForce
            node.traction = tractionVector.getDistance() / 2f

            globalSwinging += node.mass * node.swinging
            globalTraction += node.mass * node.traction

            // Store force for next frame
            node.oldForce = currentForce
        }

        // Calculate Global Speed
        val globalSpeed = if (globalSwinging > 0) {
            options.tolerance * globalTraction / globalSwinging
        } else {
            0.1f
        }.coerceIn(0.01f, 10f)

        // Apply Integration
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id] ?: Offset.Zero

            // Speed adaptation for this node
            val localSpeed = if (node.swinging > 0) {
                (globalSpeed / (1f + globalSpeed * sqrt(node.swinging))).coerceAtLeast(0.01f)
            } else {
                globalSpeed
            }

            // Apply displacement
            // Note: FA2 technically calculates displacement directly, not velocity accumulation
            // but we map it to velocity for compatibility with the engine loop structure
            val displacement = force * localSpeed

            node.vel = displacement / dt // Store effective velocity
            node.pos += displacement * dt
        }

        return newNodes
    }

    private fun calculateBounds(nodes: Collection<GraphNode>): Rect {
        if (nodes.isEmpty()) return Rect.Zero
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (node in nodes) {
            if (node.pos.x < minX) minX = node.pos.x
            if (node.pos.x > maxX) maxX = node.pos.x
            if (node.pos.y < minY) minY = node.pos.y
            if (node.pos.y > maxY) maxY = node.pos.y
        }
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