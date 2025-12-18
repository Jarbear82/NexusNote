package com.tau.nexusnote.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.NodeCreationState
import com.tau.nexusnote.datamodels.SchemaDefinition
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaToggle: (SchemaDefinition) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Node")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Labels (Schemas)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

            // Selected Schemas (Chips)
            if (nodeCreationState.selectedSchemas.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    nodeCreationState.selectedSchemas.forEach { schema ->
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
                Text("Select at least one label to begin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            // Add Schema Dropdown
            val availableToAdd = nodeCreationState.availableSchemas.filter { avail -> nodeCreationState.selectedSchemas.none { it.id == avail.id } }
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

            // Iterate over properties from ALL selected schemas
            val allProperties = nodeCreationState.selectedSchemas.flatMap { it.properties }.distinctBy { it.name }
            if (allProperties.isEmpty()) {
                Text("No properties defined for selected labels.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                allProperties.forEach { property ->
                    CodexPropertyInput(
                        property = property,
                        currentValue = nodeCreationState.properties[property.name] ?: "",
                        onValueChange = { onPropertyChanged(property.name, it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = onCreateClick,
            onSecondaryClick = onCancelClick,
            primaryEnabled = nodeCreationState.selectedSchemas.isNotEmpty()
        )
    }
}