package com.lunify.ui.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.domain.model.HistoryItem
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Playlist
import com.lunify.domain.model.Track
import com.lunify.domain.model.TrackSortOption
import com.lunify.player.AudioPlayerManager
import com.lunify.ui.components.AddToPlaylistSheet
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.SortOptionsSheet
import com.lunify.ui.components.TrackOptionsSheet
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.GlassSurfaceDark
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ==============================================================================
 * Lunify - Library & Offline Downloads Screen (LibraryScreen.kt)
 * ==============================================================================
 *
 * Hey friend! This screen manages the user's offline media library.
 *
 * FEATURES IMPLEMENTED HERE:
 * 1. Track Options Pop-up:
 *    - Vertical 3 dots icon replaces legacy copy icon.
 *    - Provides Like/Unlike, Delete/Download, Add to Playlist, Play Next,
 *      Add to Queue, and Share actions.
 * 2. Downloaded Audio Sorting:
 *    - Funnel icon button beside Play All & Shuffle.
 *    - Opens SortOptionsSheet for Name, Time created, Times played, Duration.
 * 3. Folder Playlists & Custom Metadata Playlists:
 *    - Automatic discovery of folders containing audio files as playlists.
 *    - Backward-compatible: does NOT move or copy files, preserving file tree.
 *    - User-created custom playlists saved as lightweight metadata.
 * 4. Real Media Scanning & Accurate Storage Indicators:
 *    - Real-time MediaStore indexing and cumulative storage footprint.
 * ==============================================================================
 */

val LibrarySubTabs = listOf("Downloaded", "Favorites", "Playlists", "History")

