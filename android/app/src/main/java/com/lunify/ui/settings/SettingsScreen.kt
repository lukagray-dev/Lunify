package com.lunify.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lunify.data.local.DownloadQuality
import com.lunify.data.local.LibraryStorage
import com.lunify.data.local.SettingsStorage
import com.lunify.data.local.StreamingQuality
import com.lunify.player.AudioPlayerManager
import com.lunify.player.PlayerCacheManager
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.AccentRose
import com.lunify.ui.theme.AccentWhite
import com.lunify.ui.theme.GlassBorderProminent
import com.lunify.ui.theme.GlassBorderSubtle
import com.lunify.ui.theme.GlassSheetBackground
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ==============================================================================
 * Lunify - Production Settings Screen (SettingsScreen.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the central control panel for Lunify.
 *
 * ARCHITECTURAL DESIGN:
 * ---------------------
 * All user preferences here are persisted immediately to disk via [SettingsStorage]
 * (SharedPreferences) and exposed as reactive [kotlinx.coroutines.flow.StateFlow]
 * streams so every component across the app (Media3 ExoPlayer, ChunkedAudioDownloader,
 * DuoManager, etc.) reacts instantly with zero app restarts required.
 *
 * SECTIONS IMPLEMENTED:
 * 1. Audio & Streaming:
 *    - Streaming Quality (High Opus ~160kbps, Balanced M4A ~128kbps, Data Saver ~70kbps).
 *    - Native Equalizer launcher (passes active ExoPlayer audioSessionId to Android AudioFX).
 *    - Skip Silence toggle.
 *    - Pause on Disconnect toggle.
 * 2. Cache & Storage:
 *    - Live Stream Cache monitor (computes exact byte footprint from SimpleCache).
 *    - Clear Streaming Cache action button with immediate UI update.
 *    - Stream Caching enable/disable toggle.
 * 3. Offline & Downloads:
 *    - Download Quality (High vs Standard).
 *    - Offline storage directory path display ("Music/Lunify").
 *    - Auto-Fetch Companion Synchronized Lyrics (.lrc) toggle.
 * 4. Lunify Duo:
 *    - Custom Duo Display Name with interactive modal edit dialog.
 * 5. Appearance & Display:
 *    - Liquid Glass Surfaces toggle.
 *    - Keep Screen On toggle.
 * 6. Data & Privacy:
 *    - Clear Playback History (with confirmation dialog).
 *    - Reset Play Counts (with confirmation dialog).
 * 7. About:
 *    - Application version, AGPLv3 license, open-source acknowledgements.
 * ==============================================================================
 */

@Composable
fun SettingsScreen(
    settingsStorage: SettingsStorage,
    audioPlayerManager: AudioPlayerManager,
    libraryStorage: LibraryStorage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --------------------------------------------------------------------------
    // Reactive StateFlow bindings from SettingsStorage
    // --------------------------------------------------------------------------
    val streamingQuality by settingsStorage.streamingQuality.collectAsState()
    val downloadQuality by settingsStorage.downloadQuality.collectAsState()
    val skipSilence by settingsStorage.skipSilence.collectAsState()
    val pauseOnDisconnect by settingsStorage.pauseOnDisconnect.collectAsState()
    val streamCachingEnabled by settingsStorage.streamCachingEnabled.collectAsState()
    val autoDownloadLyrics by settingsStorage.autoDownloadLyrics.collectAsState()
    val duoDeviceName by settingsStorage.duoDeviceName.collectAsState()
    val liquidGlassEnabled by settingsStorage.liquidGlassEnabled.collectAsState()
    val keepScreenOn by settingsStorage.keepScreenOn.collectAsState()

    // Cache size tracked in bytes (queried asynchronously from SimpleCache)
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }

    // Dialog state controllers
    var showStreamingQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showDuoNameDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showResetPlayCountsDialog by remember { mutableStateOf(false) }

    // Query live cache size on screen entry
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheSizeBytes = PlayerCacheManager.getCacheSizeBytes(context)
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
            // Top Navigation Bar
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LucideIconButton(
                    iconRes = LucideIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    iconSize = 22.dp,
                    buttonSize = 44.dp,
                    tint = TextPrimary,
                )

                Text(
                    text = "Settings",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // ------------------------------------------------------------------
            // Scrollable Settings Sections
            // ------------------------------------------------------------------
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 120.dp, // Generous padding so floating bottom nav doesn't cover items
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // ==============================================================
                // Section 1: Audio & Streaming
                // ==============================================================
                item {
                    SettingsSectionTitle("AUDIO & STREAMING")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickableRow(
                                title = "Streaming Audio Quality",
                                subtitle = when (streamingQuality) {
                                    StreamingQuality.HIGH -> "High Quality (Opus ~160 kbps)"
                                    StreamingQuality.BALANCED -> "Balanced (M4A ~128 kbps)"
                                    StreamingQuality.DATA_SAVER -> "Data Saver (Opus ~70 kbps)"
                                },
                                iconRes = LucideIcons.Music,
                                onClick = { showStreamingQualityDialog = true },
                            )

                            SettingsDivider()

                            SettingsClickableRow(
                                title = "Native Equalizer",
                                subtitle = "Open device audio effect panel (Dolby Atmos, AudioFX)",
                                iconRes = LucideIcons.Wave,
                                onClick = { audioPlayerManager.launchSystemEqualizer(context) },
                            )

                            SettingsDivider()

                            SettingsToggleRow(
                                title = "Skip Silence",
                                subtitle = "Automatically skip quiet sections during streaming",
                                checked = skipSilence,
                                onCheckedChange = { settingsStorage.setSkipSilence(it) },
                            )

                            SettingsDivider()

                            SettingsToggleRow(
                                title = "Pause on Disconnect",
                                subtitle = "Pause playback when headphones or Bluetooth disconnect",
                                checked = pauseOnDisconnect,
                                onCheckedChange = { settingsStorage.setPauseOnDisconnect(it) },
                            )
                        }
                    }
                }

                // ==============================================================
                // Section 2: Cache & Storage
                // ==============================================================
                item {
                    SettingsSectionTitle("CACHE & STORAGE")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Live Cache Usage & Clear Action
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Stream Cache Size",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${formatBytes(cacheSizeBytes)} cached on disk",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }

                                LucideIconButton(
                                    iconRes = LucideIcons.Trash,
                                    contentDescription = "Clear Cache",
                                    onClick = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                PlayerCacheManager.clearCache(context)
                                                cacheSizeBytes = PlayerCacheManager.getCacheSizeBytes(context)
                                            }
                                            Toast.makeText(context, "Streaming cache cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    iconSize = 18.dp,
                                    buttonSize = 36.dp,
                                    tint = AccentRose,
                                )
                            }

                            SettingsDivider()

                            SettingsToggleRow(
                                title = "Cache Streams for Replay",
                                subtitle = "Store streamed songs locally for instant zero-bandwidth replay",
                                checked = streamCachingEnabled,
                                onCheckedChange = { settingsStorage.setStreamCachingEnabled(it) },
                            )
                        }
                    }
                }

                // ==============================================================
                // Section 3: Downloads & Offline
                // ==============================================================
                item {
                    SettingsSectionTitle("OFFLINE & DOWNLOADS")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickableRow(
                                title = "Download Quality",
                                subtitle = when (downloadQuality) {
                                    DownloadQuality.HIGH -> "High Quality (Opus ~160 kbps)"
                                    DownloadQuality.STANDARD -> "Standard Quality (M4A ~128 kbps)"
                                },
                                iconRes = LucideIcons.Download,
                                onClick = { showDownloadQualityDialog = true },
                            )

                            SettingsDivider()

                            SettingsClickableRow(
                                title = "Offline Storage Path",
                                subtitle = "Music/Lunify (Device public storage)",
                                iconRes = LucideIcons.Folder,
                                onClick = {
                                    Toast.makeText(context, "Stored in standard Music/Lunify directory", Toast.LENGTH_SHORT).show()
                                },
                            )

                            SettingsDivider()

                            SettingsToggleRow(
                                title = "Auto-Fetch Companion Lyrics",
                                subtitle = "Save synchronized .lrc lyrics beside downloaded audio files",
                                checked = autoDownloadLyrics,
                                onCheckedChange = { settingsStorage.setAutoDownloadLyrics(it) },
                            )
                        }
                    }
                }

                // ==============================================================
                // Section 4: Lunify Duo
                // ==============================================================
                item {
                    SettingsSectionTitle("LUNIFY DUO")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickableRow(
                                title = "Duo Display Name",
                                subtitle = duoDeviceName.ifBlank { "Default Device Name" },
                                iconRes = LucideIcons.Duo,
                                onClick = { showDuoNameDialog = true },
                            )
                        }
                    }
                }

                // ==============================================================
                // Section 5: Appearance & Display
                // ==============================================================
                item {
                    SettingsSectionTitle("APPEARANCE & DISPLAY")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = "Liquid Glass Surfaces",
                                subtitle = "Enable frosted glass blur, specular rims, and fluid touch",
                                checked = liquidGlassEnabled,
                                onCheckedChange = { settingsStorage.setLiquidGlassEnabled(it) },
                            )

                            SettingsDivider()

                            SettingsToggleRow(
                                title = "Keep Screen On",
                                subtitle = "Prevent device screen from sleeping while player is active",
                                checked = keepScreenOn,
                                onCheckedChange = { settingsStorage.setKeepScreenOn(it) },
                            )
                        }
                    }
                }

                // ==============================================================
                // Section 6: Data & Privacy
                // ==============================================================
                item {
                    SettingsSectionTitle("DATA & PRIVACY")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickableRow(
                                title = "Clear Playback History",
                                subtitle = "Remove all recently played songs from history",
                                iconRes = LucideIcons.Clock,
                                onClick = { showClearHistoryDialog = true },
                            )

                            SettingsDivider()

                            SettingsClickableRow(
                                title = "Reset Playback Statistics",
                                subtitle = "Reset song play counters across your library",
                                iconRes = LucideIcons.Trash,
                                onClick = { showResetPlayCountsDialog = true },
                            )
                        }
                    }
                }

                // ==============================================================
                // Section 7: About Lunify
                // ==============================================================
                item {
                    SettingsSectionTitle("ABOUT LUNIFY")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LucideIcon(
                                    iconRes = LucideIcons.BrandLogo,
                                    contentDescription = "Lunify Logo",
                                    size = 20.dp,
                                    tint = AccentWhite,
                                )
                                Text(
                                    text = "Lunify v1.0.0-alpha",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Text(
                                text = "Pristine open-source YouTube Music client with offline playback and real-time Duo synchronization.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Licensed under AGPLv3. Built with Android Media3 ExoPlayer, Eclipse Paho MQTT, NewPipeExtractor, LRCLIB, and Lucide Icons. Zero ads, zero tracking, completely privacy-respecting.",
                                color = TextTertiary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        }

        // ======================================================================
        // Interactive Modals & Dialogs
        // ======================================================================

        // 1. Streaming Quality Dialog
        if (showStreamingQualityDialog) {
            Dialog(onDismissRequest = { showStreamingQualityDialog = false }) {
                LiquidGlassContainer(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = GlassSheetBackground,
                    borderWidth = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Streaming Audio Quality",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "Select your preferred bitrate tier when streaming online music:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityOptionRow(
                                label = "High Quality",
                                description = "Opus ~160 kbps (Pristine fidelity)",
                                isSelected = streamingQuality == StreamingQuality.HIGH,
                                onClick = {
                                    settingsStorage.setStreamingQuality(StreamingQuality.HIGH)
                                    showStreamingQualityDialog = false
                                },
                            )

                            QualityOptionRow(
                                label = "Balanced",
                                description = "M4A ~128 kbps (Optimal balance)",
                                isSelected = streamingQuality == StreamingQuality.BALANCED,
                                onClick = {
                                    settingsStorage.setStreamingQuality(StreamingQuality.BALANCED)
                                    showStreamingQualityDialog = false
                                },
                            )

                            QualityOptionRow(
                                label = "Data Saver",
                                description = "Opus ~70 kbps (Conserves mobile data)",
                                isSelected = streamingQuality == StreamingQuality.DATA_SAVER,
                                onClick = {
                                    settingsStorage.setStreamingQuality(StreamingQuality.DATA_SAVER)
                                    showStreamingQualityDialog = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // 2. Download Quality Dialog
        if (showDownloadQualityDialog) {
            Dialog(onDismissRequest = { showDownloadQualityDialog = false }) {
                LiquidGlassContainer(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = GlassSheetBackground,
                    borderWidth = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Download Audio Quality",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "Select audio bitrate for songs saved to local device storage:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityOptionRow(
                                label = "High Quality",
                                description = "Opus ~160 kbps (Lossless-like fidelity)",
                                isSelected = downloadQuality == DownloadQuality.HIGH,
                                onClick = {
                                    settingsStorage.setDownloadQuality(DownloadQuality.HIGH)
                                    showDownloadQualityDialog = false
                                },
                            )

                            QualityOptionRow(
                                label = "Standard Quality",
                                description = "M4A ~128 kbps (Compact file size)",
                                isSelected = downloadQuality == DownloadQuality.STANDARD,
                                onClick = {
                                    settingsStorage.setDownloadQuality(DownloadQuality.STANDARD)
                                    showDownloadQualityDialog = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // 3. Duo Display Name Dialog
        if (showDuoNameDialog) {
            var tempName by remember { mutableStateOf(duoDeviceName) }

            Dialog(onDismissRequest = { showDuoNameDialog = false }) {
                LiquidGlassContainer(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = GlassSheetBackground,
                    borderWidth = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Duo Display Name",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "This name is displayed to friends when sending or receiving pairing requests:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GlassSurfaceElevated)
                                .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            BasicTextField(
                                value = tempName,
                                onValueChange = { tempName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(AccentWhite),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Cancel",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { showDuoNameDialog = false }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentWhite)
                                    .clickable {
                                        if (tempName.isNotBlank()) {
                                            settingsStorage.setDuoDeviceName(tempName.trim())
                                        }
                                        showDuoNameDialog = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Save",
                                    color = ObsidianBackground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Clear Playback History Confirmation Dialog
        if (showClearHistoryDialog) {
            ConfirmationDialog(
                title = "Clear Playback History?",
                message = "This will remove all recently played tracks from your history. This action cannot be undone.",
                confirmText = "Clear History",
                confirmColor = AccentRose,
                onConfirm = {
                    libraryStorage.clearHistory()
                    Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                    showClearHistoryDialog = false
                },
                onDismiss = { showClearHistoryDialog = false },
            )
        }

        // 5. Reset Play Counts Confirmation Dialog
        if (showResetPlayCountsDialog) {
            ConfirmationDialog(
                title = "Reset Play Statistics?",
                message = "This will reset play counters for all tracks across your entire library to zero.",
                confirmText = "Reset Counts",
                confirmColor = AccentRose,
                onConfirm = {
                    libraryStorage.clearPlayCounts()
                    Toast.makeText(context, "Play statistics reset", Toast.LENGTH_SHORT).show()
                    showResetPlayCountsDialog = false
                },
                onDismiss = { showResetPlayCountsDialog = false },
            )
        }
    }
}

// ==============================================================================
// Reusable UI Building Blocks for Settings
// ==============================================================================

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.35f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
            ),
        )
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
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
            iconRes = iconRes,
            contentDescription = null,
            size = 18.dp,
            tint = TextSecondary,
        )
    }
}

@Composable
private fun QualityOptionRow(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) GlassBorderProminent else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = if (isSelected) TextSecondary else TextMuted,
                fontSize = 11.sp,
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(AccentWhite),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(
                    iconRes = LucideIcons.Check,
                    contentDescription = "Selected",
                    size = 12.dp,
                    tint = ObsidianBackground,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassContainer(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = GlassSheetBackground,
            borderWidth = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(confirmColor)
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = confirmText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a raw byte count into human-readable B, KB, MB, or GB.
 */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
