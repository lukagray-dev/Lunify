package com.lunify.data.repository

import com.lunify.domain.model.Album
import com.lunify.domain.model.Artist
import com.lunify.domain.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * ==============================================================================
 * Lunify - Home Discovery Feed Data Model (HomeFeedRepository.kt)
 * ==============================================================================
 *
 * Encapsulates full home feed data: Quick Picks, Trending Albums, Featured Playlists,
 * and Top Artists from YouTube Music InnerTube.
 */
data class HomeFeedData(
    val quickPicks: List<Track> = emptyList(),
    val trendingAlbums: List<Album> = emptyList(),
    val featuredPlaylists: List<Album> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val isInitialized: Boolean = false,
)

/**
 * Encapsulates mood-specific feed data (e.g., Relax, Workout, Focus).
 */
data class MoodFeedData(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val isInitialized: Boolean = false,
)

/**
 * ==============================================================================
 * Lunify - Persistent In-Memory Home Feed Repository (HomeFeedRepository.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the caching engine behind Lunify's Home screen.
 *
 * THE PROBLEM IT SOLVES:
 * ----------------------
 * Previously, every time users navigated from Home to Library or Settings and came
 * back, the Home feed would wipe its state and refetch from YouTube Music every single
 * time. This caused annoying loading spinners, visual jitter, and wasted mobile data.
 *
 * HOW THIS REPOSITORY FIXES IT:
 * -----------------------------
 * 1. Persistent In-Memory Cache:
 *    Once the home feed or any mood feed (Workout, Relax, Focus) is loaded once,
 *    it is cached in memory. Navigating between pages displays the cached content
 *    INSTANTLY with zero reload!
 *
 * 2. Dedicated Mood Feed Support:
 *    When users tap mood chips ("Relax", "Workout", "Energize", "Focus"), content
 *    dynamically shifts to curated music matching that specific mood.
 *
 * 3. Controlled Pull-to-Refresh:
 *    Reloading ONLY happens when the user explicitly triggers a hard pull-down
 *    (pull-to-refresh) from the top of the home screen, fetching fresh data from
 *    YouTube Music and updating the cache seamlessly.
 * ==============================================================================
 */
object HomeFeedRepository {

    private val onlineRepository = OnlineMusicRepository()

    // Currently selected mood chip id (persists across page navigations!)
    var selectedMoodId: String = "all"

    // Primary general Home feed cache ("All" chip)
    var homeFeedData: HomeFeedData = HomeFeedData()
        private set

    // Per-mood feed caches (key = moodId, e.g. "relax", "workout", "focus")
    private val moodFeeds = mutableMapOf<String, MoodFeedData>()

    /**
     * Loads the general Home feed data. If already cached and [forceRefresh] is false,
     * returns cached data immediately without any network roundtrip!
     */
    suspend fun getHomeFeed(forceRefresh: Boolean = false): HomeFeedData {
        if (!forceRefresh && homeFeedData.isInitialized) {
            return homeFeedData
        }

        return coroutineScope {
            val qpDeferred = async { onlineRepository.getHomeQuickPicks().getOrDefault(emptyList()) }
            val trDeferred = async { onlineRepository.getHomeTrending().getOrDefault(emptyList()) }
            val plDeferred = async { onlineRepository.getHomePlaylists().getOrDefault(emptyList()) }
            val arDeferred = async { onlineRepository.getTopArtists().getOrDefault(emptyList()) }

            val loaded = HomeFeedData(
                quickPicks = qpDeferred.await(),
                trendingAlbums = trDeferred.await(),
                featuredPlaylists = plDeferred.await(),
                topArtists = arDeferred.await(),
                isInitialized = true,
            )
            homeFeedData = loaded
            loaded
        }
    }

    /**
     * Loads mood-specific music for [moodId] (e.g. "relax", "workout", "focus").
     * Returns cached mood data instantly if previously fetched.
     */
    suspend fun getMoodFeed(moodId: String, forceRefresh: Boolean = false): MoodFeedData {
        if (moodId.lowercase() == "all") {
            val home = getHomeFeed(forceRefresh)
            return MoodFeedData(
                tracks = home.quickPicks,
                albums = home.trendingAlbums,
                isInitialized = true,
            )
        }

        val cached = moodFeeds[moodId]
        if (!forceRefresh && cached != null && cached.isInitialized) {
            return cached
        }

        return coroutineScope {
            val tracksDeferred = async { onlineRepository.getMoodTracks(moodId).getOrDefault(emptyList()) }
            val albumsDeferred = async { onlineRepository.getMoodAlbums(moodId).getOrDefault(emptyList()) }

            val loaded = MoodFeedData(
                tracks = tracksDeferred.await(),
                albums = albumsDeferred.await(),
                isInitialized = true,
            )
            moodFeeds[moodId] = loaded
            loaded
        }
    }

    /**
     * Forces a refresh for the currently active mood or general feed.
     */
    suspend fun refresh(moodId: String) {
        if (moodId.lowercase() == "all") {
            getHomeFeed(forceRefresh = true)
        } else {
            getMoodFeed(moodId, forceRefresh = true)
        }
    }

    /**
     * Clears cached home data if needed (e.g. on user data reset).
     */
    fun clearCache() {
        homeFeedData = HomeFeedData()
        moodFeeds.clear()
        selectedMoodId = "all"
    }
}
