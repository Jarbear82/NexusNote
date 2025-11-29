package com.tau.nexus_note.codex.crud.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.ui.components.CodexPropertyInput
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.CodexTextField
import com.tau.nexus_note.ui.components.FormActionRow
import com.tau.nexus_note.ui.components.editors.ListPropertyEditor

@Composable
fun EditNodeView(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    mediaRootPath: String
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Edit: ${state.schema.name}")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.schema.nodeStyle) {
                // 1. Text Focus Layout
                NodeStyle.BLOCK, NodeStyle.CODE_BLOCK,
                NodeStyle.DOCUMENT, NodeStyle.SECTION,
                NodeStyle.TAG, NodeStyle.ATTACHMENT -> {
                    TextFocusLayout(state, onPropertyChange, mediaRootPath)
                }

                // 2. List Focus Layout
                NodeStyle.LIST -> {
                    ListFocusLayout(state, onPropertyChange, mediaRootPath)
                }

                // 3. Standard Form Layout (Maps, Generic, Tables)
                else -> {
                    StandardFormLayout(state, onPropertyChange, mediaRootPath)
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

@Composable
private fun TextFocusLayout(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit,
    mediaRootPath: String
) {
    // Find the "Main" property (Display Property)
    val displayProp = state.schema.properties.find { it.isDisplayProperty }
        ?: state.schema.properties.firstOrNull()

    if (displayProp != null) {
        Text(
            text = displayProp.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        CodexTextField(
            value = state.properties[displayProp.name] ?: "",
            onValueChange = { onPropertyChange(displayProp.name, it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), // Specific height for main content
            singleLine = false,
            maxLines = Int.MAX_VALUE
        )
        Spacer(Modifier.height(24.dp))
    }

    // Render other properties (Metadata) below
    val otherProps = state.schema.properties.filter { it != displayProp }
    if (otherProps.isNotEmpty()) {
        Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        otherProps.forEach { prop ->
            CodexPropertyInput(
                property = prop,
                currentValue = state.properties[prop.name] ?: "",
                onValueChange = { onPropertyChange(prop.name, it) },
                mediaRootPath = mediaRootPath
            )
        }
    }
}

@Composable
private fun ListFocusLayout(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit,
    mediaRootPath: String
) {
    // Find the List Property
    val listProp = state.schema.properties.find { it.type == CodexPropertyDataTypes.LIST }
        ?: state.schema.properties.firstOrNull()

    if (listProp != null) {
        ListPropertyEditor(
            label = listProp.name,
            currentValueJson = state.properties[listProp.name] ?: "[]",
            onValueChange = { onPropertyChange(listProp.name, it) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }

    // Metadata
    val otherProps = state.schema.properties.filter { it != listProp }
    if (otherProps.isNotEmpty()) {
        Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        otherProps.forEach { prop ->
            CodexPropertyInput(
                property = prop,
                currentValue = state.properties[prop.name] ?: "",
                onValueChange = { onPropertyChange(prop.name, it) },
                mediaRootPath = mediaRootPath
            )
        }
    }
}

@Composable
private fun StandardFormLayout(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit,
    mediaRootPath: String
) {
    // Default iteration for Maps/Generic nodes
    state.schema.properties.forEach { schemaProperty ->
        CodexPropertyInput(
            property = schemaProperty,
            currentValue = state.properties[schemaProperty.name] ?: "",
            onValueChange = { value -> onPropertyChange(schemaProperty.name, value) },
            mediaRootPath = mediaRootPath
        )
    }
}