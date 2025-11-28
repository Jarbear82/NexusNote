package com.tau.nexus_note.datamodels

import androidx.compose.ui.geometry.Offset

/**
 * Represents the physical state of an edge in the graph simulation.
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
    val representedConnections: List<String> = emptyList(),
    // For Bezier rendering
    val isBidirectional: Boolean = false,
    val isSelfLoop: Boolean = false
)

/**
 * Represents the pan and zoom state of the graph canvas.
 */
data class TransformState(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 1.0f
)