package com.tau.nexus_note.ui.components.display

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.utils.PropertySerialization
import com.tau.nexus_note.utils.hexToColor

@Composable
fun SmartPropertyRenderer(
    property: SchemaProperty,
    value: String,
    mediaRootPath: String // Updated parameter
) {
    if (value.isBlank()) {
        Text("-", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    when (property.type) {
        CodexPropertyDataTypes.TEXT, CodexPropertyDataTypes.NUMBER, CodexPropertyDataTypes.DATE -> {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        CodexPropertyDataTypes.LONG_TEXT, CodexPropertyDataTypes.MARKDOWN -> {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        CodexPropertyDataTypes.BOOLEAN -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = value.toBooleanStrictOrNull() ?: false, onCheckedChange = null) // Read-only
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
        CodexPropertyDataTypes.COLOR -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = MaterialTheme.shapes.small,
                    color = hexToColor(value),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
        CodexPropertyDataTypes.LIST -> {
            val items = PropertySerialization.deserializeList(value)
            Column {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        CodexPropertyDataTypes.MAP -> {
            val map = PropertySerialization.deserializeMap(value)
            Column {
                map.forEach { (k, v) ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("$k:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(100.dp))
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        CodexPropertyDataTypes.IMAGE -> {
            ImagePreview(value, mediaRootPath)
        }
        CodexPropertyDataTypes.AUDIO -> {
            AudioPreview(value, mediaRootPath)
        }
    }
}