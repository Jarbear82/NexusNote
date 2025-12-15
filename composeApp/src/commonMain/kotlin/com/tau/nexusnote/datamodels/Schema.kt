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
 * The configuration hierarchy for schemas.
 * Defines the strict structure and behavior of a node type.
 */
@Serializable
sealed class SchemaConfig {

    /**
     * Legacy/Flexible configuration: A dynamic list of key-value properties.
     * Used for generic objects, people, items, etc.
     */
    @Serializable @SerialName("Map")
    data class MapConfig(val properties: List<SchemaProperty>) : SchemaConfig()

    /**
     * Configuration for Tabular data.
     */
    @Serializable @SerialName("Table")
    data class TableConfig(
        val rowHeaderType: String = "None", // None, Numeric, Alpha
        val showColumnHeaders: Boolean = true,
        val predefinedColumnHeaders: List<String> = emptyList(),
        val predefinedRowHeaders: List<String> = emptyList(),
        val maxRows: Int? = null
    ) : SchemaConfig()

    /**
     * Configuration for Code Blocks.
     */
    @Serializable @SerialName("CodeBlock")
    data class CodeBlockConfig(
        val defaultLanguage: String = "kotlin",
        val allowLanguageChange: Boolean = true,
        val showFilename: Boolean = false
    ) : SchemaConfig()

    /**
     * Configuration for Titles.
     */
    @Serializable @SerialName("Title")
    data class TitleConfig(
        val casing: String = "TitleCase" // TitleCase, UpperCase, etc.
    ) : SchemaConfig()

    /**
     * Configuration for Headings (Levels 1-6).
     */
    @Serializable @SerialName("Heading")
    data class HeadingConfig(
        val level: Int = 1,
        val casing: String = "TitleCase"
    ) : SchemaConfig()

    /**
     * Configuration for Short Text (e.g., Tweets, Status).
     */
    @Serializable @SerialName("ShortText")
    data class ShortTextConfig(
        val charLimit: Int = 140
    ) : SchemaConfig()

    /**
     * Configuration for Long Text (Documents, Articles).
     */
    @Serializable @SerialName("LongText")
    object LongTextConfig : SchemaConfig()

    /**
     * Configuration for Ordered Lists.
     */
    @Serializable @SerialName("OrderedList")
    data class OrderedListConfig(
        val indicatorStyle: String = "1." // 1., A., i., etc.
    ) : SchemaConfig()

    /**
     * Configuration for Unordered Lists (Simple bullets).
     */
    @Serializable @SerialName("UnorderedList")
    object UnorderedListConfig : SchemaConfig()

    /**
     * Singleton Configurations for simple types.
     */
    @Serializable @SerialName("TaskList")
    object TaskListConfig : SchemaConfig()

    @Serializable @SerialName("Set")
    object SetConfig : SchemaConfig()

    @Serializable @SerialName("Tag")
    object TagConfig : SchemaConfig()

    @Serializable @SerialName("Image")
    object ImageConfig : SchemaConfig()

    /**
     * Configuration for Dates.
     */
    @Serializable @SerialName("Date")
    data class DateConfig(
        val formatPattern: String = "yyyy-MM-dd"
    ) : SchemaConfig()
}

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
 * @param config The strict configuration for this schema.
 * @param roleDefinitions For EDGE schemas, the list of defined roles.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val config: SchemaConfig,
    val roleDefinitions: List<RoleDefinition>? = null
) {
    // Helper property to maintain compatibility with existing UI code that expects a property list.
    // Returns properties only if this is a MapConfig.
    val properties: List<SchemaProperty>
        get() = (config as? SchemaConfig.MapConfig)?.properties ?: emptyList()
}