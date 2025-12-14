package com.tau.nexusnote.codex.crud.update

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
import com.tau.nexusnote.datamodels.CodexPropertyDataTypes
import com.tau.nexusnote.datamodels.EdgeSchemaEditState
import com.tau.nexusnote.datamodels.SchemaProperty
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow
import com.tau.nexusnote.utils.toCamelCase
import com.tau.nexusnote.utils.toScreamingSnakeCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEdgeSchemaView(
    state: EdgeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAddConnection: (src: String, dst: String) -> Unit,
    onRemoveConnection: (Int) -> Unit
) {
    // --- Local state for the "Add Connection" UI ---
    var newSrcTable by remember { mutableStateOf<String?>(null) }
    var newDstTable by remember { mutableStateOf<String?>(null) }

    // --- Local state for the "Add Property" UI ---
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }

    // Outer Column fills the screen
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Edit Edge Schema")

        // Scrollable Content Area (Takes up remaining space)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.replace(" ", "_").toScreamingSnakeCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null,
                supportingText = { state.currentNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Connection Pairs Section ---
            Text("Connection Pairs", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Source Table Dropdown
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
                // Destination Table Dropdown
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
                // Add Button
                IconButton(
                    onClick = {
                        onAddConnection(newSrcTable!!, newDstTable!!)
                        newSrcTable = null
                        newDstTable = null
                    },
                    enabled = newSrcTable != null && newDstTable != null
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Connection Pair")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // List of connections (Using Column loop instead of LazyColumn for nesting safety)
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            ) {
                state.connections.forEachIndexed { index, connection ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(connection.src, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "to", modifier = Modifier.padding(horizontal = 8.dp))
                                Text(connection.dst, style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveConnection(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Connection")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < state.connections.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (state.connections.isEmpty()) {
                    Text(
                        "No connections defined.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Properties Section ---
            Text("Properties", style = MaterialTheme.typography.titleMedium)

            // Add Property Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Property Name
                OutlinedTextField(
                    value = newPropName,
                    onValueChange = { newPropName = it.toCamelCase() },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Property Type
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

                // Add Button (Display property option removed for Edges)
                IconButton(
                    onClick = {
                        onAddProperty(
                            SchemaProperty(
                                name = newPropName,
                                type = newPropType,
                                isDisplayProperty = false // Always false for edges
                            )
                        )
                        newPropName = ""
                        newPropType = CodexPropertyDataTypes.TEXT
                    },
                    enabled = newPropName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Property")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of properties (Using Column loop)
            Column {
                state.properties.forEachIndexed { index, property ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = property.name,
                            onValueChange = {
                                onPropertyChange(index, property.copy(name = it.toCamelCase()))
                            },
                            label = { Text("Property Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
                            supportingText = { state.propertyErrors[index]?.let { Text(it) } },
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
                        // Display checkbox removed for Edges
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Property",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (index < state.properties.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Actions (Fixed at Bottom) ---
        FormActionRow(
            primaryLabel = "Save",
            onPrimaryClick = onSave,
            primaryEnabled = state.currentName.isNotBlank() && state.connections.isNotEmpty() && state.currentNameError == null && state.propertyErrors.isEmpty(),
            onSecondaryClick = onCancel
        )
    }
}