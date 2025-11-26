package com.tau.nexus_note.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import androidx.compose.material3.Icon as M3Icon

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val density = LocalDensityTokens.current

    M3Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        // Apply density-based size (e.g., 18dp, 24dp, 32dp)
        modifier = Modifier.size(density.iconSize).then(modifier),
        tint = tint
    )
}