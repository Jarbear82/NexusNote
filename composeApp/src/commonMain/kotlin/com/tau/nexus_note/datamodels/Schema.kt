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
 * Implements the requested Zig types fully.
 */
@Serializable
enum class NodeStyle(val displayName: String, val definition: NodeDefinition) {
    // --- Requested Zig Types ---

    TITLE(
        "Title (Root)",
        NodeDefinition(NodeTopology.ROOT, "Has no parent nodes, but may have child nodes.")
    ),
    HEADING(
        "Heading",
        NodeDefinition(NodeTopology.BRANCH, "May have parent nodes, may have child nodes.")
    ),
    SHORT_TEXT(
        "Short Text",
        NodeDefinition(NodeTopology.BRANCH, "Short. Can be rendered as one line.")
    ),
    LONG_TEXT(
        "Long Text",
        NodeDefinition(NodeTopology.BRANCH, "Long. Rendered as multiple lines.")
    ),
    CODE_BLOCK(
        "Code Block",
        NodeDefinition(NodeTopology.BRANCH, "Code. Language and filename may be specified.")
    ),
    MAP(
        "Map",
        NodeDefinition(NodeTopology.BRANCH, "Contains simple key-value pairs")
    ),
    SET(
        "Set",
        NodeDefinition(NodeTopology.BRANCH, "Contains simple unique values (no duplicates)")
    ),
    UNORDERED_LIST(
        "Unordered List",
        NodeDefinition(NodeTopology.BRANCH, "Contains simple values, order doesn't matter.")
    ),
    ORDERED_LIST(
        "Ordered List",
        NodeDefinition(NodeTopology.BRANCH, "Contains simple values, order is specified.")
    ),
    TAG(
        "Tag",
        NodeDefinition(NodeTopology.LEAF, "Simple node like an idea to connect other nodes")
    ),
    TABLE(
        "Table",
        NodeDefinition(NodeTopology.BRANCH, "Tabular data with headers and rows")
    ),

    // --- Legacy / Helpers (Optional, kept for safety or mapped to above in logic) ---
    GENERIC("Generic", NodeDefinition(NodeTopology.LEAF, "Fallback")),
    DOCUMENT("Document (Legacy)", NodeDefinition(NodeTopology.ROOT, "Legacy")),
    SECTION("Section (Legacy)", NodeDefinition(NodeTopology.BRANCH, "Legacy")),
    BLOCK("Block (Legacy)", NodeDefinition(NodeTopology.LEAF, "Legacy")),
    LIST("List (Legacy)", NodeDefinition(NodeTopology.BRANCH, "Legacy")),
    ATTACHMENT("Attachment", NodeDefinition(NodeTopology.LEAF, "File Attachment"))
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
    val nodeStyle: NodeStyle = NodeStyle.GENERIC
)