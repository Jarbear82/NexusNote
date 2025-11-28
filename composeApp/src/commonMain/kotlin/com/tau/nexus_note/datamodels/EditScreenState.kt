package com.tau.nexus_note.datamodels

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

// --- Data class for Edge Creation UI State ---
data class EdgeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available EDGE schemas
    val availableNodes: List<NodeDisplayItem>,
    val selectedSchema: SchemaDefinitionItem? = null,
    val selectedConnection: ConnectionPair? = null,
    val src: NodeDisplayItem? = null,
    val dst: NodeDisplayItem? = null,
    val properties: Map<String, String> = emptyMap()
)

// --- Data class for Node Schema Creation UI State ---
data class NodeSchemaCreationState(
    val tableName: String = "",
    val nodeStyle: NodeStyle = NodeStyle.GENERIC, // Added Style
    val properties: List<SchemaProperty> = listOf(SchemaProperty("name",
        CodexPropertyDataTypes.TEXT, isDisplayProperty = true)),
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
)

// --- Data class for Edge Schema Creation UI State ---
data class EdgeSchemaCreationState(
    val tableName: String = "",
    val connections: List<ConnectionPair> = emptyList(),
    val properties: List<SchemaProperty> = emptyList(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList(), // All NODE schemas
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
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
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    val properties: Map<String, String> // Current values from DB, as strings for UI
)

// --- Data classes for Editing Schemas ---

data class NodeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val currentNodeStyle: NodeStyle, // Added Style
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
    // Note: Diffing logic will be in the ViewModel, comparing this to originalSchema
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val connections: List<ConnectionPair>,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList()
)