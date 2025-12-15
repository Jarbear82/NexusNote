package com.tau.nexusnote.datamodels

import androidx.compose.ui.geometry.Offset

/**
 * Represents the visual state of a node in the graph.
 * Decoupled from physics logic (which now lives in FcNode).
 */
data class GraphNode(
    val id: Long,
    val label: String,
    val displayProperty: String,
    val pos: Offset, // Center position

    val width: Float,
    val height: Float,
    val radius: Float, // Used for hit-testing and simple shape rendering

    val isCompound: Boolean = false,
    val isHyperNode: Boolean = false,
    val content: NodeContent? = null,
    val config: SchemaConfig? = null,
    val colorInfo: ColorInfo,
    val isFixed: Boolean = false
)

/**
 * Represents the visual state of an edge.
 */
data class GraphEdge(
    val id: Long,
    val sourceId: Long,
    val targetId: Long,
    val label: String,
    val roleLabel: String? = null,
    val strength: Float,
    val colorInfo: ColorInfo
)

/**
 * Represents the pan and zoom state of the canvas.
 */
data class TransformState(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 1.0f
)