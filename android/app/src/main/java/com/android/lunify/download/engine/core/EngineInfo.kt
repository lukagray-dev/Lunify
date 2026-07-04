package com.android.lunify.download.engine.core

/**
 * Information about the bundled download engine.
 *
 * The app no longer exposes engine upgrades or replacement, so this model only
 * tracks whether the runtime initialized successfully and which bundled version
 * is currently active.
 */
data class EngineInfo(
    val name: String,
    val installedVersion: String?,
    val isInstalled: Boolean,
    val lastChecked: Long,
    val binaryPath: String?
) {
    companion object {
        fun installed(
            name: String,
            installedVersion: String? = null,
            lastChecked: Long = 0L,
            binaryPath: String? = null,
        ) = EngineInfo(
            name = name,
            installedVersion = installedVersion,
            isInstalled = true,
            lastChecked = lastChecked,
            binaryPath = binaryPath
        )

        fun unavailable(name: String) = EngineInfo(
            name = name,
            installedVersion = null,
            isInstalled = false,
            lastChecked = 0L,
            binaryPath = null
        )
    }
}

/**
 * Supported platforms/extractors
 */
enum class SupportedPlatform(
    val displayName: String,
    val urlPatterns: List<String>
) {
    YOUTUBE("YouTube", listOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
    DAILYMOTION("Dailymotion", listOf("dailymotion.com", "dai.ly")),
    INSTAGRAM("Instagram", listOf("instagram.com", "instagr.am")),
    TWITTER("Twitter/X", listOf("twitter.com", "x.com")),
    TIKTOK("TikTok", listOf("tiktok.com", "vm.tiktok.com")),
    FACEBOOK("Facebook", listOf("facebook.com", "fb.watch")),
    VIMEO("Vimeo", listOf("vimeo.com")),
    SOUNDCLOUD("SoundCloud", listOf("soundcloud.com")),
    TWITCH("Twitch", listOf("twitch.tv", "clips.twitch.tv")),
    REDDIT("Reddit", listOf("reddit.com", "v.redd.it")),
    GENERIC("Generic", emptyList());
    
    companion object {
        fun fromUrl(url: String): SupportedPlatform {
            val lowerUrl = url.lowercase()
            return SupportedPlatform.entries.find { platform ->
                platform.urlPatterns.any { pattern -> lowerUrl.contains(pattern) }
            } ?: GENERIC
        }
    }
}
