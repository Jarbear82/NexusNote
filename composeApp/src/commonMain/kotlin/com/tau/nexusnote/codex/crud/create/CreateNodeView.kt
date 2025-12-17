package com.tau.nexusnote.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import com.tau.nexusnote.datamodels.NodeCreationState
import com.tau.nexusnote.datamodels.SchemaDefinition
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow

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
            Text("Select Schemas:", style = MaterialTheme.typography.subtitle1)
            nodeCreationState.availableSchemas.forEach { schema ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = nodeCreationState.selectedSchemas.contains(schema),
                        onCheckedChange = { onSchemaToggle(schema) }
                    )
                    Text(schema.name)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Iterate over properties from ALL selected schemas
            val allProperties = nodeCreationState.selectedSchemas.flatMap { it.properties }.distinctBy { it.name }
            allProperties.forEach { property ->
                CodexPropertyInput(
                    property = property,
                    currentValue = nodeCreationState.properties[property.name] ?: "",
                    onValueChange = { onPropertyChanged(property.name, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            primaryLabel = "Create",
            onPrimaryClick = onCreateClick,
            onSecondaryClick = onCancelClick
        )
    }
}