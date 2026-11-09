package com.lunify.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ==============================================================================
 * Lunify - Black & White Monochromatic Palette (Color.kt)
 * ==============================================================================
 *
 * Hey friend! This file defines the core color tokens for Lunify's pure
 * black-and-white minimalist dark theme.
 *
 * Designed to mirror SimpMusic's sleek obsidian aesthetic:
 * 1. True Black AMOLED Base: Pure pitch black (#000000) for OLED power saving
 *    and infinite cinematic contrast.
 * 2. Transparent-ish Liquid Glass: Translucent obsidian foundations (25%-40% alpha)
 *    that let underlying artwork and ambient shimmers refract through.
 * 3. High-Contrast Monochrome Accents: Pure White (#FFFFFF) for primary controls,
 *    active pills, sliders, and transport icons.
 * 4. Refined Glass Specular Rims: Gradient borders with white alpha steps for
 *    tactile glass refraction.
 * ==============================================================================
 */

// Foundation True-Black Obsidian Backgrounds
val ObsidianBackground = Color(0xFF000000)        // Pure AMOLED Black
val ObsidianSurfaceLowest = Color(0xFF080808)
val ObsidianSurfaceLow = Color(0xFF101010)
val ObsidianSurface = Color(0xFF161616)
val ObsidianSurfaceHigh = Color(0xFF202020)
val ObsidianSurfaceHighest = Color(0xFF2A2A2A)

// Frosted Liquid Glass Translucencies (Rich Obsidian Smokey Glass)
val GlassSurfaceDark = Color(0xB8101016)          // ~72% deep frosted obsidian glass
val GlassSurfaceElevated = Color(0xD014141E)      // ~82% dense elevated frosted glass (dissolves background items)
val GlassSheetBackground = Color(0xF2121216)      // ~95% deep obsidian frosted glass for modal bottom sheets
val GlassPill = Color(0x66242434)                 // ~40% frosted glass pill
val GlassPillActive = Color(0xFFFFFFFF)           // Pure White active pill
val GlassBorderSubtle = Color(0x3DFFFFFF)         // 24% crisp white specular outline
val GlassBorderProminent = Color(0x66FFFFFF)      // 40% white specular rim
val GlassShimmer = Color(0x4DFFFFFF)              // 30% top edge rim reflection

// Monochrome Black & White Accents
val AccentWhite = Color(0xFFFFFFFF)               // Pure White primary accent
val AccentGreyLight = Color(0xFFE2E8F0)           // Slate-200
val AccentGrey = Color(0xFF94A3B8)                // Slate-400
val AccentGreyDark = Color(0xFF475569)            // Slate-600

// Remapped primary action and playback glow to Pure White for Black & White theme
val AccentCyan = Color(0xFFFFFFFF)                // Pure White brand accent
val AccentPurple = Color(0xFFE2E8F0)              // Soft silver monochrome shimmer
val AccentEmerald = Color(0xFF10B981)             // Keep for Duo live online status pulse
val AccentRose = Color(0xFFF43F5E)                // Favorite heart indicator
val AccentAmber = Color(0xFFF59E0B)

// High-Legibility Typography Shades
val TextPrimary = Color(0xFFFFFFFF)               // 100% white for titles and primary labels
val TextSecondary = Color(0xFFB0B0BA)             // Soft clean grey for artist names and subtitles
val TextTertiary = Color(0xFF787885)              // Mid grey for durations and secondary metadata
val TextMuted = Color(0xFF52525E)                 // Dark grey for subtle inactive hints

// Ambient Monochrome Lighting Gradients
val AmbientGlowStart = Color(0x26FFFFFF)          // 15% subtle white ambient shimmer
val AmbientGlowMid = Color(0x14FFFFFF)            // 8% subtle white glow
val AmbientGlowEnd = Color(0x00000000)            // Fades to pure black
