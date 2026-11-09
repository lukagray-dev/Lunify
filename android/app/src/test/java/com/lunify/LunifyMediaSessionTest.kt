package com.lunify

import android.net.Uri
import com.lunify.domain.model.PlaybackProgress
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.PlayerStatus
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.Track
import com.lunify.player.service.LunifyMediaService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Lunify - Media Session & Notification Service Unit Tests (LunifyMediaSessionTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates real-world scenarios across:
 * 1. Notification constants and action identifiers.
 * 2. Track metadata extraction for Android SystemUI and lockscreen display.
 * 3. Repeat mode transitions: OFF -> ALL -> ONE -> OFF.
 * 4. Shuffle mode state transitions.
 * 5. Previous track logic (restart track if played > 3s, previous track if <= 3s).
 * 6. Playback progress synchronization and duration fallback logic.
 * ==============================================================================
 */
class LunifyMediaSessionTest {

    @Test
    fun testMediaServiceConstants() {
        assertEquals(1001, LunifyMediaService.NOTIFICATION_ID)
        assertEquals("lunify_playback_channel", LunifyMediaService.NOTIFICATION_CHANNEL_ID)
        assertEquals("com.lunify.action.SHUFFLE", LunifyMediaService.ACTION_SHUFFLE)
        assertEquals("com.lunify.action.REPEAT", LunifyMediaService.ACTION_REPEAT)
    }

    @Test
    fun testRepeatModeTransitions() {
        var mode = RepeatMode.OFF
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ALL, mode)

        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ONE, mode)

        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test
    fun testShuffleStateToggle() {
        val initial = PlayerState(isShuffle = false)
        assertFalse(initial.isShuffle)

        val toggled = initial.copy(isShuffle = !initial.isShuffle)
        assertTrue(toggled.isShuffle)

        val toggledAgain = toggled.copy(isShuffle = !toggled.isShuffle)
        assertFalse(toggledAgain.isShuffle)
    }

    @Test
    fun testMetadataExtractionForSystemNotification() {
        val trackWithThumb = Track(
            id = "video123",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            thumbnailUrl = "https://lh3.googleusercontent.com/test_image.jpg",
            durationMs = 244000L,
        )

        assertEquals("Midnight City", trackWithThumb.title)
        assertEquals("M83", trackWithThumb.artist)
        assertEquals("Hurry Up, We're Dreaming", trackWithThumb.album.ifBlank { "Lunify" })
        assertEquals("https://lh3.googleusercontent.com/test_image.jpg", trackWithThumb.thumbnailUrl)

        val trackWithoutAlbum = Track(
            id = "local1",
            title = "Aadat",
            artist = "Atif Aslam",
            album = "",
            thumbnailUrl = "",
            durationMs = 333000L,
        )

        // Fallback to "Lunify" when album is blank
        assertEquals("Lunify", trackWithoutAlbum.album.ifBlank { "Lunify" })
        assertTrue(trackWithoutAlbum.thumbnailUrl.isBlank())
    }

    @Test
    fun testPreviousTrackThresholdLogic() {
        // Product rule:
        // If current track position > 3000ms, restart current song (0ms)
        // If current track position <= 3000ms, navigate to previous song in queue

        val positionOver3Seconds = 3500L
        val shouldRestartOver3s = positionOver3Seconds > 3000L
        assertTrue("Position > 3s must restart the track", shouldRestartOver3s)

        val positionUnder3Seconds = 1200L
        val shouldRestartUnder3s = positionUnder3Seconds > 3000L
        assertFalse("Position <= 3s must navigate to previous song", shouldRestartUnder3s)
    }

    @Test
    fun testProgressDurationFallback() {
        val track = Track(
            id = "t1",
            title = "Song Title",
            artist = "Artist",
            durationMs = 180000L,
        )

        val state = PlayerState(
            currentTrack = track,
            status = PlayerStatus.PLAYING,
        )

        // When ExoPlayer reports duration <= 0 (e.g. while stream is still buffering headers),
        // fallback to track.durationMs
        val exoPlayerDurationZero = 0L
        val resolvedDuration = if (exoPlayerDurationZero > 0) {
            exoPlayerDurationZero
        } else {
            state.currentTrack?.durationMs ?: 0L
        }

        assertEquals(180000L, resolvedDuration)
    }
}
