package com.tau.nexusnote.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.codex.crud.editors.CodeEditor
import com.tau.nexusnote.codex.crud.editors.ListEditor
import com.tau.nexusnote.codex.crud.editors.ShortTextEditor
import com.tau.nexusnote.codex.crud.editors.TableEditor
import com.tau.nexusnote.codex.crud.editors.TagEditor
import com.tau.nexusnote.codex.crud.editors.TaskListEditor
import com.tau.nexusnote.datamodels.NodeCreationState
import com.tau.nexusnote.datamodels.NodeType
import com.tau.nexusnote.datamodels.SchemaConfig
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow
import com.tau.nexusnote.utils.FilePicker

@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onNodeTypeSelected: (NodeType) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onTextChanged: (String) -> Unit,
    onImageSelected: (String?, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit,
    // New Callbacks for Specialized Editors
    onTableDataChange: (Int, Int, String) -> Unit = {_,_,_ ->},
    onTableHeaderChange: (Int, String) -> Unit = {_,_ ->},
    onAddTableRow: () -> Unit = {},
    onAddTableColumn: () -> Unit = {},
    onCodeDataChange: (String, String, String) -> Unit = {_,_,_ ->},
    onListItemChange: (Int, String) -> Unit = {_,_ ->},
    onAddListItem: () -> Unit = {},
    onRemoveListItem: (Int) -> Unit = {},
    onTaskItemChange: (Int, String, Boolean) -> Unit = {_,_,_ ->},
    onAddTaskItem: () -> Unit = {},
    onRemoveTaskItem: (Int) -> Unit = {},
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {}
) {
    var showFilePicker by remember { mutableStateOf(false) }

    FilePicker(
        show = showFilePicker,
        title = "Select Image",
        fileExtensions = listOf("png", "jpg", "jpeg", "webp"),
        onResult = { path ->
            showFilePicker = false
            if (path != null) {
                onImageSelected(path, nodeCreationState.imageCaption)
            }
        }
    )

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Node")

        // Top-level schema selection if using Map type, or type display
        if (nodeCreationState.selectedNodeType == NodeType.MAP) {
            CodexDropdown(
                label = "Select Schema",
                options = nodeCreationState.schemas,
                selectedOption = nodeCreationState.selectedSchema,
                onOptionSelected = onSchemaSelected,
                displayTransform = { it.name }
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Text(
                "Type: ${nodeCreationState.selectedNodeType.name.replace("_", " ")}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            val config = nodeCreationState.selectedSchema?.config

            when (nodeCreationState.selectedNodeType) {
                NodeType.MAP -> {
                    if (nodeCreationState.selectedSchema != null) {
                        nodeCreationState.selectedSchema.properties.forEach { property ->
                            CodexPropertyInput(
                                property = property,
                                currentValue = nodeCreationState.properties[property.name] ?: "",
                                onValueChange = { onPropertyChanged(property.name, it) }
                            )
                        }
                    }
                }
                NodeType.TABLE -> {
                    TableEditor(
                        headers = nodeCreationState.tableHeaders,
                        rows = nodeCreationState.tableRows,
                        showColumnHeaders = (config as? SchemaConfig.TableConfig)?.showColumnHeaders ?: true,
                        onCellChange = onTableDataChange,
                        onHeaderChange = onTableHeaderChange,
                        onAddRow = onAddTableRow,
                        onAddColumn = onAddTableColumn
                    )
                }
                NodeType.CODE_BLOCK -> {
                    val codeConfig = config as? SchemaConfig.CodeBlockConfig
                    CodeEditor(
                        code = nodeCreationState.codeContent,
                        language = nodeCreationState.codeLanguage,
                        filename = nodeCreationState.codeFilename,
                        showFilename = codeConfig?.showFilename ?: true,
                        onCodeChange = { onCodeDataChange(it, nodeCreationState.codeLanguage, nodeCreationState.codeFilename) },
                        onLanguageChange = { onCodeDataChange(nodeCreationState.codeContent, it, nodeCreationState.codeFilename) },
                        onFilenameChange = { onCodeDataChange(nodeCreationState.codeContent, nodeCreationState.codeLanguage, it) }
                    )
                }
                NodeType.ORDERED_LIST, NodeType.UNORDERED_LIST, NodeType.SET -> {
                    ListEditor(
                        items = nodeCreationState.listItems,
                        onItemChange = onListItemChange,
                        onItemAdd = onAddListItem,
                        onItemRemove = onRemoveListItem,
                        ordered = nodeCreationState.selectedNodeType == NodeType.ORDERED_LIST
                    )
                }
                NodeType.TASK_LIST -> {
                    TaskListEditor(
                        items = nodeCreationState.taskListItems,
                        onItemTextChange = { i, v -> onTaskItemChange(i, v, nodeCreationState.taskListItems[i].isCompleted) },
                        onItemCheckChange = { i, c -> onTaskItemChange(i, nodeCreationState.taskListItems[i].text, c) },
                        onItemAdd = onAddTaskItem,
                        onItemRemove = onRemoveTaskItem
                    )
                }
                NodeType.TAG -> {
                    TagEditor(
                        tags = nodeCreationState.tags,
                        onTagAdd = onAddTag,
                        onTagRemove = onRemoveTag
                    )
                }
                NodeType.SHORT_TEXT -> {
                    val limit = (config as? SchemaConfig.ShortTextConfig)?.charLimit ?: 140
                    ShortTextEditor(
                        text = nodeCreationState.textContent,
                        charLimit = limit,
                        onValueChange = onTextChanged
                    )
                }
                NodeType.LONG_TEXT, NodeType.HEADING, NodeType.TITLE -> {
                    OutlinedTextField(
                        value = nodeCreationState.textContent,
                        onValueChange = onTextChanged,
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
                NodeType.IMAGE -> {
                    Button(
                        onClick = { showFilePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (nodeCreationState.imagePath == null) "Select Image File" else "Change Image")
                    }
                    if (nodeCreationState.imagePath != null) {
                        Text("Selected: ${nodeCreationState.imagePath}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nodeCreationState.imageCaption,
                        onValueChange = { onImageSelected(nodeCreationState.imagePath, it) },
                        label = { Text("Caption") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Text("Editor not implemented for this type.")
                }
            }
        }

        if (nodeCreationState.validationError != null) {
            Text(
                text = nodeCreationState.validationError,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = onCreateClick,
            primaryEnabled = nodeCreationState.validationError == null,
            onSecondaryClick = onCancelClick
        )
    }
}