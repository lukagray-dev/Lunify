package com.lunify

import com.lunify.domain.model.Track
import com.lunify.player.download.DownloadNotificationManager
import com.lunify.player.service.LunifyMediaService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Lunify - Download Notification Unit Tests (LunifyDownloadNotificationTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates real-world download notification logic:
 * 1. Deterministic notification ID generation that stays strictly positive and
 *    isolated from the playback foreground service notification ID (1001).
 * 2. Human-readable byte formatting across B, KB, MB, and GB boundaries.
 * 3. Channel configuration constants.
 * 4. Percentage progress clamping and throttling invariants.
 * ==============================================================================
 */
class LunifyDownloadNotificationTest {

    @Test
    fun testNotificationChannelConstants() {
        assertEquals("lunify_download_channel", DownloadNotificationManager.CHANNEL_ID)
        assertEquals(10000, DownloadNotificationManager.BASE_NOTIFICATION_OFFSET)
    }

    @Test
    fun testDeterministicNotificationIds() {
        val id1 = DownloadNotificationManager.getNotificationId("track_abc_123")
        val id2 = DownloadNotificationManager.getNotificationId("track_abc_123")
        val id3 = DownloadNotificationManager.getNotificationId("track_xyz_789")

        // Same track must always produce the same notification ID
        assertEquals(id1, id2)

        // Different tracks should have distinct notification IDs
        assertNotEquals(id1, id3)

        // Notification IDs must be positive and strictly greater than playback notification ID
        assertTrue(id1 >= DownloadNotificationManager.BASE_NOTIFICATION_OFFSET)
        assertTrue(id3 >= DownloadNotificationManager.BASE_NOTIFICATION_OFFSET)
        assertNotEquals(LunifyMediaService.NOTIFICATION_ID, id1)
        assertNotEquals(LunifyMediaService.NOTIFICATION_ID, id3)
    }

    @Test
    fun testFormatBytesFormatting() {
        // Zero and negative edge cases
        assertEquals("0 B", DownloadNotificationManager.formatBytes(0L))
        assertEquals("0 B", DownloadNotificationManager.formatBytes(-500L))

        // Raw bytes
        assertEquals("512 B", DownloadNotificationManager.formatBytes(512L))
        assertEquals("1000 B", DownloadNotificationManager.formatBytes(1000L))

        // Kilobytes (1024 bytes = 1.0 KB)
        assertEquals("1.0 KB", DownloadNotificationManager.formatBytes(1024L))
        assertEquals("500.0 KB", DownloadNotificationManager.formatBytes(500L * 1024L))

        // Megabytes
        assertEquals("1.0 MB", DownloadNotificationManager.formatBytes(1024L * 1024L))
        assertEquals("4.5 MB", DownloadNotificationManager.formatBytes((4.5 * 1024.0 * 1024.0).toLong()))

        // Gigabytes
        assertEquals("1.0 GB", DownloadNotificationManager.formatBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun testProgressPercentageClamping() {
        val totalBytes = 10_000_000L // 10 MB

        // 0%
        val zeroPercent = ((0L * 100) / totalBytes).toInt().coerceIn(0, 100)
        assertEquals(0, zeroPercent)

        // 50%
        val midPercent = ((5_000_000L * 100) / totalBytes).toInt().coerceIn(0, 100)
        assertEquals(50, midPercent)

        // 100%
        val completePercent = ((10_000_000L * 100) / totalBytes).toInt().coerceIn(0, 100)
        assertEquals(100, completePercent)

        // Overflow clamp to 100%
        val overflowPercent = ((15_000_000L * 100) / totalBytes).toInt().coerceIn(0, 100)
        assertEquals(100, overflowPercent)
    }

    @Test
    fun testDownloadProgressNotificationStrings() {
        val track = Track(
            id = "download_track_1",
            title = "Shape of You",
            artist = "Ed Sheeran",
            durationMs = 233000L,
        )

        val downloadedBytes = 2_500_000L
        val totalBytes = 5_000_000L
        val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)

        val downloadedStr = DownloadNotificationManager.formatBytes(downloadedBytes)
        val totalStr = DownloadNotificationManager.formatBytes(totalBytes)
        val composedText = "${track.artist} • $downloadedStr / $totalStr ($percent%)"

        assertEquals("Ed Sheeran • 2.4 MB / 4.8 MB (50%)", composedText)
    }

    @Test
    fun testIsInProgressDownloadInvariants() {
        // Hey friend! Valid in-progress percentages are between 0% and 99%,
        // or negative values (-1) for indeterminate streams where total size is unknown.
        assertTrue(DownloadNotificationManager.isInProgressDownload(0))
        assertTrue(DownloadNotificationManager.isInProgressDownload(1))
        assertTrue(DownloadNotificationManager.isInProgressDownload(50))
        assertTrue(DownloadNotificationManager.isInProgressDownload(99))
        assertTrue(DownloadNotificationManager.isInProgressDownload(-1))

        // 100% or above means the bytes are fully delivered; it MUST NOT be treated
        // as an in-progress state to avoid race conditions with the completion notification!
        org.junit.Assert.assertFalse(DownloadNotificationManager.isInProgressDownload(100))
        org.junit.Assert.assertFalse(DownloadNotificationManager.isInProgressDownload(101))
    }

    @Test
    fun testShouldDispatchProgressUpdateThrottling() {
        val baseTime = 1_000_000L

        // 1. Initial notification: first report starting from uninitialized (-1) should dispatch immediately
        assertTrue(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 0,
                lastNotifiedPercent = -1,
                currentTimeMs = baseTime,
                lastNotifiedTimeMs = baseTime,
            )
        )

        // 2. Small jump (1%) within a short interval (100ms < 400ms): should be throttled (returns false)
        org.junit.Assert.assertFalse(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 1,
                lastNotifiedPercent = 0,
                currentTimeMs = baseTime + 100L,
                lastNotifiedTimeMs = baseTime,
            )
        )

        // 3. Jump of 2% or more even if within 100ms: should dispatch (returns true)
        assertTrue(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 2,
                lastNotifiedPercent = 0,
                currentTimeMs = baseTime + 100L,
                lastNotifiedTimeMs = baseTime,
            )
        )

        // 4. Jump of only 1% but 450ms have elapsed (>= 400ms time threshold): should dispatch
        assertTrue(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 3,
                lastNotifiedPercent = 2,
                currentTimeMs = baseTime + 550L,
                lastNotifiedTimeMs = baseTime + 100L,
            )
        )

        // 5. Same percent: must never re-dispatch
        org.junit.Assert.assertFalse(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 50,
                lastNotifiedPercent = 50,
                currentTimeMs = baseTime + 2000L,
                lastNotifiedTimeMs = baseTime,
            )
        )
    }

    @Test
    fun testShouldDispatchProgressUpdateRejectsHundredPercent() {
        val baseTime = 1_000_000L

        // 100% must ALWAYS return false regardless of delta or time elapsed!
        // This guarantees that the notification never locks in the 100% ongoing progressbar state.
        org.junit.Assert.assertFalse(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 100,
                lastNotifiedPercent = 98,
                currentTimeMs = baseTime + 1000L,
                lastNotifiedTimeMs = baseTime,
            )
        )

        org.junit.Assert.assertFalse(
            DownloadNotificationManager.shouldDispatchProgressUpdate(
                currentPercent = 100,
                lastNotifiedPercent = -1,
                currentTimeMs = baseTime + 1000L,
                lastNotifiedTimeMs = baseTime,
            )
        )
    }

    @Test
    fun testSimulatedDownloadNotificationSequence() {
        // Hey friend! Let's simulate an entire song download sequence with 20 chunks
        // arriving rapidly from a 20 MB/s connection.
        val totalBytes = 5_000_000L // 5 MB track
        val chunkSize = 250_000L // 250 KB per chunk (20 chunks total)
        var simulatedDownloadedBytes = 0L
        var simulatedTimeMs = 1_000_000L

        var lastNotifiedPercent = -1
        var lastNotifiedTimeMs = 0L
        val dispatchedProgressList = mutableListOf<Int>()

        while (simulatedDownloadedBytes < totalBytes) {
            simulatedDownloadedBytes += chunkSize
            simulatedTimeMs += 50L // 50ms per chunk transfer

            val percent = ((simulatedDownloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)

            if (DownloadNotificationManager.shouldDispatchProgressUpdate(
                    currentPercent = percent,
                    lastNotifiedPercent = lastNotifiedPercent,
                    currentTimeMs = simulatedTimeMs,
                    lastNotifiedTimeMs = lastNotifiedTimeMs,
                )
            ) {
                lastNotifiedPercent = percent
                lastNotifiedTimeMs = simulatedTimeMs
                dispatchedProgressList.add(percent)
            }
        }

        // Verify that progress notifications were sent during downloading
        assertTrue(dispatchedProgressList.isNotEmpty())

        // Crucial invariant: 100% must NEVER have been dispatched as an ongoing progress notification!
        org.junit.Assert.assertFalse(dispatchedProgressList.contains(100))

        // All dispatched updates must strictly be in 0..99
        assertTrue(dispatchedProgressList.all { it in 0..99 })

        // The last dispatched progress update should be near the end of the download (e.g. 95%+)
        val highestProgressDispatched = dispatchedProgressList.maxOrNull() ?: 0
        assertTrue("Highest progress dispatched was $highestProgressDispatched", highestProgressDispatched >= 90)
    }
}
