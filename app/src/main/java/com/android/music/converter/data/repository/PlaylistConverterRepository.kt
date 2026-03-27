package com.android.music.converter.data.repository

import android.content.Context
import android.util.Log
import com.android.music.browse.auth.SpotifyAuthManager
import com.android.music.browse.auth.YouTubeAuthManager
import com.android.music.browse.data.api.SpotifyApiService
import com.android.music.browse.data.api.YouTubeApiService
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyPlaylist
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyTrack
import com.android.music.browse.data.mapper.YouTubeMapper.toYouTubeVideo
import com.android.music.browse.data.model.SpotifyTrack
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.data.network.NetworkModule
import com.android.music.converter.data.api.model.AddVideoToPlaylistRequest
import com.android.music.converter.data.api.model.CreatePlaylistRequest
import com.android.music.converter.data.api.model.PlaylistItemSnippet
import com.android.music.converter.data.api.model.PlaylistSnippet
import com.android.music.converter.data.api.model.PlaylistStatus
import com.android.music.converter.data.api.model.ResourceId
import com.android.music.converter.data.model.ConversionProgress
import com.android.music.converter.data.model.ConversionResult
import com.android.music.converter.data.model.ConversionStatus
import com.android.music.converter.data.model.TrackMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs

/**
 * Repository for playlist conversion operations.
 * Orchestrates Spotify playlist fetching, YouTube search, and playlist creation.
 */
