package com.lunify.ui.duo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.player.duo.DuoCodeUtils
import com.lunify.player.duo.DuoManager
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.components.TrackRowItem
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.GlassBorderSubtle
import com.lunify.ui.theme.GlassSheetBackground
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianSurfaceHigh
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass
import kotlinx.coroutines.delay

/**
 * ==============================================================================
 * Lunify - Duo Modal Pop-up Sheet (DuoSheet.kt)
 * ==============================================================================
 *
 * Hey friend! This modal sheet is the interactive control center for Lunify Duo.
 *
 * Instead of taking users away from their music on a separate screen, this sleek,
 * liquid glass pop-up sheet slides up right over the current view.
 *
 * KEY CAPABILITIES:
 * 1. Persistent Context: Users remain on Home/Search/Library while pairing.
 * 2. Instant Hosting: Your 6-character room code (e.g. "LN-7842") is ready to share.
 *    Tapping "Copy" copies the code to your clipboard and immediately starts hosting.
 * 3. Fast Pairing: Enter a friend's code and tap "Pair & Sync" to connect via our
 *    global relay broker in less than a second.
 * 4. Live Session Controls: Real-time latency ping indicator, connected friend details,
 *    graceful one-tap disconnect, and synchronized playlist browsing!
 * ==============================================================================
 */
