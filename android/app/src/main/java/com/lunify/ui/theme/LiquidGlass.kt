package com.lunify.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ==============================================================================
 * Lunify - Liquid Glass Modifiers (LiquidGlass.kt)
 * ==============================================================================
 *
 * Hey friend! This file provides the core `liquidGlass` modifiers that give
 * Lunify its distinct SimpMusic-inspired aesthetic.
 *
 * How our Liquid Glass works:
 * 1. Translucent Frosted Foundation:
 *    - Uses a rich, translucent dark obsidian backing (`GlassSurfaceDark` / `GlassSurfaceElevated`).
 * 2. Specular Rim Lighting (Refraction):
 *    - Applies a crisp, 45-degree angle linear gradient border that reflects
 *      light along the top and left edges, giving it real glass depth.
 * 3. Top Sheen Reflection:
 *    - Subtly lights the upper area with a vertical specular highlight gradient.
 * 4. Interactive Fluid Press Physics:
 *    - On touch, springs with a gentle scale-up (1.03f) and draws a radiant
 *      specular glow following the finger using `BlendMode.Plus`.
 * 5. Critical Architecture:
 *    - NEVER blurs the foreground content! Text, icons, and artwork remain
 *      100% razor sharp and crystal clear.
 * ==============================================================================
 */

/**
 * Applies the Liquid Glass material effect to any Compose component.
 *
 * Provides:
 * 1. Translucent frosted foundation backing (`GlassSurfaceDark` / `GlassSurfaceElevated`).
 * 2. Specular rim highlight border at 45-degree angle.
 * 3. Top specular sheen reflection for authentic glass refraction depth.
 * 4. 100% sharp child content rendering (icons, typography, artwork).
 *
 * @param shape Shape of the glass component (e.g. RoundedCornerShape(18.dp) or CircleShape).
 * @param backgroundColor Translucent foundation color.
 * @param borderBrush Gradient highlight brush outlining the glass perimeter.
 * @param interactive Kept for backward compatibility across existing calls.
 * @param borderWidth Width of the specular border (default 1.dp).
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = GlassSurfaceDark,
    borderBrush: Brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.03f),
        ),
        start = Offset(0f, 0f),
        end = Offset(300f, 300f),
    ),
    borderWidth: Dp = 1.dp,
    interactive: Boolean = true,
): Modifier = composed {
    val enabled = LocalLiquidGlassEnabled.current
    if (!enabled) {
        return@composed this
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderBrush, shape)
    }

    this
        .clip(shape)
        .background(backgroundColor)
        .border(borderWidth, borderBrush, shape)
        .drawWithContent {
            // 1. Frosted Glass Light Dispersion Layer (Soft diffuse light scatter)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.09f),
                        Color.White.copy(alpha = 0.02f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.3f, size.height * 0.25f),
                    radius = size.width * 0.75f,
                ),
            )

            // 2. Top Specular Sheen Reflection
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.55f,
                ),
            )

            // 3. Subtle Bottom Ambient Counter-Rim Reflection
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.04f),
                    ),
                    startY = size.height * 0.82f,
                    endY = size.height,
                ),
            )

            // 4. Draw children content (icons, text, thumbnails) with 100% crisp sharpness
            drawContent()
        }
}
