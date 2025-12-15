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
import com.tau.nexusnote.datamodels.EdgeCreationState
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.datamodels.ParticipantSelection
import com.tau.nexusnote.datamodels.RoleCardinality
import com.tau.nexusnote.datamodels.RoleDefinition
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow

@Composable
fun CreateEdgeView(
    edgeCreationState: EdgeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,

    // Updated callbacks for dynamic role filling
    onAddParticipant: (role: String) -> Unit,
    onRemoveParticipant: (index: Int) -> Unit,
    onParticipantNodeSelected: (index: Int, node: NodeDisplayItem) -> Unit,

    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Edge")

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Schema Dropdown
            CodexDropdown(
                label = "Select Schema",
                options = edgeCreationState.schemas,
                selectedOption = edgeCreationState.selectedSchema,
                onOptionSelected = onSchemaSelected,
                displayTransform = { it.name }
            )

            edgeCreationState.selectedSchema?.let { schema ->
                Spacer(modifier = Modifier.height(16.dp))

                // --- Dynamic Role Inputs ---
                schema.roleDefinitions?.forEach { role ->
                    RoleInputSection(
                        role = role,
                        allParticipants = edgeCreationState.participants,
                        availableNodes = edgeCreationState.availableNodes,
                        onAdd = { onAddParticipant(role.name) },
                        onRemove = onRemoveParticipant,
                        onSelect = onParticipantNodeSelected
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // --- Properties ---
                Spacer(modifier = Modifier.height(16.dp))
                Text("Properties", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (schema.properties.isEmpty()) {
                    Text("No properties defined.", style = MaterialTheme.typography.bodySmall)
                }

                schema.properties.forEach { property ->
                    CodexPropertyInput(
                        property = property,
                        currentValue = edgeCreationState.properties[property.name] ?: "",
                        onValueChange = { onPropertyChanged(property.name, it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Validation: Ensure "One" roles have 1 selection, "Many" have >= 0 (or >= 1 if logic dictates)
        // And generally ensure at least 2 nodes are involved for a valid edge.
        val participantCount = edgeCreationState.participants.count { it.node != null }
        val isValid = edgeCreationState.selectedSchema != null && participantCount >= 2

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = onCreateClick,
            primaryEnabled = isValid,
            onSecondaryClick = onCancelClick
        )
    }
}

@Composable
fun RoleInputSection(
    role: RoleDefinition,
    allParticipants: List<ParticipantSelection>,
    availableNodes: List<NodeDisplayItem>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onSelect: (Int, NodeDisplayItem) -> Unit
) {
    // Filter participants for THIS specific role
    // We need their global index to pass back to onRemove/onSelect
    val roleParticipants = allParticipants.mapIndexed { index, p -> index to p }
        .filter { it.second.role == role.name }

    // Filter available nodes based on allowed schemas
    val filteredNodes = if (role.allowedNodeSchemas.isEmpty()) {
        availableNodes
    } else {
        availableNodes.filter { it.label in role.allowedNodeSchemas }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = role.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "(${role.cardinality})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        // Render inputs
        roleParticipants.forEach { (globalIndex, participant) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CodexDropdown(
                        label = "Select Node",
                        options = filteredNodes,
                        selectedOption = participant.node,
                        onOptionSelected = { onSelect(globalIndex, it) },
                        displayTransform = { "${it.label}: ${it.displayProperty}" }
                    )
                }

                // Allow removing if cardinality is Many,
                // OR if it's One but we want to allow clearing (though typically One is fixed)
                if (role.cardinality is RoleCardinality.Many) {
                    IconButton(onClick = { onRemove(globalIndex) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Show Add button only for Many
        if (role.cardinality is RoleCardinality.Many) {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add ${role.name}")
            }
        }
    }
}