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
            conflictingSchema == null -> null // No conflict
            editingId != null && conflictingSchema.id == editingId -> null // Conflict is with itself
            else -> "Name is already used by another schema."
        }
    }

    private fun validateProperty(
        index: Int,
        property: SchemaProperty,
        allProperties: List<SchemaProperty>
    ): String? {
        if (property.name.isBlank()) return "Name cannot be blank."

        val conflict = allProperties.withIndex().find { (i, p) ->
            i != index && p.name.equals(property.name, ignoreCase = false)
        }
        return if (conflict != null) "Name is already used in this schema." else null
    }

    private fun validateRole(
        index: Int,
        role: RoleDefinition,
        allRoles: List<RoleDefinition>
    ): String? {
        if (role.name.isBlank()) return "Role name cannot be blank."
        val conflict = allRoles.withIndex().find { (i, r) ->
            i != index && r.name.equals(role.name, ignoreCase = true)
        }
        return if (conflict != null) "Role name must be unique." else null
    }


    fun saveCurrentState() {
        val stateToSave = _editScreenState.value
        if (stateToSave is EditScreenState.None) return

        val hasError = when (stateToSave) {
            is EditScreenState.CreateNodeSchema -> stateToSave.state.tableNameError != null || stateToSave.state.propertyErrors.any { it.value != null }
            is EditScreenState.EditNodeSchema -> stateToSave.state.currentNameError != null || stateToSave.state.propertyErrors.any { it.value != null }
            is EditScreenState.CreateEdgeSchema -> stateToSave.state.tableNameError != null || stateToSave.state.propertyErrors.any { it.value != null } || stateToSave.state.roleErrors.any { it.value != null }
            is EditScreenState.EditEdgeSchema -> stateToSave.state.currentNameError != null || stateToSave.state.propertyErrors.any { it.value != null } || stateToSave.state.roleErrors.any { it.value != null }
            else -> false
        }

        if (hasError) {
            println("Save aborted due to validation errors.")
            return
        }

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

    fun cancelAllEditing() {
        _editScreenState.value = EditScreenState.None
    }

    // --- Node Creation ---
    fun initiateNodeCreation(schema: SchemaDefinitionItem? = null) {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateNode(
            NodeCreationState(schemas = nodeSchemas, selectedSchema = schema)
        )
    }

    fun updateNodeCreationSchema(schemaNode: SchemaDefinitionItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            current.copy(
                state = current.state.copy(
                    selectedSchema = schemaNode,
                    properties = emptyMap()
                )
            )
        }
    }

    fun updateNodeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            val newProperties = current.state.properties.toMutableMap().apply {
                this[key] = value
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        val edgeSchemas = schemaViewModel.schema.value?.edgeSchemas ?: emptyList()
        if (metadataViewModel.nodeList.value.isEmpty()) {
            metadataViewModel.listNodes()
        }
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateEdge(
            EdgeCreationState(schemas = edgeSchemas, availableNodes = metadataViewModel.nodeList.value)
        )
    }

    fun initiateEdgeCreation(schema: SchemaDefinitionItem) {
        initiateEdgeCreation()
        updateEdgeCreationSchema(schema)
    }

    fun updateEdgeCreationSchema(schemaEdge: SchemaDefinitionItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current

            // Initializing participants based on Roles
            val initialParticipants = mutableListOf<ParticipantSelection>()

            schemaEdge.roleDefinitions?.forEach { role ->
                // Automatically add one empty slot for "One" or "Exact" roles
                if (role.cardinality is RoleCardinality.One) {
                    initialParticipants.add(ParticipantSelection(
                        id = Uuid.random().toString(),
                        role = role.name,
                        node = null
                    ))
                }
                // For "Many", we start with 0, user adds them manually.
                // Alternatively, could start with 1 empty one.
            }

            current.copy(
                state = current.state.copy(
                    selectedSchema = schemaEdge,
                    participants = initialParticipants,
                    properties = emptyMap()
                )
            )
        }
    }

    fun addEdgeCreationParticipant(roleName: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newSelection = ParticipantSelection(
                id = Uuid.random().toString(),
                role = roleName,
                node = null
            )
            current.copy(state = current.state.copy(participants = current.state.participants + newSelection))
        }
    }

    fun removeEdgeCreationParticipant(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newList = current.state.participants.toMutableList().apply {
                removeAt(index)
            }
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
            val newProperties = current.state.properties.toMutableMap().apply {
                this[key] = value
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        _editScreenState.value = EditScreenState.CreateNodeSchema(
            NodeSchemaCreationState(
                properties = listOf(SchemaProperty("name", CodexPropertyDataTypes.TEXT, isDisplayProperty = true))
            )
        )
    }

    fun onNodeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val error = isSchemaNameUnique(name)
            current.copy(state = current.state.copy(tableName = name, tableNameError = error))
        }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            val finalProperties = if (property.isDisplayProperty) {
                newProperties.mapIndexed { i, p -> if (i == index) p else p.copy(isDisplayProperty = false) }
            } else newProperties

            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)

            current.copy(state = current.state.copy(properties = finalProperties, propertyErrors = newErrors))
        }
    }

    fun onAddNodeSchemaProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val currentProps = if (property.isDisplayProperty) {
                current.state.properties.map { it.copy(isDisplayProperty = false) }
            } else current.state.properties
            current.copy(state = current.state.copy(properties = currentProps + property))
        }
    }

    fun onRemoveNodeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        // Start with basic source/target roles as defaults
        val defaultRoles = listOf(
            RoleDefinition("Source", emptyList(), RoleCardinality.One),
            RoleDefinition("Target", emptyList(), RoleCardinality.One)
        )
        _editScreenState.value = EditScreenState.CreateEdgeSchema(
            EdgeSchemaCreationState(allNodeSchemas = nodeSchemas, roles = defaultRoles)
        )
    }

    fun onEdgeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val error = isSchemaNameUnique(name)
            current.copy(state = current.state.copy(tableName = name, tableNameError = error))
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
            val error = validateRole(index, role, newRoles)
            val newErrors = current.state.roleErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)

            current.copy(state = current.state.copy(roles = newRoles, roleErrors = newErrors))
        }
    }

    fun onEdgeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { this[index] = property }
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = newProperties, propertyErrors = newErrors))
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
            if (editState != null) {
                _editScreenState.value = EditScreenState.EditNode(editState)
            }
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
            if (editState != null) {
                _editScreenState.value = EditScreenState.EditEdge(editState)
            } else {
                println("Could not initiate edit for edge ${item.id}.")
            }
        }
    }

    fun updateEdgeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdge) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Node Schema Editing ---
    fun initiateNodeSchemaEdit(schema: SchemaDefinitionItem) {
        _editScreenState.value = EditScreenState.EditNodeSchema(
            NodeSchemaEditState(
                originalSchema = schema,
                currentName = schema.name,
                properties = schema.properties
            )
        )
    }

    fun updateNodeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val error = isSchemaNameUnique(label, current.state.originalSchema.id)
            current.copy(state = current.state.copy(currentName = label, currentNameError = error))
        }
    }

    fun updateNodeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { this[index] = property }
            val finalProperties = if (property.isDisplayProperty) {
                newProperties.mapIndexed { i, p -> if (i == index) p else p.copy(isDisplayProperty = false) }
            } else newProperties
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = finalProperties, propertyErrors = newErrors))
        }
    }

    fun updateNodeSchemaEditAddProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val currentProps = if (property.isDisplayProperty) {
                current.state.properties.map { it.copy(isDisplayProperty = false) }
            } else current.state.properties
            current.copy(state = current.state.copy(properties = currentProps + property))
        }
    }

    fun updateNodeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Schema Editing ---
    fun initiateEdgeSchemaEdit(schema: SchemaDefinitionItem) {
        val allNodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        _editScreenState.value = EditScreenState.EditEdgeSchema(
            EdgeSchemaEditState(
                originalSchema = schema,
                currentName = schema.name,
                roles = schema.roleDefinitions ?: emptyList(),
                properties = schema.properties,
                allNodeSchemas = allNodeSchemas
            )
        )
    }

    fun updateEdgeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val error = isSchemaNameUnique(label, current.state.originalSchema.id)
            current.copy(state = current.state.copy(currentName = label, currentNameError = error))
        }
    }

    fun updateEdgeSchemaEditRole(index: Int, role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList().apply { this[index] = role }
            val error = validateRole(index, role, newRoles)
            val newErrors = current.state.roleErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(roles = newRoles, roleErrors = newErrors))
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
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = newProperties, propertyErrors = newErrors))
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