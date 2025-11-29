package com.tau.nexus_note.codex.graph.physics

import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge

class PhysicsEngine {

    // The active solver strategy
    var solver: ForceSolver = BarnesHutSolver()

    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        if (nodes.isEmpty()) return emptyMap()

        // Delegate the entire simulation step to the specific solver
        return solver.update(nodes, edges, options, dt)
    }

    /**
     * Helper to switch solver based on options
     */
    fun setSolverType(type: SolverType) {
        if (getSolverType(solver) != type) {
            solver = when (type) {
                SolverType.BARNES_HUT -> BarnesHutSolver()
                SolverType.FORCE_ATLAS_2 -> ForceAtlas2Solver()
                SolverType.REPULSION -> RepulsionSolver()
                SolverType.HIERARCHICAL -> HierarchicalRepulsionSolver()
            }
        }
    }

    private fun getSolverType(solver: ForceSolver): SolverType {
        return when (solver) {
            is BarnesHutSolver -> SolverType.BARNES_HUT
            is ForceAtlas2Solver -> SolverType.FORCE_ATLAS_2
            is RepulsionSolver -> SolverType.REPULSION
            is HierarchicalRepulsionSolver -> SolverType.HIERARCHICAL
            else -> SolverType.BARNES_HUT
        }
    }
}