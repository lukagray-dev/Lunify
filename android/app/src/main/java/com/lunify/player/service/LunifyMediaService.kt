package com.lunify.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.lunify.LunifyApplication
import com.lunify.MainActivity
import com.lunify.R
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Lunify - Media Session Service (LunifyMediaService.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the foreground playback service of Lunify.
 *
 * WHAT DOES THIS SERVICE DO?
 * --------------------------
 * 1. Android Media3 Integration:
 *    Hosts the active [MediaSession] linked directly to [AudioPlayerManager.exoPlayer].
 * 2. System Media Notification:
 *    Automatically builds and manages the system media notification (using Android 13+
 *    System UI MediaStyle with interactive waveform seekbar, and [DefaultMediaNotificationProvider]
 *    for Android 12 and below).
 * 3. Transport Controls:
 *    Synchronizes Play/Pause, Next, Previous, Shuffle, Repeat, and Seek scrubber actions
 *    between the device notification / lock screen / Bluetooth accessories and Lunify.
 * 4. Background Playback Resilience:
 *    Runs as a foreground service with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` to ensure
 *    the Android OS never terminates audio playback when the app is backgrounded or
 *    the device screen is locked.
 * ==============================================================================
 */
@OptIn(UnstableApi::class)
class LunifyMediaService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null

    companion object {
        private const val TAG = "LunifyMediaService"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "lunify_playback_channel"
        const val ACTION_SHUFFLE = "com.lunify.action.SHUFFLE"
        const val ACTION_REPEAT = "com.lunify.action.REPEAT"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LunifyMediaService onCreate")

        val app = application as LunifyApplication
        val audioManager = app.audioPlayerManager

        // 1. Create Android Notification Channel (Low importance = no intrusive sound alerts on song change)
        createNotificationChannel()

        // 2. Configure DefaultMediaNotificationProvider
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build().apply {
                setSmallIcon(R.drawable.ic_lucide_brand_logo)
            }
        setMediaNotificationProvider(notificationProvider)

        // 3. PendingIntent to reopen MainActivity when notification is tapped
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // 4. Build BitmapLoader with Coil for loading artwork safely
        val bitmapLoader = CoilBitmapLoader(
            this,
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        // 5. Wrap ExoPlayer in LunifyForwardingPlayer so COMMAND_SEEK_TO_NEXT is always available to SystemUI
        val forwardingPlayer = LunifyForwardingPlayer(audioManager.exoPlayer, audioManager)
        val callback = LunifyMediaSessionCallback(audioManager)
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setId("LunifyMediaSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .setBitmapLoader(bitmapLoader)
            .build()

        // 6. Listen to PlayerState to keep Shuffle, Repeat, and Media Buttons in sync
        serviceScope.launch {
            audioManager.playerState.collect { state ->
                updateMediaButtonPreferences(state)
            }
        }

        // 7. Establish internal controller connection to immediately activate MediaNotificationManager
        val sessionToken = SessionToken(this, ComponentName(this, LunifyMediaService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                controllerFuture.get()
                Log.d(TAG, "Internal MediaController successfully connected")
            } catch (e: Exception) {
                Log.w(TAG, "Internal MediaController connection failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LunifyMediaService onStartCommand action=${intent?.action}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    /**
     * Updates notification button preferences for Shuffle and Repeat states.
     * Android 13+ (API 33+) SystemUI maps these to the custom action slots in the
     * notification shade and lockscreen controls.
     */
    private fun updateMediaButtonPreferences(state: PlayerState) {
        val shuffleIcon = if (state.isShuffle) {
            CommandButton.ICON_SHUFFLE_ON
        } else {
            CommandButton.ICON_SHUFFLE_OFF
        }
        val shuffleButton = CommandButton.Builder(shuffleIcon)
            .setDisplayName(if (state.isShuffle) "Shuffle On" else "Shuffle Off")
            .setCustomIconResId(R.drawable.ic_lucide_shuffle)
            .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle()))
            .setEnabled(true)
            .build()

        val (repeatIcon, repeatName) = when (state.repeatMode) {
            RepeatMode.ONE -> Pair(CommandButton.ICON_REPEAT_ONE, "Repeat One")
            RepeatMode.ALL -> Pair(CommandButton.ICON_REPEAT_ALL, "Repeat All")
            RepeatMode.OFF -> Pair(CommandButton.ICON_REPEAT_OFF, "Repeat Off")
        }
        val repeatButton = CommandButton.Builder(repeatIcon)
            .setDisplayName(repeatName)
            .setCustomIconResId(R.drawable.ic_lucide_repeat)
            .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle()))
            .setEnabled(true)
            .build()

        val customButtons = listOf(shuffleButton, repeatButton)
        mediaSession?.setCustomLayout(customButtons)
        mediaSession?.setMediaButtonPreferences(customButtons)
    }

    /**
     * Creates the Android notification channel with IMPORTANCE_LOW to prevent
     * noisy sound interruptions whenever the song changes.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = mediaSession?.player
        // If not playing, or track has ended, cleanly stop the foreground service
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "LunifyMediaService onDestroy")
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
