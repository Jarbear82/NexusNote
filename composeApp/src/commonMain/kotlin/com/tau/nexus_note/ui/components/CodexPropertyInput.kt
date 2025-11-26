package com.tau.nexus_note.ui.components

import androidx.compose.foundation.layout.PaddingValues
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
import com.tau.nexus_note.ui.theme.LocalDensityTokens

@Composable
fun CodexPropertyInput(
    property: SchemaProperty,
    currentValue: String,
    onValueChange: (String) -> Unit,
    codexPath: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensityTokens.current
    val commonModifier = modifier.fillMaxWidth().padding(vertical = 4.dp)

    // We control the height of TextFields via contentPadding
    val inputPadding = PaddingValues(
        horizontal = 12.dp,
        vertical = density.inputVerticalPadding
    )

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
                singleLine = true,
                // Apply Density
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize),
                // Note: OutlinedTextField padding isn't directly exposed as a param in 1.0 stable easily without internal implementation details
                // or defining shape. However, standard Compose TextField handles padding internally based on minHeight.
                // A standard workaround is to use `modifier.height()` for fixed size or let it scale.
                // For OutlinedTextField, `contentPadding` is not a direct parameter in standard Material3 (it is in Material2).
                // In M3 we rely on shape and minHeight.
                // Let's try `modifier.defaultMinHeight` scaling.
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
                maxLines = 8,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
            )
        }
        CodexPropertyDataTypes.DATE -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text("${property.name} (Date)") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = commonModifier,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
            )
        }
        CodexPropertyDataTypes.BOOLEAN -> {
            Row(modifier = commonModifier, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = currentValue.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) }
                )
                Text(property.name, fontSize = density.bodyFontSize)
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
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
            )
        }
    }
}