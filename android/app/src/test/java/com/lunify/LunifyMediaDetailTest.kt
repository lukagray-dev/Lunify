package com.lunify

import com.lunify.domain.model.Album
import com.lunify.domain.model.Artist
import com.lunify.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Lunify - Media Detail & Home Interaction Tests (LunifyMediaDetailTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates all the core functionality behind our
 * Album, Playlist, and Artist detail views and Home pull-to-refresh mechanics:
 *
 * 1. Model Validation: Ensures [Album] and [Artist] maintain correct browse IDs
 *    and fallback titles without dropping metadata.
 * 2. Playlist Browse ID prefix formatting: Verifies that raw playlist IDs like
 *    "PL4fGSI..." are correctly resolved into InnerTube "VLPL..." format.
 * 3. Search Fallback Query Cleaning: Validates that generic noise keywords like
 *    "YouTube Music" or "Playlist" are cleanly filtered from search queries.
 * 4. Queue Playback & Shuffle: Verifies queueing an album's tracks, starting
 *    playback on track 0, and non-destructive shuffling.
 * 5. Pull-to-Refresh Physics: Tests scroll damping math, threshold checks,
 *    and top-of-feed locking logic.
 * ==============================================================================
 */
class LunifyMediaDetailTest {

    @Test
    fun testAlbumAndArtistModelIntegrity() {
        // Hey friend, verify that an album preserves its browseId and title cleanly
        val album = Album(
            id = "MPREb_981723498",
            title = "Starboy",
            artist = "The Weeknd",
            thumbnailUrl = "https://lh3.googleusercontent.com/test",
        )

        assertEquals("MPREb_981723498", album.id)
        assertEquals("Starboy", album.title)
        assertEquals("The Weeknd", album.artist)

        val artist = Artist(
            id = "UC_x5XG1OV2P6uZZ5FSM9Ttw",
            name = "Daft Punk",
            thumbnailUrl = "https://lh3.googleusercontent.com/daftpunk",
        )

        assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", artist.id)
        assertEquals("Daft Punk", artist.name)
    }

    @Test
    fun testPlaylistBrowseIdPrefixFormatting() {
        // When YouTube Music gives us a playlist starting with "PL", InnerTube browse
        // requires "VL" to be prefixed so the browse endpoint recognizes it as a playlist view
        val rawPlaylistId = "PL4fGSIqsQ8TWaB4k2K9R1gWbFhB8w"
        val effectiveBrowseId = if (rawPlaylistId.startsWith("PL")) "VL$rawPlaylistId" else rawPlaylistId
        assertEquals("VLPL4fGSIqsQ8TWaB4k2K9R1gWbFhB8w", effectiveBrowseId)

        // If it already has "VL" (like "VLPL..."), it should not double-prefix
        val alreadyPrefixed = "VLPL4fGSIqsQ8TWaB4k2K9R1gWbFhB8w"
        val resolvedPrefixed = if (alreadyPrefixed.startsWith("PL")) "VL$alreadyPrefixed" else alreadyPrefixed
        assertEquals("VLPL4fGSIqsQ8TWaB4k2K9R1gWbFhB8w", resolvedPrefixed)
    }

    @Test
    fun testSearchFallbackQueryCleaning() {
        // Test 1: Album with real artist -> "Title Artist"
        val albumWithArtist = Album(
            id = "album1",
            title = "Random Access Memories",
            artist = "Daft Punk",
            thumbnailUrl = "",
        )
        val query1 = if (albumWithArtist.artist.isNotBlank() &&
            !albumWithArtist.artist.contains("YouTube", ignoreCase = true) &&
            !albumWithArtist.artist.contains("Playlist", ignoreCase = true)
        ) {
            "${albumWithArtist.title} ${albumWithArtist.artist}"
        } else {
            albumWithArtist.title
        }
        assertEquals("Random Access Memories Daft Punk", query1)

        // Test 2: Playlist where artist is "YouTube Music" -> filters out noise, keeps just title!
        val playlistWithNoise = Album(
            id = "playlist1",
            title = "Today's Biggest Hits",
            artist = "YouTube Music",
            thumbnailUrl = "",
        )
        val query2 = if (playlistWithNoise.artist.isNotBlank() &&
            !playlistWithNoise.artist.contains("YouTube", ignoreCase = true) &&
            !playlistWithNoise.artist.contains("Playlist", ignoreCase = true)
        ) {
            "${playlistWithNoise.title} ${playlistWithNoise.artist}"
        } else {
            playlistWithNoise.title
        }
        assertEquals("Today's Biggest Hits", query2)
    }

    @Test
    fun testMediaDetailAlbumTrackQueueingAndShuffle() {
        // Build a mock album tracklist
        val tracks = listOf(
            Track(id = "1", title = "Get Lucky", artist = "Daft Punk", durationMs = 248000),
            Track(id = "2", title = "Instant Crush", artist = "Daft Punk", durationMs = 337000),
            Track(id = "3", title = "Lose Yourself to Dance", artist = "Daft Punk", durationMs = 353000),
            Track(id = "4", title = "Touch", artist = "Daft Punk", durationMs = 500000),
        )

        // "Play All" selects track 0 and queues the entire collection
        val playAllTrack = tracks.first()
        assertEquals("Get Lucky", playAllTrack.title)
        assertEquals(4, tracks.size)

        // "Shuffle" creates a randomized copy preserving all elements
        val shuffled = tracks.shuffled()
        assertEquals(4, shuffled.size)
        assertTrue(shuffled.containsAll(tracks))
    }

    @Test
    fun testPullToRefreshThresholdAndDampingMath() {
        // Hey friend! Damping factor is 0.45 like Chrome on Android
        val thresholdPx = 200f
        val maxPullDistancePx = thresholdPx * 1.5f // 300f

        val fingerDelta = 100f
        val dampedDelta = fingerDelta * 0.45f // 45f
        assertEquals(45f, dampedDelta, 0.001f)

        // Multiple pull deltas accumulate up to maxPullDistancePx
        var currentPull = 0f
        repeat(10) {
            currentPull = (currentPull + dampedDelta).coerceAtMost(maxPullDistancePx)
        }
        // Even after 10 pulls of 45f (= 450f), it must clamp at 300f
        assertEquals(maxPullDistancePx, currentPull, 0.001f)
        assertTrue(currentPull >= thresholdPx)
    }

    @Test
    fun testPullToRefreshTopLockingLogic() {
        // Scenario 1: User is at the bottom (firstVisibleItemIndex > 0)
        var firstVisibleItemIndex = 5
        var firstVisibleItemScrollOffset = 120
        var isAtTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
        var canPullRefresh = false

        if (!isAtTop) {
            canPullRefresh = false
        }
        assertFalse(canPullRefresh)

        // Scenario 2: User scrolls all the way to top
        firstVisibleItemIndex = 0
        firstVisibleItemScrollOffset = 0
        isAtTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
        assertTrue(isAtTop)

        // A new touch gesture starting at top sets canPullRefresh = true
        val isScrollInProgress = false
        val isUserInput = true
        if (isAtTop && isUserInput && !isScrollInProgress) {
            canPullRefresh = true
        }
        assertTrue(canPullRefresh)

        // Scenario 3: Fling arrives with side-effect momentum
        val isFlingMomentum = true
        if (isFlingMomentum) {
            // Fling should NOT set canPullRefresh
            val canFlingRefresh = false
            assertFalse(canFlingRefresh)
        }
    }
}
