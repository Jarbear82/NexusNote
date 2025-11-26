package com.tau.nexus_note.codex

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.codex.crud.update.EditItemView
import com.tau.nexus_note.codex.graph.DetangleSettingsDialog
import com.tau.nexus_note.codex.graph.GraphView
import com.tau.nexus_note.codex.metadata.MetadataView
import com.tau.nexus_note.codex.schema.SchemaView
import com.tau.nexus_note.ui.components.CodexAlertDialog
import com.tau.nexus_note.ui.components.TwoPaneLayout
import com.tau.nexus_note.ui.components.CodexTab
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexView(viewModel: CodexViewModel) {
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
    val isDetailPaneOpen by viewModel.isDetailPaneOpen.collectAsState()
    val graphViewModel = viewModel.graphViewModel
    val showDetangleDialog by graphViewModel.showDetangleDialog.collectAsState()
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

    val codexPath = viewModel.repository.dbPath

    Box(modifier = Modifier.fillMaxSize()) {
        TwoPaneLayout(
            showDetailOnMobile = isDetailPaneOpen,
            onDismissRequest = { viewModel.closeDetailPane() },
            listContent = {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        PrimaryTabRow(selectedTabIndex = selectedViewTab.value) {
                            ViewTabs.entries.forEach { tab ->
                                CodexTab(
                                    text = tab.name,
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
                                            val node = viewModel.metadataViewModel.nodeList.value.find { it.id == nodeId }
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
                            CodexTab(
                                text = tab.name,
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
                            onListEdgesClick = { viewModel.metadataViewModel.listEdges() },
                            repository = viewModel.repository
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
                            onAddEdgeClick = { schema, connection ->
                                viewModel.editCreateViewModel.initiateEdgeCreation(schema, connection)
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
                            codexPath = codexPath,
                            onNodeCreationSchemaSelected = { viewModel.editCreateViewModel.updateNodeCreationSchema(it) },
                            onNodeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateNodeCreationProperty(k, v) },
                            onEdgeCreationSchemaSelected = { viewModel.editCreateViewModel.updateEdgeCreationSchema(it) },
                            onEdgeCreationConnectionSelected = { viewModel.editCreateViewModel.updateEdgeCreationConnection(it) },
                            onEdgeCreationSrcSelected = { viewModel.editCreateViewModel.updateEdgeCreationSrc(it) },
                            onEdgeCreationDstSelected = { viewModel.editCreateViewModel.updateEdgeCreationDst(it) },
                            onEdgeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateEdgeCreationProperty(k, v) },
                            onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                            onNodeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onNodeSchemaPropertyChange(i, p) },
                            onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty(it) },
                            onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },
                            onEdgeSchemaTableNameChange = { viewModel.editCreateViewModel.onEdgeSchemaTableNameChange(it) },
                            onEdgeSchemaCreationAddConnection = { s, d -> viewModel.editCreateViewModel.onAddEdgeSchemaConnection(s, d) },
                            onEdgeSchemaCreationRemoveConnection = { viewModel.editCreateViewModel.onRemoveEdgeSchemaConnection(it) },
                            onEdgeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onEdgeSchemaPropertyChange(i, p) },
                            onAddEdgeSchemaProperty = { viewModel.editCreateViewModel.onAddEdgeSchemaProperty(it) },
                            onRemoveEdgeSchemaProperty = { viewModel.editCreateViewModel.onRemoveEdgeSchemaProperty(it) },
                            onNodeEditPropertyChange = { k, v -> viewModel.editCreateViewModel.updateNodeEditProperty(k, v) },
                            onEdgeEditPropertyChange = { k, v -> viewModel.editCreateViewModel.updateEdgeEditProperty(k, v) },
                            onNodeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateNodeSchemaEditLabel(it) },
                            onNodeSchemaEditPropertyChange = { i, p -> viewModel.editCreateViewModel.updateNodeSchemaEditProperty(i, p) },
                            onNodeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditAddProperty(it) },
                            onNodeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditRemoveProperty(it) },
                            onEdgeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateEdgeSchemaEditLabel(it) },
                            onEdgeSchemaEditPropertyChange = { i, p -> viewModel.editCreateViewModel.updateEdgeSchemaEditProperty(i, p) },
                            onEdgeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddProperty(it) },
                            onEdgeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveProperty(it) },
                            onEdgeSchemaEditAddConnection = { s, d -> viewModel.editCreateViewModel.updateEdgeSchemaEditAddConnection(s, d) },
                            onEdgeSchemaEditRemoveConnection = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveConnection(it) }
                        )
                    }
                }
            }
        )

        if (showDetangleDialog) {
            DetangleSettingsDialog(
                onDismiss = { graphViewModel.onDismissDetangleDialog() },
                onDetangle = { alg, params -> graphViewModel.startDetangle(alg, params) }
            )
        }
    }
}