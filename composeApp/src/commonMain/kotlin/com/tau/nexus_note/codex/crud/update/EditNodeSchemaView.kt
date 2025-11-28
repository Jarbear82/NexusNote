package com.tau.nexus_note.codex.crud.update

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
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.CodexTextField
import com.tau.nexus_note.ui.components.FormActionRow
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.toCamelCase
import com.tau.nexus_note.utils.toPascalCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeSchemaView(
    state: NodeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onNodeStyleChange: (NodeStyle) -> Unit, // New callback
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }
    var newIsDisplay by remember { mutableStateOf(false) }
    var newIsBackground by remember { mutableStateOf(false) }

    val density = LocalDensityTokens.current

    Column(modifier = Modifier.padding(density.contentPadding).fillMaxSize()) {
        CodexSectionHeader("Edit Node Schema")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CodexTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.toPascalCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null
            )
            if (state.currentNameError != null) Text(state.currentNameError, color = MaterialTheme.colorScheme.error)

            Spacer(modifier = Modifier.height(16.dp))

            // Node Style Dropdown
            CodexDropdown(
                label = "Visual Style",
                options = NodeStyle.entries,
                selectedOption = state.currentNodeStyle,
                onOptionSelected = onNodeStyleChange,
                displayTransform = { it.displayName }
            )

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
                        onOptionSelected = {
                            newPropType = it
                            if(it != CodexPropertyDataTypes.IMAGE) newIsBackground = false
                        },
                        displayTransform = { it.displayName }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Display", style = MaterialTheme.typography.labelSmall, fontSize = density.bodyFontSize)
                    Checkbox(checked = newIsDisplay, onCheckedChange = { newIsDisplay = it })
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BG", style = MaterialTheme.typography.labelSmall, fontSize = density.bodyFontSize)
                    Checkbox(
                        checked = newIsBackground,
                        onCheckedChange = { newIsBackground = it },
                        enabled = newPropType == CodexPropertyDataTypes.IMAGE
                    )
                }
                IconButton(
                    onClick = {
                        onAddProperty(SchemaProperty(newPropName, newPropType, newIsDisplay, newIsBackground))
                        newPropName = ""
                        newPropType = CodexPropertyDataTypes.TEXT
                        newIsDisplay = false
                        newIsBackground = false
                    },
                    enabled = newPropName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, "Add Property", modifier = Modifier.size(density.iconSize))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).fillMaxWidth()) {
                state.properties.forEachIndexed { index, property ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        CodexTextField(
                            value = property.name,
                            onValueChange = { onPropertyChange(index, property.copy(name = it.toCamelCase())) },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
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
                            Text("Disp", style = MaterialTheme.typography.labelSmall)
                            Checkbox(checked = property.isDisplayProperty, onCheckedChange = { onPropertyChange(index, property.copy(isDisplayProperty = it)) })
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BG", style = MaterialTheme.typography.labelSmall)
                            Checkbox(
                                checked = property.isBackgroundProperty,
                                onCheckedChange = { onPropertyChange(index, property.copy(isBackgroundProperty = it)) },
                                enabled = property.type == CodexPropertyDataTypes.IMAGE
                            )
                        }
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(density.iconSize))
                        }
                    }
                    if (index < state.properties.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Save",
            onPrimaryClick = onSave,
            primaryEnabled = state.currentName.isNotBlank() && state.currentNameError == null && state.propertyErrors.isEmpty(),
            onSecondaryClick = onCancel
        )
    }
}