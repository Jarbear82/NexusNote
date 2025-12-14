package com.tau.nexusnote.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/**
 * A reusable header for lists that includes a title, a search bar,
 * and an optional "Add" button.
 *
 * This header adapts its layout based on the available width using BoxWithConstraints.
 */
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
    ListItem(
        leadingContent = leadingContent,
        headlineContent = {
            // This box provides the 'maxWidth' constraint
            BoxWithConstraints(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Decide layout based on the maxWidth of the Box.
                if (maxWidth < 300.dp) {
                    // SMALL LAYOUT: Stacked
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            placeholder = { Text("Search...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                    }
                } else {
                    // LARGE LAYOUT: Original Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f) // Title takes 1/3
                        )
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            placeholder = { Text("Search...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            singleLine = true,
                            modifier = Modifier.weight(2f), // Search takes 2/3
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (onAddClick != null) {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add,
                        contentDescription = addContentDescription,
                        tint = MaterialTheme.colorScheme.primary
                        )
                }
            }
        }
    )
}