package com.lunify.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.lunify.ui.components.LiquidGlassContainer
import com.lunify.ui.components.LucideIcon
import com.lunify.ui.components.LucideIconButton
import com.lunify.ui.components.LucideIcons
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.AccentEmerald
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.ObsidianBackground
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary

/**
 * ==============================================================================
 * Lunify - Settings Screen (SettingsScreen.kt)
 * ==============================================================================
 *
 * Hey friend! This screen allows users to customize their streaming, audio,
 * offline caching, and Duo sync preferences.
 * ==============================================================================
 */

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var highQualityAudio by remember { mutableStateOf(true) }
    var autoDownloadLyrics by remember { mutableStateOf(true) }
    var streamCachingEnabled by remember { mutableStateOf(true) }
    var liquidGlassEnabled by remember { mutableStateOf(true) }

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
            // Header
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Section: Audio & Streaming
                item {
                    SettingsSectionTitle("AUDIO & STREAMING")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = "High Quality Streaming",
                                subtitle = "Opus 160 kbps pristine audio streaming",
                                checked = highQualityAudio,
                                onCheckedChange = { highQualityAudio = it },
                            )
                            SettingsToggleRow(
                                title = "Automatic Stream Caching",
                                subtitle = "Cache played songs automatically for offline replay",
                                checked = streamCachingEnabled,
                                onCheckedChange = { streamCachingEnabled = it },
                            )
                        }
                    }
                }

                // Section: Offline & Downloads
                item {
                    SettingsSectionTitle("OFFLINE & DOWNLOADS")
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassSurfaceElevated,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = "Embed Synchronized Lyrics",
                                subtitle = "Fetch and embed .lrc lyrics into downloaded songs",
                                checked = autoDownloadLyrics,
                                onCheckedChange = { autoDownloadLyrics = it },
                            )
                        }
                    }
                }

                // Section: Appearance
                item {
                    SettingsSectionTitle("APPEARANCE")
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
                                onCheckedChange = { liquidGlassEnabled = it },
                            )
                        }
                    }
                }

                // Section: About Lunify
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
                            Text(
                                text = "Lunify v1.0.0",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Unofficial YouTube Music Client with Offline Playback and Duo. Free, open-source (AGPLv3), zero ads, and zero tracking.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

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
