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
 * Now acts as the registry for Node Topology definitions.
 */
@Serializable
enum class NodeStyle(val displayName: String, val definition: NodeDefinition) {
    GENERIC(
        "Generic Card",
        NodeDefinition(NodeTopology.LEAF, "Standard Node container", supportsChildren = true)
    ),

    // --- Structural Roots & Branches ---
    DOCUMENT(
        "Document Root",
        NodeDefinition(NodeTopology.ROOT, "Root node, no parents, heavy mass.", supportsChildren = true)
    ),
    SECTION(
        "Section Header",
        NodeDefinition(NodeTopology.BRANCH, "Structural divider within a document", supportsChildren = true)
    ),
    LIST(
        "List Group",
        NodeDefinition(NodeTopology.BRANCH, "Container for list items", supportsChildren = true)
    ),

    // --- Content Leaves ---
    BLOCK(
        "Text Block",
        NodeDefinition(NodeTopology.LEAF, "Atomic unit of text content", supportsChildren = false)
    ),
    CODE_BLOCK(
        "Code Block",
        NodeDefinition(NodeTopology.LEAF, "Syntax highlighted code snippet", supportsChildren = false)
    ),
    TABLE(
        "Data Table",
        NodeDefinition(NodeTopology.LEAF, "Structured data grid", supportsChildren = false)
    ),

    // --- Concept / Asset Leaves ---
    TAG(
        "Tag / Pill",
        NodeDefinition(NodeTopology.LEAF, "Conceptual label or category", supportsChildren = false)
    ),
    ATTACHMENT(
        "Media / Attachment",
        NodeDefinition(NodeTopology.LEAF, "External file reference (Image, Audio, etc)", supportsChildren = false)
    )
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