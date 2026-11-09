package com.lunify.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunify.ui.theme.GlassSurfaceDark
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Liquid Glass Container (LiquidGlassContainer.kt)
 * ==============================================================================
 *
 * Hey friend! This composable wraps any layout in our signature liquid glass
 * surface.
 *
 * It provides:
 * 1. Automatic shape clipping and frosted translucency.
 * 2. Specular rim highlight.
 * 3. Optional click handler with smooth ripple.
 * 4. Automatic child alignment.
 * ==============================================================================
 */

@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = GlassSurfaceDark,
    interactive: Boolean = true,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickableModifier = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Color.White),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .liquidGlass(
                shape = shape,
                backgroundColor = backgroundColor,
                interactive = interactive,
                borderWidth = borderWidth,
            )
            .then(clickableModifier),
        contentAlignment = contentAlignment,
        content = content,
    )
}
