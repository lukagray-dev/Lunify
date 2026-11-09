package com.lunify.domain.model

/**
 * ==============================================================================
 * Lunify - Media Domain Models (MediaItem.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the core data definitions for Lunify media items.
 *
 * In our architecture, these data classes represent the songs, albums, artists,
 * and playlists that the user browses and plays. By keeping them immutable
 * (using `val`) and independent of Android framework specifics, we ensure they
 * can be safely shared across ViewModels, Compose screens, and offline databases.
 * ==============================================================================
 */

/**
 * Represents a single audio or video track in Lunify.
 *
 * @property id Unique YouTube video ID (e.g., "dQw4w9WgXcQ") or local file URI.
 * @property title Name of the song.
 * @property artist Name of the performing artist or channel.
 * @property album Name of the album or collection (optional fallback to title).
 * @property durationText Human-readable duration string (e.g., "3:45").
 * @property durationMs Duration in milliseconds for seek bar calculations.
 * @property thumbnailUrl Web URL or local file path for album cover art.
 * @property isVideo Whether this media item has a video stream or is audio-only.
 * @property isDownloaded Whether this media item is stored locally on the device.
 * @property isFavorite Whether the user has liked or favorited this track.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationText: String = "0:00",
    val durationMs: Long = 0L,
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val isDownloaded: Boolean = false,
    val isFavorite: Boolean = false,
    val contentUri: String = "",
    val sizeBytes: Long = 0L,
) {
    /**
     * Formats duration milliseconds into "m:ss" format for clean display.
     * If durationText is already provided, it uses it directly; otherwise it
     * computes minutes and seconds from milliseconds.
     */
    fun formattedDuration(): String {
        if (durationText.isNotEmpty() && durationText != "0:00") {
            return durationText
        }
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Indicates where a song was played from, so we can tag history items
 * and distinguish offline local device tracks from online streaming tracks.
 */
enum class PlaybackSource {
    OFFLINE,
    ONLINE,
}

/**
 * Represents an entry in the user's persistent playback history.
 *
 * @property track The song that was played.
 * @property playedAtTimestamp Epoch milliseconds when playback occurred.
 * @property source Tags whether this was played from local storage (OFFLINE) or streamed (ONLINE).
 */
data class HistoryItem(
    val track: Track,
    val playedAtTimestamp: Long = System.currentTimeMillis(),
    val source: PlaybackSource = if (track.isDownloaded) PlaybackSource.OFFLINE else PlaybackSource.ONLINE,
)

/**
 * Represents a music album or single release.
 *
 * @property id Album browse ID or playlist ID.
 * @property title Album name.
 * @property artist Main artist or curator.
 * @property year Release year (e.g., "2024").
 * @property thumbnailUrl Cover art image URL.
 * @property trackCount Number of tracks inside the album.
 */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: String = "",
    val thumbnailUrl: String = "",
    val trackCount: Int = 0,
)

/**
 * Represents an artist or music creator.
 *
 * @property id Artist channel browse ID.
 * @property name Artist name.
 * @property subscribers Subtitle info (e.g., "12.4M Subscribers").
 * @property thumbnailUrl Circular avatar image URL.
 */
data class Artist(
    val id: String,
    val name: String,
    val subscribers: String = "",
    val thumbnailUrl: String = "",
)

/**
 * Represents a curated mood or genre category (e.g., "Relax", "Workout").
 *
 * @property id Unique tag ID.
 * @property title Display name.
 * @property isSelected Whether this mood filter is currently active in the UI.
 */
data class MoodFilter(
    val id: String,
    val title: String,
    val isSelected: Boolean = false,
)
