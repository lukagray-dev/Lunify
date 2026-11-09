package com.lunify.data.local

import android.content.Context
import android.content.SharedPreferences
import com.lunify.domain.model.HistoryItem
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.Playlist
import com.lunify.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * ==============================================================================
 * Lunify - Library Persistent Storage (LibraryStorage.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the local persistence module for Lunify.
 *
 * WHAT DOES THIS CLASS DO?
 * ------------------------
 * Manages all user-level customizations and playback analytics that must survive
 * app reboots and app updates:
 * 1. Favorites: Track IDs and cached metadata for favorited songs.
 * 2. Playback History: Chronological log of played tracks with OFFLINE / ONLINE tags.
 * 3. Play Counts: Number of times each track has been played (used by "Times played" sort).
 * 4. Custom Metadata Playlists: User-created playlists containing track IDs.
 *    (Folders on device are recognized as folder playlists without copying files,
 *    and user custom playlists are stored as metadata).
 * ==============================================================================
 */
class LibraryStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // In-memory cache for ultra-fast lookups
    private val favoriteIdsCache = mutableSetOf<String>()
    private val favoriteTracksCache = mutableListOf<Track>()
    private val historyCache = mutableListOf<HistoryItem>()
    private val playCountsCache = mutableMapOf<String, Int>()
    private val customPlaylistsCache = mutableListOf<Playlist>()

    init {
        loadFavoritesFromDisk()
        loadHistoryFromDisk()
        loadPlayCountsFromDisk()
        loadPlaylistsFromDisk()
    }

    // ==========================================================================
    // FAVORITES MANAGEMENT
    // ==========================================================================

    @Synchronized
    fun isFavorite(trackId: String): Boolean {
        return favoriteIdsCache.contains(trackId)
    }

    @Synchronized
    fun getFavoriteTracks(): List<Track> {
        return favoriteTracksCache.toList()
    }

    @Synchronized
    fun toggleFavorite(track: Track): Boolean {
        val nowFavorite: Boolean
        if (favoriteIdsCache.contains(track.id)) {
            favoriteIdsCache.remove(track.id)
            favoriteTracksCache.removeAll { it.id == track.id }
            nowFavorite = false
        } else {
            favoriteIdsCache.add(track.id)
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
    // PLAY COUNT MANAGEMENT (FOR "TIMES PLAYED" SORTING)
    // ==========================================================================

    @Synchronized
    fun getPlayCount(trackId: String): Int {
        return playCountsCache[trackId] ?: 0
    }

    @Synchronized
    fun recordPlay(trackId: String) {
        val current = playCountsCache[trackId] ?: 0
        playCountsCache[trackId] = current + 1
        persistPlayCountsToDisk()
    }

    private fun loadPlayCountsFromDisk() {
        playCountsCache.clear()
        val jsonString = prefs.getString(KEY_PLAY_COUNTS, null) ?: return
        try {
            val obj = JSONObject(jsonString)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                playCountsCache[key] = obj.getInt(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistPlayCountsToDisk() {
        try {
            val obj = JSONObject()
            for ((key, count) in playCountsCache) {
                obj.put(key, count)
            }
            prefs.edit().putString(KEY_PLAY_COUNTS, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================================================
    // HISTORY MANAGEMENT
    // ==========================================================================

    @Synchronized
    fun getPlaybackHistory(): List<HistoryItem> {
        return historyCache.toList()
    }

    @Synchronized
    fun addToHistory(
        track: Track,
        source: PlaybackSource = if (track.isDownloaded) PlaybackSource.OFFLINE else PlaybackSource.ONLINE
    ) {
        // Increment play count whenever song is played
        recordPlay(track.id)

        historyCache.removeAll { it.track.id == track.id }

        val item = HistoryItem(
            track = track.copy(
                isFavorite = isFavorite(track.id),
                playCount = getPlayCount(track.id)
            ),
            playedAtTimestamp = System.currentTimeMillis(),
            source = source,
        )
        historyCache.add(0, item)

        if (historyCache.size > MAX_HISTORY_ENTRIES) {
            historyCache.subList(MAX_HISTORY_ENTRIES, historyCache.size).clear()
        }

        persistHistoryToDisk()
    }

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
                        track = track.copy(
                            isFavorite = isFavorite(track.id),
                            playCount = getPlayCount(track.id)
                        ),
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
    // CUSTOM PLAYLISTS MANAGEMENT (STORED AS METADATA)
    // ==========================================================================

    @Synchronized
    fun getCustomPlaylists(): List<Playlist> {
        return customPlaylistsCache.toList()
    }

    @Synchronized
    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled Playlist" },
            trackIds = emptyList(),
            isFolderPlaylist = false,
            createdAt = System.currentTimeMillis(),
        )
        customPlaylistsCache.add(0, playlist)
        persistPlaylistsToDisk()
        return playlist
    }

    @Synchronized
    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val index = customPlaylistsCache.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val existing = customPlaylistsCache[index]
            if (!existing.trackIds.contains(trackId)) {
                customPlaylistsCache[index] = existing.copy(
                    trackIds = existing.trackIds + trackId
                )
                persistPlaylistsToDisk()
            }
        }
    }

    @Synchronized
    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val index = customPlaylistsCache.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val existing = customPlaylistsCache[index]
            customPlaylistsCache[index] = existing.copy(
                trackIds = existing.trackIds.filter { it != trackId }
            )
            persistPlaylistsToDisk()
        }
    }

    @Synchronized
    fun deletePlaylist(playlistId: String) {
        customPlaylistsCache.removeAll { it.id == playlistId }
        persistPlaylistsToDisk()
    }

    private fun loadPlaylistsFromDisk() {
        customPlaylistsCache.clear()
        val jsonString = prefs.getString(KEY_PLAYLISTS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val isFolder = obj.optBoolean("isFolder", false)
                val folderPath = obj.optString("folderPath", "")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val trackIdsArray = obj.getJSONArray("trackIds")
                val trackIds = mutableListOf<String>()
                for (j in 0 until trackIdsArray.length()) {
                    trackIds.add(trackIdsArray.getString(j))
                }

                customPlaylistsCache.add(
                    Playlist(
                        id = id,
                        name = name,
                        trackIds = trackIds,
                        isFolderPlaylist = isFolder,
                        folderPath = folderPath,
                        createdAt = createdAt,
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistPlaylistsToDisk() {
        try {
            val jsonArray = JSONArray()
            for (p in customPlaylistsCache) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("isFolder", p.isFolderPlaylist)
                    put("folderPath", p.folderPath)
                    put("createdAt", p.createdAt)
                    val tracksArr = JSONArray()
                    for (tid in p.trackIds) {
                        tracksArr.put(tid)
                    }
                    put("trackIds", tracksArr)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
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
        private const val KEY_PLAY_COUNTS = "key_play_counts_json"
        private const val KEY_PLAYLISTS = "key_playlists_json"
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
                put("filePath", track.filePath)
                put("folderName", track.folderName)
                put("dateAdded", track.dateAdded)
                put("playCount", track.playCount)
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
                filePath = obj.optString("filePath", ""),
                folderName = obj.optString("folderName", ""),
                dateAdded = obj.optLong("dateAdded", 0L),
                playCount = obj.optInt("playCount", 0),
            )
        }
    }
}