@Composable
fun LibraryScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onPlayQueue: (List<Track>, Track) -> Unit = { queue, track -> onTrackSelect(track) },
    onOpenTrackOptions: (Track) -> Unit,
    currentSortOption: TrackSortOption = TrackSortOption.NAME,
    onOpenSortSheet: () -> Unit = {},
    refreshTrigger: Int = 0,
    audioPlayerManager: AudioPlayerManager,
    libraryStorage: LibraryStorage,
    localAudioRepository: LocalAudioRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Downloaded") }

    // Media Permissions (Android 13+ vs Android 12-)
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        )
    }

    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }

    // Active playlist being viewed in detail
    var activePlaylistDetail by remember { mutableStateOf<Playlist?>(null) }

    // Custom playlists state
    var customPlaylists by remember { mutableStateOf(libraryStorage.getCustomPlaylists()) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            isLoadingTracks = true
            localTracks = localAudioRepository.getLocalAudioTracks(libraryStorage)
            isLoadingTracks = false
        }
    }

    LaunchedEffect(hasAudioPermission, refreshTrigger) {
        if (hasAudioPermission) {
            isLoadingTracks = true
            localTracks = localAudioRepository.getLocalAudioTracks(libraryStorage)
            customPlaylists = libraryStorage.getCustomPlaylists()
            isLoadingTracks = false
        }
    }

    // Storage metric computation
    val totalStorageBytes = remember(localTracks) {
        localAudioRepository.calculateTotalStorageBytes(localTracks)
    }
    val formattedStorageString = remember(totalStorageBytes, hasAudioPermission) {
        if (!hasAudioPermission) "Storage Locked" else LocalAudioRepository.formatStorageSize(totalStorageBytes)
    }

    // Sorted local tracks based on selected sort option
    val sortedLocalTracks = remember(localTracks, currentSortOption) {
        // Attach latest play counts before sorting
        val tracksWithPlayCounts = localTracks.map { track ->
            track.copy(playCount = libraryStorage.getPlayCount(track.id))
        }
        localAudioRepository.sortTracks(tracksWithPlayCounts, currentSortOption)
    }

    // Folder playlists derived from local tracks
    val folderPlaylists = remember(localTracks) {
        localAudioRepository.getFolderPlaylists(localTracks)
    }

    // Dynamically retrieve favorites and history from persistent storage
    val favoriteTracks = remember(selectedTab, playerState.currentTrack?.isFavorite, refreshTrigger) {
        libraryStorage.getFavoriteTracks()
    }

    var historyItems by remember(selectedTab, playerState.currentTrack, refreshTrigger) {
        mutableStateOf(libraryStorage.getPlaybackHistory())
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
            // ------------------------------------------------------------------
            // 1. Header: Symmetrical Brand Icon + Title & 40dp Storage Capsule
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Icon container + "Your Library" title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                backgroundColor = Color.White.copy(alpha = 0.08f),
                                interactive = false,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Download,
                            contentDescription = "Your Library",
                            size = 20.dp,
                            tint = Color.White,
                        )
                    }

                    Text(
                        text = "Your Library",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }

                // Right: Symmetrical 40dp Liquid Glass Storage Capsule
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .liquidGlass(
                            shape = CircleShape,
                            backgroundColor = GlassSurfaceElevated,
                            interactive = false,
                            borderWidth = 1.dp,
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LucideIcon(
                        iconRes = LucideIcons.Folder,
                        contentDescription = null,
                        size = 15.dp,
                        tint = if (hasAudioPermission) Color.White else TextMuted,
                    )
                    Text(
                        text = formattedStorageString,
                        color = if (hasAudioPermission) Color.White else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ------------------------------------------------------------------
            // 2. Subtabs Row ("Downloaded", "Favorites", "Playlists", "History")
            // ------------------------------------------------------------------
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySubTabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val animatedBgColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else GlassPill,
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
                                onClick = {
                                    selectedTab = tab
                                    activePlaylistDetail = null // Reset playlist detail on tab change
                                },
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

            Spacer(modifier = Modifier.height(16.dp))

            // ------------------------------------------------------------------
            // 3. Tab Content Switcher with Smooth Swipe Transitions
            // ------------------------------------------------------------------
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val initialIdx = LibrarySubTabs.indexOf(initialState)
                    val targetIdx = LibrarySubTabs.indexOf(targetState)
                    val direction = if (targetIdx >= initialIdx) 1 else -1
                    (slideInHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { width -> width * direction } +
                            fadeIn(animationSpec = tween(320)))
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { width -> width * -direction } +
                                    fadeOut(animationSpec = tween(320))
                        )
                },
                label = "librarySubTabContentTransition",
            ) { currentTab ->
                when (currentTab) {
                    "Downloaded" -> {
                        DownloadedSection(
                            hasPermission = hasAudioPermission,
                            isLoading = isLoadingTracks,
                            tracks = sortedLocalTracks,
                            playerState = playerState,
                            currentSortOption = currentSortOption,
                            onRequestPermission = { permissionLauncher.launch(permissionToRequest) },
                            onTrackClick = { track -> onPlayQueue(sortedLocalTracks, track) },
                            onPlayAll = {
                                if (sortedLocalTracks.isNotEmpty()) {
                                    onPlayQueue(sortedLocalTracks, sortedLocalTracks.first())
                                }
                            },
                            onShuffleAll = {
                                if (sortedLocalTracks.isNotEmpty()) {
                                    val shuffled = sortedLocalTracks.shuffled()
                                    onPlayQueue(shuffled, shuffled.first())
                                }
                            },
                            onOpenSort = onOpenSortSheet,
                            onMoreClick = onOpenTrackOptions,
                        )
                    }

                    "Favorites" -> {
                        FavoritesSection(
                            tracks = favoriteTracks,
                            playerState = playerState,
                            onTrackClick = { track -> onPlayQueue(favoriteTracks, track) },
                            onMoreClick = onOpenTrackOptions,
                        )
                    }

                    "History" -> {
                        HistorySection(
                            history = historyItems,
                            playerState = playerState,
                            onTrackClick = { track -> onTrackSelect(track) },
                            onClearHistory = {
                                libraryStorage.clearHistory()
                                historyItems = emptyList()
                            },
                        )
                    }

                    "Playlists" -> {
                        if (activePlaylistDetail != null) {
                            // Detailed playlist view
                            val playlist = activePlaylistDetail!!
                            val playlistTracks = remember(playlist, localTracks) {
                                localTracks.filter { playlist.trackIds.contains(it.id) || it.folderName == playlist.name }
                            }

                            PlaylistDetailSection(
                                playlist = playlist,
                                tracks = playlistTracks,
                                playerState = playerState,
                                onBack = { activePlaylistDetail = null },
                                onTrackClick = { track -> onPlayQueue(playlistTracks, track) },
                                onPlayAll = {
                                    if (playlistTracks.isNotEmpty()) {
                                        onPlayQueue(playlistTracks, playlistTracks.first())
                                    }
                                },
                                onShuffleAll = {
                                    if (playlistTracks.isNotEmpty()) {
                                        val shuffled = playlistTracks.shuffled()
                                        onPlayQueue(shuffled, shuffled.first())
                                    }
                                },
                                onMoreClick = onOpenTrackOptions,
                            )
                        } else {
                            // Playlists overview
                            PlaylistsSection(
                                folderPlaylists = folderPlaylists,
                                customPlaylists = customPlaylists,
                                localTracksCount = localTracks.size,
                                favoritesCount = favoriteTracks.size,
                                onSelectDownloaded = { selectedTab = "Downloaded" },
                                onSelectFavorites = { selectedTab = "Favorites" },
                                onSelectPlaylist = { playlist -> activePlaylistDetail = playlist },
                                onCreatePlaylist = { name ->
                                    val created = libraryStorage.createPlaylist(name)
                                    customPlaylists = libraryStorage.getCustomPlaylists()
                                    activePlaylistDetail = created
                                },
                                onDeleteCustomPlaylist = { playlistId ->
                                    libraryStorage.deletePlaylist(playlistId)
                                    customPlaylists = libraryStorage.getCustomPlaylists()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 1. DOWNLOADED / DEVICE AUDIO SECTION
// ==============================================================================

@Composable
private fun DownloadedSection(
    hasPermission: Boolean,
    isLoading: Boolean,
    tracks: List<Track>,
    playerState: PlayerState,
    currentSortOption: TrackSortOption,
    onRequestPermission: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenSort: () -> Unit,
    onMoreClick: (Track) -> Unit,
) {
    if (!hasPermission) {
        // Permission Request Banner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        borderWidth = 1.dp,
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(GlassPill),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        iconRes = LucideIcons.Folder,
                        contentDescription = null,
                        size = 28.dp,
                        tint = Color.White,
                    )
                }

                Text(
                    text = "Device Audio Permission",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Grant Lunify access to read your local music files for high-fidelity, zero-buffering offline playback.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onRequestPermission),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Grant Access",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    } else if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Scanning device music...",
                color = TextSecondary,
                fontSize = 14.sp,
            )
        }
    } else if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Music,
                    contentDescription = null,
                    size = 40.dp,
                    tint = TextMuted,
                )
                Text(
                    text = "No Audio Files Found",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Transfer or download MP3, M4A, or FLAC songs to your device storage to listen offline.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp),
        ) {
            // Action Controls Header: Track Count + Play All + Shuffle All + Sort Funnel
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${tracks.size} Songs on Device",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Sorted by ${currentSortOption.displayName}",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play All Button
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onPlayAll)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                LucideIcon(
                                    iconRes = LucideIcons.Play,
                                    contentDescription = null,
                                    size = 13.dp,
                                    tint = Color.Black,
                                )
                                Text(
                                    text = "Play All",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // Shuffle Button
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(GlassPill)
                                .clickable(onClick = onShuffleAll)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                LucideIcon(
                                    iconRes = LucideIcons.Shuffle,
                                    contentDescription = null,
                                    size = 13.dp,
                                    tint = Color.White,
                                )
                                Text(
                                    text = "Shuffle",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        // Sort By Funnel Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassPill)
                                .clickable(onClick = onOpenSort),
                            contentAlignment = Alignment.Center,
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.Filter,
                                contentDescription = "Sort options",
                                size = 15.dp,
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            // Real audio tracks list
            items(tracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isActive = track.id == playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onClick = { onTrackClick(track) },
                    onMoreClick = { onMoreClick(track) },
                )
            }
        }
    }
}

