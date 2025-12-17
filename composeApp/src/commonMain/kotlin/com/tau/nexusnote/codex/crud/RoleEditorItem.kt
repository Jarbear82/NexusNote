package com.tau.nexusnote.codex.crud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.RelationCardinality
import com.tau.nexusnote.datamodels.RelationDirection
import com.tau.nexusnote.datamodels.RoleDefinition
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.utils.toCamelCase

/**
 * A dedicated editor component for a single Relation Role.
 * Allows setting Name, Direction (Source/Target), Cardinality, and Node Schema linkage.
 */
import com.tau.nexusnote.datamodels.SchemaDefinition

@Composable
fun RoleEditorItem(
    role: RoleDefinition,
    allNodeSchemas: List<SchemaDefinition>,
    onUpdate: (RoleDefinition) -> Unit,
    onDelete: () -> Unit,
    error: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Role Name Input
                OutlinedTextField(
                    value = role.name,
                    onValueChange = { onUpdate(role.copy(name = it.toCamelCase())) },
                    label = { Text("Role Name") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    isError = error != null
                )

                Spacer(Modifier.width(8.dp))

                // Direction Toggle
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direction", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        RelationDirection.entries.forEach { direction ->
                            val selected = role.direction == direction
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdate(role.copy(direction = direction)) },
                                label = { Text(direction.name.take(1), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove Role",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Node Schema Linkage (Multi-select)
            Text("Allowed Node Types:", style = MaterialTheme.typography.labelSmall)
            
            // Chips for selected
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                role.allowedNodeSchemas.forEach { schemaId ->
                    val schemaName = allNodeSchemas.find { it.id == schemaId }?.name ?: "Unknown($schemaId)"
                    InputChip(
                        selected = true,
                        onClick = { onUpdate(role.copy(allowedNodeSchemas = role.allowedNodeSchemas - schemaId)) },
                        label = { Text(schemaName) },
                        trailingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            // Dropdown to add
            val availableSchemas = allNodeSchemas.filter { !role.allowedNodeSchemas.contains(it.id) }
            if (availableSchemas.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    CodexDropdown(
                        label = "Add Allowed Type",
                        options = availableSchemas,
                        selectedOption = null,
                        onOptionSelected = { onUpdate(role.copy(allowedNodeSchemas = role.allowedNodeSchemas + it.id)) },
                        displayTransform = { it.name }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cardinality
            Box(modifier = Modifier.fillMaxWidth()) {
                CodexDropdown(
                    label = "Cardinality",
                    options = RelationCardinality.entries,
                    selectedOption = role.cardinality,
                    onOptionSelected = { onUpdate(role.copy(cardinality = it)) },
                    displayTransform = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}