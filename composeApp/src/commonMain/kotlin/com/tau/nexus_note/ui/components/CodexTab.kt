package com.tau.nexus_note.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tau.nexus_note.ui.theme.LocalDensityTokens

/**
 * A wrapper for Tab that respects the density token for height and font size.
 */
@Composable
fun CodexTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    unselectedContentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensityTokens.current

    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(density.tabHeight),
        enabled = enabled,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        text = {
            Text(
                text = text,
                fontSize = density.bodyFontSize
            )
        }
    )
}