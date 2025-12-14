package com.tau.nexusnote.codex.graph.fcose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the fCoSE layout pipeline.
 */
class LayoutEngine(
    private val draftPhase: LayoutPhase,
    private val polishingPhase: LayoutPhase
) {

    private var constraintProcessor: ConstraintProcessor? = null

    // --- Granular Steps for UI Control ---

    fun randomize(graph: FcGraph) {
        println(">>> Randomizing Graph Positions")
        graph.nodes.forEach {
            if (!it.isFixed && !it.isCompound()) {
                // Randomize centered around 0,0 (Range: -400 to +400)
                it.x = (Math.random() - 0.5) * 800
                it.y = (Math.random() - 0.5) * 600
            }
        }
        graph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
    }

    suspend fun runDraft(graph: FcGraph, config: LayoutConfig) {
        println("\n>>> Phase I: Draft (Spectral)")
        graph.resetState()
        draftPhase.run(graph, config)
    }

    suspend fun runTransform(graph: FcGraph) {
        println("\n>>> Phase II-A: Transform (Procrustes/Reflection)")
        withContext(Dispatchers.Main) {
            constraintProcessor = ConstraintProcessor(graph)
            constraintProcessor?.applyTransformation(
                graph.fixedConstraints,
                graph.alignConstraints,
                graph.relativeConstraints
            )
        }
    }

    suspend fun runEnforce(graph: FcGraph) {
        println("\n>>> Phase II-B: Enforce (Snapping)")
        withContext(Dispatchers.Main) {
            if (constraintProcessor == null) constraintProcessor = ConstraintProcessor(graph)

            constraintProcessor?.enforceConstraints(
                graph.fixedConstraints,
                graph.alignConstraints,
                graph.relativeConstraints
            )
        }
    }

    suspend fun runPolishing(graph: FcGraph, config: LayoutConfig) {
        println("\n>>> Phase III: Polishing (Force-Directed)")
        polishingPhase.run(graph, config)
    }
}