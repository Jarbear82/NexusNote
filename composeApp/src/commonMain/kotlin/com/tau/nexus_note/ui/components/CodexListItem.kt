package com.tau.nexus_note.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.labelToColor

@Composable
fun CodexListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    colorSeed: String = headline,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    actions: (@Composable (Color) -> Unit)? = null
) {
    val colorInfo = labelToColor(colorSeed)
    val densityTokens = LocalDensityTokens.current

    ListItem(
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontSize = densityTokens.titleFontSize
            )
        },
        supportingContent = if (supportingText != null) {
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = densityTokens.bodyFontSize
                )
            }
        } else null,
        leadingContent = if (leadingContent != null) {
            {
                Box(
                    modifier = Modifier.size(densityTokens.iconSize),
                    contentAlignment = Alignment.Center
                ) {
                    leadingContent()
                }
            }
        } else null,
        trailingContent = if (trailingContent != null) {
            {
                Box(
                    modifier = Modifier.size(densityTokens.iconSize),
                    contentAlignment = Alignment.Center
                ) {
                    trailingContent()
                }
            }
        } else null,
        modifier = modifier
            .fillMaxWidth()
            // If it has actions (Schema view), allow height to grow, otherwise fix it
            .then(if(actions == null) Modifier.height(densityTokens.listHeight) else Modifier)
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(densityTokens.cornerRadius)
            )
            .background(
                color = colorInfo.composeColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(densityTokens.cornerRadius)
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = colorInfo.composeFontColor,
            supportingColor = colorInfo.composeFontColor.copy(alpha = 0.8f),
            leadingIconColor = colorInfo.composeFontColor,
            trailingIconColor = colorInfo.composeFontColor
        )
    )
}