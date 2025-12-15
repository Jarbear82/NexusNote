package com.tau.nexusnote.datamodels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a user-defined property within a schema (used primarily by MapConfig).
 */
@Serializable
data class SchemaProperty(
    val name: String,
    val type: CodexPropertyDataTypes,
    val isDisplayProperty: Boolean = false
)

/**
 * The configuration hierarchy for schemas.
 * Structured hierarchically by the generic NodeType they enforce.
 */
@Serializable
sealed class SchemaConfig {

    // --- 1. Text Configurations ---
    @Serializable @SerialName("Text")
    sealed class TextConfig : SchemaConfig() {
        /** Config for semantic Headings */
        @Serializable @SerialName("Heading")
        data class Heading(
            val level: Int = 1,
            val casing: String = "TitleCase"
        ) : TextConfig()

        /** Config for semantic Titles */
        @Serializable @SerialName("Title")
        data class Title(
            val casing: String = "TitleCase"
        ) : TextConfig()

        /** Config for generic text (Short, Long, Paragraphs) */
        @Serializable @SerialName("Plain")
        data class PlainText(
            val charLimit: Int? = null // Null implies no limit (LongText)
        ) : TextConfig()

        /** Config for Tags/Chips */
        @Serializable @SerialName("Tag")
        data object Tag : TextConfig()
    }

    // --- 2. List Configurations ---
    @Serializable @SerialName("List")
    sealed class ListConfig : SchemaConfig() {
        @Serializable @SerialName("Ordered")
        data class Ordered(
            val indicatorStyle: String = "1."
        ) : ListConfig()

        @Serializable @SerialName("Unordered")
        data object Unordered : ListConfig()

        @Serializable @SerialName("Task")
        data object Task : ListConfig()
    }

    // --- 3. Map Configuration ---
    @Serializable @SerialName("Map")
    data class MapConfig(val properties: List<SchemaProperty>) : SchemaConfig()

    // --- 4. Table Configuration ---
    @Serializable @SerialName("Table")
    data class TableConfig(
        val rowHeaderType: String = "None",
        val showColumnHeaders: Boolean = true,
        val predefinedColumnHeaders: List<String> = emptyList(),
        val predefinedRowHeaders: List<String> = emptyList(),
        val maxRows: Int? = null
    ) : SchemaConfig()

    // --- 5. Code Configuration ---
    @Serializable @SerialName("Code")
    data class CodeConfig(
        val defaultLanguage: String = "kotlin",
        val allowLanguageChange: Boolean = true,
        val showFilename: Boolean = false
    ) : SchemaConfig()

    // --- 6. Media Configuration ---
    @Serializable @SerialName("Media")
    data object MediaConfig : SchemaConfig()

    // --- 7. Timestamp Configuration ---
    @Serializable @SerialName("Timestamp")
    data class TimestampConfig(
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
 */
@Serializable
enum class RoleDirection {
    @SerialName("Source") Source,
    @SerialName("Target") Target
}

/**
 * Represents a Role within an Edge Schema.
 */
@Serializable
data class RoleDefinition(
    val name: String,
    val allowedNodeSchemas: List<String>,
    val cardinality: RoleCardinality = RoleCardinality.One,
    val direction: RoleDirection = RoleDirection.Target
)

/**
 * UI-facing model for a schema definition.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val config: SchemaConfig,
    val roleDefinitions: List<RoleDefinition>? = null
) {
    // Helper to access properties if this is a Map schema
    val properties: List<SchemaProperty>
        get() = (config as? SchemaConfig.MapConfig)?.properties ?: emptyList()
}