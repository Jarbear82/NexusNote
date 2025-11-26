package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.EdgeSchemaCreationState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.CodexTextField
import com.tau.nexus_note.ui.components.FormActionRow
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.toCamelCase
import com.tau.nexus_note.utils.toScreamingSnakeCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEdgeSchemaView(
    state: EdgeSchemaCreationState,
    onTableNameChange: (String) -> Unit,
    onAddConnection: (src: String, dst: String) -> Unit,
    onRemoveConnection: (Int) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onCreate: (EdgeSchemaCreationState) -> Unit,
    onCancel: () -> Unit
) {
    var newSrcTable by remember { mutableStateOf<String?>(null) }
    var newDstTable by remember { mutableStateOf<String?>(null) }
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }

    val density = LocalDensityTokens.current

    Column(modifier = Modifier.padding(density.contentPadding).fillMaxSize()) {
        CodexSectionHeader("Create Edge Schema")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CodexTextField(
                value = state.tableName,
                onValueChange = { onTableNameChange(it.replace(" ", "_").toScreamingSnakeCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.tableNameError != null
            )
            if (state.tableNameError != null) Text(state.tableNameError, color = MaterialTheme.colorScheme.error)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Connection Pairs", style = MaterialTheme.typography.titleMedium, fontSize = density.titleFontSize)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CodexDropdown(
                        label = "From...",
                        options = state.allNodeSchemas,
                        selectedOption = state.allNodeSchemas.find { it.name == newSrcTable },
                        onOptionSelected = { newSrcTable = it.name },
                        displayTransform = { it.name }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CodexDropdown(
                        label = "To...",
                        options = state.allNodeSchemas,
                        selectedOption = state.allNodeSchemas.find { it.name == newDstTable },
                        onOptionSelected = { newDstTable = it.name },
                        displayTransform = { it.name }
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onAddConnection(newSrcTable!!, newDstTable!!)
                        newSrcTable = null
                        newDstTable = null
                    },
                    enabled = newSrcTable != null && newDstTable != null
                ) {
                    Icon(Icons.Default.Add, "Add Connection", modifier = Modifier.size(density.iconSize))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)) {
                state.connections.forEachIndexed { index, connection ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(connection.src, fontSize = density.bodyFontSize)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "to", modifier = Modifier.padding(horizontal = 8.dp).size(density.iconSize))
                                Text(connection.dst, fontSize = density.bodyFontSize)
                            }
                        },
                        modifier = Modifier.height(density.listHeight),
                        trailingContent = {
                            IconButton(onClick = { onRemoveConnection(index) }) {
                                Icon(Icons.Default.Delete, "Remove", modifier = Modifier.size(density.iconSize))
                            }
                        }
                    )
                    if (index < state.connections.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Properties", style = MaterialTheme.typography.titleMedium, fontSize = density.titleFontSize)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CodexTextField(
                    value = newPropName,
                    onValueChange = { newPropName = it.toCamelCase() },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CodexDropdown(
                        label = "Type",
                        options = CodexPropertyDataTypes.entries,
                        selectedOption = newPropType,
                        onOptionSelected = { newPropType = it },
                        displayTransform = { it.displayName }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onAddProperty(SchemaProperty(newPropName, newPropType, false))
                        newPropName = ""
                        newPropType = CodexPropertyDataTypes.TEXT
                    },
                    enabled = newPropName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, "Add Property", modifier = Modifier.size(density.iconSize))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                state.properties.forEachIndexed { index, property ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        CodexTextField(
                            value = property.name,
                            onValueChange = { onPropertyChange(index, property.copy(name = it.toCamelCase())) },
                            label = { Text("Property Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.width(140.dp)) {
                            CodexDropdown(
                                label = "Type",
                                options = CodexPropertyDataTypes.entries,
                                selectedOption = property.type,
                                onOptionSelected = { onPropertyChange(index, property.copy(type = it)) },
                                displayTransform = { it.displayName }
                            )
                        }
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(Icons.Default.Delete, "Delete Property", modifier = Modifier.size(density.iconSize))
                        }
                    }
                    if (index < state.properties.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = { onCreate(state) },
            primaryEnabled = state.tableName.isNotBlank() && state.connections.isNotEmpty() && state.tableNameError == null && state.propertyErrors.isEmpty(),
            onSecondaryClick = onCancel
        )
    }
}