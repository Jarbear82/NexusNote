package com.tau.nexusnote.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexusnote.datamodels.GraphEdge
import com.tau.nexusnote.datamodels.GraphNode
import kotlin.math.sqrt

class PhysicsEngine() {
    private val forces = mutableMapOf<Long, Offset>()

    /**
     * Updates the positions and velocities of all nodes based on physics.
     * MUTATES the nodes in the map.
     * @param nodes The current map of nodes.
     * @param edges The list of edges.
     * @param options The *current* physics options from the settings UI.
     * @param dt The time delta (e.g., 16ms).
     */
    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ) {
        if (nodes.isEmpty()) return

        // 1. Initialize forces
        forces.clear()
        for (nodeId in nodes.keys) {
            forces[nodeId] = Offset.Zero
        }

        // 2. Apply forces
        // 2a. Gravity (pull to center 0,0)
        for (node in nodes.values) {
            if (node.isFixed || node.isHyperNode) continue

            val gravityForce = -node.pos * options.gravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // 2b. Repulsion (Barnes-Hut Optimization)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
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

        val quadTree = QuadTree.acquire(boundary)
        try {
            // 1. Pre-adjust masses for hypernodes
            for (node in nodes.values) {
                if (node.isHyperNode) {
                    node.mass = 0.1f
                }
            }

            // 2. Build QuadTree
            for (node in nodes.values) {
                quadTree.insert(node)
            }

            // 3. Apply Repulsion
            for (node in nodes.values) {
                if (node.isFixed) continue
                val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)
                forces[node.id] = forces[node.id]!! + repulsionForce
            }

            // 4. Restore masses (Hypernodes have a default mass of 1.0f in pushUiUpdate, but we should ideally store it if it was different)
            // Looking at GraphViewmodel.pushUiUpdate, HyperNodes are always 1.0f mass.
            for (node in nodes.values) {
                if (node.isHyperNode) {
                    node.mass = 1.0f
                }
            }
        } finally {
            QuadTree.release(quadTree)
        }


        // 2c. Spring (from edges) (Hooke's Law)
        for (edge in edges) {
            val nodeA = nodes[edge.sourceId]
            val nodeB = nodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue

                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)
                var strengthMultiplier = 1.0f
                if (nodeA.isHyperNode || nodeB.isHyperNode) {
                    strengthMultiplier = 3.0f
                }

                val displacement = dist - idealLength
                val springForce = delta.normalized() * displacement * options.spring * edge.strength * strengthMultiplier

                forces[nodeA.id] = forces[nodeA.id]!! + springForce
                forces[nodeB.id] = forces[nodeB.id]!! - springForce
            }
        }

        // 3. Calculate ForceAtlas2 Adaptive Speed
        var globalSwinging = 0f
        var globalTraction = 0f

        for (node in nodes.values) {
            if (node.isFixed) continue

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


        // 4. Update velocities and positions
        for (node in nodes.values) {
            if (node.isFixed) {
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
            node.vel = newVel
            node.pos = node.pos + displacement
        }
    }
}

// Helper for unique pairs (n=2)
private fun <T> List<T>.combinations(n: Int = 2): Sequence<Pair<T, T>> {
    if (n != 2) throw IllegalArgumentException("Only n=2 is supported for pair combinations")
    return sequence {
        for (i in 0 until this@combinations.size - 1) {
            for (j in i + 1 until this@combinations.size) {
                yield(this@combinations[i] to this@combinations[j])
            }
        }
    }
}

// Helper to normalize offset
private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}