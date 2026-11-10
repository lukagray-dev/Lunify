package com.lunify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lunify.domain.model.Track
import com.lunify.ui.theme.GlassBorderSubtle
import com.lunify.ui.theme.GlassSheetBackground
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Media Detail Modal Sheet (MediaDetailSheet.kt)
 * ==============================================================================
 *
 * Hey friend! This modal sheet is the interactive detail view for Albums,
 * Curated Playlists, and Artist Discographies.
 *
 * WHAT IT DOES:
 * -------------
 * When a user taps on any Album, Playlist, or Artist card on the Home screen:
 * 1. This frosted obsidian liquid glass sheet smoothly slides up over the page.
 * 2. It renders a prominent hero header with the artwork, title, artist, and
 *    quick transport actions ("Play All" and "Shuffle").
 * 3. It displays the full, real tracklist fetched from YouTube Music InnerTube.
 * 4. Tapping any song immediately starts playback and queues the entire collection
 *    into [com.lunify.player.AudioPlayerManager]!
 * ==============================================================================
 */

@Composable
fun MediaDetailSheet(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    isArtist: Boolean,
    tracks: List<Track>,
    isLoading: Boolean,
    activeTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMoreClick: (Track) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                backgroundColor = GlassSheetBackground,
                interactive = false,
                borderWidth = 1.dp,
            )
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 12.dp),
        ) {
            // ------------------------------------------------------------------
            // 1. Drag Handle & Header Close
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LucideIconButton(
                    iconRes = LucideIcons.Close,
                    contentDescription = "Close",
                    onClick = onClose,
                    iconSize = 20.dp,
                    buttonSize = 34.dp,
                    tint = TextPrimary,
                )
            }

            // ------------------------------------------------------------------
            // 2. Hero Media Info (Artwork, Title, Subtitle & Action Capsule)
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // High-Resolution Cover Art
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
                        .background(GlassSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        LucideIcon(
                            iconRes = if (isArtist) LucideIcons.Profile else LucideIcons.Music,
                            contentDescription = title,
                            size = 32.dp,
                            tint = Color.White,
                        )
                    }
                }

                // Text Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLoading) "Loading tracks..." else "${tracks.size} tracks",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: "Play All" and "Shuffle"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play All Button
                Button(
                    onClick = onPlayAll,
                    enabled = tracks.isNotEmpty() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Play,
                            contentDescription = null,
                            size = 15.dp,
                            tint = Color.Black,
                        )
                        Text(
                            text = "Play All",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Shuffle Button
                Button(
                    onClick = onShuffle,
                    enabled = tracks.isNotEmpty() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f),
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Shuffle,
                            contentDescription = null,
                            size = 15.dp,
                            tint = Color.White,
                        )
                        Text(
                            text = "Shuffle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------------------------
            // 3. Scrollable Tracklist
            // ------------------------------------------------------------------
            if (isLoading) {
                // Render 5 sleek skeleton loader rows while tracks are fetched
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(5) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GlassSurfaceElevated),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(13.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GlassSurfaceElevated),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.35f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ObsidianSurfaceHigh),
                                )
                            }
                        }
                    }
                }
            } else if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No songs found for this collection.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(tracks) { _, track ->
                        TrackRowItem(
                            track = track,
                            isActive = track.id == activeTrackId,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onMoreClick(track) },
                        )
                    }
                }
            }
        }
    }
}
