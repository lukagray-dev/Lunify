package com.lunify.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.lunify.data.repository.OnlineMusicRepository
import com.lunify.domain.model.Album
import com.lunify.domain.model.Artist
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.player.duo.DuoManager
import com.lunify.ui.components.AmbientThemeGlow
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.MediaCard
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.AccentPurple
import com.lunify.ui.theme.GlassBorderSubtle
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Home Master Feed (HomeScreen.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the primary discovery stage of Lunify.
 *
 * Recreating the exact experience of SimpMusic:
 * 1. Top Header: Lunify logo branding + ambient badge + settings launcher.
 * 2. Dynamic Mood Filter Chips: Relax, Workout, Focus, etc.
 * 3. Quick Picks: Direct streaming songs ready to play in one tap.
 * 4. Trending & Charts Carousel: High-res square media cards.
 * 5. Community Playlists & Curated Albums: Smooth horizontal browsing.
 * 6. Top Artists: Circular avatar discography row.
 * ==============================================================================
 */

@Composable
fun HomeScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onAlbumSelect: (Album) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDuo: () -> Unit,
    onPlayQueue: (List<Track>, Track) -> Unit = { _, t -> onTrackSelect(t) },
    onOpenTrackOptions: (Track) -> Unit = {},
    onlineRepository: OnlineMusicRepository = remember { OnlineMusicRepository() },
    duoManager: DuoManager? = null,
    modifier: Modifier = Modifier,
) {
    var selectedMoodId by remember { mutableStateOf("all") }

    // Duo synchronization status for contextual header theme & branding
    val duoConnectionState by duoManager?.connectionState?.collectAsState() ?: remember { mutableStateOf(DuoConnectionState.DISCONNECTED) }
    val isDuoConnected = duoConnectionState == DuoConnectionState.CONNECTED
    val peerInfo by duoManager?.peerInfo?.collectAsState() ?: remember { mutableStateOf(null) }

    // Dynamic state populated strictly from real YouTube Music InnerTube responses
    var quickPicks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var trendingAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var featuredPlaylists by remember { mutableStateOf<List<Album>>(emptyList()) }
    var topArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }

    var isLoadingQuickPicks by remember { mutableStateOf(true) }
    var isLoadingTrending by remember { mutableStateOf(true) }
    var isLoadingPlaylists by remember { mutableStateOf(true) }
    var isLoadingArtists by remember { mutableStateOf(true) }

    // Fetch live YouTube Music home feed data asynchronously in parallel
    LaunchedEffect(Unit) {
        launch {
            val qpResult = onlineRepository.getHomeQuickPicks()
            quickPicks = qpResult.getOrDefault(emptyList())
            isLoadingQuickPicks = false
        }
        launch {
            val trendingResult = onlineRepository.getHomeTrending()
            trendingAlbums = trendingResult.getOrDefault(emptyList())
            isLoadingTrending = false
        }
        launch {
            val playlistsResult = onlineRepository.getHomePlaylists()
            featuredPlaylists = playlistsResult.getOrDefault(emptyList())
            isLoadingPlaylists = false
        }
        launch {
            val artistsResult = onlineRepository.getTopArtists()
            topArtists = artistsResult.getOrDefault(emptyList())
            isLoadingArtists = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // Subtle ambient lighting behind header (Emerald glow when Duo is active)
        AmbientThemeGlow(
            primaryColor = if (isDuoConnected) AccentEmerald else AccentCyan,
            secondaryColor = if (isDuoConnected) AccentCyan else AccentPurple,
            alpha = if (isDuoConnected) 0.28f else 0.22f,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp), // Generous clearance for floating MiniPlayer & BottomBar dock
        ) {
            // 1. Top Lunify Brand Header (Contextual Duo Mode)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: Logo & Brand Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .liquidGlass(
                                    shape = CircleShape,
                                    backgroundColor = if (isDuoConnected) AccentEmerald.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                                    interactive = false,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LucideIcon(
                                iconRes = if (isDuoConnected) LucideIcons.Duo else LucideIcons.BrandLogo,
                                contentDescription = if (isDuoConnected) "Lunify Duo" else "Lunify Logo",
                                size = 20.dp,
                                tint = if (isDuoConnected) AccentEmerald else Color.White,
                            )
                        }

                        Column {
                            Text(
                                text = if (isDuoConnected) "Lunify Duo" else "Lunify",
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            )
                            if (isDuoConnected) {
                                Text(
                                    text = "Listening with ${peerInfo?.deviceName ?: "Partner"}",
                                    color = AccentEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    // Right: Liquid Glass Action Capsule (Duo + Settings Gear)
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                backgroundColor = if (isDuoConnected) AccentEmerald.copy(alpha = 0.12f) else GlassSurfaceElevated,
                                interactive = false,
                                borderWidth = 1.dp,
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Duo Pop-up Launcher
                        LucideIconButton(
                            iconRes = LucideIcons.Duo,
                            contentDescription = "Lunify Duo",
                            onClick = onOpenDuo,
                            iconSize = 19.dp,
                            buttonSize = 34.dp,
                            tint = if (isDuoConnected) AccentEmerald else Color.White,
                        )

                        // Subtle Translucent Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(if (isDuoConnected) AccentEmerald.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.2f)),
                        )

                        // Settings Launcher (Gear Icon)
                        LucideIconButton(
                            iconRes = LucideIcons.Settings,
                            contentDescription = "Settings",
                            onClick = onOpenSettings,
                            iconSize = 19.dp,
                            buttonSize = 34.dp,
                            tint = Color.White,
                        )
                    }
                }
            }

            // 2. Horizontal Mood Chips Row
            item {
                Spacer(modifier = Modifier.height(6.dp))
                MoodChipsRow(
                    moods = DefaultMoodFilters,
                    selectedMoodId = selectedMoodId,
                    onMoodSelected = { selectedMoodId = it },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 3. Quick Picks 4-Song Group (Real live songs from YouTube Music)
            item {
                QuickPicksSection(
                    tracks = quickPicks,
                    activeTrackId = playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onTrackClick = { track -> onPlayQueue(quickPicks, track) },
                    onMoreClick = onOpenTrackOptions,
                    isLoading = isLoadingQuickPicks,
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 4. Trending Now & Top Charts Carousel (Real albums from InnerTube)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Trending Now",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (trendingAlbums.isEmpty() && isLoadingTrending) {
                            items(3) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 145.dp, height = 185.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(GlassSurfaceElevated),
                                )
                            }
                        } else {
                            items(trendingAlbums) { album ->
                                MediaCard(
                                    title = album.title,
                                    subtitle = album.artist,
                                    thumbnailUrl = album.thumbnailUrl,
                                    cardWidth = 145.dp,
                                    onClick = { onAlbumSelect(album) },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 5. Curated Playlists Carousel (Real community playlists from InnerTube)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Featured Playlists",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (featuredPlaylists.isEmpty() && isLoadingPlaylists) {
                            items(3) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 145.dp, height = 185.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(GlassSurfaceElevated),
                                )
                            }
                        } else {
                            items(featuredPlaylists) { playlist ->
                                MediaCard(
                                    title = playlist.title,
                                    subtitle = playlist.artist,
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    cardWidth = 145.dp,
                                    onClick = { onAlbumSelect(playlist) },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 6. Top Artists Circular Row (Real top artists from InnerTube Charts)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Top Artists",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (topArtists.isEmpty() && isLoadingArtists) {
                            items(4) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(90.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .background(GlassSurfaceElevated),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ObsidianSurfaceHigh),
                                    )
                                }
                            }
                        } else {
                            items(topArtists) { artist ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(90.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .background(ObsidianSurfaceHigh),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (artist.thumbnailUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = artist.thumbnailUrl,
                                                contentDescription = artist.name,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            LucideIcon(
                                                iconRes = LucideIcons.Profile,
                                                contentDescription = artist.name,
                                                size = 32.dp,
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = artist.name,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
