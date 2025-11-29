package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.ui.components.*
import com.tau.nexus_note.ui.components.editors.ListPropertyEditor
import com.tau.nexus_note.ui.components.editors.MapPropertyEditor

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

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CodexDropdown(
                label = "Select Type",
                options = nodeCreationState.schemas,
                selectedOption = nodeCreationState.selectedSchema,
                onOptionSelected = onSchemaSelected,
                displayTransform = { it.name }
            )
            Spacer(modifier = Modifier.height(16.dp))

            nodeCreationState.selectedSchema?.let { schema ->
                when (schema.nodeStyle) {
                    // Text Input Based
                    NodeStyle.TITLE, NodeStyle.HEADING, NodeStyle.SHORT_TEXT, NodeStyle.LONG_TEXT, NodeStyle.CODE_BLOCK, NodeStyle.TAG -> {
                        CreateTextFocusLayout(schema, nodeCreationState.properties, onPropertyChanged)
                    }
                    // List Based
                    NodeStyle.SET, NodeStyle.UNORDERED_LIST, NodeStyle.ORDERED_LIST -> {
                        CreateListFocusLayout(schema, nodeCreationState.properties, onPropertyChanged)
                    }
                    // Map Based
                    NodeStyle.MAP -> {
                        CreateMapFocusLayout(schema, nodeCreationState.properties, onPropertyChanged)
                    }
                    else -> CreateStandardLayout(schema, nodeCreationState.properties, onPropertyChanged, mediaRootPath)
                }
            }
        }
        FormActionRow("Create", onCreateClick, true, onCancelClick)
    }
}

@Composable
fun CreateMapFocusLayout(schema: SchemaDefinitionItem, properties: Map<String, String>, onPropertyChanged: (String, String) -> Unit) {
    val mapProp = schema.properties.find { it.type == CodexPropertyDataTypes.MAP }
    if (mapProp != null) {
        MapPropertyEditor(mapProp.name, properties[mapProp.name] ?: "{}", { onPropertyChanged(mapProp.name, it) })
    }
}

@Composable
fun CreateListFocusLayout(schema: SchemaDefinitionItem, properties: Map<String, String>, onPropertyChanged: (String, String) -> Unit) {
    val listProp = schema.properties.find { it.type == CodexPropertyDataTypes.LIST }
    if (listProp != null) {
        ListPropertyEditor(listProp.name, properties[listProp.name] ?: "[]", { onPropertyChanged(listProp.name, it) })
    }
}

@Composable
fun CreateTextFocusLayout(schema: SchemaDefinitionItem, properties: Map<String, String>, onPropertyChanged: (String, String) -> Unit) {
    val displayProp = schema.properties.find { it.isDisplayProperty }
    if (displayProp != null) {
        CodexTextField(value = properties[displayProp.name] ?: "", onValueChange = { onPropertyChanged(displayProp.name, it) }, label = { Text(displayProp.name) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CreateStandardLayout(schema: SchemaDefinitionItem, properties: Map<String, String>, onPropertyChanged: (String, String) -> Unit, mediaPath: String) {
    schema.properties.forEach { CodexPropertyInput(it, properties[it.name]?:"", { v -> onPropertyChanged(it.name, v) }, mediaPath) }
}