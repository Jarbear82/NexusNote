package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EdgeCreationState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.CodexPropertyInput
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.FormActionRow

@Composable
fun CreateEdgeView(
    edgeCreationState: EdgeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onConnectionSelected: (ConnectionPair) -> Unit,
    onSrcSelected: (NodeDisplayItem) -> Unit,
    onDstSelected: (NodeDisplayItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit,
    codexPath: String
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Edge")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            CodexDropdown(
                label = "Select Schema",
                options = edgeCreationState.schemas,
                selectedOption = edgeCreationState.selectedSchema,
                onOptionSelected = onSchemaSelected,
                displayTransform = { it.name }
            )

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

            edgeCreationState.selectedConnection?.let { conn ->
                Spacer(modifier = Modifier.height(8.dp))
                val srcNodes = edgeCreationState.availableNodes.filter { it.label == conn.src }
                CodexDropdown(
                    label = "Select Source Node",
                    options = srcNodes,
                    selectedOption = edgeCreationState.src,
                    onOptionSelected = onSrcSelected,
                    displayTransform = { "${it.label} : ${it.displayProperty}" }
                )

                Spacer(modifier = Modifier.height(8.dp))
                val dstNodes = edgeCreationState.availableNodes.filter { it.label == conn.dst }
                CodexDropdown(
                    label = "Select Destination Node",
                    options = dstNodes,
                    selectedOption = edgeCreationState.dst,
                    onOptionSelected = onDstSelected,
                    displayTransform = { "${it.label} : ${it.displayProperty}" }
                )

                Spacer(modifier = Modifier.height(8.dp))
                edgeCreationState.selectedSchema?.properties?.forEach { property ->
                    CodexPropertyInput(
                        property = property,
                        currentValue = edgeCreationState.properties[property.name] ?: "",
                        onValueChange = { onPropertyChanged(property.name, it) },
                        codexPath = codexPath
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