class PlaylistConverterRepository(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistConverter"
        private const val DURATION_TOLERANCE_SECONDS = 10
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f
    }

    private val spotifyApi: SpotifyApiService = NetworkModule.spotifyApiService
    private val youtubeApi: YouTubeApiService = NetworkModule.youtubeApiService
    private val spotifyAuth = SpotifyAuthManager.getInstance(context)
    private val youtubeAuth = YouTubeAuthManager.getInstance(context)

    /**
     * Convert Spotify playlist to YouTube playlist
     */
    fun convertPlaylist(
        spotifyPlaylistUrl: String,
        targetPlaylistName: String? = null
    ): Flow<ConversionProgress> = flow {
        try {
            // Extract playlist ID from URL
            val playlistId = extractPlaylistId(spotifyPlaylistUrl)
            if (playlistId == null) {
                emit(ConversionProgress(ConversionStatus.ERROR, 0, 0, "Invalid Spotify playlist URL"))
                return@flow
            }

            // Fetch Spotify playlist
            emit(ConversionProgress(ConversionStatus.FETCHING_PLAYLIST, 0, 0, "Fetching Spotify playlist..."))
            val tracks = fetchSpotifyPlaylistTracks(playlistId)
            if (tracks.isEmpty()) {
                emit(ConversionProgress(ConversionStatus.ERROR, 0, tracks.size, "Playlist is empty or inaccessible"))
                return@flow
            }

            // Search and match tracks
            emit(ConversionProgress(ConversionStatus.SEARCHING_TRACKS, 0, tracks.size, "Searching for tracks on YouTube..."))
            val matches = mutableListOf<TrackMatch>()
            
            tracks.forEachIndexed { index, track ->
                val match = searchAndMatchTrack(track)
                matches.add(match)
                emit(ConversionProgress(
                    ConversionStatus.SEARCHING_TRACKS,
                    index + 1,
                    tracks.size,
                    "Matched: ${track.name} by ${track.artistName}"
                ))
                delay(100) // Rate limiting
            }

            // Create YouTube playlist
            emit(ConversionProgress(ConversionStatus.CREATING_PLAYLIST, tracks.size, tracks.size, "Creating YouTube playlist..."))
            val playlistName = targetPlaylistName ?: "Converted from Spotify"
            val youtubePlaylistId = createYouTubePlaylist(playlistName, "Converted from Spotify playlist")
            
            if (youtubePlaylistId == null) {
                emit(ConversionProgress(ConversionStatus.ERROR, tracks.size, tracks.size, "Failed to create YouTube playlist"))
                return@flow
            }

            // Add matched videos to playlist
            emit(ConversionProgress(ConversionStatus.ADDING_VIDEOS, 0, matches.size, "Adding videos to playlist..."))
            var addedCount = 0
            matches.forEachIndexed { index, match ->
                if (match.youtubeVideo != null && match.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                    val added = addVideoToPlaylist(youtubePlaylistId, match.youtubeVideo.id)
                    if (added) addedCount++
                    emit(ConversionProgress(
                        ConversionStatus.ADDING_VIDEOS,
                        index + 1,
                        matches.size,
                        "Added: ${match.spotifyTrack.name}"
                    ))
                    delay(100) // Rate limiting
                }
            }

            // Complete
            emit(ConversionProgress(
                ConversionStatus.COMPLETE,
                matches.size,
                matches.size,
                "Conversion complete! Added $addedCount of ${tracks.size} tracks"
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Conversion error", e)
            emit(ConversionProgress(ConversionStatus.ERROR, 0, 0, "Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract playlist ID from Spotify URL
     */
    private fun extractPlaylistId(url: String): String? {
        return try {
            // Handle different URL formats:
            // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
            // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=...
            // spotify:playlist:37i9dQZF1DXcBWIGoYBM5M
            
            when {
                url.contains("spotify:playlist:") -> {
                    // URI format: spotify:playlist:ID
                    url.substringAfter("spotify:playlist:").substringBefore("?")
                }
                url.contains("/playlist/") -> {
                    // URL format: .../playlist/ID or .../playlist/ID?si=...
                    val regex = Regex("playlist/([a-zA-Z0-9]+)")
                    regex.find(url)?.groupValues?.get(1)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract playlist ID from: $url", e)
            null
        }
    }

    /**
     * Fetch all tracks from Spotify playlist
     */
    private suspend fun fetchSpotifyPlaylistTracks(playlistId: String): List<SpotifyTrack> {
        val token = spotifyAuth.getAccessToken() ?: return emptyList()
        NetworkModule.spotifyOauthToken = token

        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 100

        try {
            do {
                val response = spotifyApi.getPlaylistTracks(playlistId, limit, offset)
                if (response.isSuccessful) {
                    val items = response.body()?.items?.mapNotNull { it.track?.toSpotifyTrack() } ?: emptyList()
                    tracks.addAll(items)
                    offset += limit
                    
                    // Check if there are more tracks
                    val hasMore = response.body()?.items?.size == limit
                    if (!hasMore) break
                } else {
                    Log.e(TAG, "Failed to fetch playlist tracks: ${response.code()}")
                    break
                }
            } while (true)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist tracks", e)
        }

        return tracks
    }

    /**
     * Search for a track on YouTube and match
     */
    private suspend fun searchAndMatchTrack(track: SpotifyTrack): TrackMatch {
        val token = youtubeAuth.getAccessToken()
        if (token == null) {
            return TrackMatch(track, null, 0f, "YouTube not authenticated")
        }
        NetworkModule.oauthToken = token

        try {
            // Search query: "track name artist name"
            val query = "${track.name} ${track.artistName}"
            val response = youtubeApi.search(query = query, maxResults = 5)

            if (response.isSuccessful) {
                val videos = response.body()?.items?.mapNotNull { it.toYouTubeVideo() } ?: emptyList()
                
                if (videos.isEmpty()) {
                    return TrackMatch(track, null, 0f, "No results found")
                }

                // Score each video
                val scoredVideos = videos.map { video ->
                    val score = calculateMatchScore(track, video)
                    Pair(video, score)
                }.sortedByDescending { it.second }

                val bestMatch = scoredVideos.first()
                return TrackMatch(
                    track,
                    bestMatch.first,
                    bestMatch.second,
                    "Matched with ${(bestMatch.second * 100).toInt()}% confidence"
                )
            } else {
                Log.e(TAG, "Search failed: ${response.code()}")
                return TrackMatch(track, null, 0f, "Search failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching track", e)
            return TrackMatch(track, null, 0f, "Error: ${e.message}")
        }
    }

    /**
     * Calculate match score between Spotify track and YouTube video
     */
    private fun calculateMatchScore(track: SpotifyTrack, video: YouTubeVideo): Float {
        var score = 0f

        // Title similarity (50% weight)
        val titleSimilarity = calculateStringSimilarity(
            track.name.lowercase(),
            video.title.lowercase()
        )
        score += titleSimilarity * 0.5f

        // Artist in title (30% weight)
        val artistInTitle = video.title.lowercase().contains(track.artistName.lowercase())
        if (artistInTitle) score += 0.3f

        // Duration match (20% weight)
        val trackDuration = parseDuration(track.duration)
        val videoDuration = parseDuration(video.duration)
        if (trackDuration > 0 && videoDuration > 0) {
            val durationDiff = abs(trackDuration - videoDuration)
            if (durationDiff <= DURATION_TOLERANCE_SECONDS) {
                score += 0.2f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Calculate string similarity using Levenshtein distance
     */
    private fun calculateStringSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0f
        
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance).toFloat() / longer.length
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(minOf(newValue, lastValue), costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }

    /**
     * Parse duration string to seconds
     */
    private fun parseDuration(duration: String): Int {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Create a new YouTube playlist
     */
    private suspend fun createYouTubePlaylist(title: String, description: String): String? {
        val token = youtubeAuth.getAccessToken() ?: return null
        NetworkModule.oauthToken = token

        try {
            val request = CreatePlaylistRequest(
                snippet = PlaylistSnippet(
                    title = title,
                    description = description
                ),
                status = PlaylistStatus(privacyStatus = "public")
            )

            val response = youtubeApi.createPlaylist(request = request)
            if (response.isSuccessful) {
                val playlistId = response.body()?.id
                Log.d(TAG, "Created playlist: $playlistId")
                return playlistId
            } else {
                Log.e(TAG, "Failed to create playlist: ${response.code()} - ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating playlist", e)
        }

        return null
    }

    /**
     * Add a video to YouTube playlist
     */
    private suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean {
        val token = youtubeAuth.getAccessToken() ?: return false
        NetworkModule.oauthToken = token

        try {
            val request = AddVideoToPlaylistRequest(
                snippet = PlaylistItemSnippet(
                    playlistId = playlistId,
                    resourceId = ResourceId(videoId = videoId)
                )
            )

            val response = youtubeApi.addVideoToPlaylist(request = request)
            if (response.isSuccessful) {
                Log.d(TAG, "Added video $videoId to playlist $playlistId")
                return true
            } else {
                Log.e(TAG, "Failed to add video: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding video to playlist", e)
        }

        return false
    }
}
