package com.android.lunify.converter.data.model

import com.android.lunify.browse.data.model.SpotifyTrack
import com.android.lunify.browse.data.model.YouTubeVideo

/**
 * Represents the status of playlist conversion
 */
enum class ConversionStatus {
    IDLE,
    FETCHING_PLAYLIST,
    SEARCHING_TRACKS,
    CREATING_PLAYLIST,
    ADDING_VIDEOS,
    COMPLETE,
    ERROR
}

/**
 * Represents a matched track with confidence score
 */
data class TrackMatch(
    val spotifyTrack: SpotifyTrack,
    val youtubeVideo: YouTubeVideo?,
    val confidence: Float, // 0.0 to 1.0
    val matchReason: String
)

/**
 * Represents the conversion progress
 */
data class ConversionProgress(
    val status: ConversionStatus,
    val currentTrack: Int,
    val totalTracks: Int,
    val message: String
) {
    val percentage: Int
        get() = if (totalTracks > 0) (currentTrack * 100) / totalTracks else 0
}

/**
 * Represents the conversion result
 */
data class ConversionResult(
    val success: Boolean,
    val playlistId: String?,
    val playlistUrl: String?,
    val totalTracks: Int,
    val matchedTracks: Int,
    val failedTracks: List<SpotifyTrack>,
    val matches: List<TrackMatch>
)
