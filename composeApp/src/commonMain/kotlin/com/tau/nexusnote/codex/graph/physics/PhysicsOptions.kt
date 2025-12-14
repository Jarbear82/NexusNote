package com.tau.nexusnote.codex.graph.physics

import kotlinx.serialization.Serializable

/**
 * Data class to hold all the constants for the physics simulation.
 *
 * @param gravity The strength of the pull towards the center (0,0).
 * @param repulsion The base strength of the force pushing nodes away from each other.
 * @param spring The base stiffness of the edges (springs).
 * @param damping The multiplier applied to velocity each frame to simulate friction.
 * @param nodeBaseRadius The minimum radius of a node with zero edges.
 * @param nodeRadiusEdgeFactor How much to increase the radius for each attached edge.
 * @param minDistance The minimum pixel buffer to maintain between node edges.
 * @param barnesHutTheta The approximation parameter for Barnes-Hut.
 * Higher values are faster but less accurate. (Default: 1.2)
 * @param tolerance The "Tolerance (speed)" parameter for ForceAtlas2 adaptive speed.
 * Controls how much node "swinging" is allowed. (Default: 1.0)
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
