package com.lunify.data.remote

import android.util.Log
import com.lunify.domain.model.Album
import com.lunify.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ==============================================================================
 * Lunify - YouTube Music InnerTube API Client (YouTubeMusicClient.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to our direct communicator with YouTube Music's backend!
 *
 * How does SimpMusic and Lunify talk to YouTube Music?
 * YouTube Music uses an internal API called "InnerTube". The base endpoint is:
 * https://music.youtube.com/youtubei/v1/
 *
 * When the official YouTube Music web client or mobile app talks to the server,
 * it sends a JSON payload with a `context` object containing client information
 * (we use clientName: "WEB_REMIX" and version "1.20241028.01.00").
 *
 * In this file, we handle:
 * 1. Searching tracks, videos, and albums with filter params.
 * 2. Instant search suggestions as the user types in the search bar.
 * 3. Browsing the home feed for "Quick picks" and "Trending Now" albums.
 * 4. Fetching the "Up Next" queue / radio stream for continuous music playback!
 *
 * Everything is fully error-handled, non-blocking with coroutines, and parses
 * JSON safely with robust fallbacks so the UI never crashes on malformed data.
 * ==============================================================================
 */
class YouTubeMusicClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {

    companion object {
        private const val TAG = "YouTubeMusicClient"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Filter parameters recognized by YouTube Music's search endpoint
        // Songs only filter
        const val FILTER_SONGS = "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D"
        // Albums only filter
        const val FILTER_ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        // Music videos filter
        const val FILTER_VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
        // Community and official playlists
        const val FILTER_PLAYLISTS = "EgWKAQIoAWoKEAkQChAFEAMQBA%3D%3D"

        // Browser headers mirroring modern Chrome on desktop
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    /**
     * Builds the standard InnerTube request body JSON with client context.
     * YouTube expects every request to identify the client app and locale.
     */
    private fun createBaseContext(): JSONObject {
        val client = JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20241028.01.00")
            put("hl", "en")
            put("gl", "US")
        }
        return JSONObject().apply {
            put("client", client)
        }
    }

    /**
     * Executes a POST request to the given InnerTube endpoint and returns the raw JSON string.
     */
    private suspend fun postInnerTube(endpoint: String, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val fullUrl = BASE_URL + endpoint
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(fullUrl)
            .post(requestBody)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://music.youtube.com/")
            .addHeader("Origin", "https://music.youtube.com")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("InnerTube call to '$endpoint' failed with HTTP $code")
        }

        response.body?.string() ?: throw IOException("Empty response body from '$endpoint'")
    }

    /**
     * Searches YouTube Music for songs matching [query].
     *
     * @param query Search query text entered by the user.
     * @param filter Optional search filter parameter (defaults to [FILTER_SONGS]).
     * @return Result containing parsed list of [Track] items.
     */
    suspend fun searchSongs(
        query: String,
        filter: String? = FILTER_SONGS,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", createBaseContext())
                put("query", query)
                if (!filter.isNullOrEmpty()) {
                    put("params", filter)
                }
            }

            val jsonString = postInnerTube("search", payload)
            val root = JSONObject(jsonString)
            val tracks = parseSearchTrackResults(root)
            Result.success(tracks)
        } catch (e: Throwable) {
            Log.e(TAG, "Search failed for '$query': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Searches YouTube Music for albums matching [query].
     */
    suspend fun searchAlbums(query: String): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", createBaseContext())
                put("query", query)
                put("params", FILTER_ALBUMS)
            }

            val jsonString = postInnerTube("search", payload)
            val root = JSONObject(jsonString)
            val albums = parseSearchAlbumResults(root)
            Result.success(albums)
        } catch (e: Throwable) {
            Log.e(TAG, "Album search failed for '$query': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches real-time search query completions as the user types.
     * E.g. "ed sh" -> ["ed sheeran", "ed sheeran songs", "ed sheeran shape of you"]
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val payload = JSONObject().apply {
                put("context", createBaseContext())
                put("input", query)
            }

            val jsonString = postInnerTube("music/get_search_suggestions", payload)
            val root = JSONObject(jsonString)
            val suggestions = mutableListOf<String>()

            val contents = root.optJSONArray("contents") ?: return@withContext emptyList()
            if (contents.length() > 0) {
                val firstSection = contents.optJSONObject(0)?.optJSONObject("searchSuggestionsSectionRenderer")
                val itemContents = firstSection?.optJSONArray("contents") ?: JSONArray()
                for (i in 0 until itemContents.length()) {
                    val suggestionObj = itemContents.optJSONObject(i)?.optJSONObject("searchSuggestionRenderer")
                    val runs = suggestionObj?.optJSONObject("suggestion")?.optJSONArray("runs") ?: JSONArray()
                    val textBuilder = StringBuilder()
                    for (j in 0 until runs.length()) {
                        textBuilder.append(runs.optJSONObject(j)?.optString("text").orEmpty())
                    }
                    val suggestionText = textBuilder.toString().trim()
                    if (suggestionText.isNotEmpty()) {
                        suggestions.add(suggestionText)
                    }
                }
            }
            suggestions
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fetch suggestions for '$query': ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches the YouTube Music Home page and extracts "Quick picks" tracks.
     * This feeds the Home screen with trending and recommended songs.
     */
    suspend fun getHomeQuickPicks(): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", "FEmusic_home")
            }

            val jsonString = postInnerTube("browse", payload)
            val root = JSONObject(jsonString)
            val tracks = parseHomeQuickPicks(root)
            Result.success(tracks)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch home quick picks: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches trending albums and releases from the YouTube Music Home page.
     */
    suspend fun getHomeTrendingAlbums(): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", "FEmusic_home")
            }

            val jsonString = postInnerTube("browse", payload)
            val root = JSONObject(jsonString)
            val albums = parseHomeAlbums(root)
            Result.success(albums)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch home trending albums: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches the Up-Next queue / related radio songs for a given [videoId].
     * This allows Lunify to auto-enqueue related songs when the current track finishes.
     */
    suspend fun getRelatedQueue(videoId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", createBaseContext())
                put("videoId", videoId)
            }

            val jsonString = postInnerTube("next", payload)
            val root = JSONObject(jsonString)
            val tracks = parseNextQueue(root)
            Result.success(tracks)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fetch related queue for '$videoId': ${e.message}")
            Result.failure(e)
        }
    }

    // ==============================================================================
    // Internal JSON Response Parsers
    // ==============================================================================

    /**
     * Extracts [Track] items from a YouTube Music search response JSON.
     * Traverses tabbedSearchResultsRenderer -> sectionListRenderer -> musicShelfRenderer.
     */
    private fun parseSearchTrackResults(root: JSONObject): List<Track> {
        val tracks = mutableListOf<Track>()

        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return tracks

        if (tabs.length() == 0) return tracks
        val firstTab = tabs.optJSONObject(0)
        val sections = firstTab?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return tracks

        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue

            // Case A: Filtered search results inside musicShelfRenderer
            val shelf = section.optJSONObject("musicShelfRenderer")
            if (shelf != null) {
                val contents = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until contents.length()) {
                    val item = contents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveListItemToTrack(item)?.let { tracks.add(it) }
                }
            }

            // Case B: General search results inside itemSectionRenderer
            val itemSection = section.optJSONObject("itemSectionRenderer")
            if (itemSection != null) {
                val itemContents = itemSection.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until itemContents.length()) {
                    val item = itemContents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveListItemToTrack(item)?.let { tracks.add(it) }
                }
            }
        }

        return tracks
    }

    /**
     * Extracts [Album] items from search results.
     */
    private fun parseSearchAlbumResults(root: JSONObject): List<Album> {
        val albums = mutableListOf<Album>()

        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return albums

        if (tabs.length() == 0) return albums
        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return albums

        for (i in 0 until sections.length()) {
            val shelf = sections.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue
            val contents = shelf.optJSONArray("contents") ?: JSONArray()
            for (j in 0 until contents.length()) {
                val item = contents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val flexColumns = item.optJSONArray("flexColumns") ?: JSONArray()
                if (flexColumns.length() < 2) continue

                val title = flexColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: continue

                val artistRuns = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: JSONArray()

                val artistBuilder = StringBuilder()
                for (k in 0 until artistRuns.length()) {
                    artistBuilder.append(artistRuns.optJSONObject(k)?.optString("text").orEmpty())
                }

                val browseId = item.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId").orEmpty()

                val thumb = extractThumbnail(item)

                albums.add(
                    Album(
                        id = browseId.ifEmpty { title },
                        title = title,
                        artist = artistBuilder.toString().trim(),
                        thumbnailUrl = thumb,
                    ),
                )
            }
        }
        return albums
    }

    /**
     * Parses the Home feed's "Quick picks" shelf into real playable [Track] objects.
     */
    private fun parseHomeQuickPicks(root: JSONObject): List<Track> {
        val tracks = mutableListOf<Track>()

        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return tracks

        if (tabs.length() == 0) return tracks
        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return tracks

        for (i in 0 until sections.length()) {
            val carousel = sections.optJSONObject(i)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val headerTitle = carousel.optJSONObject("header")
                ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()

            // We match the "Quick picks" carousel or the first available carousel shelf
            if (headerTitle.contains("Quick picks", ignoreCase = true) || tracks.isEmpty()) {
                val contents = carousel.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until contents.length()) {
                    val item = contents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveListItemToTrack(item)?.let { tracks.add(it) }
                }
                if (tracks.isNotEmpty()) {
                    break
                }
            }
        }

        return tracks
    }

    /**
     * Parses album carousels from the Home feed (e.g., "Trending Now", "Hits").
     */
    private fun parseHomeAlbums(root: JSONObject): List<Album> {
        val albums = mutableListOf<Album>()

        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return albums

        if (tabs.length() == 0) return albums
        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return albums

        // Skip the first carousel (which is usually Quick picks) and parse album/playlist carousels
        for (i in 1 until sections.length()) {
            val carousel = sections.optJSONObject(i)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val contents = carousel.optJSONArray("contents") ?: JSONArray()
            for (j in 0 until contents.length()) {
                val item = contents.optJSONObject(j) ?: continue
                // Two-row item renderer is commonly used for album and playlist cards on the home page
                val twoRow = item.optJSONObject("musicTwoRowItemRenderer")
                if (twoRow != null) {
                    val title = twoRow.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: continue

                    val subtitleRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs") ?: JSONArray()
                    val subtitleBuilder = StringBuilder()
                    for (k in 0 until subtitleRuns.length()) {
                        subtitleBuilder.append(subtitleRuns.optJSONObject(k)?.optString("text").orEmpty())
                    }

                    val browseId = twoRow.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId").orEmpty()

                    val thumb = extractThumbnailFromTwoRow(twoRow)

                    albums.add(
                        Album(
                            id = browseId.ifEmpty { title },
                            title = title,
                            artist = subtitleBuilder.toString().trim(),
                            thumbnailUrl = thumb,
                        ),
                    )
                }
            }
            if (albums.isNotEmpty()) {
                break
            }
        }

        return albums
    }

    /**
     * Parses the Next Queue returned by the `/next` endpoint.
     */
    private fun parseNextQueue(root: JSONObject): List<Track> {
        val tracks = mutableListOf<Track>()

        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs") ?: return tracks

        if (tabs.length() == 0) return tracks
        val queueRenderer = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicQueueRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("playlistPanelRenderer") ?: return tracks

        val contents = queueRenderer.optJSONArray("contents") ?: JSONArray()
        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
            val videoId = item.optString("videoId")
            if (videoId.isEmpty()) continue

            val title = item.optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text") ?: "Unknown Title"

            val bylineRuns = item.optJSONObject("longBylineText")?.optJSONArray("runs") ?: JSONArray()
            val artistBuilder = StringBuilder()
            for (j in 0 until bylineRuns.length()) {
                artistBuilder.append(bylineRuns.optJSONObject(j)?.optString("text").orEmpty())
            }

            val duration = item.optJSONObject("lengthText")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text") ?: "0:00"

            val thumb = extractThumbnailFromPanel(item)

            tracks.add(
                Track(
                    id = videoId,
                    title = title,
                    artist = artistBuilder.toString().trim(),
                    durationText = duration,
                    durationMs = parseDurationStringToMs(duration),
                    thumbnailUrl = thumb,
                    isDownloaded = false,
                    contentUri = "", // Will be resolved dynamically by YouTubeExtractor
                ),
            )
        }

        return tracks
    }

    /**
     * Converts a single [musicResponsiveListItemRenderer] JSON node into our clean [Track] model.
     */
    private fun parseResponsiveListItemToTrack(item: JSONObject): Track? {
        val playlistItemData = item.optJSONObject("playlistItemData")
        var videoId = playlistItemData?.optString("videoId").orEmpty()

        // Fallback: Check navigationEndpoint.watchEndpoint.videoId if playlistItemData is missing
        if (videoId.isEmpty()) {
            videoId = item.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId").orEmpty()
        }
        if (videoId.isEmpty()) return null

        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        if (flexColumns.length() == 0) return null

        // Column 0: Track Title
        val col0Runs = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs") ?: return null
        val title = col0Runs.optJSONObject(0)?.optString("text").orEmpty()
        if (title.isEmpty()) return null

        // Column 1: Artist, Album, Duration separated by bullet points
        var artist = "Unknown Artist"
        var album = ""
        var durationText = "0:00"

        if (flexColumns.length() > 1) {
            val col1Runs = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: JSONArray()

            val parts = mutableListOf<String>()
            for (k in 0 until col1Runs.length()) {
                val text = col1Runs.optJSONObject(k)?.optString("text").orEmpty()
                if (text.isNotBlank() && text != "•") {
                    parts.add(text.trim())
                }
            }

            if (parts.isNotEmpty()) {
                artist = parts[0]
            }
            if (parts.size >= 2) {
                // If the second part looks like mm:ss or has letters, check if it's album or duration
                val second = parts[1]
                if (second.contains(":") && second.length <= 6) {
                    durationText = second
                } else {
                    album = second
                }
            }
            if (parts.size >= 3) {
                val third = parts.last()
                if (third.contains(":") && third.length <= 6) {
                    durationText = third
                }
            }
        }

        val thumbnail = extractThumbnail(item)
        val durationMs = parseDurationStringToMs(durationText)

        return Track(
            id = videoId,
            title = title,
            artist = artist,
            album = album,
            durationText = durationText,
            durationMs = durationMs,
            thumbnailUrl = thumbnail,
            isDownloaded = false,
            contentUri = "", // Dynamic stream extraction
        )
    }

    /**
     * Extracts the highest quality thumbnail URL from a musicResponsiveListItemRenderer.
     */
    private fun extractThumbnail(item: JSONObject): String {
        val thumbs = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails") ?: return ""

        if (thumbs.length() == 0) return ""
        val rawUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        // Upgrade low resolution thumbnails (e.g. w120-h120) to crystal-clear 544x544
        return rawUrl.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }

    private fun extractThumbnailFromTwoRow(twoRow: JSONObject): String {
        val thumbs = twoRow.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails") ?: return ""

        if (thumbs.length() == 0) return ""
        val rawUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        return rawUrl.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }

    private fun extractThumbnailFromPanel(panel: JSONObject): String {
        val thumbs = panel.optJSONObject("thumbnail")
            ?.optJSONObject("croppedSquareThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails") ?: return ""

        if (thumbs.length() == 0) return ""
        val rawUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        return rawUrl.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }

    /**
     * Parses "m:ss" or "h:mm:ss" duration strings into milliseconds.
     */
    private fun parseDurationStringToMs(durationStr: String): Long {
        if (durationStr.isBlank() || durationStr == "0:00") return 0L
        val parts = durationStr.split(":")
        return try {
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toLong()
                    (minutes * 60 + seconds) * 1000L
                }
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toLong()
                    (hours * 3600 + minutes * 60 + seconds) * 1000L
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
