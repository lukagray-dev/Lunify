package com.android.lunify.videoplayer.ui

/**
 * Resolves the first frame position when reopening the local video player.
 *
 * The logic prefers an in-memory active session because that represents the
 * most recent playback state. If the session was fully stopped, it falls back
 * to the last saved stop position for the same video.
 */
internal object LocalVideoPlaybackResumeResolver {

    fun resolve(
        requestedVideoId: Long,
        currentSessionVideoId: Long?,
        currentSessionPositionMs: Long?,
        lastStoppedVideoId: Long?,
        lastStoppedPositionMs: Long?
    ): Long {
        return when {
            currentSessionVideoId == requestedVideoId && currentSessionPositionMs != null -> {
                currentSessionPositionMs.coerceAtLeast(0L)
            }
            lastStoppedVideoId == requestedVideoId && lastStoppedPositionMs != null -> {
                lastStoppedPositionMs.coerceAtLeast(0L)
            }
            else -> 0L
        }
    }
}
