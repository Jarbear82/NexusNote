package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge

/**
 * Common contract for calculating forces in the physics simulation.
 * Implementations can define different behaviors (e.g., Barnes-Hut, Brute Force, Grid-based).
 */
interface ForceSolver {
    /**
     * Calculates the net force vector for every node in the graph based on the implementation's rules.
     *
     * @param nodes The current state of all nodes.
     * @param edges The list of connections between nodes.
     * @param options Configuration parameters (gravity, repulsion strength, etc.).
     * @return A map associating each Node ID with its calculated Force vector (Offset).
     */
    fun applyForces(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions
    ): Map<Long, Offset>
}