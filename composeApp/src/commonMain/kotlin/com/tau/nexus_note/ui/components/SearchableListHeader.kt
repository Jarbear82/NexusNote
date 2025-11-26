package com.tau.nexus_note.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens

@Composable
fun SearchableListHeader(
    title: String,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onAddClick: (() -> Unit)? = null,
    addContentDescription: String = "Add",
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit
) {
    val density = LocalDensityTokens.current

    ListItem(
        leadingContent = leadingContent,
        headlineContent = {
            BoxWithConstraints(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Responsive Layout Logic (Stacked on small screens)
                if (maxWidth < 300.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = density.titleFontSize,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            placeholder = { Text("Search...", fontSize = density.bodyFontSize) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(density.iconSize)) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = density.titleFontSize,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            placeholder = { Text("Search...", fontSize = density.bodyFontSize) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(density.iconSize)) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize),
                            modifier = Modifier.weight(2f),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (onAddClick != null) {
                IconButton(onClick = onAddClick) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = addContentDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(density.iconSize)
                    )
                }
            }
        }
    )
}