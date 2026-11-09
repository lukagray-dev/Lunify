package com.lunify.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Library & Offline Downloads Screen (LibraryScreen.kt)
 * ==============================================================================
 *
 * Hey friend! This screen manages the user's offline media library.
 *
 * As requested:
 * - Lunify supports downloading YouTube songs & videos with full metadata and
 *   synced lyrics for 100% offline playback.
 * - This screen displays downloaded tracks, storage consumption metrics, and
 *   allows zero-internet listening!
 * ==============================================================================
 */

val LibrarySubTabs = listOf("Downloaded", "Favorites", "Playlists", "History")

@Composable
fun LibraryScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf("Downloaded") }

    // Mock Offline Downloaded Library
    val downloadedTracks = remember {
        listOf(
            Track("dl1", "Resonance (Hi-Res Audio)", "HOME", "Odyssey", "3:32", 212000L, isDownloaded = true, isFavorite = true),
            Track("dl2", "Midnight City (Lossless)", "M83", "Hurry Up", "4:04", 244000L, isDownloaded = true),
            Track("dl3", "Starboy (Master)", "The Weeknd ft. Daft Punk", "Starboy", "3:50", 230000L, isDownloaded = true, isFavorite = true),
            Track("dl4", "Get Lucky (4K Video)", "Daft Punk", "Random Access", "4:08", 248000L, isVideo = true, isDownloaded = true),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // 1. Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your Library",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Storage usage badge
                LiquidGlassContainer(
                    shape = CircleShape,
                    backgroundColor = GlassSurfaceElevated,
                    modifier = Modifier.height(32.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Folder,
                            contentDescription = null,
                            size = 14.dp,
                            tint = Color.White,
                        )
                        Text(
                            text = "1.2 GB Used",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // 2. Subtabs Row ("Downloaded", "Favorites", "Playlists", "History")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySubTabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val animatedBgColor by animateColorAsState(
                        targetValue = if (isSelected) AccentCyan else GlassPill,
                        animationSpec = spring(),
                        label = "libSubTabBg",
                    )
                    val animatedTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color.Black else TextSecondary,
                        animationSpec = spring(),
                        label = "libSubTabText",
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                backgroundColor = animatedBgColor,
                                interactive = true,
                                borderWidth = if (isSelected) 0.dp else 1.dp,
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(color = Color.White),
                                onClick = { selectedTab = tab },
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab,
                            color = animatedTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Offline Status Banner Card
            LiquidGlassContainer(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = GlassSurfaceElevated,
                interactive = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.Check,
                                contentDescription = null,
                                size = 18.dp,
                                tint = AccentEmerald,
                            )
                            Text(
                                text = "Offline Ready",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Text(
                            text = "${downloadedTracks.size} tracks saved",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Downloaded with high-resolution album art & synchronized lyrics. Plays with zero internet connection.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Downloaded Tracks List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 180.dp),
            ) {
                items(downloadedTracks) { track ->
                    TrackRowItem(
                        track = track,
                        isActive = track.id == playerState.currentTrack?.id,
                        isPlaying = playerState.isPlaying,
                        onClick = { onTrackSelect(track) },
                        onMoreClick = { /* Options */ },
                    )
                }
            }
        }
    }
}
