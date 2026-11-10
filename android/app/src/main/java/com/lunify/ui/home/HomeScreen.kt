package com.lunify.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lunify.data.repository.HomeFeedRepository
import com.lunify.data.repository.OnlineMusicRepository
import com.lunify.domain.model.Album
import com.lunify.domain.model.Artist
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.player.duo.DuoManager
import com.lunify.ui.components.AmbientThemeGlow
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.MediaCard
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.AccentPurple
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.liquidGlass
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Lunify - Production Home Feed Screen (HomeScreen.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the primary music discovery stage of Lunify!
 *
 * ARCHITECTURAL DESIGN & RECENT ENHANCEMENTS:
 * -------------------------------------------
 * 1. ZERO Navigation Reload Friction:
 *    Previously, navigating from Home to Library or Settings and back wiped the screen
 *    and refetched everything from YouTube, causing flashing and lag.
 *    Now, all feed items are cached in [HomeFeedRepository]. Switching pages displays
 *    the content INSTANTLY with zero reload!
 *
 * 2. Chrome-Style Pull-to-Refresh:
 *    Reloading happens EXCLUSIVELY when the user hard-pulls down from the top of the
 *    home page (just like Google Chrome). An animated obsidian liquid glass bubble
 *    descends from the top with the official spinning loader
 *    (d:\Lunify\assets\icons\animations\loading.svg -> ic_loading), spins while
 *    fetching live updates, and retracts smoothly back offscreen!
 *
 * 3. Dynamic Mood & Genre Filter Chips:
 *    Tapping mood chips ("All", "Relax", "Workout", "Energize", "Focus", "Commute",
 *    "Party", "Sleep") dynamically loads and updates the Quick Picks and Album
 *    carousels with curated content matching that exact vibe, with per-mood caching!
 *
 * 4. Contextual Duo Branding:
 *    When paired in a live Lunify Duo session, the top header smoothly transitions
 *    into "Lunify Duo" with an ambient Emerald pulse and partner indicator.
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Active mood chip id (persists across tab navigations via HomeFeedRepository)
    var selectedMoodId by remember { mutableStateOf(HomeFeedRepository.selectedMoodId) }

    // Duo synchronization status for contextual header theme & branding
    val duoConnectionState by duoManager?.connectionState?.collectAsState()
        ?: remember { mutableStateOf(DuoConnectionState.DISCONNECTED) }
    val isDuoConnected = duoConnectionState == DuoConnectionState.CONNECTED
    val peerInfo by duoManager?.peerInfo?.collectAsState() ?: remember { mutableStateOf(null) }

    // Displayed feed items
    var quickPicks by remember {
        mutableStateOf(HomeFeedRepository.homeFeedData.quickPicks)
    }
    var trendingAlbums by remember {
        mutableStateOf(HomeFeedRepository.homeFeedData.trendingAlbums)
    }
    var featuredPlaylists by remember {
        mutableStateOf(HomeFeedRepository.homeFeedData.featuredPlaylists)
    }
    var topArtists by remember {
        mutableStateOf(HomeFeedRepository.homeFeedData.topArtists)
    }

    // Initial loading indicator only shown when cache is completely empty on cold boot
    var isLoading by remember {
        mutableStateOf(!HomeFeedRepository.homeFeedData.isInitialized)
    }

    // Scroll state for LazyColumn
    val lazyListState = rememberLazyListState()

    // --------------------------------------------------------------------------
    // Chrome-Style Pull-to-Refresh Physics & Gesture Engine
    // --------------------------------------------------------------------------
    val pullRefreshThreshold = 76.dp
    val pullRefreshThresholdPx = with(density) { pullRefreshThreshold.toPx() }
    val maxPullDistancePx = pullRefreshThresholdPx * 1.5f

    val pullAnimatable = remember { Animatable(0f) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Infinite rotation for the spinner when active
    val infiniteTransition = rememberInfiniteTransition(label = "pullSpinnerTransition")
    val spinnerSpinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
        ),
        label = "spinnerSpinAngle",
    )

    // NestedScrollConnection intercepting overscroll pull-down at top of page
    val nestedScrollConnection = remember(selectedMoodId, isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If indicator is visible and user is scrolling upwards, collapse indicator first
                if (!isRefreshing && pullAnimatable.value > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullAnimatable.value)
                    coroutineScope.launch {
                        pullAnimatable.snapTo(pullAnimatable.value + consumedY)
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // When at the very top of LazyColumn and user pulls downwards:
                val isAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                        lazyListState.firstVisibleItemScrollOffset == 0

                if (!isRefreshing && available.y > 0f && isAtTop) {
                    val dampedDelta = available.y * 0.45f // Damping friction like Chrome
                    val target = (pullAnimatable.value + dampedDelta).coerceAtMost(maxPullDistancePx)
                    coroutineScope.launch {
                        pullAnimatable.snapTo(target)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshing && pullAnimatable.value >= pullRefreshThresholdPx) {
                    // Trigger hard refresh!
                    isRefreshing = true
                    coroutineScope.launch {
                        pullAnimatable.animateTo(
                            targetValue = pullRefreshThresholdPx,
                            animationSpec = spring(stiffness = 250f),
                        )
                        try {
                            // Force-refresh current mood / home feed from YouTube
                            HomeFeedRepository.refresh(selectedMoodId)
                            if (selectedMoodId.lowercase() == "all") {
                                val freshHome = HomeFeedRepository.getHomeFeed()
                                quickPicks = freshHome.quickPicks
                                trendingAlbums = freshHome.trendingAlbums
                                featuredPlaylists = freshHome.featuredPlaylists
                                topArtists = freshHome.topArtists
                            } else {
                                val freshMood = HomeFeedRepository.getMoodFeed(selectedMoodId)
                                quickPicks = freshMood.tracks
                                trendingAlbums = freshMood.albums
                            }
                        } finally {
                            isRefreshing = false
                            pullAnimatable.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(stiffness = 300f),
                            )
                        }
                    }
                    return available
                } else if (!isRefreshing && pullAnimatable.value > 0f) {
                    // Pull wasn't deep enough: snap back offscreen smoothly
                    coroutineScope.launch {
                        pullAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(stiffness = 300f),
                        )
                    }
                    return Velocity.Zero
                }
                return Velocity.Zero
            }
        }
    }

    // --------------------------------------------------------------------------
    // Data Loading & Mood Switching (Cached - ZERO Reload on Navigation!)
    // --------------------------------------------------------------------------
    LaunchedEffect(selectedMoodId) {
        if (selectedMoodId.lowercase() == "all") {
            if (HomeFeedRepository.homeFeedData.isInitialized) {
                // Instantly display cached home feed with zero flicker!
                val cached = HomeFeedRepository.homeFeedData
                quickPicks = cached.quickPicks
                trendingAlbums = cached.trendingAlbums
                featuredPlaylists = cached.featuredPlaylists
                topArtists = cached.topArtists
                isLoading = false
            } else {
                isLoading = true
                val loaded = HomeFeedRepository.getHomeFeed()
                quickPicks = loaded.quickPicks
                trendingAlbums = loaded.trendingAlbums
                featuredPlaylists = loaded.featuredPlaylists
                topArtists = loaded.topArtists
                isLoading = false
            }
        } else {
            // Mood selected! Check if already cached
            val cachedMood = HomeFeedRepository.getMoodFeed(selectedMoodId, forceRefresh = false)
            if (cachedMood.isInitialized && cachedMood.tracks.isNotEmpty()) {
                quickPicks = cachedMood.tracks
                trendingAlbums = cachedMood.albums
                isLoading = false
            } else {
                isLoading = true
                val loaded = HomeFeedRepository.getMoodFeed(selectedMoodId, forceRefresh = false)
                quickPicks = loaded.tracks
                trendingAlbums = loaded.albums
                isLoading = false
            }
        }
    }

    // Active mood title label
    val currentMoodFilter = remember(selectedMoodId) {
        DefaultMoodFilters.firstOrNull { it.id.equals(selectedMoodId, ignoreCase = true) }
    }
    val isAllMood = selectedMoodId.lowercase() == "all"
    val moodLabel = currentMoodFilter?.title ?: selectedMoodId.replaceFirstChar { it.uppercase() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .nestedScroll(nestedScrollConnection),
    ) {
        // Subtle ambient lighting behind header (Emerald glow when Duo is active)
        AmbientThemeGlow(
            primaryColor = if (isDuoConnected) AccentEmerald else AccentCyan,
            secondaryColor = if (isDuoConnected) AccentCyan else AccentPurple,
            alpha = if (isDuoConnected) 0.28f else 0.22f,
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp), // Clearance for floating MiniPlayer & BottomBar dock
        ) {
            // ------------------------------------------------------------------
            // 1. Top Lunify Brand Header (Contextual Duo Mode)
            // ------------------------------------------------------------------
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

            // ------------------------------------------------------------------
            // 2. Horizontal Mood Filter Chips Row
            // ------------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(6.dp))
                MoodChipsRow(
                    moods = DefaultMoodFilters,
                    selectedMoodId = selectedMoodId,
                    onMoodSelected = { newMoodId ->
                        if (selectedMoodId != newMoodId) {
                            selectedMoodId = newMoodId
                            HomeFeedRepository.selectedMoodId = newMoodId
                        }
                    },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ------------------------------------------------------------------
            // 3. Quick Picks 4-Song Section (Filtered dynamically by selected mood!)
            // ------------------------------------------------------------------
            item {
                QuickPicksSection(
                    tracks = quickPicks,
                    activeTrackId = playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onTrackClick = { track -> onPlayQueue(quickPicks, track) },
                    onMoreClick = onOpenTrackOptions,
                    isLoading = isLoading,
                    title = if (isAllMood) "Quick Picks" else "$moodLabel Picks",
                    subtitle = if (isAllMood) "START RADIO FROM A SONG" else "CURATED FOR YOUR VIBE",
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // ------------------------------------------------------------------
            // 4. Trending Now / Mood Albums & Mixes Carousel
            // ------------------------------------------------------------------
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
                            text = if (isAllMood) "Trending Now" else "$moodLabel Mixes & Albums",
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
                        if (trendingAlbums.isEmpty() && isLoading) {
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

            // ------------------------------------------------------------------
            // 5. Featured Playlists Carousel (Shown in All Mode)
            // ------------------------------------------------------------------
            if (isAllMood && (featuredPlaylists.isNotEmpty() || isLoading)) {
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
                            if (featuredPlaylists.isEmpty() && isLoading) {
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
            }

            // ------------------------------------------------------------------
            // 6. Top Artists Circular Row (Shown in All Mode)
            // ------------------------------------------------------------------
            if (isAllMood && (topArtists.isNotEmpty() || isLoading)) {
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
                            if (topArtists.isEmpty() && isLoading) {
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

        // ----------------------------------------------------------------------
        // 7. Chrome-Style Pull-to-Refresh Indicator (Animated from Top)
        // ----------------------------------------------------------------------
        val currentPull = pullAnimatable.value
        val showIndicator = currentPull > 2f || isRefreshing
        if (showIndicator) {
            val pullFraction = (currentPull / pullRefreshThresholdPx).coerceIn(0f, 1f)
            // Starts offscreen above status bar (-48.dp) and glides smoothly into view
            val indicatorY = with(density) {
                (-44).dp + currentPull.toDp()
            }
            val rotationAngle = if (isRefreshing) spinnerSpinAngle else (pullFraction * 300f)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .offset(y = indicatorY)
                    .size(42.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape)
                    .liquidGlass(
                        shape = CircleShape,
                        backgroundColor = GlassSurfaceElevated,
                        borderWidth = 1.2.dp,
                        interactive = false,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Loading,
                    contentDescription = "Loading",
                    size = 20.dp,
                    tint = Color.White,
                    modifier = Modifier.rotate(rotationAngle),
                )
            }
        }
    }
}
