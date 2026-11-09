package com.lunify.player.download

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ==============================================================================
 * Lunify - High-Throughput Chunked Audio Downloader (ChunkedAudioDownloader.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the download acceleration engine of Lunify!
 *
 * WHY WAS DOWNLOADING SO SLOW PREVIOUSLY (~2 MINUTES FOR A 4MB TRACK)?
 * -------------------------------------------------------------------
 * YouTube's Google Video CDN (`googlevideo.com`) continuously monitors media streaming.
 * When a client initiates an open-ended HTTP GET request without byte ranges (like a
 * standard file download), YouTube's servers identify the connection as a media stream
 * and artificially clamp bandwidth down to the real-time audio playback bitrate (~32 KB/s)!
 *
 * On a 30 MB/s connection, downloading a 4MB song at 32 KB/s takes over 120 seconds!
 *
 * HOW THIS CHUNKED DOWNLOADER SOLVES IT:
 * ---------------------------------------
 * 1. Discrete 1 MB Range Requests:
 *    By chunking the download into 1 MB slices using `Range: bytes=start-end`, YouTube's
 *    CDN treats every chunk request as a new burst transfer. It serves each 1 MB chunk
 *    at unthrottled line speed (typically 20–30+ MB/s), taking just ~30 milliseconds
 *    per chunk!
 *
 * 2. Persistent HTTP Connection Reuse:
 *    Using an [OkHttpClient] backed by an active [ConnectionPool], TCP and TLS
 *    handshakes are negotiated once on the first chunk and reused across all subsequent
 *    range requests via HTTP keep-alive.
 *
 * 3. Automatic Total-Size Discovery:
 *    Resolves the exact file size from the URL's `clen` parameter or the HTTP 206
 *    `Content-Range` header (e.g. `bytes 0-1048575/4251023`).
 *
 * 4. Resilient Per-Chunk Retry Policy:
 *    If an individual chunk encounters a transient socket timeout or network blip,
 *    it retries up to 3 times with exponential backoff before failing.
 *
 * 5. Corrupt File Cleanup:
 *    If a download is aborted or fails, partially downloaded files on disk are
 *    automatically deleted so corrupt tracks never linger in storage.
 * ==============================================================================
 */
open class ChunkedAudioDownloader(
    private val okHttpClient: OkHttpClient = createDefaultClient(),
) {
    companion object {
        private const val TAG = "ChunkedDownloader"

        // 1 MB chunks allow maximum burst speed without triggering YouTube's throttle timer
        const val DEFAULT_CHUNK_SIZE_BYTES = 1024L * 1024L // 1 MB

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        /**
         * Creates an OkHttpClient optimized for persistent keep-alive chunk streaming.
         */
        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        /**
         * Parses the total byte length from an HTTP Content-Range header.
         * Example: "bytes 0-1048575/4251023" -> 4251023L
         *
         * @param contentRange The raw Content-Range header value.
         * @return Total length in bytes, or -1 if unknown or malformed.
         */
        fun parseTotalBytesFromContentRange(contentRange: String?): Long {
            if (contentRange.isNullOrBlank()) return -1L
            val slashIndex = contentRange.lastIndexOf('/')
            if (slashIndex != -1 && slashIndex + 1 < contentRange.length) {
                val totalPart = contentRange.substring(slashIndex + 1).trim()
                return totalPart.toLongOrNull() ?: -1L
            }
            return -1L
        }

        /**
         * Calculates the ending byte offset for a range slice.
         *
         * @param startByte The starting byte index.
         * @param chunkSize The chunk size in bytes.
         * @param totalBytes Total file size if known, or -1.
         * @return Safe ending byte index for the Range header.
         */
        fun calculateChunkEndByte(startByte: Long, chunkSize: Long, totalBytes: Long): Long {
            val potentialEnd = startByte + chunkSize - 1L
            return if (totalBytes > 0L) {
                potentialEnd.coerceAtMost(totalBytes - 1L)
            } else {
                potentialEnd
            }
        }
    }

    /**
     * Downloads an audio stream from [streamUrl] to [destinationFile] using HTTP Range chunking.
     *
     * @param streamUrl Direct audio stream URL.
     * @param destinationFile Local destination file on disk.
     * @param chunkSize Size of each discrete range request in bytes (defaults to 1 MB).
     * @param onProgress Callback invoked as bytes are saved: (downloadedBytes, totalBytes).
     */
    @Throws(IOException::class)
    open suspend fun downloadToFile(
        streamUrl: String,
        destinationFile: File,
        chunkSize: Long = DEFAULT_CHUNK_SIZE_BYTES,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ) {
        // Ensure destination folder exists
        destinationFile.parentFile?.mkdirs()

        // Remove any partial file from a prior failed download attempt
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        // Check if the URL query string already contains the "clen" parameter (Content-Length)
        var totalBytes = try {
            Uri.parse(streamUrl).getQueryParameter("clen")?.toLongOrNull() ?: -1L
        } catch (e: Exception) {
            -1L
        }

        var downloadedBytes = 0L

        try {
            FileOutputStream(destinationFile, /* append = */ true).use { output ->
                while (totalBytes <= 0L || downloadedBytes < totalBytes) {
                    val endByte = calculateChunkEndByte(downloadedBytes, chunkSize, totalBytes)

                    var chunkSuccess = false
                    var attempt = 0
                    var lastException: Exception? = null

                    while (attempt < 3 && !chunkSuccess) {
                        attempt++
                        try {
                            val request = Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", USER_AGENT)
                                .header("Range", "bytes=$downloadedBytes-$endByte")
                                .header("Accept", "*/*")
                                .header("Accept-Encoding", "identity")
                                .build()

                            val response = okHttpClient.newCall(request).execute()

                            // Case 1: Server signals range out of bounds (EOF reached)
                            if (response.code == 416) {
                                response.close()
                                chunkSuccess = true
                                return@use // Successfully completed all content
                            }

                            // Case 2: Server does not support Range requests and returns full stream (HTTP 200 OK)
                            if (response.code == 200) {
                                val body = response.body
                                val fullLength = body?.contentLength() ?: -1L
                                if (totalBytes <= 0L && fullLength > 0L) {
                                    totalBytes = fullLength
                                }
                                body?.byteStream()?.use { input ->
                                    val buffer = ByteArray(32 * 1024)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        downloadedBytes += read
                                        onProgress?.invoke(downloadedBytes, totalBytes)
                                    }
                                }
                                response.close()
                                chunkSuccess = true
                                return@use // Full stream consumed
                            }

                            // Case 3: Expected response HTTP 206 Partial Content
                            if (response.code != 206) {
                                val code = response.code
                                response.close()
                                throw IOException("HTTP Range chunk request failed with code $code")
                            }

                            // Discover totalBytes from Content-Range if not known yet
                            if (totalBytes <= 0L) {
                                val contentRange = response.header("Content-Range")
                                val parsedTotal = parseTotalBytesFromContentRange(contentRange)
                                if (parsedTotal > 0L) {
                                    totalBytes = parsedTotal
                                }
                            }

                            // Write chunk bytes to disk
                            var bytesInThisChunk = 0L
                            response.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(16 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read
                                    bytesInThisChunk += read
                                    onProgress?.invoke(downloadedBytes, totalBytes)
                                }
                            }
                            response.close()

                            if (bytesInThisChunk == 0L) {
                                // Zero bytes returned signals end of stream
                                chunkSuccess = true
                                return@use
                            }

                            chunkSuccess = true
                        } catch (e: Exception) {
                            lastException = e
                            Log.w(TAG, "Chunk attempt $attempt failed for range $downloadedBytes-$endByte: ${e.message}")
                            if (attempt < 3) {
                                delay(300L * attempt)
                            }
                        }
                    }

                    if (!chunkSuccess) {
                        throw lastException ?: IOException("Failed to download chunk after 3 attempts")
                    }
                }
                output.flush()
            }
        } catch (e: Exception) {
            // Delete corrupt partial file on failure
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            throw e
        }
    }
}
