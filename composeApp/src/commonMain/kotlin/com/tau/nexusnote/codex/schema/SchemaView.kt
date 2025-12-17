package com.tau.nexusnote.codex.schema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.ui.components.SearchableListHeader
import com.tau.nexusnote.datamodels.SchemaDefinition
import com.tau.nexusnote.utils.labelToColor

@Composable
fun SchemaView(
    schema: SchemaData?,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    onNodeClick: (SchemaDefinition) -> Unit,
    onEdgeClick: (SchemaDefinition) -> Unit,
    onEditNodeClick: (SchemaDefinition) -> Unit,
    onEditEdgeClick: (SchemaDefinition) -> Unit,
    onDeleteNodeClick: (SchemaDefinition) -> Unit,
    onDeleteEdgeClick: (SchemaDefinition) -> Unit,
    onAddNodeSchemaClick: () -> Unit,
    onAddEdgeSchemaClick: () -> Unit,
    onAddNodeClick: (SchemaDefinition) -> Unit,
    onAddEdgeClick: (SchemaDefinition) -> Unit,
    nodeSchemaSearchText: String,
    onNodeSchemaSearchChange: (String) -> Unit,
    edgeSchemaSearchText: String,
    onEdgeSchemaSearchChange: (String) -> Unit,
    schemaVisibility: Map<String, Boolean>,
    onToggleSchemaVisibility: (String) -> Unit
) {
    if (schema == null) {
        Text("Schema not loaded.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            // --- Node Schemas ---
            SearchableListHeader(
                title = "Node Schemas:",
                searchText = nodeSchemaSearchText,
                onSearchTextChange = onNodeSchemaSearchChange,
                onAddClick = onAddNodeSchemaClick,
                addContentDescription = "New Node Schema",
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Node Schema",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn {
                val filteredNodeSchemas = schema.nodeSchemas.filter {
                    it.name.contains(nodeSchemaSearchText, ignoreCase = true)
                }
                items(filteredNodeSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table

                    val propertiesText = table.properties.joinToString(separator = "\n") { prop ->
                        val suffix = if (prop.isDisplayProperty) ": (Display)" else ""
                        "  - ${prop.name}: ${prop.type}$suffix"
                    }

                    CodexListItem(
                        headline = table.name,
                        supportingText = propertiesText,
                        colorSeed = table.name,
                        isSelected = isSelected,
                        onClick = { onNodeClick(table) },
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                        actions = { fontColor ->
                            NodeSchemaActions(
                                table = table,
                                fontColor = fontColor,
                                schemaVisibility = schemaVisibility,
                                onToggleSchemaVisibility = onToggleSchemaVisibility,
                                onAddNodeClick = onAddNodeClick,
                                onEditNodeClick = onEditNodeClick,
                                onDeleteNodeClick = onDeleteNodeClick
                            )
                        }
                    )
                }
            }
        }

        // --- Edge Schemas ---
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            SearchableListHeader(
                title = "Edge Schemas:",
                searchText = edgeSchemaSearchText,
                onSearchTextChange = onEdgeSchemaSearchChange,
                onAddClick = onAddEdgeSchemaClick,
                addContentDescription = "New Edge Schema",
                leadingContent = {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = "Edge Schema",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn {
                val filteredEdgeSchemas = schema.edgeSchemas.filter {
                    it.name.contains(edgeSchemaSearchText, ignoreCase = true)
                }
                items(filteredEdgeSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table

                    // Constructing Roles text
                    val rolesText = table.roles.joinToString("\n") { role ->
                        "  - ${role.name} (${role.cardinality}): ${role.allowedNodeSchemas.joinToString(", ").ifEmpty { "Any" }}"
                    }

                    CodexListItem(
                        headline = table.name,
                        supportingText = rolesText.ifBlank { "No roles defined" },
                        colorSeed = table.name,
                        isSelected = isSelected,
                        onClick = { onEdgeClick(table) },
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                        actions = { fontColor ->
                            EdgeSchemaActions(
                                table = table,
                                fontColor = fontColor,
                                schemaVisibility = schemaVisibility,
                                onToggleSchemaVisibility = onToggleSchemaVisibility,
                                onEditEdgeClick = onEditEdgeClick,
                                onDeleteEdgeClick = onDeleteEdgeClick,
                                onAddEdgeClick = onAddEdgeClick
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    colorSeed: String = headline,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    actions: @Composable (fontColor: Color) -> Unit
) {
    val colorInfo = labelToColor(colorSeed)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(colorInfo.composeColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                color = colorInfo.composeFontColor,
            )
        }
        HorizontalDivider(color = colorInfo.composeFontColor)

        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = colorInfo.composeFontColor,
                modifier = Modifier.padding(8.dp)
            )
        }

        HorizontalDivider(color = colorInfo.composeFontColor)

        actions(colorInfo.composeFontColor)
    }
}

@Composable
private fun NodeSchemaActions(
    table: SchemaDefinition,
    fontColor: Color,
    schemaVisibility: Map<String, Boolean>,
    onToggleSchemaVisibility: (String) -> Unit,
    onAddNodeClick: (SchemaDefinition) -> Unit,
    onEditNodeClick: (SchemaDefinition) -> Unit,
    onDeleteNodeClick: (SchemaDefinition) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onToggleSchemaVisibility(table.id) }) {
            val isVisible = schemaVisibility[table.id] ?: true
            Icon(
                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isVisible) "Hide Schema" else "Show Schema",
                tint = fontColor,
            )
        }
        IconButton(onClick = { onAddNodeClick(table) }) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New Node",
                tint = fontColor,
            )
        }
        IconButton(onClick = { onEditNodeClick(table) }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit Node Schema",
                tint = fontColor,
            )
        }
        IconButton(onClick = { onDeleteNodeClick(table) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete Node Schema",
                tint = fontColor,
            )
        }
    }
}

@Composable
private fun EdgeSchemaActions(
    table: SchemaDefinition,
    fontColor: Color,
    schemaVisibility: Map<String, Boolean>,
    onToggleSchemaVisibility: (String) -> Unit,
    onEditEdgeClick: (SchemaDefinition) -> Unit,
    onDeleteEdgeClick: (SchemaDefinition) -> Unit,
    onAddEdgeClick: (SchemaDefinition) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Quick add button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Create Edge", style=MaterialTheme.typography.bodySmall, color=fontColor)
            IconButton(onClick = { onAddEdgeClick(table) }) {
                Icon(Icons.Default.Add, "Add Edge", tint = fontColor)
            }
        }

        IconButton(onClick = { onToggleSchemaVisibility(table.id) }) {
            val isVisible = schemaVisibility[table.id] ?: true
            Icon(
                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isVisible) "Hide Schema" else "Show Schema",
                tint = fontColor
            )
        }
        IconButton(onClick = { onEditEdgeClick(table) }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit Edge Schema",
                tint = fontColor
            )
        }
        IconButton(onClick = { onDeleteEdgeClick(table) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete Edge Schema",
                tint = fontColor
            )
        }
    }
}