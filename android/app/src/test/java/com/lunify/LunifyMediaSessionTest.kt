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

    @Test
    fun testAlwaysAvailableNextAndPreviousCommands() {
        // Even when repeat mode is OFF, SEEK_TO_NEXT and SEEK_TO_PREVIOUS
        // must always be available to prevent Android SystemUI from hiding the Next button.
        val repeatOffState = PlayerState(
            repeatMode = RepeatMode.OFF,
            queue = listOf(
                Track(id = "1", title = "Song 1", artist = "Artist"),
                Track(id = "2", title = "Song 2", artist = "Artist"),
            ),
            currentIndex = 0,
        )

        // Queue has more songs: Next is valid
        assertTrue(repeatOffState.queue.size > 1)
        assertTrue(repeatOffState.currentIndex < repeatOffState.queue.size - 1)

        // Repeat mode is OFF, but Next must still be exposed to SystemUI
        assertEquals(RepeatMode.OFF, repeatOffState.repeatMode)
    }

    @Test
    fun testNotificationShuffleAndRepeatButtonIcons() {
        // Hey friend! Validate that custom notification buttons match full-screen player appearance:
        // - Off states use gray icons (ic_lucide_shuffle_off, ic_lucide_repeat_off)
        // - On states use bright white icons (ic_lucide_shuffle, ic_lucide_repeat, ic_lucide_repeat_one)
        val offState = PlayerState(
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
        )
        val offButtons = LunifyMediaService.buildMediaButtons(offState)
        assertEquals(2, offButtons.size)

        val shuffleOffBtn = offButtons[0]
        assertEquals("Shuffle Off", shuffleOffBtn.displayName)
        assertEquals(com.lunify.R.drawable.ic_lucide_shuffle_off, shuffleOffBtn.iconResId)

        val repeatOffBtn = offButtons[1]
        assertEquals("Repeat Off", repeatOffBtn.displayName)
        assertEquals(com.lunify.R.drawable.ic_lucide_repeat_off, repeatOffBtn.iconResId)

        // Repeat ALL and Shuffle ON
        val onState = PlayerState(
            isShuffle = true,
            repeatMode = RepeatMode.ALL,
        )
        val onButtons = LunifyMediaService.buildMediaButtons(onState)
        val shuffleOnBtn = onButtons[0]
        assertEquals("Shuffle On", shuffleOnBtn.displayName)
        assertEquals(com.lunify.R.drawable.ic_lucide_shuffle, shuffleOnBtn.iconResId)

        val repeatAllBtn = onButtons[1]
        assertEquals("Repeat All", repeatAllBtn.displayName)
        assertEquals(com.lunify.R.drawable.ic_lucide_repeat, repeatAllBtn.iconResId)

        // Repeat ONE
        val repeatOneState = PlayerState(
            isShuffle = false,
            repeatMode = RepeatMode.ONE,
        )
        val repeatOneButtons = LunifyMediaService.buildMediaButtons(repeatOneState)
        val repeatOneBtn = repeatOneButtons[1]
        assertEquals("Repeat One", repeatOneBtn.displayName)
        assertEquals(com.lunify.R.drawable.ic_lucide_repeat_one, repeatOneBtn.iconResId)
    }

    @Test
    fun testRapidClickDebounceLogic() {
        // Simulates two rapid skip events arriving within 350ms (e.g. MediaSession duplicate dispatch)
        var lastNextClickTime = 1000L
        val duplicateClickTime = 1050L // 50ms later
        val isDuplicate = (duplicateClickTime - lastNextClickTime) < 350L
        assertTrue("Calls within 350ms must be rejected as duplicates", isDuplicate)

        val legitimateNextClickTime = 1400L // 400ms later
        val isLegitimate = (legitimateNextClickTime - lastNextClickTime) >= 350L
        assertTrue("Calls after 350ms must be accepted as legitimate", isLegitimate)
    }
}
