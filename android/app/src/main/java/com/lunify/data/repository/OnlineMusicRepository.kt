package com.lunify.data.repository

import com.lunify.data.remote.YouTubeExtractor
import com.lunify.data.remote.YouTubeMusicClient
import com.lunify.domain.model.Album
import com.lunify.domain.model.Artist
import com.lunify.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ==============================================================================
 * Lunify - Online Music Repository (OnlineMusicRepository.kt)
 * ==============================================================================
 *
 * Hey friend! This repository is the unified single source of truth for all
 * online streaming data in Lunify.
 *
 * It bridges:
 * 1. [YouTubeMusicClient] for searching, browsing, suggestions, and radio queues.
 * 2. [YouTubeExtractor] for resolving direct audio streaming URLs with PipePipe.
 *
 * By isolating network concerns here, our UI ViewModels and [AudioPlayerManager]
 * simply call clean, high-level methods like `searchSongs()` or `getAudioStreamUrl()`,
 * without worrying about InnerTube JSON schemas or decryption ciphers.
 * ==============================================================================
 */
class OnlineMusicRepository(
    private val client: YouTubeMusicClient = YouTubeMusicClient(),
    private val extractor: YouTubeExtractor = YouTubeExtractor,
) {

    /**
     * Searches YouTube Music for songs matching [query].
     * Can filter by Songs, Videos, etc.
     */
    suspend fun searchSongs(
        query: String,
        filter: String? = YouTubeMusicClient.FILTER_SONGS,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        client.searchSongs(query, filter)
    }

    /**
     * Searches YouTube Music for albums matching [query].
     */
    suspend fun searchAlbums(query: String): Result<List<Album>> = withContext(Dispatchers.IO) {
        client.searchAlbums(query)
    }

    /**
     * Fetches real-time search suggestions as the user types.
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        client.getSearchSuggestions(query)
    }

    /**
     * Fetches the Home feed's "Quick picks" tracks.
     */
    suspend fun getHomeQuickPicks(): Result<List<Track>> = withContext(Dispatchers.IO) {
        client.getHomeQuickPicks()
    }

    /**
     * Fetches the Home feed's trending album carousels.
     */
    suspend fun getHomeTrending(): Result<List<Album>> = withContext(Dispatchers.IO) {
        client.getHomeTrendingAlbums()
    }

    /**
     * Fetches real community and curated playlists from YouTube Music.
     */
    suspend fun getHomePlaylists(): Result<List<Album>> = withContext(Dispatchers.IO) {
        client.getHomePlaylists()
    }

    /**
     * Fetches real top artists from YouTube Music Charts.
     */
    suspend fun getTopArtists(): Result<List<Artist>> = withContext(Dispatchers.IO) {
        client.getTopArtists()
    }

    /**
     * Fetches tracks matching the specified mood filter (e.g. Relax, Workout, Focus).
     */
    suspend fun getMoodTracks(moodId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        val query = when (moodId.lowercase()) {
            "relax" -> "Relaxing chill acoustic songs"
            "workout" -> "Workout gym motivation hype songs"
            "energize" -> "Upbeat energizing pop dance"
            "focus" -> "Deep focus study beats instrumental"
            "commute" -> "Road trip commute driving songs"
            "party" -> "Party dance club hits upbeat"
            "sleep" -> "Sleep relaxation ambient peaceful"
            else -> return@withContext getHomeQuickPicks()
        }
        client.searchSongs(query, YouTubeMusicClient.FILTER_SONGS)
    }

    /**
     * Fetches playlists and albums matching the specified mood filter.
     */
    suspend fun getMoodAlbums(moodId: String): Result<List<Album>> = withContext(Dispatchers.IO) {
        val query = when (moodId.lowercase()) {
            "relax" -> "Relaxing chill vibes playlist"
            "workout" -> "Workout gym motivation playlist"
            "energize" -> "Energizing upbeat dance playlist"
            "focus" -> "Deep focus study beats playlist"
            "commute" -> "Road trip commute playlist"
            "party" -> "Party dance hits playlist"
            "sleep" -> "Sleep ambient peaceful playlist"
            else -> return@withContext getHomeTrending()
        }
        client.searchAlbums(query)
    }

    /**
     * Resolves the playable direct audio streaming URL for a YouTube video ID conforming to [quality].
     */
    suspend fun getAudioStreamUrl(
        videoId: String,
        quality: com.lunify.data.local.StreamingQuality = com.lunify.data.local.StreamingQuality.HIGH,
    ): Result<String> = withContext(Dispatchers.IO) {
        extractor.getAudioStreamUrl(videoId, quality)
    }

    /**
     * Fetches real playable tracks for an [Album] or playlist.
     */
    suspend fun getAlbumTracks(album: Album): Result<List<Track>> = withContext(Dispatchers.IO) {
        client.getAlbumTracks(album)
    }

    /**
     * Fetches top songs for an [Artist].
     */
    suspend fun getArtistTopTracks(artist: Artist): Result<List<Track>> = withContext(Dispatchers.IO) {
        client.getArtistTopTracks(artist)
    }

    /**
     * Fetches the related radio queue / up-next songs for [videoId].
     */
    suspend fun getRelatedQueue(videoId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        client.getRelatedQueue(videoId)
    }
}
