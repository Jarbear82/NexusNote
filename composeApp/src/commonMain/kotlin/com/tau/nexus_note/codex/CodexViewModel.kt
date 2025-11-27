package com.tau.nexus_note.codex

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.SqliteDbService
import com.tau.nexus_note.codex.crud.EditCreateViewModel
import com.tau.nexus_note.codex.graph.GraphViewmodel
import com.tau.nexus_note.codex.metadata.MetadataViewModel
import com.tau.nexus_note.codex.schema.SchemaViewModel
import com.tau.nexus_note.datamodels.EditScreenState
import com.tau.nexus_note.settings.SettingsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.tau.nexus_note.doc_parser.MarkdownExporter
import java.io.File

class CodexViewModel(
    private val dbService: SqliteDbService,
    private val settingsFlow: StateFlow<SettingsData>
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // 1. Create the Repository
    val repository = CodexRepository(dbService, viewModelScope)

    // 2. Create child ViewModels
    val schemaViewModel = SchemaViewModel(repository, viewModelScope)
    val metadataViewModel = MetadataViewModel(repository, viewModelScope)
    val editCreateViewModel = EditCreateViewModel(repository, viewModelScope, schemaViewModel, metadataViewModel)

    val graphViewModel = GraphViewmodel(
        viewModelScope = viewModelScope,
        settingsFlow = settingsFlow,
        mediaPath = repository.mediaDirectoryPath // CHANGED: Pass media path instead of dbPath
    )

    // Expose Repository Error Flow
    val errorFlow = repository.errorFlow
    fun clearError() = repository.clearError()

    // --- Layout State ---
    private val _isDetailPaneOpen = MutableStateFlow(false)
    val isDetailPaneOpen = _isDetailPaneOpen.asStateFlow()

    // --- Export State ---
    private val _showExportDirPicker = MutableStateFlow(false)
    val showExportDirPicker = _showExportDirPicker.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshAll()
        }

        // Auto-open detail pane when an item is selected
        viewModelScope.launch {
            metadataViewModel.primarySelectedItem.collectLatest {
                if (it != null) {
                    _isDetailPaneOpen.value = true
                    if (editCreateViewModel.editScreenState.value is EditScreenState.None) {
                        selectDataTab(DataViewTabs.METADATA)
                    }
                }
            }
        }

        // Auto-open detail pane when editing starts
        viewModelScope.launch {
            editCreateViewModel.editScreenState.collectLatest {
                if (it !is EditScreenState.None) {
                    _isDetailPaneOpen.value = true
                    selectDataTab(DataViewTabs.EDIT)
                }
            }
        }

        // Combine lists with visibility state & Pass to Graph
        viewModelScope.launch {
            combine(
                metadataViewModel.nodeList,
                metadataViewModel.edgeList,
                metadataViewModel.nodeVisibility,
                metadataViewModel.edgeVisibility
            ) { nodes, edges, nodeViz, edgeViz ->
                // Basic visibility filtering
                val visibleNodes = nodes.filter { nodeViz[it.id] ?: true }
                val visibleNodeIds = visibleNodes.map { it.id }.toSet()
                val visibleEdges = edges.filter {
                    (edgeViz[it.id] ?: true) &&
                            (it.src.id in visibleNodeIds) &&
                            (it.dst.id in visibleNodeIds)
                }
                // GraphViewModel handles the complex "Collapse/Hide" logic internally now
                // We pass the "potentially visible" set
                visibleNodes to visibleEdges
            }.collectLatest { (visibleNodes, visibleEdges) ->
                graphViewModel.updateGraphData(visibleNodes, visibleEdges)
            }
        }

        // Correlate Schema visibility with Item visibility
        viewModelScope.launch {
            schemaViewModel.schemaVisibility.collectLatest { schemaVizMap ->
                schemaVizMap.forEach { (schemaId, isVisible) ->
                    val isNodeSchema = repository.schema.value?.nodeSchemas?.any { it.id == schemaId } ?: false
                    if (isNodeSchema) {
                        metadataViewModel.setNodeVisibilityForSchema(schemaId, isVisible)
                    } else {
                        metadataViewModel.setEdgeVisibilityForSchema(schemaId, isVisible)
                    }
                }
            }
        }
    }

    private val _selectedDataTab = MutableStateFlow(DataViewTabs.SCHEMA)
    val selectedDataTab = _selectedDataTab.asStateFlow()

    fun selectDataTab(tab: DataViewTabs) {
        if (_selectedDataTab.value == DataViewTabs.EDIT && tab != DataViewTabs.EDIT) {
            editCreateViewModel.cancelAllEditing()
            metadataViewModel.clearSelectedItem()
        }
        _selectedDataTab.value = tab
    }

    private val _selectedViewTab = MutableStateFlow(ViewTabs.GRAPH)
    val selectedViewTab = _selectedViewTab.asStateFlow()

    fun selectViewTab(tab: ViewTabs) {
        _selectedViewTab.value = tab
    }

    fun openDetailPane() {
        _isDetailPaneOpen.value = true
    }

    fun closeDetailPane() {
        _isDetailPaneOpen.value = false
        metadataViewModel.clearSelectedItem()
        editCreateViewModel.cancelAllEditing()
    }

    fun onCleared() {
        graphViewModel.onCleared()
        dbService.close()
        viewModelScope.cancel()
    }

    // --- Export Logic ---

    fun onExportClicked(saveAs: Boolean) {
        if (saveAs) {
            _showExportDirPicker.value = true
        } else {
            // "Save" - overwrite current location if possible
            // We assume the parent dir of the DB is the project root for now?
            // Or typically "Save" for an import-based workflow implies writing back to source.
            // However, we don't track original source paths of imported files perfectly in the DB metadata yet.
            // Fallback: Just trigger Save As if we don't know where to save.
            // For now, let's assume we save alongside the DB file in an "Export" folder to be safe.
            val dbFile = File(repository.dbPath)
            performExport(dbFile.parent)
        }
    }

    fun onExportDirSelected(path: String?) {
        _showExportDirPicker.value = false
        if (path != null) {
            performExport(path)
        }
    }

    private fun performExport(path: String) {
        viewModelScope.launch {
            try {
                val exporter = MarkdownExporter(repository)
                exporter.export(path)
                // Show success message (using error flow for convenience or add notification flow)
                // _errorFlow.value = "Export successful to $path"
            } catch (e: Exception) {
                // _errorFlow.value = "Export failed: ${e.message}"
            }
        }
    }
}

enum class DataViewTabs(val value: Int) {
    METADATA(0),
    SCHEMA(1),
    EDIT(2)
}

enum class ViewTabs(val value: Int) {
    LIST(0),
    GRAPH(1)
}