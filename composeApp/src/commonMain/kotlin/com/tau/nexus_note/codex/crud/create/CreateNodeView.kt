package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.CodexPropertyInput
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.CodexTextField
import com.tau.nexus_note.ui.components.FormActionRow
import com.tau.nexus_note.ui.components.editors.ListPropertyEditor

@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit,
    mediaRootPath: String
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

            Spacer(modifier = Modifier.height(16.dp))

            nodeCreationState.selectedSchema?.let { schema ->
                when (schema.nodeStyle) {
                    NodeStyle.BLOCK, NodeStyle.CODE_BLOCK,
                    NodeStyle.DOCUMENT, NodeStyle.SECTION,
                    NodeStyle.TAG, NodeStyle.ATTACHMENT -> {
                        CreateTextFocusLayout(schema, nodeCreationState.properties, onPropertyChanged, mediaRootPath)
                    }
                    NodeStyle.LIST -> {
                        CreateListFocusLayout(schema, nodeCreationState.properties, onPropertyChanged, mediaRootPath)
                    }
                    else -> {
                        CreateStandardLayout(schema, nodeCreationState.properties, onPropertyChanged, mediaRootPath)
                    }
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

@Composable
private fun CreateTextFocusLayout(
    schema: SchemaDefinitionItem,
    properties: Map<String, String>,
    onPropertyChanged: (String, String) -> Unit,
    mediaRootPath: String
) {
    val displayProp = schema.properties.find { it.isDisplayProperty }
        ?: schema.properties.firstOrNull()

    if (displayProp != null) {
        Text(
            text = displayProp.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        CodexTextField(
            value = properties[displayProp.name] ?: "",
            onValueChange = { onPropertyChanged(displayProp.name, it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            singleLine = false,
            maxLines = Int.MAX_VALUE
        )
        Spacer(Modifier.height(24.dp))
    }

    val otherProps = schema.properties.filter { it != displayProp }
    if (otherProps.isNotEmpty()) {
        Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        otherProps.forEach { prop ->
            CodexPropertyInput(prop, properties[prop.name] ?: "", { onPropertyChanged(prop.name, it) }, mediaRootPath)
        }
    }
}

@Composable
private fun CreateListFocusLayout(
    schema: SchemaDefinitionItem,
    properties: Map<String, String>,
    onPropertyChanged: (String, String) -> Unit,
    mediaRootPath: String
) {
    val listProp = schema.properties.find { it.type == CodexPropertyDataTypes.LIST }
        ?: schema.properties.firstOrNull()

    if (listProp != null) {
        ListPropertyEditor(
            label = listProp.name,
            currentValueJson = properties[listProp.name] ?: "[]",
            onValueChange = { onPropertyChanged(listProp.name, it) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }

    val otherProps = schema.properties.filter { it != listProp }
    if (otherProps.isNotEmpty()) {
        Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        otherProps.forEach { prop ->
            CodexPropertyInput(prop, properties[prop.name] ?: "", { onPropertyChanged(prop.name, it) }, mediaRootPath)
        }
    }
}

@Composable
private fun CreateStandardLayout(
    schema: SchemaDefinitionItem,
    properties: Map<String, String>,
    onPropertyChanged: (String, String) -> Unit,
    mediaRootPath: String
) {
    schema.properties.forEach { property ->
        CodexPropertyInput(
            property = property,
            currentValue = properties[property.name] ?: "",
            onValueChange = { onPropertyChanged(property.name, it) },
            mediaRootPath = mediaRootPath
        )
    }
}