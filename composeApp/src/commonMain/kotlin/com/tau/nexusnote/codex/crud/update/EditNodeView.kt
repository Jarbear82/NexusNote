package com.tau.nexusnote.codex.crud.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.NodeEditState
import com.tau.nexusnote.ui.components.CodexPropertyInput
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.ui.components.FormActionRow

@Composable
fun EditNodeView(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        CodexSectionHeader("Edit Node: ${state.schema.name}")

        Text("Properties", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Iterate over schema properties
            state.schema.properties.forEach { schemaProperty ->
                CodexPropertyInput(
                    property = schemaProperty,
                    currentValue = state.properties[schemaProperty.name] ?: "",
                    onValueChange = { value -> onPropertyChange(schemaProperty.name, value) }
                )
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