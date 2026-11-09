package com.lunify.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ==============================================================================
 * Lunify - Dark Theme Engine (Theme.kt)
 * ==============================================================================
 *
 * Hey friend! This file configures the global theme for Lunify.
 *
 * As specified in requirements:
 * 1. Lunify is ALWAYS in dark mode! (No blinding light mode switches).
 * 2. Status bars and navigation bars are configured transparent for edge-to-edge
 *    cinematic immersion.
 * 3. Liquid Glass configuration is exposed through CompositionLocal so any
 *    composable can query or toggle glass settings seamlessly.
 * ==============================================================================
 */

/**
 * Controls whether Liquid Glass rendering effects (blur, border shimmers,
 * touch glow) are enabled across the app.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

private val LunifyDarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color.Black,
    primaryContainer = ObsidianSurfaceHigh,
    onPrimaryContainer = Color.White,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = ObsidianSurface,
    onSecondaryContainer = Color.White,
    tertiary = AccentEmerald,
    onTertiary = Color.Black,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurfaceLow,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderSubtle,
    outlineVariant = GlassBorderProminent,
)

@Composable
fun LunifyTheme(
    enableLiquidGlass: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Set system status bar icon colors to light (since background is dark)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides enableLiquidGlass,
    ) {
        MaterialTheme(
            colorScheme = LunifyDarkColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
