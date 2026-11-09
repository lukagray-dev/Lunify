package com.lunify.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.SyncedLyrics
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary

/**
 * ==============================================================================
 * Lunify - Synchronized Scrolling Lyrics View (LyricsView.kt)
 * ==============================================================================
 *
 * Hey friend! This component delivers the Apple Music / Spotify style
 * synchronized lyrics experience seen in SimpMusic.
 *
 * Key features:
 * 1. Automatic Vertical Centering:
 *    - Uses `LazyListState.animateScrollToItem()` to keep the currently sung
 *      line centered on screen as audio progresses.
 * 2. Expressive Active Line Physics:
 *    - Active line scales up (1.05f), glows bright white, and features bold weight.
 *    - Past/future lines smoothly dim to secondary slate gray.
 * 3. Interactive Seek-On-Tap:
 *    - Tapping any lyric line instantly seeks the track directly to that timestamp!
 * 4. Graceful Fallback:
 *    - Handles instrumental breaks and plain text lyrics when synced data is unavailable.
 * ==============================================================================
 */

@Composable
fun LyricsView(
    lyrics: SyncedLyrics,
    currentPositionMs: Long,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics.lines.isEmpty()) {
        // Empty state / Searching fallback
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Music,
                    contentDescription = null,
                    size = 40.dp,
                    tint = TextMuted,
                )
                Text(
                    text = if (lyrics.plainText.isNotEmpty()) lyrics.plainText else "No lyrics available for this track",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        return
    }

    val activeIndex = lyrics.findActiveIndex(currentPositionMs)
    val listState = rememberLazyListState()

    // Smoothly scroll active line to center when it changes
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            // Offset scroll so line sits nicely in upper-middle viewport
            listState.animateScrollToItem(
                index = (activeIndex - 1).coerceAtLeast(0),
                scrollOffset = -100,
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeIndex
            val animatedColor by animateColorAsState(
                targetValue = if (isActive) TextPrimary else TextMuted.copy(alpha = 0.5f),
                animationSpec = spring(),
                label = "lyricTextColor",
            )
            val animatedScale by animateFloatAsState(
                targetValue = if (isActive) 1.05f else 0.98f,
                animationSpec = spring(),
                label = "lyricTextScale",
            )

            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.White),
                        onClick = { onSeekToLine(line.timestampMs) },
                    )
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = line.text,
                    color = animatedColor,
                    fontSize = if (isActive) 22.sp else 18.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = 28.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    },
                )
            }
        }
    }
}
