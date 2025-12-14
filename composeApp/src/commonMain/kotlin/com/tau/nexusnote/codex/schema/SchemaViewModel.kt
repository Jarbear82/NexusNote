package com.tau.nexusnote.codex.schema

import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchemaData(
    val nodeSchemas: List<SchemaDefinitionItem>,
    val edgeSchemas: List<SchemaDefinitionItem>
)

class SchemaViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope
) {
    // --- State is now observed from the repository ---
    val schema = repository.schema

    // --- State for managing the delete confirmation dialog (This is UI state) ---
    private val _schemaToDelete = MutableStateFlow<SchemaDefinitionItem?>(null)
    val schemaToDelete = _schemaToDelete.asStateFlow()

    private val _schemaDependencyCount = MutableStateFlow(0L)
    val schemaDependencyCount = _schemaDependencyCount.asStateFlow()

    // --- Search State ---
    private val _nodeSchemaSearchText = MutableStateFlow("")
    val nodeSchemaSearchText = _nodeSchemaSearchText.asStateFlow()

    private val _edgeSchemaSearchText = MutableStateFlow("")
    val edgeSchemaSearchText = _edgeSchemaSearchText.asStateFlow()

    // --- Visibility State ---
    private val _schemaVisibility = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val schemaVisibility = _schemaVisibility.asStateFlow()


    fun showSchema() {
        // Launch a coroutine to call the suspend function
        viewModelScope.launch {
            repository.refreshSchema()
        }
    }

    fun requestDeleteSchema(item: SchemaDefinitionItem) {
        viewModelScope.launch {
            val totalCount = repository.getSchemaDependencyCount(item.id)

            if (totalCount == 0L) {
                // No dependencies, delete immediately
                repository.deleteSchema(item.id)
                clearDeleteSchemaRequest() // Clear state just in case
            } else if (totalCount > 0L) {
                // Dependencies found, show dialog
                _schemaDependencyCount.value = totalCount
                _schemaToDelete.value = item
            }
            // else (count == -1L) an error occurred, do nothing
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

    // --- Search Handlers ---
    fun onNodeSchemaSearchChange(text: String) {
        _nodeSchemaSearchText.value = text
    }

    fun onEdgeSchemaSearchChange(text: String) {
        _edgeSchemaSearchText.value = text
    }

    // --- Toggle Function ---
    fun toggleSchemaVisibility(schemaId: Long) {
        _schemaVisibility.update {
            val newMap = it.toMutableMap()
            newMap[schemaId] = !(it[schemaId] ?: true) // Default to visible
            newMap
        }
    }
}