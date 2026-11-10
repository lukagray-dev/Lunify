package com.lunify.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ==============================================================================
 * Lunify - Audio Streaming Quality Enum (SettingsStorage.kt)
 * ==============================================================================
 *
 * Defines user-selectable audio streaming tiers:
 * - HIGH: Opus ~160 kbps (pristine fidelity from YouTube Music)
 * - BALANCED: M4A/AAC ~128 kbps (balanced audio vs mobile data usage)
 * - DATA_SAVER: Opus ~70 kbps (minimum bandwidth for cellular roaming)
 */
enum class StreamingQuality(
    val label: String,
    val subtitle: String,
) {
    HIGH("High Quality", "Opus ~160 kbps (Pristine fidelity)"),
    BALANCED("Balanced", "M4A ~128 kbps (Standard data usage)"),
    DATA_SAVER("Data Saver", "Opus ~70 kbps (Minimum bandwidth)");

    companion object {
        fun fromString(value: String?): StreamingQuality {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HIGH
        }
    }
}

/**
 * ==============================================================================
 * Lunify - Offline Download Quality Enum (SettingsStorage.kt)
 * ==============================================================================
 *
 * Defines audio format quality for offline downloads:
 * - HIGH: Best available Opus ~160 kbps audio stream
 * - STANDARD: Standard M4A ~128 kbps audio stream
 */
enum class DownloadQuality(
    val label: String,
    val subtitle: String,
) {
    HIGH("High Quality", "Opus ~160 kbps (Best Available)"),
    STANDARD("Standard", "M4A ~128 kbps");

    companion object {
        fun fromString(value: String?): DownloadQuality {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HIGH
        }
    }
}

/**
 * ==============================================================================
 * Lunify - Application Settings Storage (SettingsStorage.kt)
 * ==============================================================================
 *
 * Hey friend! This class is the central, persistent configuration store for Lunify.
 *
 * WHY THIS ARCHITECTURE?
 * ----------------------
 * 1. Synchronous Warm-Up: SharedPreferences loads instantly into memory upon app
 *    launch, preventing UI flickering or default value pop-in.
 * 2. Reactive StateFlows: Every setting is exposed as a reactive [StateFlow],
 *    so any toggle changed in [com.lunify.ui.settings.SettingsScreen] immediately
 *    updates ExoPlayer, YouTube stream extractor, and Duo coordinators in real time!
 * 3. Safe Defaults: If a user has never opened settings, production-safe, high-quality
 *    defaults are applied automatically.
 * ==============================================================================
 */
