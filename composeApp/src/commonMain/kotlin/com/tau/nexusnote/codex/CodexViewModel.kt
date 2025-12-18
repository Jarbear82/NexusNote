package com.tau.nexusnote.codex

import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.SqliteDbService
import com.tau.nexusnote.codex.crud.EditCreateViewModel
import com.tau.nexusnote.codex.graph.GraphViewmodel
import com.tau.nexusnote.codex.metadata.MetadataViewModel
import com.tau.nexusnote.codex.schema.SchemaViewModel
import com.tau.nexusnote.datamodels.EditScreenState
import com.tau.nexusnote.settings.SettingsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job

class CodexViewModel(
    private val dbService: SqliteDbService,
    private val settingsFlow: StateFlow<SettingsData>,
    val repository: CodexRepository,
    val schemaViewModel: SchemaViewModel,
    val metadataViewModel: MetadataViewModel,
    val editCreateViewModel: EditCreateViewModel,
    val graphViewModel: GraphViewmodel
) {
    private val viewModelScope = repository.coroutineScope

    // Expose Repository Error Flow
    val errorFlow = repository.errorFlow
    fun clearError() = repository.clearError()

    // --- Layout State ---
    // Explicitly tracks if the side panel/drawer is open
    private val _isDetailPaneOpen = MutableStateFlow(false)
    val isDetailPaneOpen = _isDetailPaneOpen.asStateFlow()

    init {
        // ...
        viewModelScope.launch {
            repository.refreshAll()
        }

        // Auto-open detail pane when an item is selected
        viewModelScope.launch {
            metadataViewModel.primarySelectedItem.collectLatest {
                if (it != null) {
                    _isDetailPaneOpen.value = true
                    // Switch to metadata tab if we are not in edit mode
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

        // Combine lists with visibility state
        viewModelScope.launch {
            combine(
                metadataViewModel.nodeList,
                metadataViewModel.edgeList,
                metadataViewModel.nodeVisibility,
                metadataViewModel.edgeVisibility
            ) { nodes, edges, nodeViz, edgeViz ->
                val visibleNodes = nodes.filter { nodeViz[it.id] ?: true }
                val visibleNodeIds = visibleNodes.map { it.id }.toSet()

                val visibleEdges = edges.filter { edge ->
                    val isExplicitlyVisible = edgeViz[edge.id] ?: true
                    // For N-nary edges, the edge is visible if explicitly set AND
                    // at least one participant is visible (to avoid floating orphan edges)
                    // Updated to access the node inside the participant wrapper
                    val hasVisibleParticipants = edge.participatingNodes.any { it.node.id in visibleNodeIds }

                    isExplicitlyVisible && hasVisibleParticipants
                }

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

    // --- Layout Handlers ---

    fun openDetailPane() {
        _isDetailPaneOpen.value = true
    }

    fun closeDetailPane() {
        _isDetailPaneOpen.value = false
        // Optional: Clear selection when closing drawer
        metadataViewModel.clearSelectedItem()
        editCreateViewModel.cancelAllEditing()
    }

    fun onCleared() {
        graphViewModel.onCleared()
        dbService.close()
        viewModelScope.cancel()
    }

    companion object {
        fun create(
            dbService: SqliteDbService,
            settingsFlow: StateFlow<SettingsData>
        ): CodexViewModel {
            val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            val repository = CodexRepository(dbService, scope)
            val schemaViewModel = SchemaViewModel(repository, scope)
            val metadataViewModel = MetadataViewModel(repository, scope)
            val editCreateViewModel = EditCreateViewModel(repository, scope, schemaViewModel, metadataViewModel)
            val graphViewModel = GraphViewmodel(
                viewModelScope = scope,
                settingsFlow = settingsFlow,
                repository = repository
            )

            return CodexViewModel(
                dbService,
                settingsFlow,
                repository,
                schemaViewModel,
                metadataViewModel,
                editCreateViewModel,
                graphViewModel
            )
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