package com.lunify.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.Track
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary

/**
 * ==============================================================================
 * Lunify - Quick Picks Section (QuickPicksSection.kt)
 * ==============================================================================
 *
 * Hey friend! This section renders real trending hit songs dynamically fetched
 * from YouTube Music's InnerTube API.
 *
 * Each track displays its artwork, title, artist, and duration, allowing users
 * to tap directly and start streaming with immediate queue support.
 *
 * When loading, sleek translucent skeleton placeholders are rendered instead of
 * any fake hardcoded mock songs.
 * ==============================================================================
 */

@Composable
fun QuickPicksSection(
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onMoreClick: (Track) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "START RADIO FROM A SONG",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "Quick Picks",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tracks.isEmpty() && isLoading) {
            // Render 4 translucent skeleton placeholder rows while live data is loading
            repeat(4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassSurfaceElevated),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(GlassSurfaceElevated),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ObsidianSurfaceHigh),
                        )
                    }
                }
            }
        } else {
            tracks.take(4).forEach { track ->
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
