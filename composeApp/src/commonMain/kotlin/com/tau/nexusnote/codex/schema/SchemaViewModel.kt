package com.tau.nexusnote.codex.schema

import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.datamodels.SchemaDefinition
import com.tau.nexusnote.datamodels.SchemaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Manages the state for the Schema visualization and management screens.
 */
class SchemaViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope
) {
    // Segregate Node and Relation schemas for the UI layer
    val schema: StateFlow<SchemaData?> = repository.schema.map { data ->
        data?.let {
            SchemaData(
                nodeSchemas = it.nodeSchemas,
                edgeSchemas = it.edgeSchemas // Relation schemas
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _schemaToDelete = MutableStateFlow<SchemaDefinition?>(null)
    val schemaToDelete = _schemaToDelete.asStateFlow()

    private val _schemaDependencyCount = MutableStateFlow(0L)
    val schemaDependencyCount = _schemaDependencyCount.asStateFlow()

    private val _nodeSchemaSearchText = MutableStateFlow("")
    val nodeSchemaSearchText = _nodeSchemaSearchText.asStateFlow()

    private val _edgeSchemaSearchText = MutableStateFlow("")
    val edgeSchemaSearchText = _edgeSchemaSearchText.asStateFlow()

    private val _schemaVisibility = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val schemaVisibility = _schemaVisibility.asStateFlow()

    fun showSchema() {
        viewModelScope.launch { repository.refreshSchema() }
    }

    fun requestDeleteSchema(item: SchemaDefinition) {
        viewModelScope.launch {
            val totalCount = repository.getSchemaDependencyCount(item.id)
            if (totalCount == 0L) {
                repository.deleteSchema(item.id)
                clearDeleteSchemaRequest()
            } else {
                _schemaDependencyCount.value = totalCount
                _schemaToDelete.value = item
            }
        }
    }

    fun confirmDeleteSchema() {
        viewModelScope.launch {
            val item = _schemaToDelete.value ?: return@launch
            repository.deleteSchema(item.id)
            clearDeleteSchemaRequest()
        }
    }

    fun clearDeleteSchemaRequest() {
        _schemaToDelete.value = null
        _schemaDependencyCount.value = 0
    }

    fun onNodeSchemaSearchChange(text: String) { _nodeSchemaSearchText.value = text }
    fun onEdgeSchemaSearchChange(text: String) { _edgeSchemaSearchText.value = text }

    fun toggleSchemaVisibility(schemaId: Long) {
        _schemaVisibility.update {
            val newMap = it.toMutableMap()
            newMap[schemaId] = !(it[schemaId] ?: true)
            newMap
        }
    }
}