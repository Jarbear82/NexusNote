package com.tau.nexusnote.codex.crud.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.NodeEditState
import com.tau.nexusnote.datamodels.SchemaDefinition
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeView(
    state: NodeEditState,
    onSchemaToggle: (SchemaDefinition) -> Unit,
    onPropertyChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        val schemaNames = state.schemas.joinToString(", ") { it.name }
        CodexSectionHeader("Edit Node: $schemaNames")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Schemas (Labels)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            
            // Selected Schemas (Chips)
            if (state.schemas.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.schemas.forEach { schema ->
                        InputChip(
                            selected = true,
                            onClick = { onSchemaToggle(schema) },
                            label = { Text(schema.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove Label",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            } else {
                 Text("No labels selected. Node must have at least one label.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            // Add Schema Dropdown
            val availableToAdd = state.availableSchemas.filter { avail -> state.schemas.none { it.id == avail.id } }
            if (availableToAdd.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    CodexDropdown(
                        label = "Add Label",
                        options = availableToAdd,
                        selectedOption = null,
                        onOptionSelected = { onSchemaToggle(it) },
                        displayTransform = { it.name }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Properties", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

            // Iterate over ALL schema properties
            val allProperties = state.schemas.flatMap { it.properties }.distinctBy { it.name }
            if (allProperties.isEmpty()) {
                Text("No properties defined for these labels.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                allProperties.forEach { schemaProperty ->
                    CodexPropertyInput(
                        property = schemaProperty,
                        currentValue = state.properties[schemaProperty.name] ?: "",
                        onValueChange = { value -> onPropertyChange(schemaProperty.name, value) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Save",
            onPrimaryClick = onSave,
            onSecondaryClick = onCancel
        )
    }
}