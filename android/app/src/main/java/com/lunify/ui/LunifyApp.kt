package com.lunify.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lunify.domain.model.Album
import com.lunify.domain.model.LyricLine
import com.lunify.domain.model.PlaybackProgress
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.PlayerStatus
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import com.lunify.ui.components.AppBottomNavigationBar
import com.lunify.ui.duo.DuoSheet
import com.lunify.ui.navigation.AppNavigationGraph
import com.lunify.ui.navigation.NavRoute
import com.lunify.ui.player.FullscreenPlayer
import com.lunify.ui.player.MiniPlayer
import com.lunify.ui.player.QueueSheet
import com.lunify.ui.theme.ObsidianBackground
import kotlinx.coroutines.delay

/**
 * ==============================================================================
 * Lunify - Master Application Container (LunifyApp.kt)
 * ==============================================================================
 *
 * Hey friend! This is the master coordinator for the entire Lunify UI.
 *
 * It manages:
 * 1. Global Navigation State: Tracks active destination and handles tab switching.
 * 2. Active Playback Session:
 *    - Automatically shows the floating MiniPlayer when a song starts playing.
 *    - Drives the real-time position clock tick.
 *    - Handles next, previous, play, pause, and seeking.
 * 3. Fullscreen Now Playing Overlay:
 *    - Smoothly slides up from the bottom when the MiniPlayer is clicked.
 *    - Seamlessly displays synced Apple Music style lyrics.
 * 4. Queue Drawer:
 *    - Allows inspecting and managing upcoming songs.
 * ==============================================================================
 */

