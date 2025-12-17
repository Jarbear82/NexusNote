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
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }
    var newIsDisplay by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Edit Node Schema")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.toPascalCase()) },
                label = { Text("Schema Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null,
                supportingText = { state.currentNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Properties", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newPropName, onValueChange = { newPropName = it.toCamelCase() }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CodexDropdown("Type", CodexPropertyDataTypes.entries, newPropType, { newPropType = it }, { it.displayName })
                }
                IconButton(onClick = { onAddProperty(SchemaProperty(name = newPropName, type = newPropType, isDisplayProperty = newIsDisplay)); newPropName = "" }, enabled = newPropName.isNotBlank()) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            state.properties.forEachIndexed { index, property ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    // ID-based edit ensures backend performs UPDATE instead of DELETE/INSERT
                    OutlinedTextField(value = property.name, onValueChange = { onPropertyChange(index, property.copy(name = it.toCamelCase())) }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(120.dp)) {
                        CodexDropdown("Type", CodexPropertyDataTypes.entries, property.type, { onPropertyChange(index, property.copy(type = it)) }, { it.displayName })
                    }
                    Checkbox(checked = property.isDisplayProperty, onCheckedChange = { onPropertyChange(index, property.copy(isDisplayProperty = it)) })
                    IconButton(onClick = { onRemoveProperty(index) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        FormActionRow("Save", onSave, state.currentName.isNotBlank() && state.currentNameError == null, onCancel)
    }
}