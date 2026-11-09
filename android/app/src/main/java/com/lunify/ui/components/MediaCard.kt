package com.lunify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassBorderSubtle
import com.lunify.ui.theme.GlassSurfaceDark
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Media Card Component (MediaCard.kt)
 * ==============================================================================
 *
 * Hey friend! This component renders the rich media cards seen across Lunify's
 * home screen and library:
 *
 * - Album & Playlist covers
 * - Quick pick suggestions
 * - Top charts items
 *
 * It features a 1:1 aspect ratio square artwork with smooth corner rounding (14dp),
 * a subtle glass play badge in the lower-right corner, and two-line typography.
 * ==============================================================================
 */

@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 140.dp,
    thumbnailUrl: String = "",
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick,
            ),
    ) {
        // Square Artwork Container with Liquid Glass Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .liquidGlass(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = GlassSurfaceDark,
                    interactive = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Background Artwork Placeholder / Fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ObsidianSurfaceHigh,
                                GlassSurfaceDark,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Music,
                    contentDescription = null,
                    size = 40.dp,
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }

            // Quick Play Badge Button (Lower-right corner)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        (onPlayClick ?: onClick).invoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(
                    iconRes = if (isPlaying) LucideIcons.Pause else LucideIcons.Play,
                    contentDescription = "Play",
                    size = 14.dp,
                    tint = Color.Black,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title text
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        // Subtitle text (Artist, album, or playlist tag)
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
