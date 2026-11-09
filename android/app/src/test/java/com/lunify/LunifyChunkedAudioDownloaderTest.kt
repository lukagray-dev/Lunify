package com.lunify

import com.lunify.player.download.ChunkedAudioDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ==============================================================================
 * Lunify - Chunked Audio Downloader Unit Tests (LunifyChunkedAudioDownloaderTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates real-world audio download acceleration:
 * 1. Content-Range header parsing across normal, wildcard, and malformed responses.
 * 2. Range boundary slicing and clamping to exact EOF boundaries.
 * 3. Default chunk size configuration (1 MB burst slices to bypass CDN throttling).
 * 4. Multi-chunk byte reassembly and 200 OK fallback streaming.
 * ==============================================================================
 */
class LunifyChunkedAudioDownloaderTest {

    @Test
    fun testChunkSizeConstants() {
        assertEquals(1024L * 1024L, ChunkedAudioDownloader.DEFAULT_CHUNK_SIZE_BYTES)
    }

    @Test
    fun testParseTotalBytesFromContentRange() {
        // Standard Content-Range responses
        assertEquals(
            4251023L,
            ChunkedAudioDownloader.parseTotalBytesFromContentRange("bytes 0-1048575/4251023"),
        )
        assertEquals(
            15200300L,
            ChunkedAudioDownloader.parseTotalBytesFromContentRange("bytes 1048576-2097151/15200300"),
        )

        // Wildcard / unknown total length
        assertEquals(
            -1L,
            ChunkedAudioDownloader.parseTotalBytesFromContentRange("bytes 0-1048575/*"),
        )

        // Null and blank strings
        assertEquals(-1L, ChunkedAudioDownloader.parseTotalBytesFromContentRange(null))
        assertEquals(-1L, ChunkedAudioDownloader.parseTotalBytesFromContentRange(""))
        assertEquals(-1L, ChunkedAudioDownloader.parseTotalBytesFromContentRange("   "))

        // Malformed strings
        assertEquals(-1L, ChunkedAudioDownloader.parseTotalBytesFromContentRange("invalid_header"))
        assertEquals(-1L, ChunkedAudioDownloader.parseTotalBytesFromContentRange("bytes 0-100/not_a_number"))
    }

    @Test
    fun testCalculateChunkEndByte() {
        val oneMb = 1024L * 1024L
        val totalSize = 3_500_000L // 3.5 MB

        // Chunk 0: bytes 0 to (1MB - 1)
        val endByte0 = ChunkedAudioDownloader.calculateChunkEndByte(0L, oneMb, totalSize)
        assertEquals(1048575L, endByte0)

        // Chunk 1: bytes 1048576 to (2MB - 1)
        val endByte1 = ChunkedAudioDownloader.calculateChunkEndByte(1048576L, oneMb, totalSize)
        assertEquals(2097151L, endByte1)

        // Chunk 2: bytes 2097152 to (3MB - 1)
        val endByte2 = ChunkedAudioDownloader.calculateChunkEndByte(2097152L, oneMb, totalSize)
        assertEquals(3145727L, endByte2)

        // Chunk 3: bytes 3145728 to end (should clamp to totalSize - 1L = 3499999L)
        val endByte3 = ChunkedAudioDownloader.calculateChunkEndByte(3145728L, oneMb, totalSize)
        assertEquals(3499999L, endByte3)

        // If totalSize is unknown (-1L), do not clamp
        val unconstrainedEnd = ChunkedAudioDownloader.calculateChunkEndByte(0L, oneMb, -1L)
        assertEquals(1048575L, unconstrainedEnd)
    }

    @Test
    fun testChunkedDownloadAssemblyWithMockClient() {
        runBlocking {
            // Create 2 chunks of test data
            val chunk1Data = ByteArray(500) { 1 }
            val chunk2Data = ByteArray(300) { 2 }
            val totalExpected = chunk1Data + chunk2Data

            val tempFile = File.createTempFile("lunify_test_download", ".m4a")
            tempFile.deleteOnExit()

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val range = request.header("Range") ?: ""

                    val (statusCode, responseBytes, contentRange) = when {
                        range.startsWith("bytes=0-") -> {
                            Triple(206, chunk1Data, "bytes 0-499/800")
                        }
                        range.startsWith("bytes=500-") -> {
                            Triple(206, chunk2Data, "bytes 500-799/800")
                        }
                        else -> {
                            Triple(416, ByteArray(0), null)
                        }
                    }

                    val responseBuilder = Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message(if (statusCode == 206) "Partial Content" else "Range Not Satisfiable")
                        .body(responseBytes.toResponseBody("audio/mp4".toMediaType()))

                    if (contentRange != null) {
                        responseBuilder.header("Content-Range", contentRange)
                    }

                    responseBuilder.build()
                }
                .build()

            val downloader = ChunkedAudioDownloader(mockClient)
            var lastReportedProgress = 0L

            downloader.downloadToFile(
                streamUrl = "https://mock.googlevideo.com/videoplayback",
                destinationFile = tempFile,
                chunkSize = 500L,
            ) { downloaded, _ ->
                lastReportedProgress = downloaded
            }

            assertTrue(tempFile.exists())
            assertEquals(800L, tempFile.length())
            assertEquals(800L, lastReportedProgress)

            val writtenBytes = tempFile.readBytes()
            assertArrayEquals(totalExpected, writtenBytes)

            tempFile.delete()
        }
    }

    @Test
    fun testDownloadFallbackOnHttp200Ok() {
        runBlocking {
            val testData = "lunify_direct_payload".toByteArray()
            val tempFile = File.createTempFile("lunify_test_200", ".m4a")
            tempFile.deleteOnExit()

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(testData.toResponseBody("audio/mp4".toMediaType()))
                        .build()
                }
                .build()

            val downloader = ChunkedAudioDownloader(mockClient)
            downloader.downloadToFile(
                streamUrl = "https://mock.server.com/audio.mp3",
                destinationFile = tempFile,
            )

            assertTrue(tempFile.exists())
            assertEquals(testData.size.toLong(), tempFile.length())
            assertArrayEquals(testData, tempFile.readBytes())

            tempFile.delete()
        }
    }
}
