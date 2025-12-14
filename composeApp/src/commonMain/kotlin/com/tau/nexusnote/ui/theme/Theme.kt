package com.tau.nexusnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.tau.nexusnote.settings.ThemeMode
import com.tau.nexusnote.settings.ThemeSettings

@Composable
fun NexusNoteTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()

    // Re-calculate the color scheme only when settings or system theme changes
    val colorScheme = remember(settings, systemIsDark) {
        val accentColor = Color(settings.accentColor)
        val onAccentColor = getOnColor(accentColor)

        // Generate a container color (lighter/transparent version of accent)
        val accentContainerColor = accentColor.copy(alpha = 0.3f)
        val onAccentContainerColor = getOnColor(accentContainerColor)

        // 1. Determine the background color and whether we are in "Dark Mode"
        val (useDarkTheme, backgroundColor) = when (settings.themeMode) {
            ThemeMode.LIGHT -> false to Color.White
            ThemeMode.DARK -> true to Color.Black
            ThemeMode.SYSTEM -> systemIsDark to if (systemIsDark) Color.Black else Color.White
            ThemeMode.CUSTOM -> {
                val customBg = Color(settings.customBackgroundColor)
                // If custom background is dark (requires white text), treat as dark mode
                val isDark = getOnColor(customBg) == Color.White
                isDark to customBg
            }
        }

        // 2. Start with the standard Material 3 baseline
        val baseScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()

        // 3. Overlay our dynamic colors
        baseScheme.copy(
            // Apply Accent to Primary roles
            primary = accentColor,
            onPrimary = onAccentColor,
            primaryContainer = accentContainerColor,
            onPrimaryContainer = onAccentContainerColor,

            // Apply Accent to Secondary roles (Unified branding)
            secondary = accentColor,
            onSecondary = onAccentColor,
            secondaryContainer = accentContainerColor,
            onSecondaryContainer = onAccentContainerColor,

            // Apply Accent to Tertiary roles
            tertiary = accentColor,
            onTertiary = onAccentColor,
            tertiaryContainer = accentContainerColor,
            onTertiaryContainer = onAccentContainerColor,

            // Apply Background roles
            background = backgroundColor,
            onBackground = getOnColor(backgroundColor),

            // Surface matches background for a clean, flat look
            surface = backgroundColor,
            onSurface = getOnColor(backgroundColor)

            // Note: 'error', 'outline', etc. fall back to baseScheme defaults
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}