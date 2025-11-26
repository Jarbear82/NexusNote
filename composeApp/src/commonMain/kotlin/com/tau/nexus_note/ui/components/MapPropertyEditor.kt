package com.tau.nexus_note.ui.components.editors

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.PropertySerialization

@Composable
fun MapPropertyEditor(
    label: String,
    currentValueJson: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val map = remember(currentValueJson) { PropertySerialization.deserializeMap(currentValueJson) }
    val entries = map.entries.toList()
    val density = LocalDensityTokens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = density.bodyFontSize)

        Spacer(Modifier.height(4.dp))

        entries.forEachIndexed { index, (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { newKey ->
                        val newMap = map.toMutableMap()
                        newMap.remove(key) // Remove old key
                        newMap[newKey] = value // Add new key
                        onValueChange(PropertySerialization.serializeMap(newMap))
                    },
                    placeholder = { Text("Key", fontSize = density.bodyFontSize) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
                )
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { newValue ->
                        val newMap = map.toMutableMap()
                        newMap[key] = newValue
                        onValueChange(PropertySerialization.serializeMap(newMap))
                    },
                    placeholder = { Text("Value", fontSize = density.bodyFontSize) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
                )
                IconButton(onClick = {
                    val newMap = map.toMutableMap()
                    newMap.remove(key)
                    onValueChange(PropertySerialization.serializeMap(newMap))
                }) {
                    Icon(Icons.Default.Delete, "Remove Entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(density.iconSize))
                }
            }
        }

        Button(
            onClick = {
                val newMap = map.toMutableMap()
                var newKey = "New Key"
                var counter = 1
                while (newMap.containsKey(newKey)) {
                    newKey = "New Key $counter"
                    counter++
                }
                newMap[newKey] = ""
                onValueChange(PropertySerialization.serializeMap(newMap))
            },
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp).height(density.buttonHeight)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(density.iconSize))
            Spacer(Modifier.width(4.dp))
            Text("Add Pair")
        }
    }
}