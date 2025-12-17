package com.tau.nexusnote.datamodels

import androidx.compose.ui.geometry.Offset

/**
 * Step 2.1: GraphNode now implements GraphEntity.
 * Replaced single 'label' with 'schemas' list.
 */
data class GraphNode(
    override val id: Long,
    val schemas: List<SchemaDefinition>, // Replaces single label
    val displayProperty: String,
    // Physical state
    var pos: Offset,
    var vel: Offset,
    val mass: Float,
    val radius: Float,
    val width: Float,
    val height: Float,
    val isCompound: Boolean = false,
    val isHyperNode: Boolean = false,
    val colorInfo: ColorInfo,
    var isFixed: Boolean = false,
    // Physics internals
    var oldForce: Offset = Offset.Zero,
    var swinging: Float = 0f,
    var traction: Float = 0f,
    // Data state
    override val properties: List<CodexProperty>
) : GraphEntity {
    // Computed helper for backward compatibility / simple display
    val label: String get() = schemas.firstOrNull()?.name ?: "Unknown"
}

/**
 * Step 2.1: GraphEdge now implements GraphEntity.
 * It has its own ID and properties, making it a first-class citizen.
 */
data class GraphEdge(
    override val id: Long,
    val schemas: List<SchemaDefinition>, // Edges can also have types
    override val properties: List<CodexProperty>,
    val sourceId: Long,
    val targetId: Long,
    // UI Helpers
    val roleLabel: String? = null,
    val strength: Float,
    val colorInfo: ColorInfo
) : GraphEntity {
    val label: String get() = schemas.firstOrNull()?.name ?: "Unknown"
}

/**
 * Aggregates all graph elements for a snapshot of the current state.
 */
data class Graph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

/**
 * Represents the pan and zoom state of the graph canvas.
 */
data class TransformState(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 1.0f
)