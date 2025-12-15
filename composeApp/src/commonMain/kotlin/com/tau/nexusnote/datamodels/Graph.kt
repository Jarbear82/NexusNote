package com.tau.nexusnote.datamodels

import androidx.compose.ui.geometry.Offset

/**
 * Represents the physical state of a node in the graph simulation.
 * @param id The unique ID from the database. For Hypernodes (Edges), this is usually negative.
 * @param label The schema name (e.g., "Person").
 * @param displayProperty The text to show (e.g., "John Doe").
 * @param pos The current x/y position in the simulation space (Center).
 * @param vel The current x/y velocity.
 * @param mass The mass of the node (influences physics).
 * @param radius The visual radius of the node (used for simple nodes and approximation).
 * @param width The width of the node (important for compound nodes).
 * @param height The height of the node (important for compound nodes).
 * @param isCompound True if this node contains other nodes.
 * @param isHyperNode True if this node represents an Edge (N-nary relationship).
 * @param content The actual content data of the node (e.g., Image path, Code snippet).
 * @param config The schema configuration for styling this node.
 * @param colorInfo The color for drawing.
 * @param isFixed True if the node is being dragged by the user.
 * @param oldForce The net force applied to this node in the *previous* frame.
 * @param swinging The magnitude of the change in force between frames.
 * @param traction The magnitude of the consistent force between frames.
 */
data class GraphNode(
    val id: Long,
    val label: String,
    val displayProperty: String,
    var pos: Offset,
    var vel: Offset,
    val mass: Float,
    val radius: Float,
    val width: Float,
    val height: Float,
    val isCompound: Boolean = false,
    val isHyperNode: Boolean = false, // Flag for Hypernodes (Phase 5)
    val content: NodeContent? = null, // Phase 3: Added to support complex rendering
    val config: SchemaConfig? = null, // Phase 6: Styling configuration
    val colorInfo: ColorInfo,
    var isFixed: Boolean = false,
    // --- State for ForceAtlas2 Adaptive Speed ---
    var oldForce: Offset = Offset.Zero,
    var swinging: Float = 0f,
    var traction: Float = 0f
)

/**
 * Represents the physical state of an edge in the graph simulation.
 * @param id The unique ID from the database.
 * @param sourceId The ID of the source node.
 * @param targetId The ID of the target node.
 * @param label The schema name (e.g., "KNOWS").
 * @param roleLabel The role this connection represents (e.g. "Source", "Target").
 * @param strength The "springiness" of the edge.
 * @param colorInfo The color for drawing.
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
 * Represents the pan and zoom state of the graph canvas.
 * @param pan The current x/y offset (pan) in world coordinates.
 * @param zoom The current zoom multiplier.
 */
data class TransformState(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 1.0f
)