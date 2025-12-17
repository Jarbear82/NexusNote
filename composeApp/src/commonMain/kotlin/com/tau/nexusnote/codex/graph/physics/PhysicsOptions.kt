package com.tau.nexusnote.codex.graph.physics

import kotlinx.serialization.Serializable

/**
 * Configuration for the Continuous Physics (ForceAtlas2) simulation.
 * distinct from LayoutConfig (which is for One-Shot fCoSE).
 */
@Serializable
data class PhysicsOptions(
    val gravity: Float = 0.05f,
    val repulsion: Float = 2000f,
    val spring: Float = 0.1f,
    val damping: Float = 0.9f,
    val nodeBaseRadius: Float = 15f,
    val nodeRadiusEdgeFactor: Float = 2.0f,
    val minDistance: Float = 2.0f, // Extra buffer between nodes
    val barnesHutTheta: Float = 1.2f,
    val tolerance: Float = 1.0f
)