// ==============================================================================
// 2. FAVORITES SECTION
// ==============================================================================

@Composable
private fun FavoritesSection(
    tracks: List<Track>,
    playerState: PlayerState,
    onTrackClick: (Track) -> Unit,
    onMoreClick: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Heart,
                    contentDescription = null,
                    size = 40.dp,
                    tint = TextMuted,
                )
                Text(
                    text = "No Favorites Yet",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tap the heart icon on any playing song to add it to your favorites collection.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${tracks.size} Favorited Songs",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            items(tracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isActive = track.id == playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onClick = { onTrackClick(track) },
                    onMoreClick = { onMoreClick(track) },
                )
            }
        }
    }
}

// ==============================================================================
// 3. HISTORY SECTION
// ==============================================================================

@Composable
private fun HistorySection(
    history: List<HistoryItem>,
    playerState: PlayerState,
    onTrackClick: (Track) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Clock,
                    contentDescription = null,
                    size = 40.dp,
                    tint = TextMuted,
                )
                Text(
                    text = "No Playback History",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Songs you listen to will automatically be logged here with timestamps and source tags.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${history.size} Recently Played",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(GlassPill)
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Clear",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            items(history, key = { "${it.track.id}_${it.playedAtTimestamp}" }) { item ->
                HistoryRowItem(
                    historyItem = item,
                    isActive = item.track.id == playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onClick = { onTrackClick(item.track) },
                )
            }
        }
    }
}

