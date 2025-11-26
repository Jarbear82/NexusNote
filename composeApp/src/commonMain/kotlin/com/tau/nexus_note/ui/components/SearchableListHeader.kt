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
import com.tau.nexus_note.ui.components.Icon
import com.tau.nexus_note.ui.components.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
                // Stacking logic for small screens
                val isSmall = maxWidth < 300.dp

                val content = @Composable {
                    if (isSmall) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = density.titleFontSize,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = density.titleFontSize,
                            // modifier = Modifier.weight(1f)
                        )
                    }

                    CodexTextField(
                        value = searchText,
                        onValueChange = onSearchTextChange,
                        placeholder = { Text("Search...", fontSize = density.bodyFontSize) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(density.iconSize)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )
                }

                if (isSmall) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { content() }
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