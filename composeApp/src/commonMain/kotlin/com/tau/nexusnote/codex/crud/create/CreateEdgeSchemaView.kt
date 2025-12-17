package com.tau.nexusnote.codex.crud.create

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
fun CreateEdgeSchemaView(
    state: EdgeSchemaCreationState,
    onTableNameChange: (String) -> Unit,
    onAddRole: (RoleDefinition) -> Unit,
    onRemoveRole: (Int) -> Unit,
    onRoleChange: (Int, RoleDefinition) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onCreate: (EdgeSchemaCreationState) -> Unit,
    onCancel: () -> Unit
) {
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }

    // Validation: Require at least one Source and one Target
    val hasSource = state.roles.any { it.direction == RelationDirection.SOURCE }
    val hasTarget = state.roles.any { it.direction == RelationDirection.TARGET }
    val isValid = state.tableName.isNotBlank() && state.roles.size >= 2 && hasSource && hasTarget && state.tableNameError == null

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Relation Schema")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = state.tableName,
                onValueChange = { onTableNameChange(it.toScreamingSnakeCase()) },
                label = { Text("Schema Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.tableNameError != null,
                supportingText = { state.tableNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Roles", style = MaterialTheme.typography.titleMedium)
            Text("Every relation must have at least one 'Source' and one 'Target' role.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))

            state.roles.forEachIndexed { index, role ->
                RoleEditorItem(
                    role = role,
                    allNodeSchemas = state.allNodeSchemas.map { it.name },
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
                Box(modifier = Modifier.weight(1f)) {
                    CodexDropdown("Type", CodexPropertyDataTypes.entries, newPropType, { newPropType = it }, { it.displayName })
                }
                IconButton(onClick = { onAddProperty(SchemaProperty(name = newPropName, type = newPropType)); newPropName = "" }, enabled = newPropName.isNotBlank()) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            state.properties.forEachIndexed { index, property ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedTextField(value = property.name, onValueChange = { onPropertyChange(index, property.copy(name = it.toCamelCase())) }, label = { Text("Prop Name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(120.dp)) {
                        CodexDropdown("Type", CodexPropertyDataTypes.entries, property.type, { onPropertyChange(index, property.copy(type = it)) }, { it.displayName })
                    }
                    IconButton(onClick = { onRemoveProperty(index) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        FormActionRow("Create", { onCreate(state) }, isValid, onCancel)
    }
}