package com.lunify.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.Track
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary

/**
 * ==============================================================================
 * Lunify - Quick Picks Section (QuickPicksSection.kt)
 * ==============================================================================
 *
 * Hey friend! This section renders the 4-track "Quick Picks" group seen on the
 * home screen.
 *
 * Each track displays its artwork, title, artist, and duration, allowing users
 * to tap directly and start streaming in sub-300ms without having to dive into
 * deep album pages first!
 * ==============================================================================
 */

@Composable
fun QuickPicksSection(
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onMoreClick: (Track) -> Unit,
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
