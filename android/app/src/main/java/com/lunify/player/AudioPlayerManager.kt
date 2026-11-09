package com.lunify.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lunify.data.local.LibraryStorage
import com.lunify.domain.model.PlaybackProgress
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.PlayerStatus
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Lunify - Audio Player Manager (AudioPlayerManager.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the heartbeat of audio playback in Lunify!
 *
 * WHAT DOES THIS CLASS DO?
 * ------------------------
 * This manager is the single source of truth for audio playback throughout the
 * entire application. It wraps Google's production-grade `androidx.media3.exoplayer`,
 * configuring it strictly for lightweight, highly reliable audio-only playback
 * (music, podcasts, local tracks).
 *
 * KEY FEATURES IMPLEMENTED HERE:
 * 1. ExoPlayer Lifecycle & Audio Focus: Configured with `AUDIO_CONTENT_TYPE_MUSIC`
 *    and automatic audio focus handling (so if a phone call comes in, music pauses!).
 * 2. Real-Time State Streaming: Exposes a reactive `StateFlow<PlayerState>` that
 *    both the MiniPlayer and FullscreenPlayer observe simultaneously.
 * 3. Smart Transport Controls:
 *    - Play / Pause / Seek.
 *    - Next track: advances queue or loops based on repeat mode.
 *    - **Previous Button Logic**:
 *      - If song has played for > 3 seconds (3000ms), restart from the beginning (0ms).
 *      - If song has played <= 3 seconds, jump to the previous song in queue.
 * 4. Automatic History Recording: Whenever a track begins playback, it is logged
 *    to persistent `LibraryStorage` tagged as OFFLINE or ONLINE.
 * 5. Persistent Favorites Sync: Toggling like/heart reflects across all player surfaces
 *    and the library screen immediately.
 * ==============================================================================
 */
class AudioPlayerManager(
    private val context: Context,
    val libraryStorage: LibraryStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressTickerJob: Job? = null

    // Configure ExoPlayer purely for audio playback with audio focus and noisy-intent handling
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    // Internal mutable state flow driving the UI
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    init {
        setupPlayerListener()
    }

    /**
     * Connects Media3 ExoPlayer callbacks to our reactive [PlayerState].
     */
    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val status = when (playbackState) {
                    Player.STATE_IDLE -> PlayerStatus.IDLE
                    Player.STATE_BUFFERING -> PlayerStatus.BUFFERING
                    Player.STATE_READY -> if (exoPlayer.isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                    Player.STATE_ENDED -> {
                        // Song completed: automatically move to next song in queue
                        playNext()
                        PlayerStatus.PAUSED
                    }
                    else -> PlayerStatus.IDLE
                }

                _playerState.update { it.copy(status = status) }
                syncProgress()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val status = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                _playerState.update { it.copy(status = status) }

                if (isPlaying) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                    syncProgress()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                _playerState.update { it.copy(status = PlayerStatus.ERROR) }
                stopProgressTicker()
            }
        })
    }

    /**
     * Starts continuous polling of playback position while playing (every 250ms).
     * This provides butter-smooth seekbar movement in both MiniPlayer and FullscreenPlayer.
     */
    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                syncProgress()
                delay(250)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    /**
     * Synchronizes current ExoPlayer position, duration, and buffer into PlayerState.
     */
    private fun syncProgress() {
        val currentMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val durationMs = if (exoPlayer.duration > 0) {
            exoPlayer.duration
        } else {
            _playerState.value.currentTrack?.durationMs ?: 0L
        }
        val bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)

        _playerState.update { state ->
            state.copy(
                progress = PlaybackProgress(
                    currentPositionMs = currentMs,
                    totalDurationMs = durationMs,
                    bufferedPositionMs = bufferedMs,
                )
            )
        }
    }

    // ==========================================================================
    // PLAYBACK CONTROLS
    // ==========================================================================

    /**
     * Plays a specific track, optionally replacing the current active playlist queue.
     *
     * @param track The song to play.
     * @param newQueue Optional list of tracks forming the active queue/playlist.
     */
    fun playTrack(track: Track, newQueue: List<Track>? = null) {
        val updatedQueue = newQueue ?: _playerState.value.queue.ifEmpty { listOf(track) }
        val index = updatedQueue.indexOfFirst { it.id == track.id }.let { if (it == -1) 0 else it }

        // Update favorite flag from persistent storage
        val trackWithFavorite = track.copy(isFavorite = libraryStorage.isFavorite(track.id))

        _playerState.update { state ->
            state.copy(
                currentTrack = trackWithFavorite,
                queue = updatedQueue,
                currentIndex = index,
                status = PlayerStatus.BUFFERING,
            )
        }

        // Build Media3 MediaItem with rich metadata
        val mediaUri = if (track.contentUri.isNotBlank()) {
            Uri.parse(track.contentUri)
        } else if (track.id.startsWith("content://") || track.id.startsWith("http")) {
            Uri.parse(track.id)
        } else {
            null
        }

        if (mediaUri != null) {
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build()

            val exoItem = ExoMediaItem.Builder()
                .setMediaId(track.id)
                .setUri(mediaUri)
                .setMediaMetadata(metadata)
                .build()

            exoPlayer.setMediaItem(exoItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            // For mock/demo items without real URI, simulate ready playback
            _playerState.update { it.copy(status = PlayerStatus.PLAYING) }
            startProgressTicker()
        }

        // Record into persistent history
        val source = if (track.isDownloaded) PlaybackSource.OFFLINE else PlaybackSource.ONLINE
        libraryStorage.addToHistory(trackWithFavorite, source)
    }

    /**
     * Toggles play/pause state.
     */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE && _playerState.value.currentTrack != null) {
                playTrack(_playerState.value.currentTrack!!)
            } else {
                exoPlayer.play()
            }
        }
    }

    /**
     * Moves to the next track in the queue.
     */
    fun playNext() {
        val currentState = _playerState.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        val nextIndex = currentState.currentIndex + 1
        if (nextIndex < queue.size) {
            playTrack(queue[nextIndex])
        } else if (currentState.repeatMode == RepeatMode.ALL) {
            playTrack(queue[0])
        } else {
            // Queue ended
            exoPlayer.pause()
            exoPlayer.seekTo(0L)
            syncProgress()
        }
    }

    /**
     * Inserts a track immediately after the currently playing song in the queue,
     * so it will be the very next track played!
     *
     * @param track The song to play next.
     */
    fun insertNext(track: Track) {
        val currentQueue = _playerState.value.queue.toMutableList()
        val insertIndex = (_playerState.value.currentIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIndex, track)
        _playerState.update { it.copy(queue = currentQueue) }
    }

    /**
     * Appends a track to the end of the active queue.
     *
     * @param track The song to append.
     */
    fun addToQueue(track: Track) {
        val currentQueue = _playerState.value.queue.toMutableList()
        currentQueue.add(track)
        _playerState.update { it.copy(queue = currentQueue) }
    }

    /**
     * Handles the previous button action according to product requirements:
     *
     * RULE:
     * - If the currently playing track has played for MORE THAN 3 SECONDS (> 3000ms),
     *   restart the track from the beginning (0ms).
     * - If the track has played 3 SECONDS OR LESS (<= 3000ms), navigate to the
     *   previous track in the queue.
     */
    fun playPrevious() {
        val currentPositionMs = exoPlayer.currentPosition
        val currentState = _playerState.value

        if (currentPositionMs > 3000L) {
            // Track played > 3 seconds: Seek back to start
            exoPlayer.seekTo(0L)
            syncProgress()
        } else {
            // Track played <= 3 seconds: Jump to previous track
            val prevIndex = currentState.currentIndex - 1
            if (prevIndex >= 0 && prevIndex < currentState.queue.size) {
                playTrack(currentState.queue[prevIndex])
            } else {
                // Already at first track: just restart
                exoPlayer.seekTo(0L)
                syncProgress()
            }
        }
    }

    /**
     * Seeks playback position to a target timestamp in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(safePosition)
        syncProgress()
    }

    /**
     * Toggles repeat mode: OFF -> ALL -> ONE -> OFF.
     */
    fun toggleRepeat() {
        val currentMode = _playerState.value.repeatMode
        val nextMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        when (nextMode) {
            RepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        }

        _playerState.update { it.copy(repeatMode = nextMode) }
    }

    /**
     * Toggles shuffle mode on/off.
     */
    fun toggleShuffle() {
        val nextShuffle = !_playerState.value.isShuffle
        exoPlayer.shuffleModeEnabled = nextShuffle
        _playerState.update { it.copy(isShuffle = nextShuffle) }
    }

    /**
     * Toggles favorite status for a song in persistent storage and updates state.
     */
    fun toggleFavorite(track: Track) {
        val isNowFavorite = libraryStorage.toggleFavorite(track)

        // If current track is the one being toggled, update playerState
        if (_playerState.value.currentTrack?.id == track.id) {
            _playerState.update { state ->
                state.copy(
                    currentTrack = state.currentTrack?.copy(isFavorite = isNowFavorite)
                )
            }
        }
    }

    /**
     * Sets queue without immediately changing current playing track.
     */
    fun setQueue(newQueue: List<Track>, activeIndex: Int = 0) {
        _playerState.update {
            it.copy(
                queue = newQueue,
                currentIndex = activeIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
            )
        }
    }

    /**
     * Cleanly releases ExoPlayer resources when the app or service is destroyed.
     */
    fun release() {
        stopProgressTicker()
        scope.cancel()
        exoPlayer.release()
    }
}
