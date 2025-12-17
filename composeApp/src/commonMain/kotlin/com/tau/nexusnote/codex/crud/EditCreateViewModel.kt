package com.tau.nexusnote.codex.crud

import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.codex.metadata.MetadataViewModel
import com.tau.nexusnote.codex.schema.SchemaViewModel
import com.tau.nexusnote.datamodels.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class EditCreateViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {
    private val _editScreenState = MutableStateFlow<EditScreenState>(EditScreenState.None)
    val editScreenState = _editScreenState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>(replay = 0)
    val navigationEventFlow = _navigationEvent.asSharedFlow()

    // --- Validation Helpers ---

    private fun isSchemaNameUnique(name: String, editingId: Long? = null): String? {
        if (name.isBlank()) return "Name cannot be blank."
        val allSchemas = (schemaViewModel.schema.value?.nodeSchemas ?: emptyList()) +
                (schemaViewModel.schema.value?.edgeSchemas ?: emptyList())
        val conflictingSchema = allSchemas.find { it.name.equals(name, ignoreCase = false) }
        return when {
            conflictingSchema == null -> null
            editingId != null && conflictingSchema.id == editingId -> null
            else -> "Name is already used by another schema."
        }
    }

    private fun validateProperty(index: Int, property: SchemaProperty, allProperties: List<SchemaProperty>): String? {
        if (property.name.isBlank()) return "Name cannot be blank."
        val conflict = allProperties.withIndex().find { (i, p) -> i != index && p.name.equals(property.name, ignoreCase = false) }
        return if (conflict != null) "Name is already used in this schema." else null
    }

    private fun validateRole(index: Int, role: RoleDefinition, allRoles: List<RoleDefinition>): String? {
        if (role.name.isBlank()) return "Role name cannot be blank."
        val conflict = allRoles.withIndex().find { (i, r) -> i != index && r.name.equals(role.name, ignoreCase = true) }
        return if (conflict != null) "Role name must be unique." else null
    }

    // --- Save Actions ---
    fun saveCurrentState() {
        val stateToSave = _editScreenState.value
        if (stateToSave is EditScreenState.None) return

        // Simple validation check before launch (detailed checks are reactive in UI)
        val hasError = when (stateToSave) {
            is EditScreenState.CreateNodeSchema -> stateToSave.state.tableNameError != null
            else -> false
        }
        if (hasError) return

        viewModelScope.launch {
            when (stateToSave) {
                is EditScreenState.CreateNode -> repository.createNode(stateToSave.state)
                is EditScreenState.CreateEdge -> repository.createEdge(stateToSave.state)
                is EditScreenState.CreateNodeSchema -> repository.createNodeSchema(stateToSave.state)
                is EditScreenState.CreateEdgeSchema -> repository.createEdgeSchema(stateToSave.state)
                is EditScreenState.EditNode -> repository.updateNode(stateToSave.state)
                is EditScreenState.EditEdge -> repository.updateEdge(stateToSave.state)
                is EditScreenState.EditNodeSchema -> repository.updateNodeSchema(stateToSave.state)
                is EditScreenState.EditEdgeSchema -> repository.updateEdgeSchema(stateToSave.state)
                is EditScreenState.None -> {}
            }
            cancelAllEditing()
            metadataViewModel.clearSelectedItem()
            _navigationEvent.emit(Unit)
        }
    }

    fun cancelAllEditing() { _editScreenState.value = EditScreenState.None }

    // --- Node Creation ---
    fun initiateNodeCreation(schema: SchemaDefinition? = null) {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        metadataViewModel.clearSelectedItem()
        val selected = if (schema != null) listOf(schema) else emptyList()
        _editScreenState.value = EditScreenState.CreateNode(NodeCreationState(availableSchemas = nodeSchemas, selectedSchemas = selected))
    }

    fun toggleNodeCreationSchema(schemaNode: SchemaDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            val currentSelected = current.state.selectedSchemas
            val newSelected = if (currentSelected.contains(schemaNode)) {
                currentSelected - schemaNode
            } else {
                currentSelected + schemaNode
            }
            current.copy(state = current.state.copy(selectedSchemas = newSelected))
        }
    }

    fun toggleNodeEditSchema(schemaNode: SchemaDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current
            val currentSelected = current.state.schemas
            val newSelected = if (currentSelected.any { it.id == schemaNode.id }) {
                currentSelected.filter { it.id != schemaNode.id }
            } else {
                currentSelected + schemaNode
            }
            // Retain properties for schemas that are still selected
            // Properties for removed schemas will be implicitly ignored on save if we rebuild the property map,
            // but the current implementation of `updateNode` in Repo looks at `state.properties` and matches them to `state.schemas`.
            // So we just update the schema list.
            current.copy(state = current.state.copy(schemas = newSelected))
        }
    }

    fun updateNodeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation(schema: SchemaDefinition? = null) {
        val edgeSchemas = schemaViewModel.schema.value?.edgeSchemas ?: emptyList()
        if (metadataViewModel.nodeList.value.isEmpty()) metadataViewModel.listNodes()
        metadataViewModel.clearSelectedItem()

        val initialState = EdgeCreationState(schemas = edgeSchemas, availableNodes = metadataViewModel.nodeList.value)
        _editScreenState.value = EditScreenState.CreateEdge(initialState)

        if(schema != null) updateEdgeCreationSchema(schema)
    }

    fun updateEdgeCreationSchema(schemaEdge: SchemaDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current

            // Auto-populate participants for Roles
            val initialParticipants = mutableListOf<ParticipantSelection>()
            schemaEdge.roles.forEach { role ->
                if (role.cardinality == RelationCardinality.ONE) {
                    initialParticipants.add(ParticipantSelection(id = Uuid.random().toString(), role = role.name))
                }
            }

            current.copy(state = current.state.copy(selectedSchema = schemaEdge, participants = initialParticipants, properties = emptyMap()))
        }
    }

    fun addEdgeCreationParticipant(roleName: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newSelection = ParticipantSelection(id = Uuid.random().toString(), role = roleName)
            current.copy(state = current.state.copy(participants = current.state.participants + newSelection))
        }
    }

    fun removeEdgeCreationParticipant(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newList = current.state.participants.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(participants = newList))
        }
    }

    fun updateEdgeCreationParticipantNode(index: Int, node: NodeDisplayItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newList = current.state.participants.toMutableList()
            if (index in newList.indices) {
                newList[index] = newList[index].copy(node = node)
            }
            current.copy(state = current.state.copy(participants = newList))
        }
    }

    fun updateEdgeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        _editScreenState.value = EditScreenState.CreateNodeSchema(NodeSchemaCreationState())
    }

    fun onNodeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(tableName = name, tableNameError = isSchemaNameUnique(name)))
        }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProps = current.state.properties.toMutableList().apply { this[index] = property }
            current.copy(state = current.state.copy(properties = newProps))
        }
    }

    fun onAddNodeSchemaProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }

    fun onRemoveNodeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProps = current.state.properties.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(properties = newProps))
        }
    }

    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        val defaultRoles = listOf(
            RoleDefinition(name = "Source", direction = RelationDirection.SOURCE, cardinality = RelationCardinality.ONE),
            RoleDefinition(name = "Target", direction = RelationDirection.TARGET, cardinality = RelationCardinality.ONE)
        )
        _editScreenState.value = EditScreenState.CreateEdgeSchema(EdgeSchemaCreationState(allNodeSchemas = nodeSchemas, roles = defaultRoles))
    }

    fun onEdgeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(tableName = name, tableNameError = isSchemaNameUnique(name)))
        }
    }

    fun onAddEdgeSchemaRole(role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(roles = current.state.roles + role))
        }
    }

    fun onRemoveEdgeSchemaRole(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(roles = newRoles))
        }
    }

    fun onEdgeSchemaRoleChange(index: Int, role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList().apply { this[index] = role }
            current.copy(state = current.state.copy(roles = newRoles))
        }
    }

    fun onEdgeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { this[index] = property }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun onAddEdgeSchemaProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }

    fun onRemoveEdgeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Node Editing ---
    fun initiateNodeEdit(item: NodeDisplayItem) {
        viewModelScope.launch {
            val editState = repository.getNodeEditState(item.id)
            if (editState != null) _editScreenState.value = EditScreenState.EditNode(editState)
        }
    }

    fun updateNodeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Editing ---
    fun initiateEdgeEdit(item: EdgeDisplayItem) {
        viewModelScope.launch {
            val editState = repository.getEdgeEditState(item)
            if (editState != null) _editScreenState.value = EditScreenState.EditEdge(editState)
        }
    }

    fun updateEdgeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdge) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Schema Editing Stubs (simplified for Phase 2) ---
    fun initiateNodeSchemaEdit(schema: SchemaDefinition) {
        _editScreenState.value = EditScreenState.EditNodeSchema(NodeSchemaEditState(originalSchema = schema, currentName = schema.name, properties = schema.properties))
    }

    fun updateNodeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            current.copy(state = current.state.copy(currentName = label))
        }
    }

    fun updateNodeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProps = current.state.properties.toMutableList().apply { this[index] = property }
            current.copy(state = current.state.copy(properties = newProps))
        }
    }

    fun updateNodeSchemaEditAddProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }

    fun updateNodeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProps = current.state.properties.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(properties = newProps))
        }
    }

    fun initiateEdgeSchemaEdit(schema: SchemaDefinition) {
        val allNodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        _editScreenState.value = EditScreenState.EditEdgeSchema(EdgeSchemaEditState(originalSchema = schema, currentName = schema.name, roles = schema.roles, properties = schema.properties, allNodeSchemas = allNodeSchemas))
    }

    fun updateEdgeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(currentName = label))
        }
    }

    fun updateEdgeSchemaEditRole(index: Int, role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList().apply { this[index] = role }
            current.copy(state = current.state.copy(roles = newRoles))
        }
    }

    fun updateEdgeSchemaEditAddRole(role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(roles = current.state.roles + role))
        }
    }

    fun updateEdgeSchemaEditRemoveRole(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(roles = newRoles))
        }
    }

    fun updateEdgeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { this[index] = property }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateEdgeSchemaEditAddProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }

    fun updateEdgeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
}