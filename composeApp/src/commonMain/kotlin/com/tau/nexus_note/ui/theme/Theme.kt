package com.tau.nexus_note.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.settings.DensityMode
import com.tau.nexus_note.settings.ThemeMode
import com.tau.nexus_note.settings.ThemeSettings

@Composable
fun NexusNoteTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()

    // 1. Resolve Density Tokens
    val baseDensityTokens = when (settings.densityMode) {
        DensityMode.COMPACT -> CompactTokens
        DensityMode.LARGE -> LargeTokens
        DensityMode.COMFORTABLE -> ComfortableTokens
    }

    // 2. Apply Corner Radius Override
    // If useRoundedCorners is false, force 0.dp everywhere
    val effectiveCornerRadius = if (settings.useRoundedCorners) baseDensityTokens.cornerRadius else 0.dp
    val finalDensityTokens = baseDensityTokens.copy(cornerRadius = effectiveCornerRadius)

    // 3. Re-calculate the color scheme only when settings or system theme changes
    val colorScheme = remember(settings, systemIsDark) {
        val accentColor = Color(settings.accentColor)
        val onAccentColor = getOnColor(accentColor)

        // Generate a container color (lighter/transparent version of accent)
        val accentContainerColor = accentColor.copy(alpha = 0.3f)
        val onAccentContainerColor = getOnColor(accentContainerColor)

        // Determine the background color and whether we are in "Dark Mode"
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

        // Start with the standard Material 3 baseline
        val baseScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()

        // Overlay our dynamic colors
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
        )
    }

    // 4. Create Shapes consistent with density/settings
    // We apply the effective corner radius to standard Material shapes as well
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(effectiveCornerRadius),
        small = RoundedCornerShape(effectiveCornerRadius),
        medium = RoundedCornerShape(effectiveCornerRadius),
        large = RoundedCornerShape(effectiveCornerRadius),
        extraLarge = RoundedCornerShape(effectiveCornerRadius)
    )

    // 5. Inject Density Tokens into CompositionLocal
    CompositionLocalProvider(LocalDensityTokens provides finalDensityTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = getAdaptiveTypography(finalDensityTokens),
            content = content
        )
    }
}