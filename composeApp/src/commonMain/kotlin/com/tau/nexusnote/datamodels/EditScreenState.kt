package com.tau.nexusnote.datamodels

/**
 * Refactored State classes to use the new SchemaDefinition instead of Item.
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

data class NodeCreationState(
    val availableSchemas: List<SchemaDefinition>,
    val selectedSchemas: List<SchemaDefinition> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
    val availableNodes: List<NodeDisplayItem> = emptyList()
)

data class EdgeCreationState(
    val schemas: List<SchemaDefinition>,
    val availableNodes: List<NodeDisplayItem>,
    val selectedSchema: SchemaDefinition? = null,
    val participants: List<ParticipantSelection> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)

data class NodeSchemaCreationState(
    val tableName: String = "",
    val canBePropertyType: Boolean = true,
    val properties: List<SchemaProperty> = listOf(SchemaProperty(0, "name", CodexPropertyDataTypes.TEXT, null, true)),
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val allNodeSchemas: List<SchemaDefinition> = emptyList()
)

data class EdgeSchemaCreationState(
    val tableName: String = "",
    val roles: List<RoleDefinition> = emptyList(),
    val properties: List<SchemaProperty> = emptyList(),
    val allNodeSchemas: List<SchemaDefinition> = emptyList(),
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val roleErrors: Map<Int, String?> = emptyMap()
)

data class NodeEditState(
    val id: Long,
    val schemas: List<SchemaDefinition>,
    val availableSchemas: List<SchemaDefinition> = emptyList(),
    val properties: Map<String, String>,
    val availableNodes: List<NodeDisplayItem> = emptyList()
)

data class EdgeEditState(
    val id: Long,
    val schema: SchemaDefinition,
    val participants: List<ParticipantSelection>,
    val properties: Map<String, String>
)

data class NodeSchemaEditState(
    val originalSchema: SchemaDefinition,
    val currentName: String,
    val canBePropertyType: Boolean,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val allNodeSchemas: List<SchemaDefinition> = emptyList()
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaDefinition,
    val currentName: String,
    val roles: List<RoleDefinition>,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val roleErrors: Map<Int, String?> = emptyMap(),
    val allNodeSchemas: List<SchemaDefinition> = emptyList()
)

// Helper remains same
data class ParticipantSelection(
    val id: String,
    val role: String,
    val node: NodeDisplayItem? = null
)