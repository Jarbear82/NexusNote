package com.tau.nexus_note.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.ui.components.editors.ColorPropertyEditor
import com.tau.nexus_note.ui.components.editors.ListPropertyEditor
import com.tau.nexus_note.ui.components.editors.MapPropertyEditor
import com.tau.nexus_note.ui.components.editors.MediaPropertyEditor

@Composable
fun CodexPropertyInput(
    property: SchemaProperty,
    currentValue: String,
    onValueChange: (String) -> Unit,
    codexPath: String, // Pass this down for Media
    modifier: Modifier = Modifier
) {
    val commonModifier = modifier.fillMaxWidth().padding(vertical = 4.dp)

    when (property.type) {
        CodexPropertyDataTypes.NUMBER -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = {
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
        CodexPropertyDataTypes.LONG_TEXT, CodexPropertyDataTypes.MARKDOWN -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text("${property.name} (${property.type.displayName})") },
                modifier = commonModifier,
                singleLine = false,
                minLines = 3,
                maxLines = 8
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
        CodexPropertyDataTypes.BOOLEAN -> {
            Row(modifier = commonModifier, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = currentValue.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) }
                )
                Text(property.name)
            }
        }
        CodexPropertyDataTypes.COLOR -> {
            ColorPropertyEditor(property.name, currentValue, onValueChange, commonModifier)
        }
        CodexPropertyDataTypes.LIST -> {
            ListPropertyEditor(property.name, currentValue, onValueChange, commonModifier)
        }
        CodexPropertyDataTypes.MAP -> {
            MapPropertyEditor(property.name, currentValue, onValueChange, commonModifier)
        }
        CodexPropertyDataTypes.IMAGE -> {
            MediaPropertyEditor(
                label = property.name,
                currentValue = currentValue,
                codexPath = codexPath,
                onValueChange = onValueChange,
                extensions = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"),
                modifier = commonModifier
            )
        }
        CodexPropertyDataTypes.AUDIO -> {
            MediaPropertyEditor(
                label = property.name,
                currentValue = currentValue,
                codexPath = codexPath,
                onValueChange = onValueChange,
                extensions = listOf("mp3", "wav", "ogg"),
                modifier = commonModifier
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