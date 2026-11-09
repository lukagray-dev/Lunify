package com.lunify.data.local

import android.content.Context
import android.content.SharedPreferences
import com.lunify.domain.model.HistoryItem
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * ==============================================================================
 * Lunify - Library Persistent Storage (LibraryStorage.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the local persistence module for Lunify.
 *
 * WHY DOES THIS EXIST?
 * --------------------
 * When a user hearts a song (Favorites) or plays music throughout their day
 * (History), we want those actions to be remembered even after the app is closed,
 * updated, or the device is rebooted.
 *
 * HOW IT WORKS:
 * -------------
 * Instead of adding heavy database bloat (like SQLite migrations or Room annotation
 * processing) for simple user preferences, we utilize Android's rock-solid
 * `SharedPreferences` paired with lightweight JSON serialization (`org.json`).
 *
 * This provides:
 * 1. Zero extra binary footprint (built directly into the Android framework).
 * 2. Instant synchronous reads on startup without blocking the UI thread.
 * 3. Simple, human-readable data structures that are easy to debug and test.
 * ==============================================================================
 */
class LibraryStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // In-memory cache for fast lookups
    private val favoriteIdsCache = mutableSetOf<String>()
    private val favoriteTracksCache = mutableListOf<Track>()
    private val historyCache = mutableListOf<HistoryItem>()

    init {
        // Load initial favorites and history into memory
        loadFavoritesFromDisk()
        loadHistoryFromDisk()
    }

    // ==========================================================================
    // FAVORITES MANAGEMENT
    // ==========================================================================

    /**
     * Checks if a track ID has been favorited by the user.
     */
    @Synchronized
    fun isFavorite(trackId: String): Boolean {
        return favoriteIdsCache.contains(trackId)
    }

    /**
     * Retrieves all tracks favorited by the user.
     */
    @Synchronized
    fun getFavoriteTracks(): List<Track> {
        return favoriteTracksCache.toList()
    }

    /**
     * Toggles the favorite status of a track.
     *
     * If the track was already in favorites, it is removed.
     * If it was not favorited, it is added to the top of the favorites list.
     *
     * @param track The song to toggle.
     * @return The new favorite status (true if now liked, false if unliked).
     */
    @Synchronized
    fun toggleFavorite(track: Track): Boolean {
        val nowFavorite: Boolean
        if (favoriteIdsCache.contains(track.id)) {
            // Remove from favorites
            favoriteIdsCache.remove(track.id)
            favoriteTracksCache.removeAll { it.id == track.id }
            nowFavorite = false
        } else {
            // Add to favorites
            favoriteIdsCache.add(track.id)
            // Ensure track marked as favorite
            favoriteTracksCache.add(0, track.copy(isFavorite = true))
            nowFavorite = true
        }

        persistFavoritesToDisk()
        return nowFavorite
    }

    private fun loadFavoritesFromDisk() {
        favoriteIdsCache.clear()
        favoriteTracksCache.clear()

        val jsonString = prefs.getString(KEY_FAVORITES, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val track = deserializeTrack(obj).copy(isFavorite = true)
                favoriteIdsCache.add(track.id)
                favoriteTracksCache.add(track)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistFavoritesToDisk() {
        try {
            val jsonArray = JSONArray()
            for (track in favoriteTracksCache) {
                jsonArray.put(serializeTrack(track))
            }
            prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================================================
    // HISTORY MANAGEMENT
    // ==========================================================================

    /**
     * Retrieves the list of recently played tracks with their timestamps and source tags.
     */
    @Synchronized
    fun getPlaybackHistory(): List<HistoryItem> {
        return historyCache.toList()
    }

    /**
     * Records a track into the user's playback history.
     *
     * If the same song was played previously, its older entry is removed and
     * moved to the very top with the newest timestamp.
     * We keep a maximum of [MAX_HISTORY_ENTRIES] to prevent unbounded storage growth.
     *
     * @param track The song that was played.
     * @param source Tags whether it was played from device storage (OFFLINE) or streamed (ONLINE).
     */
    @Synchronized
    fun addToHistory(
        track: Track,
        source: PlaybackSource = if (track.isDownloaded) PlaybackSource.OFFLINE else PlaybackSource.ONLINE
    ) {
        // Remove any existing entry of the exact same track so it moves to top
        historyCache.removeAll { it.track.id == track.id }

        // Create new history entry
        val item = HistoryItem(
            track = track.copy(isFavorite = isFavorite(track.id)),
            playedAtTimestamp = System.currentTimeMillis(),
            source = source,
        )
        historyCache.add(0, item)

        // Trim history list to max capacity
        if (historyCache.size > MAX_HISTORY_ENTRIES) {
            historyCache.subList(MAX_HISTORY_ENTRIES, historyCache.size).clear()
        }

        persistHistoryToDisk()
    }

    /**
     * Clears all recorded playback history.
     */
    @Synchronized
    fun clearHistory() {
        historyCache.clear()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun loadHistoryFromDisk() {
        historyCache.clear()

        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val trackObj = obj.getJSONObject("track")
                val playedAt = obj.optLong("playedAt", System.currentTimeMillis())
                val sourceName = obj.optString("source", PlaybackSource.ONLINE.name)
                val source = try {
                    PlaybackSource.valueOf(sourceName)
                } catch (e: Exception) {
                    PlaybackSource.ONLINE
                }

                val track = deserializeTrack(trackObj)
                historyCache.add(
                    HistoryItem(
                        track = track.copy(isFavorite = isFavorite(track.id)),
                        playedAtTimestamp = playedAt,
                        source = source,
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistHistoryToDisk() {
        try {
            val jsonArray = JSONArray()
            for (item in historyCache) {
                val itemObj = JSONObject().apply {
                    put("track", serializeTrack(item.track))
                    put("playedAt", item.playedAtTimestamp)
                    put("source", item.source.name)
                }
                jsonArray.put(itemObj)
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================================================
    // JSON SERIALIZATION HELPERS
    // ==========================================================================

    companion object {
        private const val PREFS_NAME = "lunify_library_prefs"
        private const val KEY_FAVORITES = "key_favorite_tracks_json"
        private const val KEY_HISTORY = "key_history_json"
        private const val MAX_HISTORY_ENTRIES = 100

        fun serializeTrack(track: Track): JSONObject {
            return JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationText", track.durationText)
                put("durationMs", track.durationMs)
                put("thumbnailUrl", track.thumbnailUrl)
                put("isVideo", track.isVideo)
                put("isDownloaded", track.isDownloaded)
                put("isFavorite", track.isFavorite)
                put("contentUri", track.contentUri)
                put("sizeBytes", track.sizeBytes)
            }
        }

        fun deserializeTrack(obj: JSONObject): Track {
            return Track(
                id = obj.getString("id"),
                title = obj.getString("title"),
                artist = obj.optString("artist", "Unknown Artist"),
                album = obj.optString("album", ""),
                durationText = obj.optString("durationText", "0:00"),
                durationMs = obj.optLong("durationMs", 0L),
                thumbnailUrl = obj.optString("thumbnailUrl", ""),
                isVideo = obj.optBoolean("isVideo", false),
                isDownloaded = obj.optBoolean("isDownloaded", false),
                isFavorite = obj.optBoolean("isFavorite", false),
                contentUri = obj.optString("contentUri", ""),
                sizeBytes = obj.optLong("sizeBytes", 0L),
            )
        }
    }
}
