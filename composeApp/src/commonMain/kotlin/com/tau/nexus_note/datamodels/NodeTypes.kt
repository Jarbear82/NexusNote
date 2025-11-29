package com.tau.nexus_note.datamodels

import kotlinx.serialization.Serializable

/**
 * Phase 1: Defines the physical role of a node in the graph simulation.
 * This determines default mass, gravity, and interaction behavior.
 */
@Serializable
enum class NodeTopology {
    ROOT,   // Heavy mass, central gravity, acts as an anchor (e.g., Documents)
    BRANCH, // Medium mass, connects flows, acts as a structural bridge (e.g., Sections, Lists)
    LEAF    // Light mass, peripheral, usually pushed to the edges (e.g., Tags, atomic text, images)
}

/**
 * Detailed definition of a node's capabilities and structural role.
 */
@Serializable
data class NodeDefinition(
    val topology: NodeTopology,
    val description: String,
    val supportsChildren: Boolean = true
)