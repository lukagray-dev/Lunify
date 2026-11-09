package com.lunify.data.repository

import com.lunify.data.remote.YouTubeExtractor
import com.lunify.data.remote.YouTubeMusicClient
import com.lunify.domain.model.Album
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
     * Resolves the playable direct audio streaming URL for a YouTube video ID.
     */
    suspend fun getAudioStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        extractor.getAudioStreamUrl(videoId)
    }

    /**
     * Fetches the related radio queue / up-next songs for [videoId].
     */
    suspend fun getRelatedQueue(videoId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        client.getRelatedQueue(videoId)
    }
}
