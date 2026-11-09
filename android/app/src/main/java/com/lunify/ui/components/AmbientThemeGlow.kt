package com.lunify.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lunify.ui.theme.AccentWhite
import com.lunify.ui.theme.ObsidianBackground

/**
 * ==============================================================================
 * Lunify - Ambient Theme Glow (AmbientThemeGlow.kt)
 * ==============================================================================
 *
 * Hey friend! This component renders subtle monochrome ambient illumination
 * behind hero cards and the active player.
 *
 * Provides a soft silver-white spotlight that refracts through transparent-ish
 * liquid glass surfaces without introducing distracting colored tints!
 * ==============================================================================
 */

@Composable
fun AmbientThemeGlow(
    modifier: Modifier = Modifier,
    primaryColor: Color = AccentWhite,
    secondaryColor: Color = Color(0xFFE2E8F0),
    alpha: Float = 0.12f,
) {
    val animatedPrimary by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientPrimaryGlow",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientSecondaryGlow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimary.copy(alpha = alpha),
                        animatedSecondary.copy(alpha = alpha * 0.4f),
                        ObsidianBackground,
                    ),
                    center = Offset(300f, 200f),
                    radius = 900f,
                ),
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ObsidianBackground.copy(alpha = 0.65f),
                        ObsidianBackground,
                    ),
                ),
            ),
    )
}
