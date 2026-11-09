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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.Album
import com.lunify.domain.model.Artist
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.ui.components.AmbientThemeGlow
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.MediaCard
import com.lunify.ui.theme.AccentCyan
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
    onOpenTrackOptions: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedMoodId by remember { mutableStateOf("all") }

    // Mock Sample Data for Immediate UI Review
    val sampleQuickPicks = remember {
        listOf(
            Track("t1", "Midnight City", "M83", "Hurry Up, We're Dreaming", "4:04", 244000L),
            Track("t2", "Starboy", "The Weeknd ft. Daft Punk", "Starboy", "3:50", 230000L),
            Track("t3", "Instant Crush", "Daft Punk ft. Julian Casablancas", "Random Access Memories", "5:37", 337000L),
            Track("t4", "Resonance", "HOME", "Odyssey", "3:32", 212000L),
        )
    }

    val sampleTrending = remember {
        listOf(
            Album("a1", "After Hours", "The Weeknd", "2020", "", 14),
            Album("a2", "Random Access Memories", "Daft Punk", "2013", "", 13),
            Album("a3", "Currents", "Tame Impala", "2015", "", 13),
            Album("a4", "Hurry Up, We're Dreaming", "M83", "2011", "", 22),
        )
    }

    val samplePlaylists = remember {
        listOf(
            Album("p1", "Synthwave Cyberpunk Nights", "Lunify Sound Lab", "2026", "", 45),
            Album("p2", "Lofi Beats for Coding", "Chillhop Music", "2026", "", 60),
            Album("p3", "Deep Focus Techno", "Lunify Curators", "2026", "", 32),
        )
    }

    val sampleArtists = remember {
        listOf(
            Artist("art1", "Daft Punk", "10.2M Fans"),
            Artist("art2", "The Weeknd", "35.4M Fans"),
            Artist("art3", "M83", "4.8M Fans"),
            Artist("art4", "Tame Impala", "7.1M Fans"),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // Subtle ambient lighting behind header
        AmbientThemeGlow(
            primaryColor = AccentCyan,
            secondaryColor = AccentPurple,
            alpha = 0.22f,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp), // Generous clearance for floating MiniPlayer & BottomBar dock
        ) {
            // 1. Top Lunify Brand Header
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
                                    backgroundColor = Color.White.copy(alpha = 0.08f),
                                    interactive = false,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LucideIcon(
                                iconRes = LucideIcons.BrandLogo,
                                contentDescription = "Lunify Logo",
                                size = 20.dp,
                                tint = Color.White,
                            )
                        }

                        Text(
                            text = "Lunify",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                    }

                    // Right: Liquid Glass Action Capsule (Duo + Settings Gear)
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                backgroundColor = GlassSurfaceElevated,
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
                            tint = Color.White,
                        )

                        // Subtle Translucent Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(Color.White.copy(alpha = 0.2f)),
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

            // 3. Quick Picks 4-Song Group
            item {
                QuickPicksSection(
                    tracks = sampleQuickPicks,
                    activeTrackId = playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onTrackClick = onTrackSelect,
                    onMoreClick = onOpenTrackOptions,
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 4. Trending Now & Top Charts Carousel
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
                        items(sampleTrending) { album ->
                            MediaCard(
                                title = album.title,
                                subtitle = album.artist,
                                cardWidth = 145.dp,
                                onClick = { onAlbumSelect(album) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 5. Curated Playlists Carousel
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
                        items(samplePlaylists) { playlist ->
                            MediaCard(
                                title = playlist.title,
                                subtitle = playlist.artist,
                                cardWidth = 145.dp,
                                onClick = { onAlbumSelect(playlist) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 6. Top Artists Circular Row
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
                        items(sampleArtists) { artist ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(90.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .liquidGlass(
                                            shape = CircleShape,
                                            backgroundColor = ObsidianSurfaceHigh,
                                            interactive = true,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LucideIcon(
                                        iconRes = LucideIcons.Profile,
                                        contentDescription = artist.name,
                                        size = 32.dp,
                                        tint = Color.White,
                                    )
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
