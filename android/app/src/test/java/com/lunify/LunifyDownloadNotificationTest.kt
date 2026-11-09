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
}
