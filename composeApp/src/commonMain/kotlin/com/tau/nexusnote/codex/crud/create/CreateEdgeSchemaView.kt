package com.tau.nexusnote.codex.crud.create

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.CodexPropertyDataTypes
import com.tau.nexusnote.datamodels.EdgeSchemaCreationState
import com.tau.nexusnote.datamodels.RoleCardinality
import com.tau.nexusnote.datamodels.RoleDefinition
import com.tau.nexusnote.datamodels.RoleDirection
import com.tau.nexusnote.datamodels.SchemaProperty
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

    // Role callbacks
    onAddRole: (RoleDefinition) -> Unit,
    onRemoveRole: (Int) -> Unit,
    onRoleChange: (Int, RoleDefinition) -> Unit,

    // Property callbacks
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,

    onCreate: (EdgeSchemaCreationState) -> Unit,
    onCancel: () -> Unit
) {
    // --- Local state for inputs ---
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Edge Schema")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Table Name ---
            OutlinedTextField(
                value = state.tableName,
                onValueChange = { onTableNameChange(it.replace(" ", "_").toScreamingSnakeCase()) },
                label = { Text("Schema Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.tableNameError != null,
                supportingText = { state.tableNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Roles Section ---
            Text("Roles", style = MaterialTheme.typography.titleMedium)
            Text(
                "Define the participants in this relationship (e.g., 'Source', 'Target', 'Actor').",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            state.roles.forEachIndexed { index, role ->
                RoleEditorItem(
                    role = role,
                    allNodeSchemas = state.allNodeSchemas.map { it.name },
                    onUpdate = { updatedRole -> onRoleChange(index, updatedRole) },
                    onDelete = { onRemoveRole(index) },
                    error = state.roleErrors[index]
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    onAddRole(RoleDefinition("New Role", emptyList(), RoleCardinality.One, RoleDirection.Target))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Role")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Properties Section ---
            Text("Properties", style = MaterialTheme.typography.titleMedium)

            // Add Property Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
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
                        onAddProperty(
                            SchemaProperty(
                                name = newPropName,
                                type = newPropType,
                                isDisplayProperty = false
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

            // Property List
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
                            label = { Text("Prop Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
                            supportingText = { state.propertyErrors[index]?.let { Text(it) } },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(0.8f)) {
                            CodexDropdown(
                                label = "Type",
                                options = CodexPropertyDataTypes.entries,
                                selectedOption = property.type,
                                onOptionSelected = { onPropertyChange(index, property.copy(type = it)) },
                                displayTransform = { it.displayName }
                            )
                        }
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (index < state.properties.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = { onCreate(state) },
            primaryEnabled = state.tableName.isNotBlank() && state.roles.size >= 2 && state.tableNameError == null && state.propertyErrors.isEmpty() && state.roleErrors.isEmpty(),
            onSecondaryClick = onCancel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleEditorItem(
    role: RoleDefinition,
    allNodeSchemas: List<String>,
    onUpdate: (RoleDefinition) -> Unit,
    onDelete: () -> Unit,
    error: String?
) {
    val cardinalityOptions = listOf(RoleCardinality.One, RoleCardinality.Many)
    val directionOptions = RoleDirection.entries

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Updated: Use weights instead of fixed widths for responsiveness
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Role Name
                OutlinedTextField(
                    value = role.name,
                    onValueChange = { onUpdate(role.copy(name = it)) },
                    label = { Text("Role Name") },
                    modifier = Modifier.weight(1f),
                    isError = error != null,
                    singleLine = true
                )

                Spacer(Modifier.width(8.dp))

                // Direction
                Box(modifier = Modifier.weight(0.6f)) {
                    CodexDropdown(
                        label = "Dir",
                        options = directionOptions,
                        selectedOption = role.direction,
                        onOptionSelected = { onUpdate(role.copy(direction = it)) },
                        displayTransform = { it.name }
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Cardinality
                Box(modifier = Modifier.weight(0.6f)) {
                    CodexDropdown(
                        label = "Count",
                        options = cardinalityOptions,
                        selectedOption = role.cardinality,
                        onOptionSelected = { onUpdate(role.copy(cardinality = it)) },
                        displayTransform = { it.toString() }
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Role", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            // Allowed Types (Multi-select simulation via FlowRow of chips or similar)
            Text("Allowed Node Types (Empty = Any)", style = MaterialTheme.typography.labelSmall)

            // Simple Dropdown to add type
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text("Add Allowed Type")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    allNodeSchemas.forEach { schemaName ->
                        if (!role.allowedNodeSchemas.contains(schemaName)) {
                            DropdownMenuItem(
                                text = { Text(schemaName) },
                                onClick = {
                                    onUpdate(role.copy(allowedNodeSchemas = role.allowedNodeSchemas + schemaName))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Chips for selected types
            if (role.allowedNodeSchemas.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    role.allowedNodeSchemas.forEach { schemaName ->
                        InputChip(
                            selected = true,
                            onClick = {
                                onUpdate(role.copy(allowedNodeSchemas = role.allowedNodeSchemas - schemaName))
                            },
                            label = { Text(schemaName) },
                            trailingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }
    }
}