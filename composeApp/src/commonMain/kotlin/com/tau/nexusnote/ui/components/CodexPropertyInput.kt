package com.tau.nexusnote.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.CodexPropertyDataTypes
import com.tau.nexusnote.datamodels.SchemaProperty
import com.tau.nexusnote.datamodels.NodeDisplayItem

@Composable
fun CodexPropertyInput(
    property: SchemaProperty,
    currentValue: String,
    onValueChange: (String) -> Unit,
    allNodes: List<NodeDisplayItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val commonModifier = modifier.fillMaxWidth().padding(vertical = 4.dp)

    when (property.type) {
        CodexPropertyDataTypes.NUMBER -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = {
                    // Centralized Number Validation Logic
                    if (it.isEmpty() || it == "-" || it.matches(Regex("-?\\d*(\\.\\d*)?"))) {
                        onValueChange(it)
                    }
                },
                label = { Text("${property.name} (Number)") },
                modifier = commonModifier,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        CodexPropertyDataTypes.LONG_TEXT -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text("${property.name} (LongText)") },
                modifier = commonModifier,
                singleLine = false,
                minLines = 3,
                maxLines = 5
            )
        }
        CodexPropertyDataTypes.DATE -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text("${property.name} (Date)") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = commonModifier,
                singleLine = true
            )
        }
        CodexPropertyDataTypes.IMAGE, CodexPropertyDataTypes.AUDIO -> {
            Row(
                modifier = commonModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = onValueChange,
                    label = { Text("${property.name} (${property.type.displayName})") },
                    modifier = Modifier.weight(1f),
                    readOnly = true
                )
                Button(
                    onClick = { /* Future: Launch file picker */ },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("...")
                }
            }
        }
        CodexPropertyDataTypes.REFERENCE -> {
            val options = allNodes.filter { it.schemaId == property.referenceSchemaId }
            val selectedOption = options.find { it.id.toString() == currentValue }
            
            CodexDropdown(
                label = property.name,
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { onValueChange(it.id.toString()) },
                displayTransform = { it.displayProperty.ifBlank { "ID:${it.id}" } }
            )
        }
        else -> { // Default "Text"
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text(property.name) },
                modifier = commonModifier,
                singleLine = true
            )
        }
    }
}