@Composable
fun LunifyApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoute.HOME

    // Sample initial queue for instant testability
    val initialQueue = remember {
        listOf(
            Track("t1", "Midnight City", "M83", "Hurry Up, We're Dreaming", "4:04", 244000L),
            Track("t2", "Starboy", "The Weeknd ft. Daft Punk", "Starboy", "3:50", 230000L),
            Track("t3", "Instant Crush", "Daft Punk ft. Julian Casablancas", "Random Access Memories", "5:37", 337000L),
            Track("t4", "Resonance", "HOME", "Odyssey", "3:32", 212000L),
        )
    }

    // Realistic Synced Lyrics Sample for "Midnight City"
    val sampleLyrics = remember {
        SyncedLyrics(
            trackId = "t1",
            lines = listOf(
                LyricLine(0L, "(Instrumental synth intro)"),
                LyricLine(12000L, "Waiting in a car"),
                LyricLine(16000L, "Waiting for a ride in the dark"),
                LyricLine(21000L, "The night city grows"),
                LyricLine(25000L, "Look and see her eyes, they glow"),
                LyricLine(31000L, "Waiting in a car"),
                LyricLine(35000L, "Waiting for a ride in the dark"),
                LyricLine(40000L, "The night city grows"),
                LyricLine(45000L, "Look and see her eyes, they glow"),
                LyricLine(52000L, "The city is my church"),
                LyricLine(57000L, "It wraps me in the sparkling twilight"),
                LyricLine(65000L, "(Saxophone solo)"),
            ),
        )
    }

    // Master UI Playback State
    var currentIndex by remember { mutableIntStateOf(0) }
    var playerStatus by remember { mutableStateOf(PlayerStatus.PLAYING) }
    var currentPositionMs by remember { mutableLongStateOf(14000L) }
    var isShuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showDuoSheet by remember { mutableStateOf(false) }

    val currentTrack = initialQueue.getOrNull(currentIndex)
    val totalDurationMs = currentTrack?.durationMs ?: 244000L

    val playerState = PlayerState(
        currentTrack = currentTrack,
        status = playerStatus,
        progress = PlaybackProgress(
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            bufferedPositionMs = (totalDurationMs * 0.7f).toLong(),
        ),
        queue = initialQueue,
        currentIndex = currentIndex,
        isShuffle = isShuffle,
        repeatMode = repeatMode,
    )

    // Background playback clock simulator: advances timestamp smoothly when playing
    LaunchedEffect(playerStatus) {
        while (playerStatus == PlayerStatus.PLAYING) {
            delay(500)
            if (currentPositionMs + 500L < totalDurationMs) {
                currentPositionMs += 500L
            } else {
                // Loop or advance to next track
                if (currentIndex + 1 < initialQueue.size) {
                    currentIndex++
                    currentPositionMs = 0L
                } else {
                    currentPositionMs = 0L
                    playerStatus = PlayerStatus.PAUSED
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // 1. Primary Navigation Host (Home, Search, Library, Settings)
        AppNavigationGraph(
            navController = navController,
            playerState = playerState,
            onTrackSelect = { track ->
                val idx = initialQueue.indexOfFirst { it.id == track.id }
                if (idx != -1) {
                    currentIndex = idx
                    currentPositionMs = 0L
                    playerStatus = PlayerStatus.PLAYING
                }
            },
            onAlbumSelect = { album ->
                // Navigate into album detail or play first track
                currentIndex = 0
                currentPositionMs = 0L
                playerStatus = PlayerStatus.PLAYING
            },
            onOpenDuo = { showDuoSheet = true },
        )

        // 2. Floating Bottom Dock (MiniPlayer + Floating Bottom Bar)
        // Hidden when FullscreenPlayer is expanded or when modal sheets (Queue, Duo) are open
        if (!isPlayerExpanded && !showQueue && !showDuoSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                // Floating MiniPlayer (appears whenever a song is loaded)
                if (playerState.hasTrack) {
                    MiniPlayer(
                        playerState = playerState,
                        onExpand = { isPlayerExpanded = true },
                        onTogglePlayPause = {
                            playerStatus = if (playerState.isPlaying) PlayerStatus.PAUSED else PlayerStatus.PLAYING
                        },
                        onNext = {
                            if (currentIndex + 1 < initialQueue.size) {
                                currentIndex++
                                currentPositionMs = 0L
                                playerStatus = PlayerStatus.PLAYING
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Floating Navigation Bar (Capsule + Search FAB)
                AppBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { targetRoute ->
                        if (targetRoute != currentRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(NavRoute.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }

        // 3. Fullscreen Now Playing Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FullscreenPlayer(
                playerState = playerState,
                lyrics = sampleLyrics,
                onCollapse = { isPlayerExpanded = false },
                onTogglePlayPause = {
                    playerStatus = if (playerState.isPlaying) PlayerStatus.PAUSED else PlayerStatus.PLAYING
                },
                onPrevious = {
                    if (currentIndex > 0) {
                        currentIndex--
                        currentPositionMs = 0L
                    } else {
                        currentPositionMs = 0L
                    }
                },
                onNext = {
                    if (currentIndex + 1 < initialQueue.size) {
                        currentIndex++
                        currentPositionMs = 0L
                    }
                },
                onSeek = { targetMs ->
                    currentPositionMs = targetMs
                },
                onToggleShuffle = { isShuffle = !isShuffle },
                onToggleRepeat = {
                    repeatMode = when (repeatMode) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                },
                onToggleFavorite = {
                    // Toggle favorite state
                },
                onToggleLyrics = { showLyrics = !showLyrics },
                onOpenQueue = { showQueue = true },
                onOpenDuo = { showDuoSheet = true },
                showLyrics = showLyrics,
            )
        }

        // Dimmed modal backdrop for Queue or Duo sheet
        if (showQueue || showDuoSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable {
                        showQueue = false
                        showDuoSheet = false
                    },
            )
        }

        // 4. Sliding Up-Next Queue Drawer Overlay
        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            QueueSheet(
                queue = initialQueue,
                currentIndex = currentIndex,
                onTrackClick = { index ->
                    currentIndex = index
                    currentPositionMs = 0L
                    playerStatus = PlayerStatus.PLAYING
                    showQueue = false
                },
                onClose = { showQueue = false },
                onClearQueue = { showQueue = false },
            )
        }

        // 5. Sliding Duo Pop-up Sheet Overlay
        AnimatedVisibility(
            visible = showDuoSheet,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DuoSheet(
                playerState = playerState,
                onTrackSelect = { track ->
                    val idx = initialQueue.indexOfFirst { it.id == track.id }
                    if (idx != -1) {
                        currentIndex = idx
                        currentPositionMs = 0L
                        playerStatus = PlayerStatus.PLAYING
                    }
                    showDuoSheet = false
                },
                onClose = { showDuoSheet = false },
            )
        }
    }
}
