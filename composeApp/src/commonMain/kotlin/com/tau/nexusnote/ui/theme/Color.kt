package com.tau.nexusnote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tau.nexusnote.utils.getFontColor
import com.tau.nexusnote.utils.hexToColor

// Default Fallback Colors
val FallbackAccent = Color(0xFF33C3FF)
val FallbackBackground = Color(0xFF121212)

/**
 * Calculates a high-contrast "on" color (black or white) for a given base color.
 * Uses the luminance formula helper from ViewUtils.
 */
fun getOnColor(baseColor: Color): Color {
    val colorInt = baseColor.toArgb()
    val r = (colorInt shr 16) and 0xFF
    val g = (colorInt shr 8) and 0xFF
    val b = colorInt and 0xFF

    // Reuse the existing utility that calculates WCAG contrast
    val hex = getFontColor(intArrayOf(r, g, b))
    return hexToColor(hex)
}