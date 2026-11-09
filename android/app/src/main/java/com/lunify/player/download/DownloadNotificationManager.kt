package com.lunify.player.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lunify.MainActivity
import com.lunify.R
import com.lunify.domain.model.Track
import java.util.Locale

/**
 * ==============================================================================
 * Lunify - Download Notification Manager (DownloadNotificationManager.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the download notification controller for Lunify!
 *
 * WHY DO WE NEED A DEDICATED DOWNLOAD NOTIFICATION MANAGER?
 * --------------------------------------------------------
 * When a user taps the download icon on a song, they might lock their phone, switch
 * to another app, or browse other playlists. Having a reliable, system-level notification
 * gives the user immediate peace of mind and live transparency:
 *
 * 1. Low-Interruption Notification Channel:
 *    We create a separate channel [CHANNEL_ID] configured with [NotificationManager.IMPORTANCE_LOW].
 *    Why? Because progress bar notifications update frequently (as chunks of bytes arrive).
 *    If the channel had default or high importance, the phone would vibrate or chime on
 *    every single percentage change! Low importance keeps it completely silent in the shade.
 *
 * 2. Visual States:
 *    - In Progress: Ongoing notification displaying a determinate progress bar (0% - 100%)
 *      with human-readable byte sizes (e.g., "2.4 MB / 5.1 MB • 47%"), or a smooth
 *      indeterminate progress bar if the server didn't specify Content-Length.
 *    - Completed: Non-ongoing notification marked with a checkmark drawable, letting the
 *      user know their music is ready for offline listening. It can be swiped away freely.
 *    - Failed: Non-ongoing notification with an error icon if network or storage fails.
 *
 * 3. Safe Re-entry:
 *    Tapping any download notification opens [MainActivity] without clearing or resetting
 *    active music playback.
 *
 * 4. Android 13+ (API 33+) Permission Guard:
 *    Safely checks [Manifest.permission.POST_NOTIFICATIONS] prior to posting to avoid
 *    any runtime [SecurityException] crashes if permissions were revoked.
 * ==============================================================================
 */
open class DownloadNotificationManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "DownloadNotification"

        // Unique identifier for the downloads notification channel
        const val CHANNEL_ID = "lunify_download_channel"

        // Base ID offset to prevent collisions with playback notification ID (1001)
        const val BASE_NOTIFICATION_OFFSET = 10000

        /**
         * Calculates a deterministic, positive integer notification ID for a given track ID.
         * Using the hash code with a modulo offset guarantees that each track gets its own
         * independent notification slot without colliding with playback controls.
         */
        fun getNotificationId(trackId: String): Int {
            return (trackId.hashCode() and 0x7FFFFFFF) % 100000 + BASE_NOTIFICATION_OFFSET
        }

        /**
         * Formats raw byte counts into human-readable strings like "1.2 MB" or "450 KB".
         */
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups >= units.size) digitGroups = units.size - 1
            if (digitGroups <= 0) return "$bytes B"
            val value = bytes.toDouble() / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    init {
        createChannelIfNeeded()
    }

    /**
     * Registers our notification channel with Android's NotificationManager on API 26+.
     */
    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (systemManager != null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.download_channel_description)
                    setShowBadge(false)
                }
                systemManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Builds a PendingIntent that opens MainActivity when the user taps the notification.
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Checks if the app has permission to post notifications on Android 13+ (API 33+).
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Displays or updates the active download notification for a track.
     *
     * @param track The song being downloaded.
     * @param progressPercent Current progress percentage (0 to 100), or -1 for indeterminate.
     * @param downloadedBytes Bytes written to disk so far.
     * @param totalBytes Expected total file size in bytes, or -1 if unknown.
     */
    open fun showDownloadProgress(
        track: Track,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
    ) {
        if (!hasNotificationPermission()) {
            return
        }

        val notificationId = getNotificationId(track.id)
        val isIndeterminate = progressPercent < 0 || totalBytes <= 0L

        // Compose detailed status text: e.g. "Adele • 2.4 MB / 5.1 MB (47%)"
        val statusText = if (isIndeterminate) {
            if (downloadedBytes > 0L) {
                "${track.artist} • ${formatBytes(downloadedBytes)}"
            } else {
                "${track.artist} • ${context.getString(R.string.download_notification_downloading)}"
            }
        } else {
            val downloadedStr = formatBytes(downloadedBytes)
            val totalStr = formatBytes(totalBytes)
            "${track.artist} • $downloadedStr / $totalStr ($progressPercent%)"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lucide_download)
            .setContentTitle(track.title)
            .setContentText(statusText)
            .setSubText(context.getString(R.string.download_notification_downloading))
            .setContentIntent(createContentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (isIndeterminate) 0 else progressPercent.coerceIn(0, 100), isIndeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update download notification: ${e.message}")
        }
    }

    /**
     * Transitions the notification to the completed state with a checkmark icon,
     * removing the progress bar and making it swipeable.
     *
     * @param track The song that completed downloading.
     */
    open fun showDownloadComplete(track: Track) {
        if (!hasNotificationPermission()) {
            return
        }

        val notificationId = getNotificationId(track.id)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lucide_check)
            .setContentTitle(track.title)
            .setContentText(context.getString(R.string.download_notification_completed))
            .setSubText(track.artist)
            .setContentIntent(createContentIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update download complete notification: ${e.message}")
        }
    }

    /**
     * Transitions the notification to the failed state with an error icon,
     * removing the progress bar and making it swipeable.
     *
     * @param track The song that failed to download.
     */
    open fun showDownloadFailed(track: Track) {
        if (!hasNotificationPermission()) {
            return
        }

        val notificationId = getNotificationId(track.id)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lucide_close)
            .setContentTitle(track.title)
            .setContentText(context.getString(R.string.download_notification_failed))
            .setSubText(track.artist)
            .setContentIntent(createContentIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update download failed notification: ${e.message}")
        }
    }

    /**
     * Dismisses the notification for the given track ID.
     */
    open fun cancel(trackId: String) {
        try {
            notificationManager.cancel(getNotificationId(trackId))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel notification: ${e.message}")
        }
    }
}
