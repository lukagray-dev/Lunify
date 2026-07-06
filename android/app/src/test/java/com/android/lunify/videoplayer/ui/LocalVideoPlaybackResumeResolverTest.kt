package com.android.lunify.videoplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalVideoPlaybackResumeResolverTest {

    @Test
    fun `returns active session position for the same video`() {
        val position = LocalVideoPlaybackResumeResolver.resolve(
            requestedVideoId = 7L,
            currentSessionVideoId = 7L,
            currentSessionPositionMs = 42_000L,
            lastStoppedVideoId = 7L,
            lastStoppedPositionMs = 11_000L
        )

        assertEquals(42_000L, position)
    }

    @Test
    fun `falls back to last stopped position when there is no live session`() {
        val position = LocalVideoPlaybackResumeResolver.resolve(
            requestedVideoId = 7L,
            currentSessionVideoId = null,
            currentSessionPositionMs = null,
            lastStoppedVideoId = 7L,
            lastStoppedPositionMs = 11_000L
        )

        assertEquals(11_000L, position)
    }

    @Test
    fun `returns zero when the stored session belongs to a different video`() {
        val position = LocalVideoPlaybackResumeResolver.resolve(
            requestedVideoId = 7L,
            currentSessionVideoId = 8L,
            currentSessionPositionMs = 42_000L,
            lastStoppedVideoId = 9L,
            lastStoppedPositionMs = 11_000L
        )

        assertEquals(0L, position)
    }
}
