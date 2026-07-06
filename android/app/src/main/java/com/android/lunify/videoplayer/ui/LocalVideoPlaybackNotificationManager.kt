package com.android.lunify.videoplayer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.lunify.R
import com.android.lunify.data.model.Video
import java.util.ArrayList

/**
 * Posts a lightweight ongoing notification for local video playback.
 *
 * The notification stays in sync with the shared local video session so users can
 * return to the player after leaving the playback screen.
 */
internal object LocalVideoPlaybackNotificationManager {

    private const val CHANNEL_ID = "local_video_playback_channel"
    private const val CHANNEL_NAME = "Local Video Playback"
    private const val NOTIFICATION_ID = 6202

    fun show(
        context: Context,
        video: Video,
        playlist: List<Video>,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_video)
            .setContentTitle(video.title)
            .setContentText(if (isPlaying) "Playing video" else "Video paused")
            .setContentIntent(createContentIntent(appContext, video, playlist, positionMs))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)

        if (durationMs > 0L) {
            builder.setProgress(
                durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                false
            )
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channelExists = notificationManager.getNotificationChannel(CHANNEL_ID) != null
        if (!channelExists) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for local video playback"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createContentIntent(
        context: Context,
        video: Video,
        playlist: List<Video>,
        positionMs: Long
    ): PendingIntent {
        val launchIntent = Intent(context, LocalVideoPlayerActivity::class.java).apply {
            putExtra(LocalVideoPlayerActivity.EXTRA_VIDEO, video)
            putParcelableArrayListExtra(
                LocalVideoPlayerActivity.EXTRA_VIDEO_LIST,
                ArrayList(playlist.ifEmpty { listOf(video) })
            )
            putExtra(LocalVideoPlayerActivity.EXTRA_START_POSITION, positionMs.coerceAtLeast(0L))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
