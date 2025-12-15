package com.tau.nexusnote.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexusnote.datamodels.GraphEdge
import com.tau.nexusnote.datamodels.GraphNode
import kotlin.math.sqrt

class PhysicsEngine() {

    /**
     * Updates the positions and velocities of all nodes based on physics.
     * @param nodes The current map of nodes.
     * @param edges The list of edges.
     * @param options The *current* physics options from the settings UI.
     * @param dt The time delta (e.g., 16ms).
     * @return A new map of updated nodes.
     */
    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        if (nodes.isEmpty()) return emptyMap()

        val forces = mutableMapOf<Long, Offset>()
        // Create copies to modify
        val newNodes = nodes.mapValues { (_, node) -> node.copy() }

        // 1. Initialize forces
        for (node in newNodes.values) {
            forces[node.id] = Offset.Zero
        }

        // 2. Apply forces
        // 2a. Gravity (pull to center 0,0)
        for (node in newNodes.values) {
            // Do not apply gravity to fixed (dragged) nodes
            if (node.isFixed) continue

            val gravityForce = -node.pos * options.gravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // 2b. Repulsion (Barnes-Hut Optimization)
        // First, determine the boundaries of all nodes
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (node in newNodes.values) {
            if (node.pos.x < minX) minX = node.pos.x
            if (node.pos.x > maxX) maxX = node.pos.x
            if (node.pos.y < minY) minY = node.pos.y
            if (node.pos.y > maxY) maxY = node.pos.y
        }
        // Add padding to prevent nodes from being exactly on the edge
        val width = (maxX - minX) * 1.2f + 100f // +100f for empty graphs
        val height = (maxY - minY) * 1.2f + 100f
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val boundary = Rect(centerX - width/2, centerY - height/2, centerX + width/2, centerY + height/2)

        // Build the QuadTree
        val quadTree = QuadTree(boundary)
        for (node in newNodes.values) {
            // Phase 5 Tuning: Lower repulsion mass of Hypernodes so they don't push 'real' nodes too aggressively
            val massForRepulsion = if (node.isHyperNode) 0.1f else node.mass
            // We create a temporary copy just for insertion to affect the tree calculation
            quadTree.insert(node.copy(mass = massForRepulsion))
        }

        // Calculate repulsion force for each node
        for (node in newNodes.values) {
            // Do not apply repulsion to fixed nodes (but they still repel others)
            if (node.isFixed) continue

            val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)
            forces[node.id] = forces[node.id]!! + repulsionForce
        }


        // 2c. Spring (from edges) (Hooke's Law)
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                // Do not apply spring forces if *either* node is fixed
                if (nodeA.isFixed || nodeB.isFixed) continue

                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue

                // The "ideal" length of the spring is the sum of radii + a buffer
                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)

                // Phase 5 Tuning: Increase spring strength for edges connected to Hypernodes
                // This keeps the n-nary cluster tight.
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

        // 3. Calculate ForceAtlas2 Adaptive Speed (Swinging & Traction)
        var globalSwinging = 0f
        var globalTraction = 0f

        for (node in newNodes.values) {
            if (node.isFixed) continue

            val currentForce = forces[node.id]!!

            // 3a. Calculate Swinging (how much the force vector changed direction)
            val swingingVector = currentForce - node.oldForce
            node.swinging = swingingVector.getDistance()

            // 3b. Calculate Traction (how much force is in a consistent direction)
            val tractionVector = currentForce + node.oldForce
            node.traction = tractionVector.getDistance() / 2f

            // 3c. Sum global values (weighted by mass, which is deg+1)
            globalSwinging += node.mass * node.swinging
            globalTraction += node.mass * node.traction

            // 3d. Store current force for next frame
            node.oldForce = currentForce
        }

        // 3e. Calculate Global Speed
        // This is the "adaptive cooling" part from FA2 paper
        val globalSpeed = if (globalSwinging > 0) {
            options.tolerance * globalTraction / globalSwinging
        } else {
            0.1f // Default speed if no movement
        }.coerceIn(0.01f, 10f) // Add min/max caps


        // 4. Update velocities and positions (Euler integration)
        for (node in newNodes.values) {
            // If node is fixed, set velocity to zero and skip position update
            if (node.isFixed) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id]!!

            // F = ma -> a = F/m
            val acceleration = force / node.mass

            // v = v0 + at
            var newVel = node.vel + (acceleration * dt)

            // Apply damping
            newVel *= options.damping

            // Apply FA2 Adaptive Speed
            // 4a. Calculate Local Speed (slows down swinging nodes)
            val localSpeed = if (node.swinging > 0) {
                (globalSpeed / (1f + globalSpeed * sqrt(node.swinging))).coerceAtLeast(0.01f)
            } else {
                globalSpeed
            }

            // 4b. Apply displacement modulated by local speed
            // We use the localSpeed as a multiplier on the time-step (dt * localSpeed)
            val displacement = newVel * (dt * localSpeed)
            val newPos = node.pos + displacement

            // Update the node in the map
            node.vel = newVel
            node.pos = newPos
        }

        return newNodes
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