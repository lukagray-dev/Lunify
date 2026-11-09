package com.lunify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.Track
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassSurfaceDark
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Track Row Item (TrackRowItem.kt)
 * ==============================================================================
 *
 * Hey friend! This component renders individual song rows across search lists,
 * playlist tables, offline libraries, and the up-next queue.
 *
 * It features:
 * 1. 48dp rounded artwork thumbnail.
 * 2. Active playing indicator (neon cyan wave icon or tint).
 * 3. Title & artist/duration layout.
 * 4. Offline download badge (showing users which tracks work with zero internet).
 * 5. Instant tap-to-play with smooth ripple feedback.
 * ==============================================================================
 */

@Composable
fun TrackRowItem(
    track: Track,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail Artwork Container
        Box(
            modifier = Modifier
                .size(48.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = GlassSurfaceDark,
                    interactive = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(
                iconRes = if (track.isVideo) LucideIcons.Video else LucideIcons.Music,
                contentDescription = null,
                size = 22.dp,
                tint = if (isActive) Color.White else TextPrimary.copy(alpha = 0.85f),
            )

            // Active Playing Soundwave Overlay
            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        iconRes = LucideIcons.Wave,
                        contentDescription = "Active Playing",
                        size = 22.dp,
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                color = if (isActive) Color.White else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Offline downloaded pill badge
                if (track.isDownloaded) {
                    LucideIcon(
                        iconRes = LucideIcons.Check,
                        contentDescription = "Offline ready",
                        size = 12.dp,
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = "${track.artist} • ${track.formattedDuration()}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Options or action button
        if (onMoreClick != null) {
            LucideIconButton(
                iconRes = LucideIcons.MoreVertical,
                contentDescription = "Track options",
                onClick = onMoreClick,
                iconSize = 18.dp,
                buttonSize = 36.dp,
                tint = TextMuted,
            )
        }
    }
}
