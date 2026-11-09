package com.lunify.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.lunify.domain.model.PlayerState
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassBorderProminent
import com.lunify.ui.theme.GlassSurfaceDark
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Liquid Glass MiniPlayer (MiniPlayer.kt)
 * ==============================================================================
 *
 * Hey friend! This component renders the floating MiniPlayer capsule positioned
 * directly above the bottom navigation bar.
 *
 * Following SimpMusic's design:
 * 1. Shows active track artwork, title with auto-scrolling marquee, and artist.
 * 2. Play/Pause and Next track controls directly accessible.
 * 3. Thin 2dp progress bar integrated into the lower edge.
 * 4. Tapping anywhere on the capsule smoothly opens the Fullscreen Now Playing screen.
 * ==============================================================================
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack ?: return
    val interactionSource = remember { MutableInteractionSource() }

    // Interactive pull-down-to-dismiss gesture state
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "miniPlayerDragOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = animatedOffsetY.roundToInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    // Only allow downward pulling (positive delta)
                    val newOffset = dragOffsetY + delta
                    dragOffsetY = if (newOffset > 0f) newOffset else 0f
                },
                onDragStopped = { velocity ->
                    // Pulling down past threshold (90px) or fast downward flick triggers dismissal
                    if (dragOffsetY > 90f || velocity > 500f) {
                        onClose()
                    }
                    dragOffsetY = 0f
                },
            )
            .padding(horizontal = 16.dp)
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = GlassSurfaceElevated,
                interactive = true,
                borderWidth = 1.dp,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onExpand,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle top drag handle indicating pull-down action
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Track Artwork Thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassSurfaceDark),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        iconRes = if (track.isVideo) LucideIcons.Video else LucideIcons.Music,
                        contentDescription = null,
                        size = 20.dp,
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title (with basicMarquee) and Artist Subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Action Button (Pure White circle with Black Icon)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        iconRes = if (playerState.isPlaying) LucideIcons.Pause else LucideIcons.Play,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        size = 18.dp,
                        tint = Color.Black,
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Skip Next Action Button
                LucideIconButton(
                    iconRes = LucideIcons.SkipForward,
                    contentDescription = "Skip Forward",
                    onClick = onNext,
                    iconSize = 20.dp,
                    buttonSize = 36.dp,
                    tint = Color.White,
                )
            }

            // Real-time Linear Progress Bar (2dp line at bottom edge)
            LinearProgressIndicator(
                progress = { playerState.progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}
