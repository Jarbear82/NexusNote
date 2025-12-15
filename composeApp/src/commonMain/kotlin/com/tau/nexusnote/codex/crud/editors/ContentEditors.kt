package com.tau.nexusnote.codex.crud.editors

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.TaskItem
import com.tau.nexusnote.ui.components.CodexDropdown

@Composable
fun TableEditor(
    headers: List<String>,
    rows: List<List<String>>,
    showColumnHeaders: Boolean,
    onCellChange: (rowIndex: Int, colIndex: Int, value: String) -> Unit,
    onHeaderChange: (colIndex: Int, value: String) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Headers
        if (showColumnHeaders) {
            Row(modifier = Modifier.fillMaxWidth()) {
                headers.forEachIndexed { index, header ->
                    OutlinedTextField(
                        value = header,
                        onValueChange = { onHeaderChange(index, it) },
                        modifier = Modifier.weight(1f).padding(2.dp),
                        label = { Text("Col ${index + 1}") },
                        singleLine = true
                    )
                }
                // Add Column Button
                IconButton(onClick = onAddColumn, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Add, "Add Column")
                }
            }
        } else {
            Button(onClick = onAddColumn) { Text("Add Column") }
        }

        Spacer(Modifier.height(8.dp))

        // Rows
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, cell ->
                    OutlinedTextField(
                        value = cell,
                        onValueChange = { onCellChange(rowIndex, colIndex, it) },
                        modifier = Modifier.weight(1f).padding(2.dp),
                        singleLine = true
                    )
                }
                // Placeholder to align with the Add Column button in the header row
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        Button(
            onClick = onAddRow,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Row")
        }
    }
}

@Composable
fun CodeEditor(
    code: String,
    language: String,
    filename: String,
    showFilename: Boolean,
    onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onFilenameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf("kotlin", "java", "python", "javascript", "json", "xml", "sql", "text")

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                CodexDropdown(
                    label = "Language",
                    options = languages,
                    selectedOption = language,
                    onOptionSelected = onLanguageChange
                )
            }
            if (showFilename) {
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = filename,
                    onValueChange = onFilenameChange,
                    label = { Text("Filename") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Code") },
            modifier = Modifier.fillMaxWidth().height(300.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
fun ListEditor(
    items: List<String>,
    onItemChange: (index: Int, value: String) -> Unit,
    onItemAdd: () -> Unit,
    onItemRemove: (index: Int) -> Unit,
    ordered: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ordered) {
                    Text("${index + 1}.", modifier = Modifier.width(24.dp))
                }
                OutlinedTextField(
                    value = item,
                    onValueChange = { onItemChange(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { onItemRemove(index) }) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(onClick = onItemAdd, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Default.Add, null)
            Text("Add Item")
        }
    }
}

@Composable
fun TaskListEditor(
    items: List<TaskItem>,
    onItemTextChange: (index: Int, value: String) -> Unit,
    onItemCheckChange: (index: Int, checked: Boolean) -> Unit,
    onItemAdd: () -> Unit,
    onItemRemove: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onItemCheckChange(index, it) }
                )
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { onItemTextChange(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { onItemRemove(index) }) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(onClick = onItemAdd, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Default.Add, null)
            Text("Add Task")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagEditor(
    tags: List<String>,
    onTagAdd: (String) -> Unit,
    onTagRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTag by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it.replace(" ", "") }, // Prevent spaces
                label = { Text("New Tag (no spaces)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                prefix = { Text("#") }
            )
            IconButton(
                onClick = {
                    if (newTag.isNotBlank() && !tags.contains(newTag)) {
                        onTagAdd(newTag)
                        newTag = ""
                    }
                },
                enabled = newTag.isNotBlank()
            ) {
                Icon(Icons.Default.Add, "Add Tag")
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { onTagRemove(tag) },
                    label = { Text("#$tag") },
                    trailingIcon = { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp)) }
                )
            }
        }
    }
}

@Composable
fun ShortTextEditor(
    text: String,
    charLimit: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= charLimit && !it.contains("\n")) onValueChange(it) },
            label = { Text("Content") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text("${text.length} / $charLimit")
            }
        )
    }
}