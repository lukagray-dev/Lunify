package com.lunify.data.remote

import android.util.Log
import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.stream.AudioStream
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ==============================================================================
 * Lunify - YouTube Stream Extractor (YouTubeExtractor.kt)
 * ==============================================================================
 *
 * Hey friend! This singleton is our stream resolution powerhouse.
 *
 * When a user taps a song from Search or Home (e.g., "Shape of You" with videoId
 * "xTvyyoF_LZY"), we don't have a direct MP3 or AAC file URL yet. YouTube serves
 * media streams through encrypted tokens and expiring CDN URLs.
 *
 * This class uses the PipePipe/NewPipe extractor:
 * 1. It initializes the downloader engine once.
 * 2. It queries YouTube Music's player for the video ID.
 * 3. It filters through all available audio-only streams (Opus 160kbps, M4A 128kbps, etc.).
 * 4. It picks the best audio format and returns a direct HTTPS streaming URL
 *    that ExoPlayer can buffer, stream, and cache immediately!
 * ==============================================================================
 */
object YouTubeExtractor {

    private const val TAG = "YouTubeExtractor"

    @Volatile
    private var isInitialized = false

    private val initLock = Any()

    /**
     * Ensures NewPipe extractor and its OkHttp downloader are initialized.
     * Safe to call repeatedly; only executes the setup once.
     */
    fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(initLock) {
                if (!isInitialized) {
                    try {
                        val downloader = NewPipeDownloaderImpl()
                        NewPipe.init(downloader)
                        // By leaving localDecoder null, PipePipe automatically leverages the
                        // server-side challenge solver (api.pipepipe.dev) whenever signatures or
                        // n-throttling challenges are encountered.
                        YoutubeApiDecoder.setLocalDecoder(null)
                        isInitialized = true
                        Log.i(TAG, "NewPipeExtractor initialized successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize NewPipeExtractor", e)
                    }
                }
            }
        }
    }

    /**
     * Resolves the direct audio stream URL for a YouTube [videoId] conforming to [quality].
     *
     * @param videoId The 11-character YouTube video ID (e.g., "dQw4w9WgXcQ").
     * @param quality The desired audio bitrate tier (High, Balanced, Data Saver).
     * @return Result containing the direct HTTPS audio streaming URL, or an error.
     */
    suspend fun getAudioStreamUrl(
        videoId: String,
        quality: com.lunify.data.local.StreamingQuality = com.lunify.data.local.StreamingQuality.HIGH,
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureInitialized()

        try {
            // First attempt: YouTube Music endpoint
            val musicWatchUrl = "https://music.youtube.com/watch?v=$videoId"
            var streamInfo: StreamInfo? = runCatching {
                StreamInfo.getInfo(ServiceList.YouTube, musicWatchUrl)
            }.getOrNull()

            // Fallback attempt: Standard YouTube watch URL if music endpoint had no streams
            if (streamInfo == null || streamInfo.audioStreams.isNullOrEmpty()) {
                val standardWatchUrl = "https://www.youtube.com/watch?v=$videoId"
                streamInfo = StreamInfo.getInfo(ServiceList.YouTube, standardWatchUrl)
            }

            val validAudioStreams: List<AudioStream> = streamInfo.audioStreams.orEmpty()
                .filter { it.content != null && it.content.isNotEmpty() }

            if (validAudioStreams.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No audio streams found for videoId: $videoId"))
            }

            // Select stream based on user's configured audio fidelity
            val selectedAudioStream = when (quality) {
                com.lunify.data.local.StreamingQuality.HIGH -> {
                    // Maximum bitrate stream (typically itag 251 Opus ~160kbps)
                    validAudioStreams.maxByOrNull { it.averageBitrate } ?: validAudioStreams.first()
                }
                com.lunify.data.local.StreamingQuality.BALANCED -> {
                    // Balanced stream closest to ~128 kbps (itag 140 M4A or 128kbps Opus)
                    validAudioStreams.minByOrNull { kotlin.math.abs(it.averageBitrate - 128000) } ?: validAudioStreams.first()
                }
                com.lunify.data.local.StreamingQuality.DATA_SAVER -> {
                    // Low bandwidth stream (~50-70 kbps) for data saving
                    val lowOptions = validAudioStreams.filter { it.averageBitrate in 40000..96000 }
                    if (lowOptions.isNotEmpty()) {
                        lowOptions.minByOrNull { it.averageBitrate } ?: validAudioStreams.minByOrNull { it.averageBitrate } ?: validAudioStreams.first()
                    } else {
                        validAudioStreams.minByOrNull { it.averageBitrate } ?: validAudioStreams.first()
                    }
                }
            }

            val streamUrl = selectedAudioStream.content
                ?: return@withContext Result.failure(IllegalStateException("Stream content URL was null for videoId: $videoId"))

            Log.d(TAG, "Resolved stream for $videoId (quality=$quality, format=${selectedAudioStream.format}, bitrate=${selectedAudioStream.averageBitrate}bps)")
            Result.success(streamUrl)
        } catch (e: Throwable) {
            Log.e(TAG, "Error resolving audio stream for $videoId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
