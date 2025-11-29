package com.tau.nexus_note.datamodels

import kotlinx.serialization.Serializable

/**
 * Represents a user-defined property within a schema.
 */
@Serializable
data class SchemaProperty(
    val name: String,
    val type: CodexPropertyDataTypes,
    val isDisplayProperty: Boolean = false,
    val isBackgroundProperty: Boolean = false
)

/**
 * Represents a connection pair for an edge schema.
 */
@Serializable
data class ConnectionPair(
    val src: String,
    val dst: String
)

/**
 * Defines the visual rendering style for nodes of a specific schema.
 */
@Serializable
enum class NodeStyle(val displayName: String) {
    GENERIC("Generic Card"),
    DOCUMENT("Document Root"),
    SECTION("Section Header"),
    BLOCK("Text Block"),
    CODE_BLOCK("Code Block"),
    TABLE("Data Table"),
    TAG("Tag / Pill"),
    ATTACHMENT("Media / Attachment"),
    // NEW: List Style
    LIST("List Group")
}

/**
 * UI-facing model for a schema definition.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val properties: List<SchemaProperty>,
    val connections: List<ConnectionPair>? = null,
    val nodeStyle: NodeStyle = NodeStyle.GENERIC // Added Style
)