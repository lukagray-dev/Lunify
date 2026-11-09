package com.lunify.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.Track
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.GlassSheetBackground
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Queue Sheet (QueueSheet.kt)
 * ==============================================================================
 *
 * Hey friend! This component renders the sliding up-next queue sheet:
 *
 * - Shows the full playlist queue in order.
 * - Highlights the currently active song.
 * - Allows tapping any track to jump directly to it.
 * - Provides clear/shuffle controls.
 * ==============================================================================
 */

@Composable
fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    onClose: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
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
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Drag handle indicator
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(
                        color = TextMuted.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Up Next Queue",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${queue.size} tracks queued",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LucideIconButton(
                        iconRes = LucideIcons.Trash,
                        contentDescription = "Clear Queue",
                        onClick = onClearQueue,
                        iconSize = 20.dp,
                        buttonSize = 40.dp,
                        tint = TextMuted,
                    )
                    LucideIconButton(
                        iconRes = LucideIcons.Close,
                        contentDescription = "Close Queue",
                        onClick = onClose,
                        iconSize = 20.dp,
                        buttonSize = 40.dp,
                        tint = TextPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Queue List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(queue) { index, track ->
                    val isActive = index == currentIndex
                    TrackRowItem(
                        track = track,
                        isActive = isActive,
                        isPlaying = isActive,
                        onClick = { onTrackClick(index) },
                    )
                }
            }
        }
    }
}
