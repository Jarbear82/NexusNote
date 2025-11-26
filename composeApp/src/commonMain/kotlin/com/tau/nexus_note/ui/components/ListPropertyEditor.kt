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
import com.tau.nexus_note.utils.PropertySerialization

@Composable
fun ListPropertyEditor(
    label: String,
    currentValueJson: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Parse JSON string to List
    val items = remember(currentValueJson) { PropertySerialization.deserializeList(currentValueJson) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(4.dp))

        // Display Items
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { newValue ->
                        val newItems = items.toMutableList()
                        newItems[index] = newValue
                        onValueChange(PropertySerialization.serializeList(newItems))
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = {
                    val newItems = items.toMutableList()
                    newItems.removeAt(index)
                    onValueChange(PropertySerialization.serializeList(newItems))
                }) {
                    Icon(Icons.Default.Delete, "Remove Item", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Add Button
        Button(
            onClick = {
                val newItems = items + ""
                onValueChange(PropertySerialization.serializeList(newItems))
            },
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Item")
        }
    }
}