package com.tau.nexusnote.datamodels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a user-defined property within a schema.
 * This is serialized to/from JSON.
 * @param name The name of the property (e.g., "Description", "Due Date").
 * @param type The data type for the UI (e.g., "Text", "Image", "Date").
 */
@Serializable
data class SchemaProperty(
    val name: String,
    val type: CodexPropertyDataTypes,
    val isDisplayProperty: Boolean = false
)

/**
 * Defines how many nodes can/must fill a specific role.
 */
@Serializable
sealed class RoleCardinality {
    @Serializable @SerialName("One") object One : RoleCardinality()
    @Serializable @SerialName("Many") object Many : RoleCardinality()
    @Serializable @SerialName("Exact") data class Exact(val count: Int) : RoleCardinality()

    override fun toString(): String {
        return when(this) {
            is One -> "One"
            is Many -> "Many"
            is Exact -> "Exact ($count)"
        }
    }
}

/**
 * Defines the direction of the edge relative to the node filling this role.
 * Used for visual graph rendering.
 */
@Serializable
enum class RoleDirection {
    @SerialName("Source") Source, // Arrow points FROM Node TO Hypernode
    @SerialName("Target") Target  // Arrow points FROM Hypernode TO Node
}

/**
 * Represents a Role within an Edge Schema.
 * E.g., for a "Directed Edge", roles might be "Source" (One) and "Target" (One).
 * For a "Meeting", roles might be "Organizer" (One), "Attendees" (Many), "Room" (One).
 *
 * @param direction Determines the visual arrow direction in the graph. Defaults to Target.
 */
@Serializable
data class RoleDefinition(
    val name: String,
    val allowedNodeSchemas: List<String>, // List of Schema Names (Strings)
    val cardinality: RoleCardinality = RoleCardinality.One,
    val direction: RoleDirection = RoleDirection.Target
)

/**
 * UI-facing model for a schema definition (either Node or Edge).
 * This is built from the 'SchemaDefinition' table.
 * @param id The unique ID from the 'SchemaDefinition' table.
 * @param type "NODE" or "EDGE".
 * @param name The name of the schema (e.g., "Person", "KNOWS").
 * @param properties The list of user-defined properties.
 * @param roleDefinitions For EDGE schemas, the list of defined roles.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val properties: List<SchemaProperty>,
    val roleDefinitions: List<RoleDefinition>? = null
)