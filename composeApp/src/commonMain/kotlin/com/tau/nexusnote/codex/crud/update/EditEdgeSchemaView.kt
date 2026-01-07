package com.tau.nexusnote.codex.crud.update

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
import com.tau.nexusnote.codex.crud.RoleEditorItem
import com.tau.nexusnote.datamodels.*
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
    onRoleChange: (Int, RoleDefinition) -> Unit,
    onAddRole: (RoleDefinition) -> Unit,
    onRemoveRole: (Int) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }

    // Validation: Require at least 2 roles with cardinality ONE, OR at least 1 role with cardinality MANY
    val hasTwoSingleRoles = state.roles.count { it.cardinality == RelationCardinality.ONE } >= 2
    val hasOneManyRole = state.roles.any { it.cardinality == RelationCardinality.MANY }
    val isValid = state.currentName.isNotBlank() && state.currentNameError == null && (hasTwoSingleRoles || hasOneManyRole)

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Edit Relation Schema")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.toScreamingSnakeCase()) },
                label = { Text("Schema Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null,
                supportingText = { state.currentNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Roles", style = MaterialTheme.typography.titleMedium)
            Text("Every relation must have at least 2 roles with cardinality ONE, or at least 1 role with cardinality MANY.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))

            state.roles.forEachIndexed { index, role ->
                RoleEditorItem(
                    role = role,
                    allNodeSchemas = state.allNodeSchemas,
                    onUpdate = { onRoleChange(index, it) },
                    onDelete = { onRemoveRole(index) },
                    error = state.roleErrors[index]
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(onClick = { onAddRole(RoleDefinition(name = "New Role", direction = RelationDirection.TARGET, cardinality = RelationCardinality.ONE)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Role")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Properties", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newPropName, onValueChange = { newPropName = it.toCamelCase() }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onAddProperty(SchemaProperty(name = newPropName, type = newPropType)); newPropName = "" }, enabled = newPropName.isNotBlank()) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            state.properties.forEachIndexed { index, property ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    // ID-based edit: Tracking property.id ensures backend knows it is an update/rename
                    OutlinedTextField(value = property.name, onValueChange = { onPropertyChange(index, property.copy(name = it.toCamelCase())) }, label = { Text("Property Name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(120.dp)) {
                        CodexDropdown("Type", CodexPropertyDataTypes.entries, property.type, { onPropertyChange(index, property.copy(type = it)) }, { it.displayName })
                    }
                    IconButton(onClick = { onRemoveProperty(index) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        FormActionRow("Save Changes", onSave, isValid, onCancel)
    }
}