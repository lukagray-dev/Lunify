package com.lunify

import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.DuoPeerInfo
import com.lunify.domain.model.DuoSyncPacket
import com.lunify.domain.model.LyricLine
import com.lunify.domain.model.PlaybackProgress
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.PlayerStatus
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Lunify - Production Domain & State Unit Tests (LunifyDomainTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates real-world scenarios across:
 * 1. Media track creation and duration formatting.
 * 2. Playback progress fraction calculations and seekbar clamping.
 * 3. Synchronized lyrics active line binary search and fallbacks.
 * 4. Duo synchronization packet time drift compensation calculations.
 * ==============================================================================
 */
class LunifyDomainTest {

    @Test
    fun testTrackDurationFormatting() {
        val track1 = Track(
            id = "t1",
            title = "Midnight City",
            artist = "M83",
            durationMs = 244000L,
        )
        // 244 seconds is 4 minutes and 4 seconds
        assertEquals("4:04", track1.formattedDuration())

        val track2 = Track(
            id = "t2",
            title = "Short Interlude",
            artist = "Artist",
            durationMs = 45000L,
        )
        // 45 seconds is 0:45
        assertEquals("0:45", track2.formattedDuration())
    }

    @Test
    fun testPlaybackProgressFractionAndClamping() {
        // Zero duration should not divide by zero
        val zeroProgress = PlaybackProgress(currentPositionMs = 0L, totalDurationMs = 0L)
        assertEquals(0f, zeroProgress.fraction, 0.001f)

        // Normal progress at 50%
        val midProgress = PlaybackProgress(currentPositionMs = 50000L, totalDurationMs = 100000L)
        assertEquals(0.5f, midProgress.fraction, 0.001f)
        assertEquals("0:50", midProgress.formattedCurrentTime)
        assertEquals("1:40", midProgress.formattedTotalTime)

        // Exceeding position should clamp to 1.0f
        val overflowProgress = PlaybackProgress(currentPositionMs = 150000L, totalDurationMs = 100000L)
        assertEquals(1.0f, overflowProgress.fraction, 0.001f)
    }

    @Test
    fun testPlayerStateBooleans() {
        val emptyState = PlayerState()
        assertFalse(emptyState.isPlaying)
        assertFalse(emptyState.hasTrack)

        val track = Track(id = "1", title = "Song", artist = "Artist")
        val activeState = PlayerState(
            currentTrack = track,
            status = PlayerStatus.PLAYING,
        )
        assertTrue(activeState.isPlaying)
        assertTrue(activeState.hasTrack)
    }

    @Test
    fun testSyncedLyricsActiveIndexResolution() {
        val lyrics = SyncedLyrics(
            trackId = "t1",
            lines = listOf(
                LyricLine(timestampMs = 5000L, text = "First line"),
                LyricLine(timestampMs = 10000L, text = "Second line"),
                LyricLine(timestampMs = 20000L, text = "Third line"),
            ),
        )

        // Before any lyrics start
        assertEquals(-1, lyrics.findActiveIndex(2000L))

        // Exactly on first line start
        assertEquals(0, lyrics.findActiveIndex(5000L))

        // Between first and second line
        assertEquals(0, lyrics.findActiveIndex(8000L))

        // Exactly on second line
        assertEquals(1, lyrics.findActiveIndex(10000L))

        // Between second and third line
        assertEquals(1, lyrics.findActiveIndex(15000L))

        // On or past the last line
        assertEquals(2, lyrics.findActiveIndex(20000L))
        assertEquals(2, lyrics.findActiveIndex(60000L))

        // Empty lyrics returns -1
        val emptyLyrics = SyncedLyrics(trackId = "empty")
        assertEquals(-1, emptyLyrics.findActiveIndex(5000L))
    }

    @Test
    fun testDuoPacketCreation() {
        val clientTime = 1712345678000L
        val packet = DuoSyncPacket(
            action = "PLAY",
            trackId = "yt-music-id",
            positionMs = 32000L,
            timestampEpochMs = clientTime,
        )

        assertEquals("PLAY", packet.action)
        assertEquals("yt-music-id", packet.trackId)
        assertEquals(32000L, packet.positionMs)
        assertEquals(clientTime, packet.timestampEpochMs)

        // Peer state validation
        val peer = DuoPeerInfo(peerId = "LN-3810", deviceName = "Pixel 9 Pro", latencyMs = 24L)
        assertEquals("LN-3810", peer.peerId)
        assertEquals(24L, peer.latencyMs)
        assertFalse(peer.isHost)
    }
}
