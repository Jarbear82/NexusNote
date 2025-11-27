package com.tau.nexus_note.datamodels

import kotlinx.serialization.Serializable

/**
 * Represents a user-defined property within a schema.
 * This is serialized to/from JSON.
 * @param name The name of the property (e.g., "Description", "Due Date").
 * @param type The data type for the UI (e.g., "Text", "Image", "Date").
 * @param isDisplayProperty If true, this property's value is used as the node's label in the graph.
 * @param isBackgroundProperty If true, and type is IMAGE, this property's value is used as the node's background.
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
 * @param src The name of the source node schema (e.t., "Person").
 * @param dst The name of the destination node schema (e.g., "Location").
 */
@Serializable // For storing in SchemaDefinition properties
data class ConnectionPair(
    val src: String,
    val dst: String
)

/**
 * UI-facing model for a schema definition (either Node or Edge).
 * This is built from the 'SchemaDefinition' table.
 * @param id The unique ID from the 'SchemaDefinition' table.
 * @param type "NODE" or "EDGE".
 * @param name The name of the schema (e.g., "Person", "KNOWS").
 * @param properties The list of user-defined properties.
 * @param connections For EDGE schemas, the list of allowed connections.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val properties: List<SchemaProperty>,
    val connections: List<ConnectionPair>? = null
)