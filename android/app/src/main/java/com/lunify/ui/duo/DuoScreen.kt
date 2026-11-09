package com.lunify.ui.duo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.ui.components.AmbientThemeGlow
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.AccentPurple
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Duo (Listen Together) Screen (DuoScreen.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to Lunify's flagship differentiator: the Duo screen.
 *
 * Duo allows two people to stay in sync with the same playlist:
 * - Over the internet via WebRTC DataChannels.
 * - Nearby / offline via Wi-Fi Direct.
 *
 * This screen provides:
 * 1. Pairing Mode: User's own ID with copy button + friend ID input with connect action.
 * 2. Connected Mode: Live green pulse indicator, latency metrics, and shared queue.
 * ==============================================================================
 */

@Composable
fun DuoScreen(
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var connectionState by remember { mutableStateOf(DuoConnectionState.DISCONNECTED) }
    var myDuoId by remember { mutableStateOf("LN-7842") }
    var targetPeerId by remember { mutableStateOf("") }
    var connectedPeerId by remember { mutableStateOf("LN-3810") }
    var isCopied by remember { mutableStateOf(false) }

    // Mock Shared Common Queue for Duo Session
    val sharedTracks = remember {
        listOf(
            Track("d1", "Resonance", "HOME", "Odyssey", "3:32", 212000L),
            Track("d2", "Midnight City", "M83", "Hurry Up", "4:04", 244000L),
            Track("d3", "Starboy", "The Weeknd", "Starboy", "3:50", 230000L),
            Track("d4", "Instant Crush", "Daft Punk", "Random Access", "5:37", 337000L),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // Ambient green & purple glow for Duo sync aura
        AmbientThemeGlow(
            primaryColor = AccentEmerald,
            secondaryColor = AccentPurple,
            alpha = 0.25f,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                backgroundColor = AccentEmerald.copy(alpha = 0.2f),
                                interactive = false,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Duo,
                            contentDescription = null,
                            size = 20.dp,
                            tint = AccentEmerald,
                        )
                    }

                    Text(
                        text = "Lunify Duo",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (connectionState == DuoConnectionState.CONNECTED) {
                    // Disconnect button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable {
                                connectionState = DuoConnectionState.DISCONNECTED
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Disconnect",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (connectionState == DuoConnectionState.DISCONNECTED) {
                // ==============================================================
                // State 1: Disconnected / Pairing Mode
                // ==============================================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Hero Explanatory Card
                    item {
                        LiquidGlassContainer(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = GlassSurfaceElevated,
                            interactive = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LucideIcon(
                                        iconRes = LucideIcons.Duo,
                                        contentDescription = null,
                                        size = 28.dp,
                                        tint = AccentEmerald,
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Listen Together in Perfect Sync",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Whether you are across the world or sitting in the same room, Duo keeps you in the same musical moment with real-time drift compensation.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }

                    // Your Duo ID Card
                    item {
                        LiquidGlassContainer(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = GlassSurfaceElevated,
                            interactive = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "YOUR DUO PAIRING ID",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = myDuoId,
                                        color = AccentEmerald,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(AccentEmerald.copy(alpha = 0.2f))
                                        .clickable {
                                            isCopied = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        LucideIcon(
                                            iconRes = if (isCopied) LucideIcons.Check else LucideIcons.Copy,
                                            contentDescription = null,
                                            size = 14.dp,
                                            tint = AccentEmerald,
                                        )
                                        Text(
                                            text = if (isCopied) "Copied!" else "Copy ID",
                                            color = AccentEmerald,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Connect to Partner Input Card
                    item {
                        LiquidGlassContainer(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = GlassSurfaceElevated,
                            interactive = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = "CONNECT WITH FRIEND",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ObsidianSurfaceHigh)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (targetPeerId.isEmpty()) {
                                        Text(
                                            text = "Enter partner's ID (e.g. LN-3810)",
                                            color = TextMuted,
                                            fontSize = 14.sp,
                                        )
                                    }

                                    BasicTextField(
                                        value = targetPeerId,
                                        onValueChange = { targetPeerId = it.uppercase() },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 1.sp,
                                        ),
                                        cursorBrush = SolidColor(AccentEmerald),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (targetPeerId.isNotEmpty()) {
                                            connectedPeerId = targetPeerId
                                            connectionState = DuoConnectionState.CONNECTED
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                ) {
                                    Text(
                                        text = "Pair & Listen Together",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ==============================================================
                // State 2: Connected Mode (Live Synchronized Room)
                // ==============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    // Live Sync Status Pill Bar
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Green pulse dot
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald),
                                )
                                Column {
                                    Text(
                                        text = "Connected with $connectedPeerId",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "In Sync • 16ms Latency • WebRTC P2P",
                                        color = AccentEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            LucideIcon(
                                iconRes = LucideIcons.Sparkles,
                                contentDescription = null,
                                size = 20.dp,
                                tint = AccentEmerald,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Synchronized Shared Queue",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 180.dp),
                    ) {
                        items(sharedTracks) { track ->
                            TrackRowItem(
                                track = track,
                                isActive = track.id == playerState.currentTrack?.id,
                                isPlaying = playerState.isPlaying,
                                onClick = { onTrackSelect(track) },
                                onMoreClick = { /* Options */ },
                            )
                        }
                    }
                }
            }
        }
    }
}
