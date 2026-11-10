package com.lunify

import android.content.SharedPreferences
import com.lunify.data.local.DownloadQuality
import com.lunify.data.local.SettingsStorage
import com.lunify.data.local.StreamingQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * ==============================================================================
 * Lunify - Settings & Persistent Configuration Unit Tests (LunifySettingsTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates all logic behind Lunify's settings:
 *
 * 1. Enum String Parsing & Resiliency:
 *    Ensures invalid or legacy saved values gracefully fall back to production-safe
 *    defaults instead of throwing exceptions.
 *
 * 2. SettingsStorage In-Memory & Disk Synchronization:
 *    Ensures that every mutator updates both the reactive StateFlow (for immediate
 *    UI / ExoPlayer updates) and commits to SharedPreferences (for persistence).
 *
 * 3. Default Configuration Guarantees:
 *    Ensures brand-new app installations have optimal defaults:
 *    - High Quality Opus audio streaming
 *    - Auto-pause on headphone disconnect enabled
 *    - Stream disk caching enabled
 *    - Companion synchronized lyrics (.lrc) enabled
 *    - Liquid glass visual effects enabled
 * ==============================================================================
 */
class LunifySettingsTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var backingMap: MutableMap<String, Any?>
    private lateinit var settingsStorage: SettingsStorage

    @Before
    fun setUp() {
        backingMap = mutableMapOf()
        mockPrefs = createMockSharedPreferences(backingMap)
        settingsStorage = SettingsStorage(mockPrefs)
    }

    @Test
    fun testStreamingQualityParsing() {
        assertEquals(StreamingQuality.HIGH, StreamingQuality.fromString("HIGH"))
        assertEquals(StreamingQuality.HIGH, StreamingQuality.fromString("high"))
        assertEquals(StreamingQuality.BALANCED, StreamingQuality.fromString("BALANCED"))
        assertEquals(StreamingQuality.BALANCED, StreamingQuality.fromString("balanced"))
        assertEquals(StreamingQuality.DATA_SAVER, StreamingQuality.fromString("DATA_SAVER"))
        assertEquals(StreamingQuality.DATA_SAVER, StreamingQuality.fromString("data_saver"))

        // Resilient fallback to HIGH on null or unknown value
        assertEquals(StreamingQuality.HIGH, StreamingQuality.fromString(null))
        assertEquals(StreamingQuality.HIGH, StreamingQuality.fromString("UNKNOWN_VALUE"))
        assertEquals(StreamingQuality.HIGH, StreamingQuality.fromString(""))
    }

    @Test
    fun testDownloadQualityParsing() {
        assertEquals(DownloadQuality.HIGH, DownloadQuality.fromString("HIGH"))
        assertEquals(DownloadQuality.HIGH, DownloadQuality.fromString("high"))
        assertEquals(DownloadQuality.STANDARD, DownloadQuality.fromString("STANDARD"))
        assertEquals(DownloadQuality.STANDARD, DownloadQuality.fromString("standard"))

        // Resilient fallback to HIGH on null or unknown value
        assertEquals(DownloadQuality.HIGH, DownloadQuality.fromString(null))
        assertEquals(DownloadQuality.HIGH, DownloadQuality.fromString("INVALID"))
        assertEquals(DownloadQuality.HIGH, DownloadQuality.fromString(""))
    }

    @Test
    fun testDefaultSettingsValues() {
        assertEquals(StreamingQuality.HIGH, settingsStorage.streamingQuality.value)
        assertEquals(DownloadQuality.HIGH, settingsStorage.downloadQuality.value)
        assertFalse("Skip silence should default to false", settingsStorage.skipSilence.value)
        assertTrue("Pause on disconnect should default to true", settingsStorage.pauseOnDisconnect.value)
        assertTrue("Stream caching should default to true", settingsStorage.streamCachingEnabled.value)
        assertTrue("Auto download lyrics should default to true", settingsStorage.autoDownloadLyrics.value)
        assertTrue("Liquid glass should default to true", settingsStorage.liquidGlassEnabled.value)
        assertFalse("Keep screen on should default to false", settingsStorage.keepScreenOn.value)
    }

    @Test
    fun testMutationsUpdateStateFlowAndPrefs() {
        // 1. Streaming Quality
        settingsStorage.setStreamingQuality(StreamingQuality.DATA_SAVER)
        assertEquals(StreamingQuality.DATA_SAVER, settingsStorage.streamingQuality.value)
        assertEquals("DATA_SAVER", backingMap["pref_streaming_quality"])

        // 2. Download Quality
        settingsStorage.setDownloadQuality(DownloadQuality.STANDARD)
        assertEquals(DownloadQuality.STANDARD, settingsStorage.downloadQuality.value)
        assertEquals("STANDARD", backingMap["pref_download_quality"])

        // 3. Skip Silence
        settingsStorage.setSkipSilence(true)
        assertTrue(settingsStorage.skipSilence.value)
        assertEquals(true, backingMap["pref_skip_silence"])

        // 4. Pause on Disconnect
        settingsStorage.setPauseOnDisconnect(false)
        assertFalse(settingsStorage.pauseOnDisconnect.value)
        assertEquals(false, backingMap["pref_pause_on_disconnect"])

        // 5. Stream Caching
        settingsStorage.setStreamCachingEnabled(false)
        assertFalse(settingsStorage.streamCachingEnabled.value)
        assertEquals(false, backingMap["pref_stream_caching"])

        // 6. Auto Download Lyrics
        settingsStorage.setAutoDownloadLyrics(false)
        assertFalse(settingsStorage.autoDownloadLyrics.value)
        assertEquals(false, backingMap["pref_auto_download_lyrics"])

        // 7. Duo Device Name
        settingsStorage.setDuoDeviceName("Pixel 9 Pro")
        assertEquals("Pixel 9 Pro", settingsStorage.duoDeviceName.value)
        assertEquals("Pixel 9 Pro", backingMap["pref_duo_device_name"])

        // 8. Liquid Glass
        settingsStorage.setLiquidGlassEnabled(false)
        assertFalse(settingsStorage.liquidGlassEnabled.value)
        assertEquals(false, backingMap["pref_liquid_glass"])

        // 9. Keep Screen On
        settingsStorage.setKeepScreenOn(true)
        assertTrue(settingsStorage.keepScreenOn.value)
        assertEquals(true, backingMap["pref_keep_screen_on"])
    }

    @Test
    fun testRestorationFromExistingPreferences() {
        // Pre-populate preferences map
        val preMap = mutableMapOf<String, Any?>(
            "pref_streaming_quality" to "BALANCED",
            "pref_download_quality" to "STANDARD",
            "pref_skip_silence" to true,
            "pref_pause_on_disconnect" to false,
            "pref_stream_caching" to false,
            "pref_auto_download_lyrics" to false,
            "pref_duo_device_name" to "Custom Device",
            "pref_liquid_glass" to false,
            "pref_keep_screen_on" to true,
        )

        val restoredStorage = SettingsStorage(createMockSharedPreferences(preMap))

        assertEquals(StreamingQuality.BALANCED, restoredStorage.streamingQuality.value)
        assertEquals(DownloadQuality.STANDARD, restoredStorage.downloadQuality.value)
        assertTrue(restoredStorage.skipSilence.value)
        assertFalse(restoredStorage.pauseOnDisconnect.value)
        assertFalse(restoredStorage.streamCachingEnabled.value)
        assertFalse(restoredStorage.autoDownloadLyrics.value)
        assertEquals("Custom Device", restoredStorage.duoDeviceName.value)
        assertFalse(restoredStorage.liquidGlassEnabled.value)
        assertTrue(restoredStorage.keepScreenOn.value)
    }

    /**
     * Creates an in-memory proxy implementation of [SharedPreferences] and [SharedPreferences.Editor]
     * for deterministic, zero-dependency unit testing.
     */
    private fun createMockSharedPreferences(map: MutableMap<String, Any?>): SharedPreferences {
        val editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
            object : InvocationHandler {
                private val pending = mutableMapOf<String, Any?>()

                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    when (method.name) {
                        "putString" -> {
                            val key = args?.get(0) as String
                            val value = args?.get(1) as? String
                            pending[key] = value
                            return proxy
                        }
                        "putBoolean" -> {
                            val key = args?.get(0) as String
                            val value = args?.get(1) as Boolean
                            pending[key] = value
                            return proxy
                        }
                        "apply", "commit" -> {
                            map.putAll(pending)
                            pending.clear()
                            return if (method.name == "commit") true else Unit
                        }
                        else -> return proxy
                    }
                }
            }
        ) as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    val key = args?.get(0) as String
                    val def = args?.get(1) as? String
                    map[key] as? String ?: def
                }
                "getBoolean" -> {
                    val key = args?.get(0) as String
                    val def = args?.get(1) as Boolean
                    map[key] as? Boolean ?: def
                }
                "edit" -> editorProxy
                "contains" -> {
                    val key = args?.get(0) as String
                    map.containsKey(key)
                }
                else -> null
            }
        } as SharedPreferences
    }
}
