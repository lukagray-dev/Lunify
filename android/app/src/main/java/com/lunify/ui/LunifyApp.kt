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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.domain.model.LyricLine
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import com.lunify.player.AudioPlayerManager
import com.lunify.ui.components.AppBottomNavigationBar
import com.lunify.ui.duo.DuoSheet
import com.lunify.ui.navigation.AppNavigationGraph
import com.lunify.ui.navigation.NavRoute
import com.lunify.ui.player.FullscreenPlayer
import com.lunify.ui.player.MiniPlayer
import com.lunify.ui.player.QueueSheet
import com.lunify.ui.theme.ObsidianBackground

/**
 * ==============================================================================
 * Lunify - Master Application Container (LunifyApp.kt)
 * ==============================================================================
 *
 * Hey friend! This is the master coordinator for the entire Lunify UI.
 *
 * It manages:
 * 1. Global Navigation State: Tracks active destination and handles tab switching.
 * 2. Real Audio Playback Integration:
 *    - Connects directly to [AudioPlayerManager], which wraps Media3 ExoPlayer.
 *    - Both the floating MiniPlayer and FullscreenPlayer share real-time state.
 *    - Implements precise Previous button logic (> 3s restart vs <= 3s previous track).
 * 3. Offline Library & Storage Integration:
 *    - Passes [LibraryStorage] and [LocalAudioRepository] down to the library graph.
 * 4. Fullscreen Now Playing Overlay & Modal Sheets (Queue & Duo).
 * ==============================================================================
 */

@Composable
fun LunifyApp(
    audioPlayerManager: AudioPlayerManager,
    libraryStorage: LibraryStorage,
    localAudioRepository: LocalAudioRepository,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoute.HOME

    // Sample initial demo queue for instant exploration if player queue is empty
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

    // Reactive single source of truth for playback state
    val playerState by audioPlayerManager.playerState.collectAsState()

    // Initialize queue with initial tracks if empty
    LaunchedEffect(Unit) {
        if (playerState.queue.isEmpty()) {
            audioPlayerManager.setQueue(initialQueue, 0)
        }
    }

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showDuoSheet by remember { mutableStateOf(false) }
    var isMiniPlayerDismissed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // ----------------------------------------------------------------------
        // 1. Primary Navigation Host (Home, Search, Library, Settings)
        // ----------------------------------------------------------------------
        AppNavigationGraph(
            navController = navController,
            playerState = playerState,
            onTrackSelect = { track ->
                isMiniPlayerDismissed = false
                audioPlayerManager.playTrack(track)
            },
            onPlayQueue = { queue, track ->
                isMiniPlayerDismissed = false
                audioPlayerManager.playTrack(track, queue)
            },
            onAlbumSelect = { album ->
                isMiniPlayerDismissed = false
                if (playerState.queue.isNotEmpty()) {
                    audioPlayerManager.playTrack(playerState.queue.first())
                }
            },
            onOpenDuo = { showDuoSheet = true },
            libraryStorage = libraryStorage,
            localAudioRepository = localAudioRepository,
        )

        // ----------------------------------------------------------------------
        // 2. Progressive Dissolve Scrim behind Bottom Dock
        // ----------------------------------------------------------------------
        if (!isPlayerExpanded && !showQueue && !showDuoSheet) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ObsidianBackground.copy(alpha = 0.55f),
                                ObsidianBackground.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )
        }

        // ----------------------------------------------------------------------
        // 3. Floating Bottom Dock (MiniPlayer + Floating Bottom Bar)
        // ----------------------------------------------------------------------
        if (!isPlayerExpanded && !showQueue && !showDuoSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                // Floating MiniPlayer with pull-down-to-dismiss gesture
                AnimatedVisibility(
                    visible = playerState.hasTrack && !isMiniPlayerDismissed,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(
                        playerState = playerState,
                        onExpand = { isPlayerExpanded = true },
                        onTogglePlayPause = { audioPlayerManager.togglePlayPause() },
                        onNext = { audioPlayerManager.playNext() },
                        onClose = {
                            isMiniPlayerDismissed = true
                            if (playerState.isPlaying) {
                                audioPlayerManager.togglePlayPause()
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

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

        // ----------------------------------------------------------------------
        // 4. Fullscreen Now Playing Overlay
        // ----------------------------------------------------------------------
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FullscreenPlayer(
                playerState = playerState,
                lyrics = sampleLyrics,
                onCollapse = { isPlayerExpanded = false },
                onTogglePlayPause = { audioPlayerManager.togglePlayPause() },
                onPrevious = { audioPlayerManager.playPrevious() },
                onNext = { audioPlayerManager.playNext() },
                onSeek = { targetMs -> audioPlayerManager.seekTo(targetMs) },
                onToggleShuffle = { audioPlayerManager.toggleShuffle() },
                onToggleRepeat = { audioPlayerManager.toggleRepeat() },
                onToggleFavorite = {
                    playerState.currentTrack?.let { track ->
                        audioPlayerManager.toggleFavorite(track)
                    }
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

        // ----------------------------------------------------------------------
        // 5. Sliding Up-Next Queue Drawer Overlay
        // ----------------------------------------------------------------------
        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            QueueSheet(
                queue = playerState.queue,
                currentIndex = playerState.currentIndex,
                onTrackClick = { index ->
                    val clickedTrack = playerState.queue.getOrNull(index)
                    if (clickedTrack != null) {
                        isMiniPlayerDismissed = false
                        audioPlayerManager.playTrack(clickedTrack)
                    }
                    showQueue = false
                },
                onClose = { showQueue = false },
                onClearQueue = { showQueue = false },
            )
        }

        // ----------------------------------------------------------------------
        // 6. Sliding Duo Pop-up Sheet Overlay
        // ----------------------------------------------------------------------
        AnimatedVisibility(
            visible = showDuoSheet,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DuoSheet(
                playerState = playerState,
                onTrackSelect = { track ->
                    isMiniPlayerDismissed = false
                    audioPlayerManager.playTrack(track)
                    showDuoSheet = false
                },
                onClose = { showDuoSheet = false },
            )
        }
    }
}
