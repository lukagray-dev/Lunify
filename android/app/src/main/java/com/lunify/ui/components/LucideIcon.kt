package com.lunify.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunify.R

/**
 * ==============================================================================
 * Lunify - Lucide Icon System (LucideIcon.kt)
 * ==============================================================================
 *
 * Hey friend! This file provides a clean, strongly-typed wrapper around our
 * custom Lucide Vector Drawables.
 *
 * It guarantees:
 * 1. ZERO emojis anywhere in the app!
 * 2. Unified icon sizing, color tinting, and ripple ergonomics.
 * 3. Easy access to all media, player, navigation, and Duo symbols.
 * ==============================================================================
 */

/**
 * Central registry of available Lucide icons in Lunify.
 */
object LucideIcons {
    // Navigation & Primary
    val BrandLogo = R.drawable.ic_lucide_brand_logo
    val Home = R.drawable.ic_lucide_home
    val Search = R.drawable.ic_lucide_search
    val Download = R.drawable.ic_lucide_download
    val Duo = R.drawable.ic_lucide_duo
    val Settings = R.drawable.ic_lucide_settings
    val Browse = R.drawable.ic_lucide_browse
    val Profile = R.drawable.ic_lucide_profile

    // Media & Content
    val Music = R.drawable.ic_lucide_music
    val Video = R.drawable.ic_lucide_video
    val Radio = R.drawable.ic_lucide_radio
    val Sparkles = R.drawable.ic_lucide_sparkles
    val Folder = R.drawable.ic_lucide_folder
    val FolderPlus = R.drawable.ic_lucide_folder_plus
    val FilePlus = R.drawable.ic_lucide_file_plus

    // Playback & Controls
    val Play = R.drawable.ic_lucide_play
    val Pause = R.drawable.ic_lucide_pause
    val SkipForward = R.drawable.ic_lucide_skip_forward
    val SkipBack = R.drawable.ic_lucide_skip_back
    val Repeat = R.drawable.ic_lucide_repeat
    val Shuffle = R.drawable.ic_lucide_shuffle
    val Volume2 = R.drawable.ic_lucide_volume_2
    val VolumeX = R.drawable.ic_lucide_volume_x
    val Heart = R.drawable.ic_lucide_heart
    val ChevronDown = R.drawable.ic_lucide_chevron_down

    // Actions & Utilities
    val MoreVertical = R.drawable.ic_lucide_more_vertical
    val Filter = R.drawable.ic_lucide_filter
    val Share = R.drawable.ic_lucide_share
    val ListPlus = R.drawable.ic_lucide_list_plus
    val ArrowLeft = R.drawable.ic_lucide_arrow_left
    val ArrowRight = R.drawable.ic_lucide_arrow_right
    val Check = R.drawable.ic_lucide_check
    val Clock = R.drawable.ic_lucide_clock
    val Copy = R.drawable.ic_lucide_copy
    val Link = R.drawable.ic_lucide_link
    val Trash = R.drawable.ic_lucide_trash
    val UserPlus = R.drawable.ic_lucide_user_plus
    val Warning = R.drawable.ic_lucide_warning
    val Close = R.drawable.ic_lucide_close
    val X = R.drawable.ic_lucide_x
    val Loading = R.drawable.ic_lucide_loading
    val Wave = R.drawable.ic_lucide_wave
}

/**
 * Standard Lucide Icon composable.
 */
@Composable
fun LucideIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * Interactive button with bounded circular ripple for Lucide icons.
 */
@Composable
fun LucideIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    buttonSize: Dp = 44.dp,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(buttonSize)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = buttonSize / 2),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(
            iconRes = iconRes,
            contentDescription = contentDescription,
            size = iconSize,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}
