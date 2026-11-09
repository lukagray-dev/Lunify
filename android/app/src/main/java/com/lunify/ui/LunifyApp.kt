package com.lunify.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.data.repository.LyricsRepository
import com.lunify.domain.model.LyricLine
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import com.lunify.domain.model.TrackSortOption
import com.lunify.player.AudioPlayerManager
import com.lunify.ui.components.AddToPlaylistSheet
import com.lunify.ui.components.AppBottomNavigationBar
import com.lunify.ui.components.SortOptionsSheet
import com.lunify.ui.components.TrackOptionsSheet
import com.lunify.ui.duo.DuoSheet
import com.lunify.ui.library.shareTrack
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

    val context = LocalContext.current

    // Sample initial demo queue for instant exploration if player queue is empty
    val initialQueue = remember {
        listOf(
            Track("t1", "Midnight City", "M83", "Hurry Up, We're Dreaming", "4:04", 244000L),
            Track("t2", "Starboy", "The Weeknd ft. Daft Punk", "Starboy", "3:50", 230000L),
            Track("t3", "Instant Crush", "Daft Punk ft. Julian Casablancas", "Random Access Memories", "5:37", 337000L),
            Track("t4", "Resonance", "HOME", "Odyssey", "3:32", 212000L),
        )
    }

    // Reactive single source of truth for playback state
    val playerState by audioPlayerManager.playerState.collectAsState()

    // Real lyrics lookup from companion .lrc or embedded tags (null if track has no lyrics)
    val currentLyrics = remember(playerState.currentTrack) {
        playerState.currentTrack?.let { track ->
            LyricsRepository.getLyricsForTrack(track, context)
        }
    }

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

    // Global Modal Sheets State (always hoisted above the bottom dock!)
    var trackForOptions by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(TrackSortOption.NAME) }
    var libraryRefreshTrigger by remember { mutableStateOf(0) }

    val hasActiveModalSheet = showQueue || showDuoSheet || trackForOptions != null || trackForPlaylist != null || isSortSheetOpen

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
            onOpenTrackOptions = { track -> trackForOptions = track },
            currentSortOption = currentSortOption,
            onOpenSortSheet = { isSortSheetOpen = true },
            libraryRefreshTrigger = libraryRefreshTrigger,
            audioPlayerManager = audioPlayerManager,
            libraryStorage = libraryStorage,
            localAudioRepository = localAudioRepository,
        )

        // ----------------------------------------------------------------------
        // 2. Progressive Dissolve Scrim behind Bottom Dock
        // ----------------------------------------------------------------------
        if (!isPlayerExpanded && !hasActiveModalSheet) {
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
        if (!isPlayerExpanded && !hasActiveModalSheet) {
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
                lyrics = currentLyrics,
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
                onOpenTrackOptions = { track -> trackForOptions = track },
                showLyrics = showLyrics,
            )
        }

        // Dimmed modal backdrop for any active modal sheet
        if (hasActiveModalSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable {
                        showQueue = false
                        showDuoSheet = false
                        trackForOptions = null
                        trackForPlaylist = null
                        isSortSheetOpen = false
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

        // ----------------------------------------------------------------------
        // 7. Sort Options Pop-up Sheet (Elevated ABOVE Bottom Tab Bar!)
        // ----------------------------------------------------------------------
        AnimatedVisibility(
            visible = isSortSheetOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SortOptionsSheet(
                activeSortOption = currentSortOption,
                onSelectSortOption = { newOption ->
                    currentSortOption = newOption
                    isSortSheetOpen = false
                },
                onClose = { isSortSheetOpen = false },
            )
        }

        // ----------------------------------------------------------------------
        // 8. Track Options Pop-up Sheet (Elevated ABOVE Bottom Tab Bar!)
        // ----------------------------------------------------------------------
        AnimatedVisibility(
            visible = trackForOptions != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            trackForOptions?.let { track ->
                TrackOptionsSheet(
                    track = track,
                    isFavorite = libraryStorage.isFavorite(track.id),
                    onToggleFavorite = {
                        audioPlayerManager.toggleFavorite(track)
                        trackForOptions = null
                    },
                    onDeleteOrDownload = {
                        trackForOptions = null
                        if (track.isDownloaded) {
                            val deleted = localAudioRepository.deleteTrack(track)
                            if (deleted) {
                                Toast.makeText(context, "Deleted \"${track.title}\"", Toast.LENGTH_SHORT).show()
                                libraryRefreshTrigger++
                            } else {
                                Toast.makeText(context, "Could not delete track", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Downloaded \"${track.title}\"", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddToPlaylist = {
                        trackForPlaylist = track
                        trackForOptions = null
                    },
                    onPlayNext = {
                        audioPlayerManager.insertNext(track)
                        Toast.makeText(context, "Playing next: \"${track.title}\"", Toast.LENGTH_SHORT).show()
                        trackForOptions = null
                    },
                    onAddToQueue = {
                        audioPlayerManager.addToQueue(track)
                        Toast.makeText(context, "Added to queue: \"${track.title}\"", Toast.LENGTH_SHORT).show()
                        trackForOptions = null
                    },
                    onShare = {
                        trackForOptions = null
                        shareTrack(context, track)
                    },
                    onClose = { trackForOptions = null },
                )
            }
        }

        // ----------------------------------------------------------------------
        // 9. Add to Playlist Pop-up Sheet (Elevated ABOVE Bottom Tab Bar!)
        // ----------------------------------------------------------------------
        AnimatedVisibility(
            visible = trackForPlaylist != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            trackForPlaylist?.let { track ->
                AddToPlaylistSheet(
                    track = track,
                    playlists = libraryStorage.getCustomPlaylists(),
                    onAddToPlaylist = { playlist ->
                        libraryStorage.addTrackToPlaylist(playlist.id, track.id)
                        Toast.makeText(context, "Added to \"${playlist.name}\"", Toast.LENGTH_SHORT).show()
                        libraryRefreshTrigger++
                        trackForPlaylist = null
                    },
                    onCreatePlaylist = { name ->
                        val created = libraryStorage.createPlaylist(name)
                        libraryStorage.addTrackToPlaylist(created.id, track.id)
                        Toast.makeText(context, "Created & added to \"${created.name}\"", Toast.LENGTH_SHORT).show()
                        libraryRefreshTrigger++
                        trackForPlaylist = null
                    },
                    onClose = { trackForPlaylist = null },
                )
            }
        }
    }
}