class SettingsStorage(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        private const val PREFS_NAME = "lunify_settings_prefs"

        // Preference Keys
        private const val KEY_STREAMING_QUALITY = "pref_streaming_quality"
        private const val KEY_DOWNLOAD_QUALITY = "pref_download_quality"
        private const val KEY_SKIP_SILENCE = "pref_skip_silence"
        private const val KEY_PAUSE_ON_DISCONNECT = "pref_pause_on_disconnect"
        private const val KEY_STREAM_CACHING = "pref_stream_caching"
        private const val KEY_AUTO_DOWNLOAD_LYRICS = "pref_auto_download_lyrics"
        private const val KEY_DUO_DEVICE_NAME = "pref_duo_device_name"
        private const val KEY_LIQUID_GLASS = "pref_liquid_glass"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
    }

    // Default device name from hardware manufacturer and model (e.g. "Xiaomi 2201117PI")
    private val defaultDeviceName: String = run {
        val mfr = (Build.MANUFACTURER ?: "Android").trim().replaceFirstChar { it.uppercase() }
        val mdl = (Build.MODEL ?: "Device").trim()
        "$mfr $mdl".trim()
    }

    // --------------------------------------------------------------------------
    // 1. Audio & Streaming Preferences
    // --------------------------------------------------------------------------
    private val _streamingQuality = MutableStateFlow(
        StreamingQuality.fromString(prefs.getString(KEY_STREAMING_QUALITY, StreamingQuality.HIGH.name))
    )
    val streamingQuality: StateFlow<StreamingQuality> = _streamingQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(
        DownloadQuality.fromString(prefs.getString(KEY_DOWNLOAD_QUALITY, DownloadQuality.HIGH.name))
    )
    val downloadQuality: StateFlow<DownloadQuality> = _downloadQuality.asStateFlow()

    private val _skipSilence = MutableStateFlow(
        prefs.getBoolean(KEY_SKIP_SILENCE, false)
    )
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _pauseOnDisconnect = MutableStateFlow(
        prefs.getBoolean(KEY_PAUSE_ON_DISCONNECT, true)
    )
    val pauseOnDisconnect: StateFlow<Boolean> = _pauseOnDisconnect.asStateFlow()

    // --------------------------------------------------------------------------
    // 2. Storage & Caching Preferences
    // --------------------------------------------------------------------------
    private val _streamCachingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_STREAM_CACHING, true)
    )
    val streamCachingEnabled: StateFlow<Boolean> = _streamCachingEnabled.asStateFlow()

    private val _autoDownloadLyrics = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_DOWNLOAD_LYRICS, true)
    )
    val autoDownloadLyrics: StateFlow<Boolean> = _autoDownloadLyrics.asStateFlow()

    // --------------------------------------------------------------------------
    // 3. Duo Synchronization Preferences
    // --------------------------------------------------------------------------
    private val _duoDeviceName = MutableStateFlow(
        prefs.getString(KEY_DUO_DEVICE_NAME, null)?.ifBlank { null } ?: defaultDeviceName
    )
    val duoDeviceName: StateFlow<String> = _duoDeviceName.asStateFlow()

    // --------------------------------------------------------------------------
    // 4. Appearance & Display Preferences
    // --------------------------------------------------------------------------
    private val _liquidGlassEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LIQUID_GLASS, true)
    )
    val liquidGlassEnabled: StateFlow<Boolean> = _liquidGlassEnabled.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
    )
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    // ==========================================================================
    // PREFERENCE MUTATORS (PERSIST TO DISK + UPDATE STATEFLOW)
    // ==========================================================================

    /**
     * Updates streaming audio fidelity (High, Balanced, Data Saver).
     */
    fun setStreamingQuality(quality: StreamingQuality) {
        _streamingQuality.value = quality
        prefs.edit().putString(KEY_STREAMING_QUALITY, quality.name).apply()
    }

    /**
     * Updates offline download audio quality.
     */
    fun setDownloadQuality(quality: DownloadQuality) {
        _downloadQuality.value = quality
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality.name).apply()
    }

    /**
     * Toggles automatic silence skipping in ExoPlayer.
     */
    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
    }

    /**
     * Toggles automatic pausing when headphones / Bluetooth disconnect.
     */
    fun setPauseOnDisconnect(enabled: Boolean) {
        _pauseOnDisconnect.value = enabled
        prefs.edit().putBoolean(KEY_PAUSE_ON_DISCONNECT, enabled).apply()
    }

    /**
     * Toggles automatic local disk caching of played streaming songs.
     */
    fun setStreamCachingEnabled(enabled: Boolean) {
        _streamCachingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_STREAM_CACHING, enabled).apply()
    }

    /**
     * Toggles automatic fetching and saving of companion .lrc lyrics alongside downloads.
     */
    fun setAutoDownloadLyrics(enabled: Boolean) {
        _autoDownloadLyrics.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_LYRICS, enabled).apply()
    }

    /**
     * Sets a personalized device display name for Duo pairing requests.
     */
    fun setDuoDeviceName(name: String) {
        val sanitized = name.trim().ifBlank { defaultDeviceName }
        _duoDeviceName.value = sanitized
        prefs.edit().putString(KEY_DUO_DEVICE_NAME, sanitized).apply()
    }

    /**
     * Toggles liquid glass frosted blur and specular highlights.
     */
    fun setLiquidGlassEnabled(enabled: Boolean) {
        _liquidGlassEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LIQUID_GLASS, enabled).apply()
    }

    /**
     * Toggles keeping the device display awake while listening or viewing lyrics.
     */
    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }
}
