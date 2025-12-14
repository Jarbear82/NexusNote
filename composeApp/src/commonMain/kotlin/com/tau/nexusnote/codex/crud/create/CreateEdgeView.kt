package com.tau.nexusnote.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.ConnectionPair
import com.tau.nexusnote.datamodels.EdgeCreationState
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow


@Composable
fun CreateEdgeView(
    edgeCreationState: EdgeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onConnectionSelected: (ConnectionPair) -> Unit,
    onSrcSelected: (NodeDisplayItem) -> Unit,
    onDstSelected: (NodeDisplayItem) -> Unit,
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

            // --- Connection Pair Dropdown ---
            edgeCreationState.selectedSchema?.let {
                Spacer(modifier = Modifier.height(8.dp))
                CodexDropdown(
                    label = "Select Connection Type",
                    options = it.connections ?: emptyList(),
                    selectedOption = edgeCreationState.selectedConnection,
                    onOptionSelected = onConnectionSelected,
                    displayTransform = { conn -> "${conn.src} -> ${conn.dst}" }
                )
            }

            // --- Source/Destination/Properties ---
            edgeCreationState.selectedConnection?.let { conn ->
                // Source Node Dropdown
                Spacer(modifier = Modifier.height(8.dp))
                val srcNodes = edgeCreationState.availableNodes.filter { it.label == conn.src }
                CodexDropdown(
                    label = "Select Source Node",
                    options = srcNodes,
                    selectedOption = edgeCreationState.src,
                    onOptionSelected = onSrcSelected,
                    displayTransform = { "${it.label} : ${it.displayProperty}" }
                )

                // Destination Node Dropdown
                Spacer(modifier = Modifier.height(8.dp))
                val dstNodes = edgeCreationState.availableNodes.filter { it.label == conn.dst }
                CodexDropdown(
                    label = "Select Destination Node",
                    options = dstNodes,
                    selectedOption = edgeCreationState.dst,
                    onOptionSelected = onDstSelected,
                    displayTransform = { "${it.label} : ${it.displayProperty}" }
                )

                // Properties
                Spacer(modifier = Modifier.height(8.dp))
                edgeCreationState.selectedSchema?.properties?.forEach { property ->
                    CodexPropertyInput(
                        property = property,
                        currentValue = edgeCreationState.properties[property.name] ?: "",
                        onValueChange = { onPropertyChanged(property.name, it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = onCreateClick,
            primaryEnabled = edgeCreationState.src != null && edgeCreationState.dst != null,
            onSecondaryClick = onCancelClick
        )
    }
}