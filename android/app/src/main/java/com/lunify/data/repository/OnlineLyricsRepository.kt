package com.lunify.data.repository

import android.util.Log
import com.lunify.domain.model.SyncedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ==============================================================================
 * Lunify - Online Lyrics Repository (OnlineLyricsRepository.kt)
 * ==============================================================================
 *
 * Hey friend! This repository fetches real, synchronized lyrics from LRCLIB,
 * the community-powered open-source lyrics database used by top music apps
 * like SimpMusic and Spotube.
 *
 * When an online song or an offline track without a local `.lrc` file plays,
 * this repository queries LRCLIB using the song's clean title, artist name,
 * and duration.
 *
 * It parses the response into our [SyncedLyrics] domain model with accurate
 * timestamps, allowing the FullscreenPlayer to scroll and highlight the active
 * line seamlessly as the music plays!
 * ==============================================================================
 */
class OnlineLyricsRepository(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {

    companion object {
        private const val TAG = "OnlineLyricsRepo"
        private const val LRCLIB_GET_URL = "https://lrclib.net/api/get"
        private const val LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
        private const val USER_AGENT = "LunifyMusicApp/1.0 (https://github.com/lunify)"
    }

    /**
     * Attempts to fetch synchronized lyrics for a song.
     *
     * @param trackId Unique ID of the track for caching and mapping.
     * @param title Title of the song.
     * @param artist Performing artist name.
     * @param durationSeconds Duration of the track in seconds (optional, improves match accuracy).
     * @return [SyncedLyrics] if found, or null if no lyrics are available.
     */
    suspend fun fetchLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Int = 0,
    ): SyncedLyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        if (cleanTitle.isBlank()) return@withContext null

        // Attempt 1: Exact match via /api/get
        val exactResult = queryExactLrcLib(trackId, cleanTitle, cleanArtist, durationSeconds)
        if (exactResult != null) return@withContext exactResult

        // Attempt 2: Search match via /api/search with query string
        querySearchLrcLib(trackId, cleanTitle, cleanArtist)
    }

    /**
     * Calls `GET https://lrclib.net/api/get` with exact parameters.
     */
    private fun queryExactLrcLib(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Int,
    ): SyncedLyrics? {
        val urlBuilder = LRCLIB_GET_URL.toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", title)
        if (artist.isNotBlank()) {
            urlBuilder.addQueryParameter("artist_name", artist)
        }
        if (durationSeconds > 0) {
            urlBuilder.addQueryParameter("duration", durationSeconds.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .addHeader("User-Agent", USER_AGENT)
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) return null

            val json = JSONObject(bodyString)
            parseLrcLibJson(json, trackId)
        } catch (e: Exception) {
            Log.w(TAG, "Exact lyrics lookup failed for '$title - $artist': ${e.message}")
            null
        }
    }

    /**
     * Calls `GET https://lrclib.net/api/search?q=...` as a fallback search.
     */
    private fun querySearchLrcLib(
        trackId: String,
        title: String,
        artist: String,
    ): SyncedLyrics? {
        val query = if (artist.isNotBlank()) "$title $artist" else title
        val urlBuilder = LRCLIB_SEARCH_URL.toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("q", query)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .addHeader("User-Agent", USER_AGENT)
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) return null

            val jsonArray = JSONArray(bodyString)
            if (jsonArray.length() == 0) return null

            // Prioritize items that have syncedLyrics populated
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val syncedText = item.optString("syncedLyrics").trim()
                if (syncedText.isNotBlank()) {
                    return parseLrcLibJson(item, trackId)
                }
            }

            // Fallback to first item with plain lyrics
            val firstItem = jsonArray.optJSONObject(0)
            if (firstItem != null) {
                parseLrcLibJson(firstItem, trackId)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fuzzy lyrics search failed for '$query': ${e.message}")
            null
        }
    }

    /**
     * Parses the LRCLIB JSON object containing syncedLyrics or plainLyrics.
     */
    private fun parseLrcLibJson(json: JSONObject, trackId: String): SyncedLyrics? {
        val syncedLyrics = json.optString("syncedLyrics").trim()
        if (syncedLyrics.isNotBlank()) {
            val parsed = LyricsRepository.parseLrcContent(trackId, syncedLyrics)
            if (parsed != null && parsed.lines.isNotEmpty()) {
                return parsed
            }
        }

        // If no synchronized timestamps are available, fallback to plain text lines
        val plainLyrics = json.optString("plainLyrics").trim()
        if (plainLyrics.isNotBlank()) {
            val lines = plainLyrics.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapIndexed { index, lineText ->
                    // Approximate timestamps spaced by 3 seconds for readable scrolling
                    com.lunify.domain.model.LyricLine(
                        timestampMs = index * 3000L,
                        text = lineText,
                    )
                }

            if (lines.isNotEmpty()) {
                return SyncedLyrics(
                    trackId = trackId,
                    lines = lines,
                )
            }
        }

        return null
    }

    /**
     * Cleans up common noise from song titles (like "(Official Video)", "[Lyrics]", "(ft. Drake)").
     */
    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("(?i)\\[.*?\\]"), "")
            .replace(Regex("(?i)\\(official video.*?\\)"), "")
            .replace(Regex("(?i)\\(official music video.*?\\)"), "")
            .replace(Regex("(?i)\\(official audio.*?\\)"), "")
            .replace(Regex("(?i)\\(lyrics.*?\\)"), "")
            .replace(Regex("(?i)\\(visualizer.*?\\)"), "")
            .replace(Regex("(?i)\\(audio.*?\\)"), "")
            .replace(Regex("(?i)\\(remix.*?\\)"), "")
            .replace(Regex("(?i)\\|.*$"), "")
            .replace(" - Single", "")
            .trim()
    }
}
