package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.CodexPropertyInput
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.FormActionRow

@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit,
    codexPath: String
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Create Node")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            CodexDropdown(
                label = "Select Schema",
                options = nodeCreationState.schemas,
                selectedOption = nodeCreationState.selectedSchema,
                onOptionSelected = onSchemaSelected,
                displayTransform = { it.name }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (nodeCreationState.selectedSchema != null) {
                nodeCreationState.selectedSchema.properties.forEach { property ->
                    CodexPropertyInput(
                        property = property,
                        currentValue = nodeCreationState.properties[property.name] ?: "",
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
            onSecondaryClick = onCancelClick
        )
    }
}