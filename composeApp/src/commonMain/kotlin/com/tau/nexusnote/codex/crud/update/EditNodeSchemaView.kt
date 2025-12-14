package com.tau.nexusnote.codex.crud.update

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.CodexPropertyDataTypes
import com.tau.nexusnote.datamodels.NodeSchemaEditState
import com.tau.nexusnote.datamodels.SchemaProperty
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow
import com.tau.nexusnote.utils.toCamelCase
import com.tau.nexusnote.utils.toPascalCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeSchemaView(
    state: NodeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // --- Local state for the "Add Property" UI ---
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }
    var newIsDisplay by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Edit Node Schema")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Table Name
            OutlinedTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.toPascalCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null,
                supportingText = { state.currentNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Add Property Input Row (Unified UI) ---
            Text("Properties", style = MaterialTheme.typography.titleMedium)

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

                // Display Checkbox
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Display", style = MaterialTheme.typography.labelSmall)
                    Checkbox(
                        checked = newIsDisplay,
                        onCheckedChange = { newIsDisplay = it }
                    )
                }

                // Add Button
                IconButton(
                    onClick = {
                        onAddProperty(
                            SchemaProperty(
                                name = newPropName,
                                type = newPropType,
                                isDisplayProperty = newIsDisplay
                            )
                        )
                        newPropName = ""
                        newPropType = CodexPropertyDataTypes.TEXT
                        newIsDisplay = false
                    },
                    enabled = newPropName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Property")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Existing Properties List ---
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .fillMaxWidth()
            ) {
                state.properties.forEachIndexed { index, property ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = property.name,
                            onValueChange = {
                                onPropertyChange(index, property.copy(name = it.toCamelCase()))
                            },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
                            supportingText = { state.propertyErrors[index]?.let { Text(it) } },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            CodexDropdown(
                                label = "Type",
                                options = CodexPropertyDataTypes.entries,
                                selectedOption = property.type,
                                onOptionSelected = { onPropertyChange(index, property.copy(type = it)) },
                                displayTransform = { it.displayName }
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(
                                checked = property.isDisplayProperty,
                                onCheckedChange = {
                                    onPropertyChange(index, property.copy(isDisplayProperty = it))
                                }
                            )
                        }
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

        // Fixed Buttons
        FormActionRow(
            primaryLabel = "Save",
            onPrimaryClick = onSave,
            primaryEnabled = state.currentName.isNotBlank() && state.currentNameError == null && state.propertyErrors.isEmpty(),
            onSecondaryClick = onCancel
        )
    }
}