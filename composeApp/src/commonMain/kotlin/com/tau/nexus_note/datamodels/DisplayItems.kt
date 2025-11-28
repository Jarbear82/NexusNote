package com.tau.nexus_note.datamodels

import androidx.compose.ui.graphics.Color

/**
 * Represents a Node in the UI lists and graph.
 */
data class NodeDisplayItem(
    val id: Long,
    val label: String,
    val displayProperty: String,
    val schemaId: Long,
    val backgroundImagePath: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val style: NodeStyle = NodeStyle.GENERIC // Added Style
)

/**
 * Represents an Edge in the UI lists and graph.
 */
data class EdgeDisplayItem(
    val id: Long,
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    val schemaId: Long
)

data class ColorInfo(
    val hex: String,
    val rgb: IntArray,
    val composeColor: Color,
    val composeFontColor: Color
)