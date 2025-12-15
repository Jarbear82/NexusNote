package com.tau.nexusnote.datamodels

/**
 * Represents the entire state of the "Edit" tab.
 */
sealed interface EditScreenState {
    data object None : EditScreenState
    data class CreateNode(val state: NodeCreationState) : EditScreenState
    data class CreateEdge(val state: EdgeCreationState) : EditScreenState
    data class CreateNodeSchema(val state: NodeSchemaCreationState) : EditScreenState
    data class CreateEdgeSchema(val state: EdgeSchemaCreationState) : EditScreenState
    data class EditNode(val state: NodeEditState) : EditScreenState
    data class EditEdge(val state: EdgeEditState) : EditScreenState
    data class EditNodeSchema(val state: NodeSchemaEditState) : EditScreenState
    data class EditEdgeSchema(val state: EdgeSchemaEditState) : EditScreenState
}

// --- Data class for Node Creation UI State ---
data class NodeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available NODE schemas
    val selectedSchema: SchemaDefinitionItem? = null,
    val properties: Map<String, String> = emptyMap() // UI state for text fields
)

// --- Helper for Dynamic Edge Participants ---
data class ParticipantSelection(
    val id: String, // Unique ID for UI keys (e.g. UUID or timestamp)
    val role: String, // Matches RoleDefinition.name
    val node: NodeDisplayItem? = null
)

// --- Data class for Edge Creation UI State ---
data class EdgeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available EDGE schemas
    val availableNodes: List<NodeDisplayItem>,
    val selectedSchema: SchemaDefinitionItem? = null,

    // Tracks selections for each role.
    // Logic:
    // - For "One" cardinality roles: 1 ParticipantSelection in list (can be null node)
    // - For "Many" cardinality roles: 0..N ParticipantSelection items in list
    val participants: List<ParticipantSelection> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)

// --- Data class for Node Schema Creation UI State ---
data class NodeSchemaCreationState(
    val tableName: String = "",
    val properties: List<SchemaProperty> = listOf(SchemaProperty("name",
        CodexPropertyDataTypes.TEXT, isDisplayProperty = true)),
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
)

// --- Data class for Edge Schema Creation UI State ---
data class EdgeSchemaCreationState(
    val tableName: String = "",
    val roles: List<RoleDefinition> = emptyList(),
    val properties: List<SchemaProperty> = emptyList(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList(), // All NODE schemas
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val roleErrors: Map<Int, String?> = emptyMap()
)

// --- Data classes for Editing Instances ---

data class NodeEditState(
    val id: Long,
    val schema: SchemaDefinitionItem,
    val properties: Map<String, String> // Current values from DB, as strings for UI
)

data class EdgeEditState(
    val id: Long,
    val schema: SchemaDefinitionItem,
    val participants: List<ParticipantSelection>, // Read-only for now or editable if implemented
    val properties: Map<String, String>
)

// --- Data classes for Editing Schemas ---

data class NodeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val roles: List<RoleDefinition>,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val roleErrors: Map<Int, String?> = emptyMap(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList()
)