@Composable
private fun HistoryRowItem(
    historyItem: HistoryItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val track = historyItem.track
    val isOffline = historyItem.source == PlaybackSource.OFFLINE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(GlassSurfaceDark),
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(
                iconRes = if (isActive && isPlaying) LucideIcons.Play else LucideIcons.Music,
                contentDescription = null,
                size = 18.dp,
                tint = if (isActive) Color.White else TextSecondary,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isActive) Color.White else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "•",
                    color = TextMuted,
                    fontSize = 10.sp,
                )

                Text(
                    text = formatRelativeTime(historyItem.playedAtTimestamp),
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isOffline) AccentEmerald.copy(alpha = 0.15f) else GlassPill)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isOffline) "OFFLINE" else "ONLINE",
                color = if (isOffline) AccentEmerald else TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ==============================================================================
// 4. PLAYLISTS SECTION (FOLDER PLAYLISTS + CUSTOM METADATA PLAYLISTS)
// ==============================================================================

@Composable
private fun PlaylistsSection(
    folderPlaylists: List<Playlist>,
    customPlaylists: List<Playlist>,
    localTracksCount: Int,
    favoritesCount: Int,
    onSelectDownloaded: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeleteCustomPlaylist: (String) -> Unit,
) {
    var isCreatingDialogVisible by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // "+ Create Playlist" Action Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "All Playlists",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { isCreatingDialogVisible = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.FolderPlus,
                            contentDescription = null,
                            size = 14.dp,
                            tint = Color.Black,
                        )
                        Text(
                            text = "New Playlist",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Inline Create Input if triggered
        if (isCreatingDialogVisible) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurfaceElevated)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Name your new playlist",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = newPlaylistNameInput,
                        onValueChange = { newPlaylistNameInput = it },
                        placeholder = { Text("e.g., Chill Beats", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(GlassPill)
                                .clickable { isCreatingDialogVisible = false }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    if (newPlaylistNameInput.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistNameInput.trim())
                                        newPlaylistNameInput = ""
                                        isCreatingDialogVisible = false
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Create", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Smart Playlists (Downloaded & Favorites)
        item {
            PlaylistTile(
                title = "Downloaded Songs",
                subtitle = "$localTracksCount tracks from your device",
                iconRes = LucideIcons.Folder,
                onClick = onSelectDownloaded,
            )
        }

        item {
            PlaylistTile(
                title = "Liked Songs",
                subtitle = "$favoritesCount favorited tracks",
                iconRes = LucideIcons.Heart,
                onClick = onSelectFavorites,
            )
        }

        // Section: Custom Playlists (Metadata)
        if (customPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Custom Playlists (${customPlaylists.size})",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(customPlaylists, key = { it.id }) { p ->
                PlaylistTile(
                    title = p.name,
                    subtitle = "${p.trackIds.size} songs",
                    iconRes = LucideIcons.Folder,
                    onClick = { onSelectPlaylist(p) },
                    onDelete = { onDeleteCustomPlaylist(p.id) },
                )
            }
        }

        // Section: Folder Playlists (Backward Compatibility)
        if (folderPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Storage Folders as Playlists (${folderPlaylists.size})",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(folderPlaylists, key = { it.id }) { p ->
                PlaylistTile(
                    title = p.name,
                    subtitle = "${p.trackIds.size} songs in device folder",
                    iconRes = LucideIcons.Folder,
                    onClick = { onSelectPlaylist(p) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GlassPill),
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 22.dp,
                tint = Color.White,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onDelete != null) {
            LucideIconButton(
                iconRes = LucideIcons.Trash,
                contentDescription = "Delete playlist",
                onClick = onDelete,
                iconSize = 16.dp,
                buttonSize = 36.dp,
                tint = TextMuted,
            )
        } else {
            LucideIcon(
                iconRes = LucideIcons.ArrowRight,
                contentDescription = null,
                size = 18.dp,
                tint = TextMuted,
            )
        }
    }
}

