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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Search Master Screen (SearchScreen.kt)
 * ==============================================================================
 *
 * Hey friend! This screen handles high-speed YouTube Music search:
 *
 * 1. Liquid Glass Search Bar:
 *    - Real-time text query input with instant clear button.
 * 2. Category Filter Pills:
 *    - Filter by All, Songs, Videos, Albums, Artists.
 * 3. Recent / Trending Searches:
 *    - Quick tap suggestions when search input is empty.
 * 4. Instant Results:
 *    - Direct track items ready for sub-300ms playback and downloads.
 * ==============================================================================
 */

@Composable
fun SearchScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onOpenTrackOptions: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Mock realistic sample results for instant UI testing
    val mockResults = remember {
        listOf(
            Track("s1", "Blinding Lights", "The Weeknd", "After Hours", "3:20", 200000L),
            Track("s2", "Get Lucky", "Daft Punk ft. Pharrell Williams", "Random Access Memories", "4:08", 248000L),
            Track("s3", "The Less I Know The Better", "Tame Impala", "Currents", "3:36", 216000L),
            Track("s4", "Midnight City (Remix)", "M83", "Hurry Up", "4:24", 264000L),
            Track("s5", "Resonance (Synthwave 4K)", "HOME", "Odyssey", "3:32", 212000L, isVideo = true),
        )
    }

    val recentSearches = remember {
        listOf("Daft Punk", "The Weeknd", "Synthwave 2026", "Tame Impala", "Lofi chill")
    }

    val filteredTracks = if (searchQuery.trim().isEmpty()) {
        emptyList()
    } else {
        mockResults.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
        }
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
            // 1. Header: Symmetrical Brand Icon + Title & Symmetrical 40dp Capsule
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Icon container + "Search" title
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
                            iconRes = LucideIcons.Search,
                            contentDescription = "Search",
                            size = 20.dp,
                            tint = Color.White,
                        )
                    }

                    Text(
                        text = "Search",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }

                // Right: Symmetrical 40dp Liquid Glass Capsule (Clear action or Mode indicator)
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                backgroundColor = GlassSurfaceElevated,
                                interactive = true,
                                borderWidth = 1.dp,
                            )
                            .clickable { searchQuery = "" }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.Close,
                                contentDescription = "Clear search",
                                size = 15.dp,
                                tint = Color.White,
                            )
                            Text(
                                text = "Clear",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
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
                            iconRes = LucideIcons.Sparkles,
                            contentDescription = null,
                            size = 15.dp,
                            tint = Color.White,
                        )
                        Text(
                            text = "Music",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // 2. Symmetrical Liquid Glass Search Input Bar
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .height(46.dp)
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
                        tint = TextSecondary,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search songs, albums, artists...",
                                color = TextMuted,
                                fontSize = 13.sp,
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
                            keyboardActions = KeyboardActions(onSearch = { /* Execute search */ }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        LucideIconButton(
                            iconRes = LucideIcons.Close,
                            contentDescription = "Clear search",
                            onClick = { searchQuery = "" },
                            iconSize = 16.dp,
                            buttonSize = 32.dp,
                            tint = TextSecondary,
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // 3. Category Filter Chips (Matching 16dp horizontal padding & 34dp height)
            // ------------------------------------------------------------------
            Spacer(modifier = Modifier.height(10.dp))
            SearchFilterChips(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ------------------------------------------------------------------
            // 4. Search Content (Recent Searches OR Filtered Results)
            // ------------------------------------------------------------------
            if (searchQuery.isEmpty()) {
                // Recent / Trending Searches
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = "Recent Searches",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    recentSearches.forEach { searchItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchQuery = searchItem }
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
            } else {
                // Results List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp, top = 4.dp),
                ) {
                    items(filteredTracks) { track ->
                        TrackRowItem(
                            track = track,
                            isActive = track.id == playerState.currentTrack?.id,
                            isPlaying = playerState.isPlaying,
                            onClick = { onTrackSelect(track) },
                            onMoreClick = { onOpenTrackOptions(track) },
                        )
                    }
                }
            }
        }
    }
}
