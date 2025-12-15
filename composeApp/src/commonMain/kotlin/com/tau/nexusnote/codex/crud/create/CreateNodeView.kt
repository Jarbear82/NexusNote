package com.tau.nexusnote.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.codex.crud.editors.CodeEditor
import com.tau.nexusnote.codex.crud.editors.ListEditor
import com.tau.nexusnote.codex.crud.editors.ShortTextEditor
import com.tau.nexusnote.codex.crud.editors.TableEditor
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
    // Specialized Editor Callbacks
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

        // 1. Node Type Selector (Always Visible)
        // This allows creating Primitive nodes (by selecting a type but ignoring schema)
        CodexDropdown(
            label = "Node Type",
            options = NodeType.entries,
            selectedOption = nodeCreationState.selectedNodeType,
            onOptionSelected = onNodeTypeSelected,
            displayTransform = { it.name.replace("_", " ") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 2. Schema Selector (Conditional)
        // Filter schemas to only those matching the selected type
        val compatibleSchemas = nodeCreationState.schemas.filter { schema ->
            val schemaType = when(schema.config) {
                is SchemaConfig.MapConfig -> NodeType.MAP
                is SchemaConfig.TableConfig -> NodeType.TABLE
                is SchemaConfig.CodeConfig -> NodeType.CODE
                is SchemaConfig.TextConfig -> NodeType.TEXT
                is SchemaConfig.ListConfig -> NodeType.LIST
                is SchemaConfig.MediaConfig -> NodeType.MEDIA
                is SchemaConfig.TimestampConfig -> NodeType.TIMESTAMP
            }
            schemaType == nodeCreationState.selectedNodeType
        }

        // Show Schema dropdown if there are compatible schemas OR if it's MAP (which requires one)
        if (nodeCreationState.selectedNodeType == NodeType.MAP) {
            if (compatibleSchemas.isNotEmpty()) {
                CodexDropdown(
                    label = "Select Schema (Required)",
                    options = compatibleSchemas,
                    selectedOption = nodeCreationState.selectedSchema,
                    onOptionSelected = onSchemaSelected,
                    displayTransform = { it.name }
                )
            } else {
                Text("No schemas defined for MAP type. Please create a Node Schema first.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else if (compatibleSchemas.isNotEmpty()) {
            // Optional Schema selection for primitives (e.g., "Note" schema for TEXT type)
            // We need a way to select "None" to go back to primitive
            Column {
                Text("Schema (Optional):", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        CodexDropdown(
                            label = "Select Schema",
                            options = compatibleSchemas,
                            selectedOption = nodeCreationState.selectedSchema,
                            onOptionSelected = onSchemaSelected,
                            displayTransform = { it.name }
                        )
                    }
                    if (nodeCreationState.selectedSchema != null) {
                        TextButton(onClick = { /* Logic to clear schema is handled by selecting primitive type again, effectively */
                            onNodeTypeSelected(nodeCreationState.selectedNodeType)
                        }) {
                            Text("Clear")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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

                NodeType.TEXT -> {
                    // Check Config: PlainText (short/long), Heading, Title
                    when (config) {
                        is SchemaConfig.TextConfig.PlainText -> {
                            val limit = config.charLimit
                            if (limit != null) {
                                ShortTextEditor(
                                    text = nodeCreationState.textContent,
                                    charLimit = limit,
                                    onValueChange = onTextChanged
                                )
                            } else {
                                OutlinedTextField(
                                    value = nodeCreationState.textContent,
                                    onValueChange = onTextChanged,
                                    label = { Text("Long Text Content") },
                                    modifier = Modifier.fillMaxWidth().height(200.dp)
                                )
                            }
                        }
                        is SchemaConfig.TextConfig.Heading, is SchemaConfig.TextConfig.Title -> {
                            OutlinedTextField(
                                value = nodeCreationState.textContent,
                                onValueChange = onTextChanged,
                                label = { Text("Heading/Title Text") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        else -> {
                            // Default Fallback (Primitive)
                            OutlinedTextField(
                                value = nodeCreationState.textContent,
                                onValueChange = onTextChanged,
                                label = { Text("Text Content") },
                                modifier = Modifier.fillMaxWidth().height(150.dp)
                            )
                        }
                    }
                }

                NodeType.LIST -> {
                    if (config is SchemaConfig.ListConfig.Task) {
                        TaskListEditor(
                            items = nodeCreationState.taskListItems,
                            onItemTextChange = { i, v -> onTaskItemChange(i, v, nodeCreationState.taskListItems[i].isCompleted) },
                            onItemCheckChange = { i, c -> onTaskItemChange(i, nodeCreationState.taskListItems[i].text, c) },
                            onItemAdd = onAddTaskItem,
                            onItemRemove = onRemoveTaskItem
                        )
                    } else {
                        // Standard List (Ordered or Unordered) - Primitive fallback is Unordered/Bullet
                        val isOrdered = config is SchemaConfig.ListConfig.Ordered
                        ListEditor(
                            items = nodeCreationState.listItems,
                            onItemChange = onListItemChange,
                            onItemAdd = onAddListItem,
                            onItemRemove = onRemoveListItem,
                            ordered = isOrdered
                        )
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

                NodeType.CODE -> {
                    val codeConfig = config as? SchemaConfig.CodeConfig
                    CodeEditor(
                        code = nodeCreationState.codeContent,
                        language = nodeCreationState.codeLanguage,
                        filename = nodeCreationState.codeFilename,
                        showFilename = codeConfig?.showFilename ?: true, // Default to true if primitive
                        onCodeChange = { onCodeDataChange(it, nodeCreationState.codeLanguage, nodeCreationState.codeFilename) },
                        onLanguageChange = { onCodeDataChange(nodeCreationState.codeContent, it, nodeCreationState.codeFilename) },
                        onFilenameChange = { onCodeDataChange(nodeCreationState.codeContent, nodeCreationState.codeLanguage, it) }
                    )
                }

                NodeType.MEDIA -> {
                    Button(
                        onClick = { showFilePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (nodeCreationState.imagePath == null) "Select Media File" else "Change File")
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

                NodeType.TIMESTAMP -> {
                    Text("Timestamp will be set to current time on creation.")
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