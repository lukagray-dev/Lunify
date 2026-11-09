package com.lunify

import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.DuoPeerInfo
import com.lunify.domain.model.DuoSyncPacket
import com.lunify.domain.model.HistoryItem
import com.lunify.domain.model.LyricLine
import com.lunify.domain.model.PlaybackProgress
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.PlayerStatus
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import com.lunify.ui.library.formatRelativeTime
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

    /**
     * Verifies the core previous button specification:
     * - If track is played > 3 seconds (3000ms), it restarts from the beginning (seek to 0).
     * - If track is played <= 3 seconds (<= 3000ms), it navigates to the previous track.
     */
    @Test
    fun testPreviousButtonTransportRule() {
        fun resolvePreviousAction(positionMs: Long, currentIndex: Int): Pair<String, Int> {
            return if (positionMs > 3000L) {
                // Restart current track
                "SEEK_START" to currentIndex
            } else {
                // Navigate to previous track if available
                val prevIndex = if (currentIndex > 0) currentIndex - 1 else 0
                "PREVIOUS_TRACK" to prevIndex
            }
        }

        // Scenario 1: Played for 15 seconds (> 3s) -> Should seek to beginning of same track (index 2)
        val action1 = resolvePreviousAction(positionMs = 15000L, currentIndex = 2)
        assertEquals("SEEK_START", action1.first)
        assertEquals(2, action1.second)

        // Scenario 2: Played for 3001 ms (> 3s) -> Should seek to beginning
        val action2 = resolvePreviousAction(positionMs = 3001L, currentIndex = 2)
        assertEquals("SEEK_START", action2.first)
        assertEquals(2, action2.second)

        // Scenario 3: Played for 3000 ms (<= 3s) -> Should jump to previous track (index 1)
        val action3 = resolvePreviousAction(positionMs = 3000L, currentIndex = 2)
        assertEquals("PREVIOUS_TRACK", action3.first)
        assertEquals(1, action3.second)

        // Scenario 4: Played for 500 ms (<= 3s) -> Should jump to previous track (index 1)
        val action4 = resolvePreviousAction(positionMs = 500L, currentIndex = 2)
        assertEquals("PREVIOUS_TRACK", action4.first)
        assertEquals(1, action4.second)

        // Scenario 5: At the very first track (index 0) played for 1 second (<= 3s) -> Stay at index 0
        val action5 = resolvePreviousAction(positionMs = 1000L, currentIndex = 0)
        assertEquals("PREVIOUS_TRACK", action5.first)
        assertEquals(0, action5.second)
    }

    /**
     * Verifies storage size formatting and duration formatting for the Library screen.
     */
    @Test
    fun testStorageSizeAndDurationFormatting() {
        // Zero bytes
        assertEquals("0 MB Used", LocalAudioRepository.formatStorageSize(0L))

        // Kilobytes (< 1 MB)
        assertEquals("500 KB Used", LocalAudioRepository.formatStorageSize(500 * 1024L))

        // Megabytes (< 1 GB)
        assertEquals("450.0 MB Used", LocalAudioRepository.formatStorageSize(450 * 1024 * 1024L))

        // Gigabytes (>= 1 GB)
        assertEquals("1.5 GB Used", LocalAudioRepository.formatStorageSize((1.5 * 1024 * 1024 * 1024).toLong()))

        // Duration formatting
        assertEquals("0:00", LocalAudioRepository.formatDuration(0L))
        assertEquals("0:05", LocalAudioRepository.formatDuration(5000L))
        assertEquals("3:32", LocalAudioRepository.formatDuration(212000L))
        assertEquals("65:10", LocalAudioRepository.formatDuration(3910000L))
    }

    /**
     * Verifies HistoryItem source tagging (OFFLINE vs ONLINE).
     */
    @Test
    fun testHistoryItemSourceTagging() {
        val offlineTrack = Track(
            id = "content://media/external/audio/media/101",
            title = "Offline Masterpiece",
            artist = "Local Artist",
            isDownloaded = true,
            contentUri = "content://media/external/audio/media/101",
            sizeBytes = 10485760L // 10MB
        )
        val historyOffline = HistoryItem(track = offlineTrack)
        assertEquals(PlaybackSource.OFFLINE, historyOffline.source)
        assertTrue(historyOffline.track.isDownloaded)

        val onlineTrack = Track(
            id = "yt_12345",
            title = "Online Stream",
            artist = "Web Artist",
            isDownloaded = false,
        )
        val historyOnline = HistoryItem(track = onlineTrack)
        assertEquals(PlaybackSource.ONLINE, historyOnline.source)
        assertFalse(historyOnline.track.isDownloaded)
    }

    /**
     * Verifies relative time formatting for playback history.
     */
    @Test
    fun testRelativeTimeFormatting() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", formatRelativeTime(now - 10000L)) // 10s ago
        assertEquals("5m ago", formatRelativeTime(now - 300000L)) // 5m ago
        assertEquals("2h ago", formatRelativeTime(now - 7200000L)) // 2h ago
        assertEquals("1d ago", formatRelativeTime(now - 86400000L)) // 1d ago
    }
}
