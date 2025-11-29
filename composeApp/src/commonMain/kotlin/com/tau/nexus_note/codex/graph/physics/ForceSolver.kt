package com.tau.nexus_note.codex.graph.physics

import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge

/**
 * Contract for a physics solver.
 * Responsible for calculating forces AND integrating them to update node positions.
 */
interface ForceSolver {
    /**
     * Executes one step of the simulation.
     *
     * @param nodes The current state of all nodes.
     * @param edges The list of connections.
     * @param options Physics parameters.
     * @param dt Delta time (seconds).
     * @return A new map of nodes with updated positions and velocities.
     */
    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode>
}