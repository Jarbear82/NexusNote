package com.tau.nexus_note.datamodels

import androidx.compose.ui.geometry.Offset

/**
 * Represents the physical state of an edge in the graph simulation.
 * @param id The unique ID from the database.
 * @param sourceId The ID of the source node.
 * @param targetId The ID of the target node.
 * @param label The schema name (e.g., "KNOWS").
 * @param strength The "springiness" of the edge.
 * @param colorInfo The color for drawing.
 * @param isProxy True if this edge represents aggregated connections (visual roll-up).
 * @param representedConnections A list of strings describing the underlying edges (e.g. "EdgeID: Label") for tooltips.
 */
data class GraphEdge(
    val id: Long,
    val sourceId: Long,
    val targetId: Long,
    val label: String,
    val strength: Float,
    val colorInfo: ColorInfo,
    // --- New Features ---
    val isProxy: Boolean = false,
    val representedConnections: List<String> = emptyList()
)

/**
 * Represents the pan and zoom state of the graph canvas.
 * @param pan The current x/y offset (pan) in world coordinates.
 * @param zoom The current zoom multiplier.
 */
data class TransformState(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 1.0f
)