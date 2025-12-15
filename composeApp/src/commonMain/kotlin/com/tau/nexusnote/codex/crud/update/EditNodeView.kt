package com.tau.nexusnote.codex.crud.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.codex.crud.editors.CodeEditor
import com.tau.nexusnote.codex.crud.editors.ListEditor
import com.tau.nexusnote.codex.crud.editors.ShortTextEditor
import com.tau.nexusnote.codex.crud.editors.TableEditor
import com.tau.nexusnote.codex.crud.editors.TagEditor
import com.tau.nexusnote.codex.crud.editors.TaskListEditor
import com.tau.nexusnote.datamodels.NodeEditState
import com.tau.nexusnote.datamodels.NodeType
import com.tau.nexusnote.datamodels.SchemaConfig
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow
import com.tau.nexusnote.utils.FilePicker

@Composable
fun EditNodeView(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit,
    onTextChanged: (String) -> Unit,
    onImageChanged: (String?, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    // Handlers for Specialized Editors (same signature as creation but routed to Edit logic)
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
        title = "Change Image",
        fileExtensions = listOf("png", "jpg", "jpeg", "webp"),
        onResult = { path ->
            showFilePicker = false
            if (path != null) {
                onImageChanged(path, state.imageCaption)
            }
        }
    )

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        val typeLabel = when(state.nodeType) {
            NodeType.MAP -> state.schema?.name ?: "Node"
            NodeType.HEADING -> "Note"
            else -> state.nodeType.name.replace("_", " ")
        }

        CodexSectionHeader("Edit $typeLabel")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            val config = state.schema?.config

            when (state.nodeType) {
                NodeType.MAP -> {
                    if (state.schema != null) {
                        state.schema.properties.forEach { schemaProperty ->
                            CodexPropertyInput(
                                property = schemaProperty,
                                currentValue = state.properties[schemaProperty.name] ?: "",
                                onValueChange = { value -> onPropertyChange(schemaProperty.name, value) }
                            )
                        }
                    } else {
                        Text("Missing Schema definition.", color = MaterialTheme.colorScheme.error)
                    }
                }
                NodeType.TABLE -> {
                    TableEditor(
                        headers = state.tableHeaders,
                        rows = state.tableRows,
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
                        code = state.codeContent,
                        language = state.codeLanguage,
                        filename = state.codeFilename,
                        showFilename = codeConfig?.showFilename ?: true,
                        onCodeChange = { onCodeDataChange(it, state.codeLanguage, state.codeFilename) },
                        onLanguageChange = { onCodeDataChange(state.codeContent, it, state.codeFilename) },
                        onFilenameChange = { onCodeDataChange(state.codeContent, state.codeLanguage, it) }
                    )
                }
                NodeType.ORDERED_LIST, NodeType.UNORDERED_LIST, NodeType.SET -> {
                    ListEditor(
                        items = state.listItems,
                        onItemChange = onListItemChange,
                        onItemAdd = onAddListItem,
                        onItemRemove = onRemoveListItem,
                        ordered = state.nodeType == NodeType.ORDERED_LIST
                    )
                }
                NodeType.TASK_LIST -> {
                    TaskListEditor(
                        items = state.taskListItems,
                        onItemTextChange = { i, v -> onTaskItemChange(i, v, state.taskListItems[i].isCompleted) },
                        onItemCheckChange = { i, c -> onTaskItemChange(i, state.taskListItems[i].text, c) },
                        onItemAdd = onAddTaskItem,
                        onItemRemove = onRemoveTaskItem
                    )
                }
                NodeType.TAG -> {
                    TagEditor(
                        tags = state.tags,
                        onTagAdd = onAddTag,
                        onTagRemove = onRemoveTag
                    )
                }
                NodeType.SHORT_TEXT -> {
                    val limit = (config as? SchemaConfig.ShortTextConfig)?.charLimit ?: 140
                    ShortTextEditor(
                        text = state.textContent,
                        charLimit = limit,
                        onValueChange = onTextChanged
                    )
                }
                NodeType.HEADING, NodeType.TITLE, NodeType.LONG_TEXT -> {
                    OutlinedTextField(
                        value = state.textContent,
                        onValueChange = onTextChanged,
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
                NodeType.IMAGE -> {
                    Button(
                        onClick = { showFilePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Change Image File")
                    }
                    if (state.imagePath != null) {
                        Text(
                            text = "Path: ${state.imagePath}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = state.imageCaption,
                        onValueChange = { onImageChanged(state.imagePath, it) },
                        label = { Text("Caption") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Text("Editing not supported for this node type.")
                }
            }
        }

        if (state.validationError != null) {
            Text(
                text = state.validationError,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Save",
            onPrimaryClick = onSave,
            primaryEnabled = state.validationError == null,
            onSecondaryClick = onCancel
        )
    }
}