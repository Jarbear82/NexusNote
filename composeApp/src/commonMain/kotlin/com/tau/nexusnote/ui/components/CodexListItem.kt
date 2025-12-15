package com.tau.nexusnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.utils.labelToColor

/**
 * A customizable list item used throughout the Codex UI.
 * Updated to support rich content in the headline slot.
 */
@Composable
fun CodexListItem(
    modifier: Modifier = Modifier,
    // Optional: Pass a simple string...
    headline: String? = null,
    // ...OR pass a composable for rich content (Takes precedence)
    headlineContent: (@Composable () -> Unit)? = null,

    supportingText: String? = null,
    // Used to generate the consistent background color
    colorSeed: String = headline ?: "default",
    isSelected: Boolean = false,
    onClick: () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val colorInfo = labelToColor(colorSeed)

    ListItem(
        headlineContent = {
            if (headlineContent != null) {
                headlineContent()
            } else {
                Text(headline ?: "")
            }
        },
        supportingContent = if (supportingText != null) {
            { Text(supportingText) }
        } else null,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            .background(colorInfo.composeColor),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = colorInfo.composeFontColor,
            supportingColor = colorInfo.composeFontColor.copy(alpha = 0.8f),
            leadingIconColor = colorInfo.composeFontColor,
            trailingIconColor = colorInfo.composeFontColor
        )
    )
}