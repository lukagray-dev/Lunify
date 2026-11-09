package com.lunify.domain.model

/**
 * ==============================================================================
 * Lunify - Playback State Definitions (PlaybackState.kt)
 * ==============================================================================
 *
 * Hey friend! This file houses the state representations for active media
 * playback in Lunify.
 *
 * It models everything needed to render the MiniPlayer, the Fullscreen Now
 * Playing sheet, and the Duo synchronization payload without tightly coupling
 * the UI to a specific media player framework.
 * ==============================================================================
 */

/**
 * High-level playback status of the media player.
 */
enum class PlayerStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
}

/**
 * Playlist repeat modes.
 */
enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

/**
 * Real-time playback progress metrics for driving seekbars and timestamps.
 *
 * @property currentPositionMs Current position in milliseconds.
 * @property totalDurationMs Total length of the track in milliseconds.
 * @property bufferedPositionMs How much audio has been pre-buffered in milliseconds.
 */
data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
) {
    /**
     * Fractional progress from 0.0f to 1.0f for seekbars.
     */
    val fraction: Float
        get() = if (totalDurationMs > 0) {
            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /**
     * Formats current position into "m:ss" text.
     */
    val formattedCurrentTime: String
        get() = formatMs(currentPositionMs)

    /**
     * Formats total duration into "m:ss" text.
     */
    val formattedTotalTime: String
        get() = formatMs(totalDurationMs)

    companion object {
        fun formatMs(ms: Long): String {
            if (ms <= 0L) return "0:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}

/**
 * Comprehensive snapshot of current playback state used by UI observers.
 *
 * @property currentTrack Currently active track (null if player is empty).
 * @property status Current playback status (Playing, Paused, Buffering, etc.).
 * @property progress Real-time progress metrics.
 * @property queue Active playlist queue.
 * @property currentIndex Index of current track within the queue.
 * @property isShuffle Whether shuffle mode is active.
 * @property repeatMode Current repeat configuration.
 * @property volume Volume level from 0.0f to 1.0f.
 */
data class PlayerState(
    val currentTrack: Track? = null,
    val status: PlayerStatus = PlayerStatus.IDLE,
    val progress: PlaybackProgress = PlaybackProgress(),
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
) {
    /**
     * Quick check if audio is actively producing sound.
     */
    val isPlaying: Boolean
        get() = status == PlayerStatus.PLAYING

    /**
     * Quick check if there is an active media track loaded.
     */
    val hasTrack: Boolean
        get() = currentTrack != null
}
