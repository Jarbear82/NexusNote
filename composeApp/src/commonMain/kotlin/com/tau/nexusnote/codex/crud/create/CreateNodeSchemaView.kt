package com.tau.nexusnote.codex.crud.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.CodexPropertyDataTypes
import com.tau.nexusnote.datamodels.NodeSchemaCreationState
import com.tau.nexusnote.datamodels.NodeType
import com.tau.nexusnote.datamodels.SchemaProperty
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow
import com.tau.nexusnote.utils.toCamelCase
import com.tau.nexusnote.utils.toPascalCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeSchemaView(
    state: NodeSchemaCreationState,
    onTableNameChange: (String) -> Unit,
    onTypeChange: (NodeType) -> Unit,

    // Secondary Type Handlers
    onTextTypeChange: (String) -> Unit,
    onListTypeChange: (String) -> Unit,

    // Config Update Handlers
    onTableConfigChange: (String, Boolean, String) -> Unit, // rowType, showCols, maxRows
    onCodeConfigChange: (String, Boolean) -> Unit, // lang, showFile
    onTextConfigChange: (String, Float, String) -> Unit, // casing, level, limit
    onListConfigChange: (String, String) -> Unit, // orderedType, unorderedSymbol

    // Property Handlers
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,

    onCancel: () -> Unit,
    onCreate: (NodeSchemaCreationState) -> Unit
) {
    // --- Local state for the "Add Property" UI ---
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }
    var newIsDisplay by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Node Schema")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // --- 1. Schema Name ---
            OutlinedTextField(
                value = state.tableName,
                onValueChange = { onTableNameChange(it.toPascalCase()) },
                label = { Text("Schema Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.tableNameError != null,
                supportingText = { state.tableNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. Type Selector ---
            CodexDropdown(
                label = "Schema Type",
                options = NodeType.entries,
                selectedOption = state.selectedNodeType,
                onOptionSelected = onTypeChange,
                displayTransform = { it.name.replace("_", " ") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- 3. Dynamic Configuration ---
            Text("Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = state.selectedNodeType,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }
            ) { type ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (type) {
                        NodeType.MAP -> {
                            // --- Map Config: Dynamic Properties ---
                            Text("Define specific properties for this entity type.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            PropertyEditor(
                                properties = state.properties,
                                newPropName = newPropName,
                                onNewPropNameChange = { newPropName = it },
                                newPropType = newPropType,
                                onNewPropTypeChange = { newPropType = it },
                                newIsDisplay = newIsDisplay,
                                onNewIsDisplayChange = { newIsDisplay = it },
                                onAddProperty = onAddProperty,
                                onRemoveProperty = onRemoveProperty,
                                propertyErrors = state.propertyErrors
                            )
                        }

                        NodeType.TEXT -> {
                            // Secondary: Text Style
                            CodexDropdown(
                                label = "Text Style",
                                options = listOf("Plain", "Heading", "Title"),
                                selectedOption = state.textSchemaType,
                                onOptionSelected = onTextTypeChange
                            )
                            Spacer(Modifier.height(8.dp))

                            when (state.textSchemaType) {
                                "Heading" -> {
                                    Text("Heading Level: ${state.headingLevel.toInt()}")
                                    Slider(
                                        value = state.headingLevel,
                                        onValueChange = { onTextConfigChange(state.textCasing, it, state.shortTextCharLimit) },
                                        valueRange = 1f..6f,
                                        steps = 4
                                    )
                                    CodexDropdown(
                                        label = "Casing",
                                        options = listOf("TitleCase", "UpperCase", "LowerCase", "SentenceCase"),
                                        selectedOption = state.textCasing,
                                        onOptionSelected = { onTextConfigChange(it, state.headingLevel, state.shortTextCharLimit) }
                                    )
                                }
                                "Title" -> {
                                    CodexDropdown(
                                        label = "Casing",
                                        options = listOf("TitleCase", "UpperCase", "LowerCase", "SentenceCase"),
                                        selectedOption = state.textCasing,
                                        onOptionSelected = { onTextConfigChange(it, state.headingLevel, state.shortTextCharLimit) }
                                    )
                                }
                                "Plain" -> {
                                    OutlinedTextField(
                                        value = state.shortTextCharLimit,
                                        onValueChange = { onTextConfigChange(state.textCasing, state.headingLevel, it) },
                                        label = { Text("Character Limit (Empty for Long Text)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        NodeType.LIST -> {
                            // Secondary: List Type
                            CodexDropdown(
                                label = "List Type",
                                options = listOf("Ordered", "Unordered", "Task"),
                                selectedOption = state.listSchemaType,
                                onOptionSelected = onListTypeChange
                            )
                            Spacer(Modifier.height(8.dp))

                            when (state.listSchemaType) {
                                "Ordered" -> {
                                    CodexDropdown(
                                        label = "Indicator Style",
                                        options = listOf("Numeric", "AlphaUpper", "AlphaLower", "RomanUpper", "RomanLower"),
                                        selectedOption = state.listOrderedType,
                                        onOptionSelected = { onListConfigChange(it, state.listUnorderedSymbol) }
                                    )
                                }
                                "Unordered" -> {
                                    OutlinedTextField(
                                        value = state.listUnorderedSymbol,
                                        onValueChange = { onListConfigChange(state.listOrderedType, it) },
                                        label = { Text("Symbol (e.g. •, -, *)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                else -> {
                                    Text("Task lists use standard checkboxes.")
                                }
                            }
                        }

                        NodeType.TABLE -> {
                            CodexDropdown(
                                label = "Row Header Style",
                                options = listOf("None", "Numeric", "Alpha"),
                                selectedOption = state.tableRowHeaderType,
                                onOptionSelected = { onTableConfigChange(it, state.tableShowColumnHeaders, state.tableMaxRows) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = state.tableShowColumnHeaders,
                                    onCheckedChange = { onTableConfigChange(state.tableRowHeaderType, it, state.tableMaxRows) }
                                )
                                Text("Show Column Headers")
                            }
                            OutlinedTextField(
                                value = state.tableMaxRows,
                                onValueChange = { onTableConfigChange(state.tableRowHeaderType, state.tableShowColumnHeaders, it) },
                                label = { Text("Max Rows (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        NodeType.CODE -> {
                            OutlinedTextField(
                                value = state.codeDefaultLanguage,
                                onValueChange = { onCodeConfigChange(it, state.codeShowFilename) },
                                label = { Text("Default Language") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = state.codeShowFilename,
                                    onCheckedChange = { onCodeConfigChange(state.codeDefaultLanguage, it) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Show Filename")
                            }
                        }

                        NodeType.TIMESTAMP -> {
                            // Simple placeholder, could be format string
                            Text("Standard timestamp format used.")
                        }

                        NodeType.MEDIA -> {
                            Text("Standard media container used.")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Fixed Actions ---
        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = { onCreate(state) },
            primaryEnabled = state.tableName.isNotBlank() && state.tableNameError == null,
            onSecondaryClick = onCancel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyEditor(
    properties: List<SchemaProperty>,
    newPropName: String,
    onNewPropNameChange: (String) -> Unit,
    newPropType: CodexPropertyDataTypes,
    onNewPropTypeChange: (CodexPropertyDataTypes) -> Unit,
    newIsDisplay: Boolean,
    onNewIsDisplayChange: (Boolean) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    propertyErrors: Map<Int, String?>
) {
    // Add Property Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newPropName,
            onValueChange = { onNewPropNameChange(it.toCamelCase()) },
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
                onOptionSelected = onNewPropTypeChange,
                displayTransform = { it.displayName }
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Display", style = MaterialTheme.typography.labelSmall)
            Checkbox(checked = newIsDisplay, onCheckedChange = onNewIsDisplayChange)
        }
        IconButton(
            onClick = {
                onAddProperty(SchemaProperty(newPropName, newPropType, newIsDisplay))
                onNewPropNameChange("")
                onNewIsDisplayChange(false)
            },
            enabled = newPropName.isNotBlank()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Property")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // List
    Column(
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).fillMaxWidth()
    ) {
        properties.forEachIndexed { index, property ->
            ListItem(
                headlineContent = { Text(property.name) },
                supportingContent = { Text(property.type.displayName) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (property.isDisplayProperty) {
                            Icon(Icons.Default.Visibility, "Display", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                }
            )
            if (propertyErrors[index] != null) {
                Text(propertyErrors[index]!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
            }
            if (index < properties.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}