// ==============================================================================
// 5. PLAYLIST DETAIL SCREEN (VIEWING TRACKS INSIDE A PLAYLIST)
// ==============================================================================

@Composable
private fun PlaylistDetailSection(
    playlist: Playlist,
    tracks: List<Track>,
    playerState: PlayerState,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onMoreClick: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 180.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                // Top row with Back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LucideIconButton(
                        iconRes = LucideIcons.ArrowLeft,
                        contentDescription = "Back to playlists",
                        onClick = onBack,
                        iconSize = 22.dp,
                        buttonSize = 40.dp,
                        tint = Color.White,
                    )

                    Column {
                        Text(
                            text = playlist.name,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (playlist.isFolderPlaylist) "Folder Playlist • ${tracks.size} tracks" else "Custom Playlist • ${tracks.size} tracks",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Play All & Shuffle Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.Play,
                                contentDescription = null,
                                size = 14.dp,
                                tint = Color.Black,
                            )
                            Text("Play All", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(GlassPill)
                            .clickable(onClick = onShuffleAll)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.Shuffle,
                                contentDescription = null,
                                size = 14.dp,
                                tint = Color.White,
                            )
                            Text("Shuffle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This playlist has no songs yet. Use the 3 dots menu on any song to add tracks here!",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(tracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isActive = track.id == playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onClick = { onTrackClick(track) },
                    onMoreClick = { onMoreClick(track) },
                )
            }
        }
    }
}

/**
 * Fires native Android share sheet for a song.
 */
fun shareTrack(context: Context, track: Track) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            val shareText = if (!track.isDownloaded && !track.id.startsWith("content://") && !track.id.startsWith("file://")) {
                "Listening to \"${track.title}\" by ${track.artist} on Lunify!\nhttps://music.youtube.com/watch?v=${track.id}"
            } else {
                "Listening to \"${track.title}\" by ${track.artist} on Lunify!"
            }
            putExtra(Intent.EXTRA_TEXT, shareText)
            if (track.isDownloaded && track.contentUri.isNotBlank()) {
                val uri = Uri.parse(track.contentUri)
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(shareIntent, "Share Track")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Converts a millisecond timestamp into a human-friendly relative time string.
 */
fun formatRelativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = SimpleDateFormat("MMM dd", Locale.US)
            date.format(Date(timestamp))
        }
    }
}
