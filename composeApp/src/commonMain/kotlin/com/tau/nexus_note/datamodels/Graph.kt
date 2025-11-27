package com.tau.nexus_note.datamodels

import androidx.compose.ui.geometry.Offset

/**
 * Represents the physical state of a node in the graph simulation.
 * @param id The unique ID from the database.
 * @param label The schema name (e.g., "Person").
 * @param displayProperty The text to show (e.g., "John Doe").
 * @param pos The current x/y position in the simulation space.
 * @param vel The current x/y velocity.
 * @param mass The mass of the node (influences physics).
 * @param radius The visual radius of the node.
 * @param colorInfo The color for drawing.
 * @param isFixed True if the node is being dragged by the user.
 * @param oldForce The net force applied to this node in the *previous* frame.
 * @param swinging The magnitude of the change in force between frames.
 * @param traction The magnitude of the consistent force between frames.
 * @param backgroundImagePath Absolute path to the background image file (or URL).
 * @param isCollapsed Whether this node is currently collapsed (hiding its children).
 */
data class GraphNode(
    val id: Long,
    val label: String,
    val displayProperty: String,
    var pos: Offset,
    var vel: Offset,
    val mass: Float,
    val radius: Float,
    val colorInfo: ColorInfo,
    var isFixed: Boolean = false,
    // --- State for ForceAtlas2 Adaptive Speed ---
    var oldForce: Offset = Offset.Zero,
    var swinging: Float = 0f,
    var traction: Float = 0f,
    // --- New Features ---
    val backgroundImagePath: String? = null,
    val isCollapsed: Boolean = false
)

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