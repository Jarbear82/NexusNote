package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.NodeSchemaCreationState
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
fun CreateNodeSchemaView(
    state: NodeSchemaCreationState,
    onTableNameChange: (String) -> Unit,
    onNodeStyleChange: (NodeStyle) -> Unit, // New Callback
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onCancel: () -> Unit,
    onCreate: (NodeSchemaCreationState) -> Unit
) {
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }
    var newIsDisplay by remember { mutableStateOf(false) }
    var newIsBackground by remember { mutableStateOf(false) }

    val density = LocalDensityTokens.current

    Column(modifier = Modifier.padding(density.contentPadding).fillMaxSize()) {
        CodexSectionHeader("Create Node Schema")

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            // Table Name
            CodexTextField(
                value = state.tableName,
                onValueChange = { onTableNameChange(it.toPascalCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.tableNameError != null
            )
            if (state.tableNameError != null) {
                Text(state.tableNameError, color = MaterialTheme.colorScheme.error, fontSize = density.bodyFontSize)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Node Style Dropdown
            CodexDropdown(
                label = "Visual Style",
                options = NodeStyle.entries,
                selectedOption = state.nodeStyle,
                onOptionSelected = onNodeStyleChange,
                displayTransform = { it.displayName }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Properties", style = MaterialTheme.typography.titleMedium, fontSize = density.titleFontSize)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Checkbox(
                        checked = newIsDisplay,
                        onCheckedChange = { newIsDisplay = it }
                    )
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
                    Icon(Icons.Default.Add, contentDescription = "Add Property", modifier = Modifier.size(density.iconSize))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .fillMaxWidth()
            ) {
                state.properties.forEachIndexed { index, property ->
                    ListItem(
                        headlineContent = { Text(property.name, fontSize = density.bodyFontSize) },
                        supportingContent = { Text(property.type.displayName, fontSize = density.bodyFontSize) },
                        modifier = Modifier.height(density.listHeight),
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (property.isDisplayProperty) {
                                    Icon(Icons.Default.Visibility, "Display", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(density.iconSize))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (property.isBackgroundProperty) {
                                    Icon(Icons.Default.Image, "Background", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(density.iconSize))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                IconButton(onClick = { onRemoveProperty(index) }) {
                                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(density.iconSize))
                                }
                            }
                        }
                    )
                    if (index < state.properties.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            state.propertyErrors.values.firstOrNull()?.let { errorMsg ->
                Text(text = errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontSize = density.bodyFontSize)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = { onCreate(state) },
            primaryEnabled = state.tableName.isNotBlank() && state.tableNameError == null && state.properties.isNotEmpty(),
            onSecondaryClick = onCancel
        )
    }
}