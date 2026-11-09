package com.lunify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.player.AudioPlayerManager
import com.lunify.ui.LunifyApp
import com.lunify.ui.theme.LunifyTheme

/**
 * ==============================================================================
 * Lunify - Main Activity Entrypoint (MainActivity.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the primary activity entry point of Lunify.
 *
 * Here:
 * 1. `enableEdgeToEdge()`: Instructs Android to draw behind system bars (status bar
 *    and navigation bar), allowing our liquid glass background glows and floating
 *    navigation capsule to achieve seamless cinematic immersion.
 * 2. Dependency Wiring: Initializes [LibraryStorage], [LocalAudioRepository], and
 *    the core [AudioPlayerManager] singleton with application context.
 * 3. Lifecycle Management: Releases ExoPlayer audio resources on [onDestroy] to
 *    prevent background audio leaks and battery drain.
 * 4. `LunifyTheme`: Enforces our obsidian dark palette and liquid glass providers.
 * 5. `LunifyApp`: Hosts the navigation graph, floating MiniPlayer, and Fullscreen
 *    Now Playing overlay.
 * ==============================================================================
 */
class MainActivity : ComponentActivity() {

    private lateinit var libraryStorage: LibraryStorage
    private lateinit var audioPlayerManager: AudioPlayerManager
    private lateinit var localAudioRepository: LocalAudioRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge window drawing
        enableEdgeToEdge()

        // Initialize production data and audio engines with application context
        libraryStorage = LibraryStorage(applicationContext)
        audioPlayerManager = AudioPlayerManager(applicationContext, libraryStorage)
        localAudioRepository = LocalAudioRepository(applicationContext)
        
        setContent {
            LunifyTheme {
                LunifyApp(
                    audioPlayerManager = audioPlayerManager,
                    libraryStorage = libraryStorage,
                    localAudioRepository = localAudioRepository,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanly release ExoPlayer hardware resources when activity terminates
        if (::audioPlayerManager.isInitialized) {
            audioPlayerManager.release()
        }
    }
}
