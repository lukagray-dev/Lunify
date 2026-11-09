package com.lunify.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.data.remote.YouTubeMusicClient
import com.lunify.data.repository.OnlineMusicRepository
import com.lunify.domain.model.Album
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.MediaCard
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Lunify - Real-Time YouTube Music Search Master Screen (SearchScreen.kt)
 * ==============================================================================
 *
 * Hey friend! This screen delivers the live YouTube Music streaming search:
 *
 * 1. Liquid Glass Search Bar:
 *    - Real-time text query input with instant clear button.
 * 2. Real-Time Search Suggestions:
 *    - Queries YouTube Music's suggestion engine as the user types.
 * 3. Category Filter Chips:
 *    - Songs, Videos, Albums, and All categories.
 * 4. High-Speed Streaming Search:
 *    - Hits YouTube Music's InnerTube API with debounce to retrieve real tracks,
 *      artists, and crystal-clear 544x544 cover art.
 * 5. Instant Playback & Queue Integration:
 *    - Tapping any search result passes it straight to the AudioPlayerManager,
 *      which extracts the high-quality audio stream and plays seamlessly!
 * ==============================================================================
 */

@Composable
fun SearchScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onPlayQueue: (List<Track>, Track) -> Unit = { _, t -> onTrackSelect(t) },
    onOpenTrackOptions: (Track) -> Unit = {},
    onlineRepository: OnlineMusicRepository = remember { OnlineMusicRepository() },
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<Album>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Popular default trending music searches when query is empty
    val trendingSearches = remember {
        listOf(
            "Ed Sheeran",
            "Taylor Swift",
            "Arijit Singh",
            "The Weeknd",
            "Coldplay",
            "Daft Punk",
            "Lofi Beats",
            "Synthwave 2026",
        )
    }

    // Function to trigger a YouTube Music search
    fun performSearch(query: String, category: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            albumResults = emptyList()
            suggestions = emptyList()
            isLoading = false
            return
        }

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            isLoading = true
            try {
                if (category == "Albums") {
                    val result = onlineRepository.searchAlbums(trimmed)
                    albumResults = result.getOrElse { emptyList() }
                    searchResults = emptyList()
                } else {
                    val filterParam = when (category) {
                        "Songs" -> YouTubeMusicClient.FILTER_SONGS
                        "Videos" -> YouTubeMusicClient.FILTER_VIDEOS
                        else -> YouTubeMusicClient.FILTER_SONGS // For music search, "All" defaults to songs with duration
                    }
                    val result = onlineRepository.searchSongs(trimmed, filterParam)
                    searchResults = result.getOrElse { emptyList() }
                    albumResults = emptyList()
                }
            } finally {
                isLoading = false
            }
        }
    }

    // Listen for text changes to fetch suggestions & execute debounced search
    LaunchedEffect(searchQuery, selectedCategory) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            albumResults = emptyList()
            suggestions = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        // Fetch live search query suggestions
        coroutineScope.launch {
            suggestions = onlineRepository.getSearchSuggestions(trimmed).take(5)
        }

        // Debounce search by 400ms to avoid flooding requests while typing
        delay(400)
        performSearch(trimmed, selectedCategory)
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
            // 1. Liquid Glass Search Input Bar (Aligned horizontally at 16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp)
                    .liquidGlass(
                        shape = CircleShape,
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        borderWidth = 1.dp,
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LucideIcon(
                        iconRes = LucideIcons.Search,
                        contentDescription = null,
                        size = 18.dp,
                        tint = Color.White,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search songs, albums, artists...",
                                color = TextMuted,
                                fontSize = 14.sp,
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    performSearch(searchQuery, selectedCategory)
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        LucideIconButton(
                            iconRes = LucideIcons.Close,
                            contentDescription = "Clear search",
                            onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                albumResults = emptyList()
                                suggestions = emptyList()
                            },
                            iconSize = 18.dp,
                            buttonSize = 36.dp,
                            tint = TextSecondary,
                        )
                    }
                }
            }

            // 2. Category Filter Chips (Aligned with searchbar at horizontal 16.dp)
            SearchFilterChips(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = category
                    if (searchQuery.isNotBlank()) {
                        performSearch(searchQuery, category)
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Search Suggestions Row (when typing and suggestions exist)
            if (suggestions.isNotEmpty() && searchResults.isEmpty() && isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = suggestion
                                    focusManager.clearFocus()
                                    performSearch(suggestion, selectedCategory)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.Search,
                                contentDescription = null,
                                size = 16.dp,
                                tint = TextMuted,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = suggestion,
                                color = TextPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // 4. Search Content (Trending Searches OR Real Search Results)
            if (searchQuery.isEmpty()) {
                // Popular / Trending Searches
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "Trending Searches",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    trendingSearches.forEach { searchItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = searchItem
                                    focusManager.clearFocus()
                                    performSearch(searchItem, selectedCategory)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LucideIcon(
                                    iconRes = LucideIcons.Clock,
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = TextMuted,
                                )
                                Text(
                                    text = searchItem,
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                )
                            }

                            LucideIcon(
                                iconRes = LucideIcons.ArrowRight,
                                contentDescription = null,
                                size = 16.dp,
                                tint = TextMuted,
                            )
                        }
                    }
                }
            } else if (selectedCategory == "Albums" && albumResults.isNotEmpty()) {
                // Albums Grid / List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp, top = 4.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albumResults) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Searching for the album name reveals its tracks
                                    searchQuery = "${album.title} ${album.artist}"
                                    selectedCategory = "Songs"
                                    performSearch(searchQuery, "Songs")
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MediaCard(
                                title = album.title,
                                subtitle = album.artist,
                                thumbnailUrl = album.thumbnailUrl,
                                cardWidth = 80.dp,
                                onClick = {
                                    searchQuery = "${album.title} ${album.artist}"
                                    selectedCategory = "Songs"
                                    performSearch(searchQuery, "Songs")
                                },
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = album.artist,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                // Real Songs / Videos Results List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp, top = 4.dp),
                ) {
                    items(searchResults) { track ->
                        TrackRowItem(
                            track = track,
                            isActive = track.id == playerState.currentTrack?.id,
                            isPlaying = playerState.isPlaying,
                            onClick = { onPlayQueue(searchResults, track) },
                            onMoreClick = { onOpenTrackOptions(track) },
                        )
                    }
                }
            }
        }
    }
}
