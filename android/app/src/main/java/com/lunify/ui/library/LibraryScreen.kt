package com.lunify.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
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
import com.lunify.domain.model.Track
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
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
 * Hey friend! Welcome to the real, fully functional offline library screen!
 *
 * WHAT HAS BEEN IMPLEMENTED HERE:
 * 1. Real Device Audio Scanning:
 *    - Automatically checks and requests Android audio permissions (`READ_MEDIA_AUDIO`
 *      on Android 13+ / API 33+, and `READ_EXTERNAL_STORAGE` on older APIs).
 *    - Loads real MP3, FLAC, M4A, and AAC songs present on the physical device.
 * 2. Accurate Live Storage Indicator:
 *    - Calculates the real cumulative byte size of scanned music files and displays
 *      an exact formatted metric (e.g., "450.2 MB Used" or "1.4 GB Used") in the top right.
 * 3. Subtabs Management:
 *    - Downloaded: Scanned local audio files with "Play All" and "Shuffle" actions.
 *    - Favorites: Dynamically synchronized with player heart clicks, persisted on disk.
 *    - History: Live chronological log of played songs tagged as OFFLINE or ONLINE.
 *    - Playlists: Curated groupings and smart playlists.
 * ==============================================================================
 */

val LibrarySubTabs = listOf("Downloaded", "Favorites", "Playlists", "History")

@Composable
fun LibraryScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onPlayQueue: (List<Track>, Track) -> Unit = { queue, track -> onTrackSelect(track) },
    libraryStorage: LibraryStorage,
    localAudioRepository: LocalAudioRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Downloaded") }

    // Android 13 (Tiramisu, API 33) introduced granular media permissions
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

    // Launcher for requesting runtime media permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            isLoadingTracks = true
            localTracks = localAudioRepository.getLocalAudioTracks()
            isLoadingTracks = false
        }
    }

    // Load device audio files as soon as permission is confirmed
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            isLoadingTracks = true
            localTracks = localAudioRepository.getLocalAudioTracks()
            isLoadingTracks = false
        }
    }

    // Calculate real storage consumption in bytes
    val totalStorageBytes = remember(localTracks) {
        localAudioRepository.calculateTotalStorageBytes(localTracks)
    }

    val formattedStorageString = remember(totalStorageBytes, hasAudioPermission) {
        if (!hasAudioPermission) {
            "Storage Locked"
        } else {
            LocalAudioRepository.formatStorageSize(totalStorageBytes)
        }
    }

    // Dynamically retrieve favorites and history from persistent storage
    val favoriteTracks = remember(selectedTab, playerState.currentTrack?.isFavorite) {
        libraryStorage.getFavoriteTracks()
    }

    var historyItems by remember(selectedTab, playerState.currentTrack) {
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
            // 1. Header: Title & Real Storage Usage Pill
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your Library",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Real storage usage badge reflecting scanned audio files
                LiquidGlassContainer(
                    shape = CircleShape,
                    backgroundColor = GlassSurfaceElevated,
                    modifier = Modifier.height(32.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Folder,
                            contentDescription = null,
                            size = 14.dp,
                            tint = if (hasAudioPermission) Color.White else TextMuted,
                        )
                        Text(
                            text = formattedStorageString,
                            color = if (hasAudioPermission) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // 2. Subtabs Row ("Downloaded", "Favorites", "Playlists", "History")
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                onClick = { selectedTab = tab },
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

            Spacer(modifier = Modifier.height(10.dp))

            // ------------------------------------------------------------------
            // 3. Tab Content Switcher
            // ------------------------------------------------------------------
            when (selectedTab) {
                "Downloaded" -> {
                    DownloadedSection(
                        hasPermission = hasAudioPermission,
                        isLoading = isLoadingTracks,
                        tracks = localTracks,
                        playerState = playerState,
                        onRequestPermission = { permissionLauncher.launch(permissionToRequest) },
                        onTrackClick = { track -> onPlayQueue(localTracks, track) },
                        onPlayAll = {
                            if (localTracks.isNotEmpty()) {
                                onPlayQueue(localTracks, localTracks.first())
                            }
                        },
                        onShuffleAll = {
                            if (localTracks.isNotEmpty()) {
                                val shuffled = localTracks.shuffled()
                                onPlayQueue(shuffled, shuffled.first())
                            }
                        },
                    )
                }

                "Favorites" -> {
                    FavoritesSection(
                        tracks = favoriteTracks,
                        playerState = playerState,
                        onTrackClick = { track -> onPlayQueue(favoriteTracks, track) },
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
                    PlaylistsSection(
                        localTracksCount = localTracks.size,
                        favoritesCount = favoriteTracks.size,
                        onSelectDownloaded = { selectedTab = "Downloaded" },
                        onSelectFavorites = { selectedTab = "Favorites" },
                    )
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
    onRequestPermission: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
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
            // Quick action controls row: Track count + Play All + Shuffle All
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${tracks.size} Songs on Device",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onMoreClick = { /* Options */ },
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
                    onMoreClick = { /* Options */ },
                )
            }
        }
    }
}

// ==============================================================================
// 3. HISTORY SECTION (WITH OFFLINE VS ONLINE BADGES & TIMESTAMPS)
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

                    // Clear History Button
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

/**
 * Renders an entry in the user's playback history with:
 * 1. Artwork / Music Icon.
 * 2. Title & Artist.
 * 3. Relative timestamp ("Just now", "5m ago").
 * 4. Crisp Source Tag: OFFLINE (Emerald/Cyan glow) or ONLINE (Muted glass).
 */
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
        // Thumbnail or placeholder
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

        // Title and Artist
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

        // Source Tag Badge (OFFLINE vs ONLINE)
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
// 4. PLAYLISTS SECTION
// ==============================================================================

@Composable
private fun PlaylistsSection(
    localTracksCount: Int,
    favoritesCount: Int,
    onSelectDownloaded: () -> Unit,
    onSelectFavorites: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PlaylistTile(
                title = "Downloaded Songs",
                subtitle = "$localTracksCount songs available offline",
                iconRes = LucideIcons.Folder,
                onClick = onSelectDownloaded,
            )
        }

        item {
            PlaylistTile(
                title = "Liked Songs",
                subtitle = "$favoritesCount tracks favorited",
                iconRes = LucideIcons.Heart,
                onClick = onSelectFavorites,
            )
        }
    }
}

@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
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
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }

        LucideIcon(
            iconRes = LucideIcons.ArrowRight,
            contentDescription = null,
            size = 18.dp,
            tint = TextMuted,
        )
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
