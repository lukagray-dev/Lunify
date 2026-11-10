package com.lunify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.player.AudioPlayerManager
import com.lunify.player.duo.DuoManager
import com.lunify.player.service.LunifyMediaService
import com.lunify.ui.LunifyApp
import com.lunify.ui.theme.LunifyTheme

/**
 * ==============================================================================
 * Lunify - Main Activity Entrypoint (MainActivity.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the primary visual entry point of Lunify.
 *
 * ARCHITECTURAL DESIGN & LIFECYCLE:
 * 1. `enableEdgeToEdge()`: Instructs Android to draw behind system bars (status bar
 *    and navigation bar), allowing our liquid glass background glows and floating
 *    navigation capsule to achieve seamless cinematic immersion.
 * 2. Application-Scope Singletons: Retrieves [LibraryStorage], [LocalAudioRepository],
 *    and the primary [AudioPlayerManager] singleton directly from [LunifyApplication].
 *    This ensures playback is uninterrupted when the activity is recreated.
 * 3. Android 13+ Notification Permission: Prompts for [Manifest.permission.POST_NOTIFICATIONS]
 *    on API 33+ so our media playback notification displays immediately in the
 *    notification shade and lock screen.
 * 4. Background Playback Persistence: Unlike standard apps that release audio in
 *    [onDestroy], we preserve [AudioPlayerManager] across activity teardowns so
 *    users can listen to music continuously while using other apps. Audio hardware
 *    resources are cleanly released in [LunifyMediaService] when playback finishes.
 * ==============================================================================
 */
class MainActivity : ComponentActivity() {

    private lateinit var libraryStorage: LibraryStorage
    private lateinit var audioPlayerManager: AudioPlayerManager
    private lateinit var localAudioRepository: LocalAudioRepository
    private lateinit var duoManager: DuoManager

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Enable edge-to-edge window drawing
        enableEdgeToEdge()

        // 2. Retrieve application-scoped singletons
        val app = application as LunifyApplication
        libraryStorage = app.libraryStorage
        audioPlayerManager = app.audioPlayerManager
        localAudioRepository = app.localAudioRepository
        duoManager = app.duoManager

        // 3. Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        requestNotificationPermissionIfNeeded()

        // 4. Mount Compose UI tree
        setContent {
            LunifyTheme {
                LunifyApp(
                    audioPlayerManager = audioPlayerManager,
                    libraryStorage = libraryStorage,
                    localAudioRepository = localAudioRepository,
                    duoManager = duoManager,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Ensure playback service is ready and bound
        startPlaybackService()
    }

    /**
     * Starts [LunifyMediaService] to ensure MediaSession and notification
     * controls are active.
     */
    private fun startPlaybackService() {
        try {
            val intent = Intent(this, LunifyMediaService::class.java)
            startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start LunifyMediaService on start: ${e.message}")
        }
    }

    /**
     * Requests POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
     * to ensure media controls can be displayed in the system shade.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE,
                )
            }
        }
    }
}
