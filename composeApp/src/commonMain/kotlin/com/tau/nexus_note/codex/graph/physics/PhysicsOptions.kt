package com.tau.nexus_note.codex.graph.physics

import kotlinx.serialization.Serializable

@Serializable
enum class SolverType(val displayName: String) {
    BARNES_HUT("Barnes-Hut (Standard)"),
    FORCE_ATLAS_2("Force Atlas 2 (Adaptive)"),
    REPULSION("Repulsion (Small Graph)"),
    HIERARCHICAL("Hierarchical (Tree)")
}

/**
 * Data class to hold all the constants for the physics simulation.
 */
@Serializable
data class PhysicsOptions(
    val solver: SolverType = SolverType.BARNES_HUT,
    val gravity: Float = 0.05f,
    val repulsion: Float = 2000f,
    val spring: Float = 0.1f,
    val damping: Float = 0.9f,
    val nodeBaseRadius: Float = 15f,
    val nodeRadiusEdgeFactor: Float = 2.0f,
    val minDistance: Float = 2.0f,
    val barnesHutTheta: Float = 1.2f,
    val tolerance: Float = 1.0f
)