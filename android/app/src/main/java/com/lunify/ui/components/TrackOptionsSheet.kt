package com.lunify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.Track
import com.lunify.ui.theme.AccentRose
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.GlassSheetBackground
import com.lunify.ui.theme.GlassSurfaceDark
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Track Options Menu Sheet (TrackOptionsSheet.kt)
 * ==============================================================================
 *
 * Hey friend! This pop-up sheet slides up from the bottom when the user taps
 * the three vertical dots on any track row.
 *
 * It provides full, real actions:
 * 1. Like / Unlike: Toggles the persistent favorite state.
 * 2. Delete / Download: Deletes offline audio from device storage or saves it.
 * 3. Add to playlist: Opens the playlist picker to categorize tracks.
 * 4. Play next: Inserts song right after the currently active track in queue.
 * 5. Add to queue: Appends song to the end of the queue.
 * 6. Share: Fires native Android Share Intent to share audio files with other apps.
 * ==============================================================================
 */

@Composable
fun TrackOptionsSheet(
    track: Track,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDeleteOrDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(TextMuted.copy(alpha = 0.4f), CircleShape),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Track Header Preview (Thumbnail + Title + Artist)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassSurfaceDark),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        iconRes = if (track.isVideo) LucideIcons.Video else LucideIcons.Music,
                        contentDescription = null,
                        size = 24.dp,
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} • ${track.formattedDuration()}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                LucideIconButton(
                    iconRes = LucideIcons.Close,
                    contentDescription = "Close options",
                    onClick = onClose,
                    iconSize = 20.dp,
                    buttonSize = 36.dp,
                    tint = TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Separator Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 1: Like / Unlike
            OptionRowItem(
                iconRes = LucideIcons.Heart,
                title = if (isFavorite) "Unlike" else "Like",
                tint = if (isFavorite) AccentRose else Color.White,
                onClick = {
                    onToggleFavorite()
                    onClose()
                },
            )

            // Option 2: Delete from device (if downloaded) or Download
            OptionRowItem(
                iconRes = if (track.isDownloaded) LucideIcons.Trash else LucideIcons.Download,
                title = if (track.isDownloaded) "Delete from device" else "Download",
                tint = if (track.isDownloaded) AccentRose.copy(alpha = 0.9f) else Color.White,
                onClick = {
                    onDeleteOrDownload()
                    onClose()
                },
            )

            // Option 3: Add to playlist
            OptionRowItem(
                iconRes = LucideIcons.ListPlus,
                title = "Add to playlist",
                tint = Color.White,
                onClick = {
                    onAddToPlaylist()
                    onClose()
                },
            )

            // Option 4: Play next
            OptionRowItem(
                iconRes = LucideIcons.SkipForward,
                title = "Play next",
                tint = Color.White,
                onClick = {
                    onPlayNext()
                    onClose()
                },
            )

            // Option 5: Add to queue
            OptionRowItem(
                iconRes = LucideIcons.FilePlus,
                title = "Add to queue",
                tint = Color.White,
                onClick = {
                    onAddToQueue()
                    onClose()
                },
            )

            // Option 6: Share
            OptionRowItem(
                iconRes = LucideIcons.Share,
                title = "Share track",
                tint = Color.White,
                onClick = {
                    onShare()
                    onClose()
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OptionRowItem(
    iconRes: Int,
    title: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GlassPill),
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 18.dp,
                tint = tint,
            )
        }

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
