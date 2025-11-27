package com.tau.nexus_note.datamodels

import androidx.compose.ui.graphics.Color

/**
 * Represents a Node in the UI lists and graph.
 * @param id The unique ID from the SQLite 'Node' table.
 * @param label The name of the schema (e.g., "Person", "Note").
 * @param displayProperty The user-friendly text to show (e.g., a person's name, a note's title).
 * @param schemaId The ID of the schema this node belongs to.
 * @param backgroundImagePath Relative path to the image file, if this node has a background property.
 */
data class NodeDisplayItem(
    val id: Long,
    val label: String,
    val displayProperty: String,
    val schemaId: Long,
    val backgroundImagePath: String? = null
)

/**
 * Represents an Edge in the UI lists and graph.
 * @param id The unique ID from the SQLite 'Edge' table.
 * @param label The name of the schema (e.g., "KNOWS", "REFERENCES").
 * @param src The source Node.
 * @param dst The destination Node.
 * @param schemaId The ID of the schema this edge belongs to.
 */
data class EdgeDisplayItem(
    val id: Long,
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    val schemaId: Long
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