@Composable
fun DuoSheet(
    duoManager: DuoManager,
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Observe real reactive state from DuoManager
    val connectionState by duoManager.connectionState.collectAsState()
    val myRoomCode by duoManager.myRoomCode.collectAsState()
    val peerInfo by duoManager.peerInfo.collectAsState()
    val incomingRequest by duoManager.incomingRequest.collectAsState()
    val commonOfflineTracks by duoManager.commonOfflineTracks.collectAsState()
    val errorMessage by duoManager.errorMessage.collectAsState()

    var targetPeerInput by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Now Playing Queue, 1 = Duo Shared Library

    // Reset copied icon animation after 2.5 seconds
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2500)
            isCopied = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .liquidGlass(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                backgroundColor = GlassSheetBackground,
                interactive = false,
                borderWidth = 1.dp,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 8.dp),
        ) {
            // ------------------------------------------------------------------
            // 1. Sheet Drag Handle
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------------------------
            // 2. Sheet Title & Status Row
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .clip(CircleShape)
                            .background(
                                if (connectionState == DuoConnectionState.CONNECTED) AccentEmerald.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Duo,
                            contentDescription = null,
                            size = 18.dp,
                            tint = if (connectionState == DuoConnectionState.CONNECTED) AccentEmerald else Color.White,
                        )
                    }

                    Column {
                        Text(
                            text = "Lunify Duo",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            val dotColor = when (connectionState) {
                                DuoConnectionState.CONNECTED -> AccentEmerald
                                DuoConnectionState.INCOMING_REQUEST -> Color(0xFF38BDF8) // Sky
                                DuoConnectionState.WAITING_FOR_PEER -> Color(0xFFF59E0B) // Amber
                                DuoConnectionState.CONNECTING, DuoConnectionState.SYNCING -> Color(0xFF38BDF8) // Sky
                                DuoConnectionState.ERROR -> Color(0xFFEF4444) // Red
                                DuoConnectionState.DISCONNECTED -> Color.White.copy(alpha = 0.4f)
                            }
                            val statusLabel = when (connectionState) {
                                DuoConnectionState.CONNECTED -> "In Sync • ${peerInfo?.latencyMs ?: 22}ms Latency"
                                DuoConnectionState.INCOMING_REQUEST -> "Incoming Request from ${incomingRequest?.senderDeviceName ?: "Partner"}"
                                DuoConnectionState.WAITING_FOR_PEER -> "Waiting for friend to join..."
                                DuoConnectionState.CONNECTING -> "Connecting to relay..."
                                DuoConnectionState.SYNCING -> "Syncing library manifests..."
                                DuoConnectionState.ERROR -> "Connection Error"
                                DuoConnectionState.DISCONNECTED -> "Ready to Pair"
                            }

                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Text(
                                text = statusLabel,
                                color = if (connectionState == DuoConnectionState.CONNECTED) AccentEmerald else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Close Button
                LucideIconButton(
                    iconRes = LucideIcons.Close,
                    contentDescription = "Close Duo",
                    onClick = onClose,
                    iconSize = 20.dp,
                    buttonSize = 36.dp,
                    tint = TextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------------------------
            // 3. Optional Error Alert Banner
            // ------------------------------------------------------------------
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LucideIcon(
                            iconRes = LucideIcons.Warning,
                            contentDescription = null,
                            size = 16.dp,
                            tint = Color(0xFFEF4444),
                        )
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (connectionState != DuoConnectionState.CONNECTED && connectionState != DuoConnectionState.SYNCING) {
                // ==============================================================
                // State A: Disconnected / Hosting / Pairing Mode (Scrollable for IME Insets)
                // ==============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // --------------------------------------------------------------
                    // Incoming Pairing Request Card (Host Confirmation Prompt)
                // --------------------------------------------------------------
                if (connectionState == DuoConnectionState.INCOMING_REQUEST && incomingRequest != null) {
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(18.dp),
                        backgroundColor = AccentEmerald.copy(alpha = 0.12f),
                        borderWidth = 1.5.dp,
                        interactive = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AccentEmerald.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LucideIcon(
                                    iconRes = LucideIcons.UserPlus,
                                    contentDescription = null,
                                    size = 20.dp,
                                    tint = AccentEmerald,
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Pairing Request",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${incomingRequest?.senderDeviceName ?: "A friend"} wants to connect with your room $myRoomCode and listen together.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Decline Button
                                Button(
                                    onClick = { duoManager.declinePairing() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = Color.White,
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                ) {
                                    Text(
                                        text = "Decline",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                // Accept & Sync Button
                                Button(
                                    onClick = { duoManager.acceptPairing() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentEmerald,
                                        contentColor = Color.Black,
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(40.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        LucideIcon(
                                            iconRes = LucideIcons.Check,
                                            contentDescription = null,
                                            size = 14.dp,
                                            tint = Color.Black,
                                        )
                                        Text(
                                            text = "Accept & Sync",
                                            color = Color.Black,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Listen together in real-time sync with friends anywhere in the world with sub-frame audio drift compensation.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Your Pairing ID Box
                LiquidGlassContainer(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color.White.copy(alpha = 0.05f),
                    interactive = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "YOUR PAIRING ID",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = myRoomCode,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                        }

                        // One-tap Copy & Host Button
                        val isHosting = connectionState == DuoConnectionState.WAITING_FOR_PEER
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isHosting) AccentEmerald.copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Lunify Duo Code", myRoomCode)
                                    clipboard.setPrimaryClip(clip)
                                    isCopied = true
                                    Toast.makeText(context, "Pairing code copied to clipboard", Toast.LENGTH_SHORT).show()

                                    // Automatically start hosting room if not already running
                                    if (connectionState == DuoConnectionState.DISCONNECTED) {
                                        duoManager.hostSession()
                                    }
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
                                    tint = if (isCopied || isHosting) AccentEmerald else Color.White,
                                )
                                Text(
                                    text = if (isCopied) "Copied!"
                                    else if (isHosting) "Hosting Live"
                                    else "Copy & Host",
                                    color = if (isCopied || isHosting) AccentEmerald else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connect with Partner
                Text(
                    text = "CONNECT WITH FRIEND",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Alphanumeric Input Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (targetPeerInput.isEmpty()) {
                        Text(
                            text = "Enter partner code (e.g. LN-3810)",
                            color = TextMuted,
                            fontSize = 14.sp,
                        )
                    }

                    BasicTextField(
                        value = targetPeerInput,
                        onValueChange = { raw ->
                            targetPeerInput = DuoCodeUtils.normalizeRoomCode(raw)
                            duoManager.clearError()
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pair & Sync Button
                val isConnecting = connectionState == DuoConnectionState.CONNECTING
                Button(
                    onClick = {
                        if (targetPeerInput.isNotBlank()) {
                            duoManager.joinSession(targetPeerInput)
                        }
                    },
                    enabled = targetPeerInput.isNotBlank() && !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    if (isConnecting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Connecting... Waiting for host",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Text(
                            text = "Pair & Sync",
                            color = if (targetPeerInput.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (isConnecting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(CircleShape)
                            .clickable { duoManager.disconnect() }
                            .padding(vertical = 4.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel Request",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // ==============================================================
                // State B: Connected Live Sync Session
                // ==============================================================
                // Connected Partner Capsule
                LiquidGlassContainer(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = AccentEmerald.copy(alpha = 0.08f),
                    interactive = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AccentEmerald.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LucideIcon(
                                    iconRes = LucideIcons.Duo,
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = AccentEmerald,
                                )
                            }

                            Column {
                                Text(
                                    text = peerInfo?.deviceName ?: "Lunify Partner",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Room ${myRoomCode} • Latency ${peerInfo?.latencyMs ?: 22}ms",
                                    color = AccentEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        // Disconnect Button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable { duoManager.disconnect() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Disconnect",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Switcher: "Now Playing Queue" vs "Shared Duo Library"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(3.dp),
                ) {
                    // Tab 0: Synced Queue
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (activeTab == 0) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { activeTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Active Queue (${playerState.queue.size})",
                            color = if (activeTab == 0) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        )
                    }

                    // Tab 1: Duo Shared Offline Library
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (activeTab == 1) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { activeTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Duo Library (${commonOfflineTracks.size})",
                            color = if (activeTab == 1) AccentEmerald else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val displayedTracks = if (activeTab == 0) playerState.queue else commonOfflineTracks

                if (displayedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (activeTab == 0) "Queue is empty. Play any song to sync across both phones!"
                            else "No common downloaded songs found on both devices.\nDownload the same tracks to listen offline together!",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                    ) {
                        items(displayedTracks) { track ->
                            TrackRowItem(
                                track = track,
                                isActive = track.id == playerState.currentTrack?.id,
                                isPlaying = playerState.isPlaying,
                                onClick = { onTrackSelect(track) },
                                onMoreClick = null,
                            )
                        }
                    }
                }
            }
        }
    }
}
