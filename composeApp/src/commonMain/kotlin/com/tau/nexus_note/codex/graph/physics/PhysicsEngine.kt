package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge
import kotlin.math.sqrt

class PhysicsEngine {

    // Swappable solver. Defaults to the Standard (Barnes-Hut) implementation.
    var solver: ForceSolver = StandardSolver()

    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        if (nodes.isEmpty()) return emptyMap()

        // Create copies using the interface method to ensure immutability of the previous state
        val newNodes = nodes.mapValues { (_, node) -> node.copyNode() }

        // 1. Delegate Force Calculation to the Solver
        val forces = solver.applyForces(newNodes, edges, options)

        // 2. FA2 Adaptive Speed Heuristics
        var globalSwinging = 0f
        var globalTraction = 0f

        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) continue

            val currentForce = forces[node.id] ?: Offset.Zero

            // Measure how much the force vector has changed direction (Swinging)
            val swingingVector = currentForce - node.oldForce
            node.swinging = swingingVector.getDistance()

            // Measure how much the force vector persists in the same direction (Traction)
            val tractionVector = currentForce + node.oldForce
            node.traction = tractionVector.getDistance() / 2f

            globalSwinging += node.mass * node.swinging
            globalTraction += node.mass * node.traction

            // Store force for next frame's comparison
            node.oldForce = currentForce
        }

        // Calculate Global Speed based on system stability
        val globalSpeed = if (globalSwinging > 0) {
            options.tolerance * globalTraction / globalSwinging
        } else {
            0.1f
        }.coerceIn(0.01f, 10f)

        // 3. Integration Step (Update Positions)
        for (node in newNodes.values) {
            if (node.isFixed || node.isLocked) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id] ?: Offset.Zero
            val acceleration = force / node.mass

            // Simple Euler integration with Damping
            var newVel = node.vel + (acceleration * dt)
            newVel *= options.damping

            // Apply adaptive local speed to the displacement
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