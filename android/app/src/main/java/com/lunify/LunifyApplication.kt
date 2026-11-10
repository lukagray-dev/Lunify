package com.lunify

import android.app.Application
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.player.AudioPlayerManager
import com.lunify.player.duo.DuoManager

/**
 * ==============================================================================
 * Lunify - Application Context Entrypoint (LunifyApplication.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the application root of Lunify.
 *
 * WHY DO WE NEED AN APPLICATION CLASS HERE?
 * -----------------------------------------
 * In a production-grade audio player application, music must continue playing
 * seamlessly when:
 * 1. The user locks their phone screen.
 * 2. The user switches to another app (e.g. WhatsApp, Instagram, Maps).
 * 3. The Android system destroys and recreates the [MainActivity] during screen
 *    rotation or low-memory system trimming.
 *
 * If we held the [AudioPlayerManager] inside the [MainActivity], destroying the
 * activity would destroy the player, cutting off the music abruptly!
 *
 * By creating our singletons ([AudioPlayerManager], [LibraryStorage], [LocalAudioRepository])
 * at the [Application] scope:
 * - The audio player stays alive as long as our process is running.
 * - Both the UI ([MainActivity]) and our background media notification service
 *   ([com.lunify.player.service.LunifyMediaService]) reference the EXACT SAME
 *   player instance without conflicting duplicate ExoPlayers or audio focus battles.
 * ==============================================================================
 */
class LunifyApplication : Application() {

    // Persistent JSON library storage for favorites, history, and playlists
    lateinit var libraryStorage: LibraryStorage
        private set

    // Scanner that queries the device's MediaStore for local MP3/FLAC/AAC/M4A audio files
    lateinit var localAudioRepository: LocalAudioRepository
        private set

    // Central ExoPlayer audio playback engine driving offline and online streaming
    lateinit var audioPlayerManager: AudioPlayerManager
        private set

    // Real-time Duo synchronization coordinator
    lateinit var duoManager: DuoManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize local persistent storage
        libraryStorage = LibraryStorage(applicationContext)

        // 2. Initialize device audio scanner repository
        localAudioRepository = LocalAudioRepository(applicationContext)

        // 3. Initialize the core audio player manager
        audioPlayerManager = AudioPlayerManager(applicationContext, libraryStorage)

        // 4. Initialize Duo synchronization manager
        duoManager = DuoManager(applicationContext, localAudioRepository, audioPlayerManager)
    }

    companion object {
        // Global accessor for application singleton instance
        lateinit var instance: LunifyApplication
            private set
    }
}
