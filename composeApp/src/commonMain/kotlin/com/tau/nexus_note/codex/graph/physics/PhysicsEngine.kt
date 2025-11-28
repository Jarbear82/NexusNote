package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.codex.graph.GraphNode
import kotlin.math.sqrt

class PhysicsEngine() {

    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        if (nodes.isEmpty()) return emptyMap()

        val forces = mutableMapOf<Long, Offset>()
        // Create copies using the interface method
        val newNodes = nodes.mapValues { (_, node) -> node.copyNode() }

        // 1. Initialize forces
        for (node in newNodes.values) {
            forces[node.id] = Offset.Zero
        }

        // 2. Apply forces
        // 2a. Gravity (pull to center 0,0)
        for (node in newNodes.values) {
            // Do not apply forces to fixed/locked nodes
            if (node.isFixed || node.isLocked) continue

            val gravityForce = -node.pos * options.gravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // 2b. Repulsion (Barnes-Hut)
        // Bounds calc
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (node in newNodes.values) {
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
        for (node in newNodes.values) {
            quadTree.insert(node)
        }

        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) continue
            val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)
            forces[node.id] = forces[node.id]!! + repulsionForce
        }

        // 2c. Spring
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                // If BOTH are locked/fixed, no spring needed (they are static relative to each other)
                // If ONE is locked, the spring force still acts on the OTHER.

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

        // 3. FA2 Adaptive Speed
        var globalSwinging = 0f
        var globalTraction = 0f

        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) continue

            val currentForce = forces[node.id]!!
            val swingingVector = currentForce - node.oldForce
            node.swinging = swingingVector.getDistance()
            val tractionVector = currentForce + node.oldForce
            node.traction = tractionVector.getDistance() / 2f

            globalSwinging += node.mass * node.swinging
            globalTraction += node.mass * node.traction
            node.oldForce = currentForce
        }

        val globalSpeed = if (globalSwinging > 0) {
            options.tolerance * globalTraction / globalSwinging
        } else {
            0.1f
        }.coerceIn(0.01f, 10f)

        // 4. Update
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id]!!
            val acceleration = force / node.mass
            var newVel = node.vel + (acceleration * dt)
            newVel *= options.damping

            val localSpeed = if (node.swinging > 0) {
                (globalSpeed / (1f + globalSpeed * sqrt(node.swinging))).coerceAtLeast(0.01f)
            } else {
                globalSpeed
            }

            val displacement = newVel * (dt * localSpeed)
            node.pos += displacement
            node.vel = newVel
        }

        return newNodes
    }
}

private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}