package com.tau.nexusnote.codex

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.codex.crud.update.EditItemView
import com.tau.nexusnote.codex.graph.DetangleSettingsDialog
import com.tau.nexusnote.codex.graph.GraphView
import com.tau.nexusnote.codex.metadata.MetadataView
import com.tau.nexusnote.codex.schema.SchemaView
import com.tau.nexusnote.ui.components.CodexAlertDialog
import com.tau.nexusnote.ui.components.TwoPaneLayout
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CodexView(viewModel: CodexViewModel) {
    // Observe state
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    val paginatedNodes by viewModel.metadataViewModel.paginatedNodes.collectAsState()
    val paginatedEdges by viewModel.metadataViewModel.paginatedEdges.collectAsState()

    val primarySelectedItem by viewModel.metadataViewModel.primarySelectedItem.collectAsState()
    val secondarySelectedItem by viewModel.metadataViewModel.secondarySelectedItem.collectAsState()
    val schemaToDelete by viewModel.schemaViewModel.schemaToDelete.collectAsState()
    val dependencyCount by viewModel.schemaViewModel.schemaDependencyCount.collectAsState()
    val editScreenState by viewModel.editCreateViewModel.editScreenState.collectAsState()
    val selectedDataTab by viewModel.selectedDataTab.collectAsState()
    val selectedViewTab by viewModel.selectedViewTab.collectAsState()

    // Layout State
    val isDetailPaneOpen by viewModel.isDetailPaneOpen.collectAsState()

    val graphViewModel = viewModel.graphViewModel
    val showDetangleDialog by graphViewModel.showDetangleDialog.collectAsState()

    // Search & Visibility States
    val nodeSearchText by viewModel.metadataViewModel.nodeSearchText.collectAsState()
    val edgeSearchText by viewModel.metadataViewModel.edgeSearchText.collectAsState()
    val nodeSchemaSearchText by viewModel.schemaViewModel.nodeSchemaSearchText.collectAsState()
    val edgeSchemaSearchText by viewModel.schemaViewModel.edgeSchemaSearchText.collectAsState()
    val nodeVisibility by viewModel.metadataViewModel.nodeVisibility.collectAsState()
    val edgeVisibility by viewModel.metadataViewModel.edgeVisibility.collectAsState()
    val schemaVisibility by viewModel.schemaViewModel.schemaVisibility.collectAsState()

    LaunchedEffect(viewModel.editCreateViewModel) {
        viewModel.editCreateViewModel.navigationEventFlow.collectLatest {
            viewModel.selectDataTab(DataViewTabs.SCHEMA)
        }
    }

    LaunchedEffect(selectedViewTab, graphViewModel) {
        if (selectedViewTab == ViewTabs.GRAPH) {
            graphViewModel.startSimulation()
        } else {
            graphViewModel.stopSimulation()
        }
    }

    val onSave: () -> Unit = {
        viewModel.editCreateViewModel.saveCurrentState()
        viewModel.metadataViewModel.refreshPaginatedLists()
    }

    val onCancel: () -> Unit = {
        viewModel.editCreateViewModel.cancelAllEditing()
        viewModel.metadataViewModel.clearSelectedItem()
        viewModel.selectDataTab(DataViewTabs.SCHEMA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TwoPaneLayout(
            showDetailOnMobile = isDetailPaneOpen,
            onDismissRequest = { viewModel.closeDetailPane() },
            listContent = {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        PrimaryTabRow(selectedTabIndex = selectedViewTab.value) {
                            ViewTabs.entries.forEach { tab ->
                                Tab(
                                    text = { Text(tab.name) },
                                    selected = selectedViewTab.value == tab.value,
                                    onClick = { viewModel.selectViewTab(tab) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        when (selectedViewTab) {
                            ViewTabs.LIST -> {
                                ListView(
                                    paginatedNodes = paginatedNodes,
                                    paginatedEdges = paginatedEdges,
                                    onLoadMoreNodes = viewModel.metadataViewModel::loadMoreNodes,
                                    onLoadMoreEdges = viewModel.metadataViewModel::loadMoreEdges,
                                    primarySelectedItem = primarySelectedItem,
                                    secondarySelectedItem = secondarySelectedItem,
                                    onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                                    onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                                    onEditNodeClick = { item ->
                                        viewModel.editCreateViewModel.initiateNodeEdit(item)
                                        viewModel.selectDataTab(DataViewTabs.EDIT)
                                    },
                                    onEditEdgeClick = { item ->
                                        viewModel.editCreateViewModel.initiateEdgeEdit(item)
                                        viewModel.selectDataTab(DataViewTabs.EDIT)
                                    },
                                    onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                                    onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                                    onAddNodeClick = {
                                        viewModel.editCreateViewModel.initiateNodeCreation()
                                        viewModel.selectDataTab(DataViewTabs.EDIT)
                                    },
                                    onAddEdgeClick = {
                                        viewModel.editCreateViewModel.initiateEdgeCreation()
                                        viewModel.selectDataTab(DataViewTabs.EDIT)
                                    },
                                    nodeSearchText = nodeSearchText,
                                    onNodeSearchChange = viewModel.metadataViewModel::onNodeSearchChange,
                                    edgeSearchText = edgeSearchText,
                                    onEdgeSearchChange = viewModel.metadataViewModel::onEdgeSearchChange,
                                    nodeVisibility = nodeVisibility,
                                    onToggleNodeVisibility = viewModel.metadataViewModel::toggleNodeVisibility,
                                    edgeVisibility = edgeVisibility,
                                    onToggleEdgeVisibility = viewModel.metadataViewModel::toggleEdgeVisibility
                                )
                            }

                            ViewTabs.GRAPH -> {
                                graphViewModel.let { vm ->
                                    val nodesState by vm.graphNodes.collectAsState()
                                    val edgesState by vm.graphEdges.collectAsState()
                                    val primaryId = (primarySelectedItem as? NodeDisplayItem)?.id
                                    val secondaryId = (secondarySelectedItem as? NodeDisplayItem)?.id

                                    GraphView(
                                        viewModel = vm,
                                        nodes = nodesState,
                                        edges = edgesState,
                                        primarySelectedId = primaryId,
                                        secondarySelectedId = secondaryId,
                                        onNodeTap = { nodeId ->
                                            val node =
                                                viewModel.metadataViewModel.nodeList.value.find { it.id == nodeId }
                                            if (node != null) {
                                                viewModel.metadataViewModel.selectItem(node)
                                            }
                                        },
                                        onAddNodeClick = {
                                            viewModel.editCreateViewModel.initiateNodeCreation()
                                            viewModel.selectDataTab(DataViewTabs.EDIT)
                                        },
                                        onAddEdgeClick = {
                                            viewModel.editCreateViewModel.initiateEdgeCreation()
                                            viewModel.selectDataTab(DataViewTabs.EDIT)
                                        },
                                        onDetangleClick = { vm.onShowDetangleDialog() }
                                    )
                                }
                            }
                        }
                    }

                    // Mobile-only FAB
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        if (maxWidth <= 700.dp && !isDetailPaneOpen) {
                            FloatingActionButton(
                                onClick = { viewModel.openDetailPane() },
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Info")
                            }
                        }
                    }
                }
            },
            detailContent = {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Dialog handling
                    val itemToDelete = schemaToDelete
                    if (itemToDelete != null && selectedDataTab == DataViewTabs.SCHEMA) {
                        val text = if (dependencyCount == 0L) {
                            "Are you sure you want to delete the schema '${itemToDelete.name}'?"
                        } else {
                            "Warning: This schema is used by $dependencyCount node(s) or edge(s). Deleting '${itemToDelete.name}' will also delete all of them.\n\nAre you sure you want to continue?"
                        }

                        CodexAlertDialog(
                            title = "Delete Schema?",
                            text = text,
                            confirmLabel = "Delete",
                            onConfirm = { viewModel.schemaViewModel.confirmDeleteSchema() },
                            onDismiss = { viewModel.schemaViewModel.clearDeleteSchemaRequest() },
                            isDestructive = true
                        )
                    }

                    PrimaryTabRow(selectedTabIndex = selectedDataTab.value) {
                        DataViewTabs.entries.forEach { tab ->
                            Tab(
                                text = { Text(tab.name) },
                                selected = selectedDataTab.value == tab.value,
                                onClick = { viewModel.selectDataTab(tab) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    when (selectedDataTab) {
                        DataViewTabs.METADATA -> MetadataView(
                            nodes = paginatedNodes,
                            edges = paginatedEdges,
                            primarySelectedItem = primarySelectedItem,
                            secondarySelectedItem = secondarySelectedItem,
                            onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEditNodeClick = { item ->
                                viewModel.editCreateViewModel.initiateNodeEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onEditEdgeClick = { item ->
                                viewModel.editCreateViewModel.initiateEdgeEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                            onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                            onAddNodeClick = {
                                viewModel.editCreateViewModel.initiateNodeCreation()
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onAddEdgeClick = {
                                viewModel.editCreateViewModel.initiateEdgeCreation()
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onListAllClick = { viewModel.metadataViewModel.listAll() },
                            onListNodesClick = { viewModel.metadataViewModel.listNodes() },
                            onListEdgesClick = { viewModel.metadataViewModel.listEdges() }
                        )

                        DataViewTabs.SCHEMA -> SchemaView(
                            schema = schema,
                            primarySelectedItem = primarySelectedItem,
                            secondarySelectedItem = secondarySelectedItem,
                            onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEditNodeClick = { item ->
                                viewModel.editCreateViewModel.initiateNodeSchemaEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onEditEdgeClick = { item ->
                                viewModel.editCreateViewModel.initiateEdgeSchemaEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onDeleteNodeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                            onDeleteEdgeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                            onAddNodeSchemaClick = {
                                viewModel.editCreateViewModel.initiateNodeSchemaCreation()
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onAddEdgeSchemaClick = {
                                viewModel.editCreateViewModel.initiateEdgeSchemaCreation()
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onAddNodeClick = { item ->
                                viewModel.editCreateViewModel.initiateNodeCreation(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onAddEdgeClick = { schema ->
                                viewModel.editCreateViewModel.initiateEdgeCreation(schema)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            nodeSchemaSearchText = nodeSchemaSearchText,
                            onNodeSchemaSearchChange = viewModel.schemaViewModel::onNodeSchemaSearchChange,
                            edgeSchemaSearchText = edgeSchemaSearchText,
                            onEdgeSchemaSearchChange = viewModel.schemaViewModel::onEdgeSchemaSearchChange,
                            schemaVisibility = schemaVisibility,
                            onToggleSchemaVisibility = viewModel.schemaViewModel::toggleSchemaVisibility
                        )

                        DataViewTabs.EDIT -> EditItemView(
                            editScreenState = editScreenState,
                            onSaveClick = onSave,
                            onCancelClick = onCancel,

                            // Node Creation
                            onNodeCreationSchemaSelected = { viewModel.editCreateViewModel.updateNodeCreationSchema(it) },
                            onNodeCreationTypeSelected = { viewModel.editCreateViewModel.updateNodeCreationType(it) },
                            onNodeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateNodeCreationProperty(k, v) },
                            onNodeCreationTextChanged = { viewModel.editCreateViewModel.updateNodeCreationText(it) },
                            onNodeCreationImageChanged = { path, caption -> viewModel.editCreateViewModel.updateNodeCreationImage(path, caption) },
                            onNodeCreationTableDataChange = { r, c, v -> viewModel.editCreateViewModel.updateTableData(r, c, v, isCreation = true) },
                            onNodeCreationTableHeaderChange = { c, v -> viewModel.editCreateViewModel.updateTableHeader(c, v, isCreation = true) },
                            onNodeCreationAddTableRow = { viewModel.editCreateViewModel.addTableRow(isCreation = true) },
                            onNodeCreationAddTableColumn = { viewModel.editCreateViewModel.addTableColumn(isCreation = true) },
                            onNodeCreationCodeDataChange = { c, l, f -> viewModel.editCreateViewModel.updateCodeData(c, l, f, isCreation = true) },
                            onNodeCreationListItemChange = { i, v -> viewModel.editCreateViewModel.updateListItem(i, v, isCreation = true) },
                            onNodeCreationAddListItem = { viewModel.editCreateViewModel.addListItem(isCreation = true) },
                            onNodeCreationRemoveListItem = { viewModel.editCreateViewModel.removeListItem(it, isCreation = true) },
                            onNodeCreationTaskItemChange = { i, t, c -> viewModel.editCreateViewModel.updateTaskItem(i, t, c, isCreation = true) },
                            onNodeCreationAddTaskItem = { viewModel.editCreateViewModel.addTaskItem(isCreation = true) },
                            onNodeCreationRemoveTaskItem = { viewModel.editCreateViewModel.removeTaskItem(it, isCreation = true) },
                            onNodeCreationAddTag = { viewModel.editCreateViewModel.addTag(it, isCreation = true) },
                            onNodeCreationRemoveTag = { viewModel.editCreateViewModel.removeTag(it, isCreation = true) },

                            // Edge Creation
                            onEdgeCreationSchemaSelected = { viewModel.editCreateViewModel.updateEdgeCreationSchema(it) },
                            onEdgeCreationAddParticipant = { role -> viewModel.editCreateViewModel.addEdgeCreationParticipant(role) },
                            onEdgeCreationRemoveParticipant = { index -> viewModel.editCreateViewModel.removeEdgeCreationParticipant(index) },
                            onEdgeCreationParticipantSelected = { index, node -> viewModel.editCreateViewModel.updateEdgeCreationParticipantNode(index, node) },
                            onEdgeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateEdgeCreationProperty(k, v) },

                            // Node Schema Creation
                            onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                            onNodeSchemaTypeChange = { viewModel.editCreateViewModel.onNodeSchemaTypeChange(it) },
                            onNodeSchemaTextTypeChange = { viewModel.editCreateViewModel.onNodeSchemaTextTypeChange(it) },
                            onNodeSchemaListTypeChange = { viewModel.editCreateViewModel.onNodeSchemaListTypeChange(it) },
                            onNodeSchemaTableConfigChange = { r, s, m -> viewModel.editCreateViewModel.onNodeSchemaTableConfigChange(r, s, m) },
                            onNodeSchemaCodeConfigChange = { l, f -> viewModel.editCreateViewModel.onNodeSchemaCodeConfigChange(l, f) },
                            onNodeSchemaTextConfigChange = { c, l, lim -> viewModel.editCreateViewModel.onNodeSchemaTextConfigChange(c, l, lim) },
                            onNodeSchemaListConfigChange = { t, s -> viewModel.editCreateViewModel.onNodeSchemaListConfigChange(t, s) },
                            onNodeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onNodeSchemaPropertyChange(i, p) },
                            onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty(it) },
                            onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },

                            // Edge Schema Creation
                            onEdgeSchemaTableNameChange = { viewModel.editCreateViewModel.onEdgeSchemaTableNameChange(it) },
                            onEdgeSchemaAddRole = { viewModel.editCreateViewModel.onAddEdgeSchemaRole(it) },
                            onEdgeSchemaRemoveRole = { viewModel.editCreateViewModel.onRemoveEdgeSchemaRole(it) },
                            onEdgeSchemaRoleChange = { i, r -> viewModel.editCreateViewModel.onEdgeSchemaRoleChange(i, r) },
                            onEdgeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onEdgeSchemaPropertyChange(i, p) },
                            onAddEdgeSchemaProperty = { viewModel.editCreateViewModel.onAddEdgeSchemaProperty(it) },
                            onRemoveEdgeSchemaProperty = { viewModel.editCreateViewModel.onRemoveEdgeSchemaProperty(it) },

                            // Edit Instance
                            onNodeEditPropertyChange = { k, v -> viewModel.editCreateViewModel.updateNodeEditProperty(k, v) },
                            onNodeEditTextChanged = { viewModel.editCreateViewModel.updateNodeEditText(it) },
                            onNodeEditImageChanged = { path, caption -> viewModel.editCreateViewModel.updateNodeEditImage(path, caption) },
                            onNodeEditTableDataChange = { r, c, v -> viewModel.editCreateViewModel.updateTableData(r, c, v, isCreation = false) },
                            onNodeEditTableHeaderChange = { c, v -> viewModel.editCreateViewModel.updateTableHeader(c, v, isCreation = false) },
                            onNodeEditAddTableRow = { viewModel.editCreateViewModel.addTableRow(isCreation = false) },
                            onNodeEditAddTableColumn = { viewModel.editCreateViewModel.addTableColumn(isCreation = false) },
                            onNodeEditCodeDataChange = { c, l, f -> viewModel.editCreateViewModel.updateCodeData(c, l, f, isCreation = false) },
                            onNodeEditListItemChange = { i, v -> viewModel.editCreateViewModel.updateListItem(i, v, isCreation = false) },
                            onNodeEditAddListItem = { viewModel.editCreateViewModel.addListItem(isCreation = false) },
                            onNodeEditRemoveListItem = { viewModel.editCreateViewModel.removeListItem(it, isCreation = false) },
                            onNodeEditTaskItemChange = { i, t, c -> viewModel.editCreateViewModel.updateTaskItem(i, t, c, isCreation = false) },
                            onNodeEditAddTaskItem = { viewModel.editCreateViewModel.addTaskItem(isCreation = false) },
                            onNodeEditRemoveTaskItem = { viewModel.editCreateViewModel.removeTaskItem(it, isCreation = false) },
                            onNodeEditAddTag = { viewModel.editCreateViewModel.addTag(it, isCreation = false) },
                            onNodeEditRemoveTag = { viewModel.editCreateViewModel.removeTag(it, isCreation = false) },

                            onEdgeEditPropertyChange = { k, v -> viewModel.editCreateViewModel.updateEdgeEditProperty(k, v) },

                            // Edit Node Schema
                            onNodeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateNodeSchemaEditLabel(it) },
                            onNodeSchemaEditPropertyChange = { i, p -> viewModel.editCreateViewModel.updateNodeSchemaEditProperty(i, p) },
                            onNodeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditAddProperty(it) },
                            onNodeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditRemoveProperty(it) },

                            // Edit Edge Schema
                            onEdgeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateEdgeSchemaEditLabel(it) },
                            onEdgeSchemaEditPropertyChange = { i, p -> viewModel.editCreateViewModel.updateEdgeSchemaEditProperty(i, p) },
                            onEdgeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddProperty(it) },
                            onEdgeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveProperty(it) },
                            onEdgeSchemaEditAddRole = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddRole(it) },
                            onEdgeSchemaEditRemoveRole = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveRole(it) },
                            onEdgeSchemaEditRoleChange = { i, r -> viewModel.editCreateViewModel.updateEdgeSchemaEditRole(i, r) }
                        )
                    }
                }
            }
        )

        // Detangle Dialog
        if (showDetangleDialog) {
            DetangleSettingsDialog(
                onDismiss = { graphViewModel.onDismissDetangleDialog() },
                onDetangle = { alg, params -> graphViewModel.startDetangle(alg, params) }
            )
        }
    }
}