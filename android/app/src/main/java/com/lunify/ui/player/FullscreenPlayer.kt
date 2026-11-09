package com.lunify.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import com.lunify.ui.components.AmbientThemeGlow
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.AccentPurple
import com.lunify.ui.theme.AccentRose
import com.lunify.ui.theme.AccentWhite
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Fullscreen Now Playing Screen (FullscreenPlayer.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the primary media stage of Lunify.
 *
 * Recreating SimpMusic's full-player layout:
 * 1. Ambient Blurred Backdrop: Dynamic color glow adapting to the playing song.
 * 2. High-res Artwork Hero: 1:1 aspect ratio with 24dp rounded liquid glass corners.
 * 3. Title, Artist, & Favorite Heart indicator.
 * 4. Custom Scrubbing Slider with current / total timestamps.
 * 5. Full Transport controls (Shuffle, Prev, Big Play/Pause, Next, Repeat).
 * 6. Quick toggle pills for Lyrics, Queue drawer, and Duo Sync mode!
 * ==============================================================================
 */

@Composable
fun FullscreenPlayer(
    playerState: PlayerState,
    lyrics: SyncedLyrics?,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDuo: () -> Unit,
    onOpenTrackOptions: (Track) -> Unit = {},
    showLyrics: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // 1. Ambient Dynamic Color Mesh Glow in Background
        AmbientThemeGlow(
            primaryColor = AccentWhite,
            secondaryColor = Color(0xFFE2E8F0),
            alpha = 0.15f,
        )

        // 2. Main Player Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // A. Top Bar (Chevron Down, Source Title, More Actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIconButton(
                    iconRes = LucideIcons.ChevronDown,
                    contentDescription = "Collapse Player",
                    onClick = onCollapse,
                    iconSize = 26.dp,
                    buttonSize = 44.dp,
                    tint = TextPrimary,
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = if (track.album.isNotEmpty()) track.album else "YouTube Music",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                LucideIconButton(
                    iconRes = LucideIcons.MoreVertical,
                    contentDescription = "Track options",
                    onClick = { onOpenTrackOptions(track) },
                    iconSize = 22.dp,
                    buttonSize = 44.dp,
                    tint = TextPrimary,
                )
            }

            // B. Center Content: Large Artwork OR Lyrics View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics && lyrics != null) {
                    // Synchronized Scrolling Lyrics
                    LyricsView(
                        lyrics = lyrics,
                        currentPositionMs = playerState.progress.currentPositionMs,
                        onSeekToLine = onSeek,
                    )
                } else {
                    // Hero Large Artwork Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .liquidGlass(
                                shape = RoundedCornerShape(24.dp),
                                backgroundColor = ObsidianSurfaceHigh,
                                interactive = false,
                                borderWidth = 1.5.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (track.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                ObsidianSurfaceHigh,
                                                GlassSurfaceElevated,
                                            ),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                LucideIcon(
                                    iconRes = if (track.isVideo) LucideIcons.Video else LucideIcons.Music,
                                    contentDescription = null,
                                    size = 80.dp,
                                    tint = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            // C. Metadata & Like Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                LucideIconButton(
                    iconRes = LucideIcons.Heart,
                    contentDescription = "Favorite Track",
                    onClick = onToggleFavorite,
                    iconSize = 26.dp,
                    buttonSize = 48.dp,
                    tint = if (track.isFavorite) AccentRose else TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // D. Interactive Seek Bar & Timestamps
            var isUserScrubbing by remember { mutableStateOf(false) }
            var scrubPositionFraction by remember { mutableFloatStateOf(0f) }

            val displayFraction = if (isUserScrubbing) scrubPositionFraction else playerState.progress.fraction

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = displayFraction,
                    onValueChange = { fraction ->
                        isUserScrubbing = true
                        scrubPositionFraction = fraction
                    },
                    onValueChangeFinished = {
                        val targetMs = (scrubPositionFraction * playerState.progress.totalDurationMs).toLong()
                        onSeek(targetMs)
                        isUserScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val currentDisplayMs = if (isUserScrubbing) {
                        (scrubPositionFraction * playerState.progress.totalDurationMs).toLong()
                    } else {
                        playerState.progress.currentPositionMs
                    }
                    Text(
                        text = com.lunify.domain.model.PlaybackProgress.formatMs(currentDisplayMs),
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = playerState.progress.formattedTotalTime,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // E. Transport Controls Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Shuffle Button
                LucideIconButton(
                    iconRes = LucideIcons.Shuffle,
                    contentDescription = "Shuffle",
                    onClick = onToggleShuffle,
                    iconSize = 22.dp,
                    buttonSize = 44.dp,
                    tint = if (playerState.isShuffle) Color.White else TextMuted,
                )

                // Skip Back Button
                LucideIconButton(
                    iconRes = LucideIcons.SkipBack,
                    contentDescription = "Previous Track",
                    onClick = onPrevious,
                    iconSize = 26.dp,
                    buttonSize = 48.dp,
                    tint = TextPrimary,
                )

                // Main Play/Pause Floating Circle (Pure White Circle with Black Icon)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        iconRes = if (playerState.isPlaying) LucideIcons.Pause else LucideIcons.Play,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        size = 32.dp,
                        tint = Color.Black,
                    )
                }

                // Skip Forward Button
                LucideIconButton(
                    iconRes = LucideIcons.SkipForward,
                    contentDescription = "Next Track",
                    onClick = onNext,
                    iconSize = 26.dp,
                    buttonSize = 48.dp,
                    tint = TextPrimary,
                )

                // Repeat Button (Off / All / One)
                LucideIconButton(
                    iconRes = LucideIcons.Repeat,
                    contentDescription = "Repeat",
                    onClick = onToggleRepeat,
                    iconSize = 22.dp,
                    buttonSize = 44.dp,
                    tint = if (playerState.repeatMode != RepeatMode.OFF) Color.White else TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // F. Bottom Action Pills (Lyrics, Queue, Duo)
            val hasLyrics = lyrics != null && lyrics.lines.isNotEmpty()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Lyrics Pill Toggle (disabled when track has no lyrics)
                LiquidGlassContainer(
                    shape = CircleShape,
                    backgroundColor = when {
                        !hasLyrics -> GlassSurfaceElevated.copy(alpha = 0.35f)
                        showLyrics -> Color.White
                        else -> GlassSurfaceElevated
                    },
                    onClick = if (hasLyrics) onToggleLyrics else null,
                    modifier = Modifier.height(38.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Music,
                            contentDescription = null,
                            size = 14.dp,
                            tint = when {
                                !hasLyrics -> TextMuted
                                showLyrics -> Color.Black
                                else -> TextSecondary
                            },
                        )
                        Text(
                            text = if (hasLyrics) "Lyrics" else "No Lyrics",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                !hasLyrics -> TextMuted
                                showLyrics -> Color.Black
                                else -> TextPrimary
                            },
                        )
                    }
                }

                // Queue Pill Toggle
                LiquidGlassContainer(
                    shape = CircleShape,
                    backgroundColor = GlassSurfaceElevated,
                    onClick = onOpenQueue,
                    modifier = Modifier.height(38.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Browse,
                            contentDescription = null,
                            size = 14.dp,
                            tint = TextSecondary,
                        )
                        Text(
                            text = "Queue (${playerState.queue.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                    }
                }

                // Duo Listen Together Pill Toggle
                LiquidGlassContainer(
                    shape = CircleShape,
                    backgroundColor = GlassSurfaceElevated,
                    onClick = onOpenDuo,
                    modifier = Modifier.height(38.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Duo,
                            contentDescription = null,
                            size = 14.dp,
                            tint = Color.White,
                        )
                        Text(
                            text = "Duo Sync",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
