package com.tau.nexusnote.datamodels

import androidx.compose.ui.graphics.Color

/**
 * Represents a Node in the UI lists and graph.
 * @param id The unique ID from the SQLite 'Node' table.
 * @param label The name of the schema (e.g., "Person", "Note") or the Primitive Type name.
 * @param displayProperty The user-friendly text to show (e.g., "John Doe", "My Note").
 * @param schemaId The ID of the schema this node belongs to, or -1 if primitive.
 * @param content The full polymorphic content of the node.
 * @param config The schema configuration associated with this node, determining its render style.
 * @param parentId The ID of the parent node, if any.
 * @param isCollapsed Whether this node's children are hidden (if it is a parent).
 */
data class NodeDisplayItem(
    val id: Long,
    val label: String,
    val displayProperty: String,
    val schemaId: Long,
    val content: NodeContent,
    val config: SchemaConfig? = null,
    val parentId: Long? = null,
    val isCollapsed: Boolean = false
)

/**
 * Wraps a node with its specific role in an edge (e.g., "source", "target").
 */
data class EdgeParticipant(
    val node: NodeDisplayItem,
    val role: String?
)

/**
 * Represents an Edge in the UI lists and graph.
 * Updated for N-nary support (Phase 3) with Roles (Phase 5).
 *
 * @param id The unique ID from the SQLite 'Edge' table.
 * @param label The name of the schema (e.g., "KNOWS", "REFERENCES").
 * @param properties The map of properties for this edge.
 * @param participatingNodes The list of nodes (and their roles) linked to this edge.
 * @param schemaId The ID of the schema this edge belongs to.
 */
data class EdgeDisplayItem(
    val id: Long,
    val label: String,
    val properties: Map<String, String>,
    val participatingNodes: List<EdgeParticipant>,
    val schemaId: Long
)

/**
 * Represents a persisted Layout Constraint.
 */
data class LayoutConstraintItem(
    val id: Long,
    val type: String,
    val nodeIds: List<Long>,
    val params: Map<String, String>
)

/**
 * Stores the generated hex color and its raw RGB components.
 */
data class ColorInfo(
    val hex: String,
    val rgb: IntArray,
    val composeColor: Color,
    val composeFontColor: Color
)