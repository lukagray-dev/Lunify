package com.lunify.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lunify.domain.model.Track
import java.util.Locale

/**
 * ==============================================================================
 * Lunify - Local Device Audio Repository (LocalAudioRepository.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the local audio data engine of Lunify.
 *
 * WHAT DOES THIS DO?
 * -------------------
 * This repository is responsible for talking directly to Android's built-in
 * `MediaStore` database. When a user downloads songs, transfers MP3/FLAC/AAC/M4A
 * files to their device, or has music stored on their SD card or internal storage,
 * Android's media scanner indexes them.
 *
 * We query that index using Android's `ContentResolver`. This gives us instant,
 * high-performance access to all music files without having to slowly crawl
 * the filesystem folder by folder!
 *
 * KEY RESPONSIBILITIES:
 * 1. Querying music files: Pulling id, title, artist, album, duration, file size,
 *    and album cover art.
 * 2. Filtering noise: Filtering out short notification sounds or system sound effects
 *    (e.g., songs shorter than 5 seconds or non-music files).
 * 3. Calculating storage metrics: Summing up the exact byte size of all music
 *    tracks to drive the real storage usage indicator on the Library screen.
 * ==============================================================================
 */
class LocalAudioRepository(private val context: Context) {

    /**
     * Scans and retrieves all available local audio tracks from the device's MediaStore.
     *
     * @return A list of [Track] objects ready to be queued into our audio player.
     */
    fun getLocalAudioTracks(): List<Track> {
        val tracksList = mutableListOf<Track>()

        // The URI pointing to external audio on the device
        val audioUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // The specific columns we want Android to return for each audio file
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        // Filter out ringtones, notifications, and audio clips shorter than 5 seconds (5000 ms)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"

        // Sort alphabetically by song title ignoring case
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val contentResolver: ContentResolver = context.contentResolver

        try {
            contentResolver.query(
                audioUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                // Cache column indices for optimal cursor reading performance
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val rawTitle = cursor.getString(titleColumn) ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val rawAlbum = cursor.getString(albumColumn) ?: ""
                    val durationMs = cursor.getLong(durationColumn)
                    val sizeBytes = cursor.getLong(sizeColumn)
                    val albumId = cursor.getLong(albumIdColumn)

                    // Clean up default fallback strings from Android MediaStore
                    val cleanArtist = if (rawArtist == "<unknown>" || rawArtist.isBlank()) {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }

                    val cleanAlbum = if (rawAlbum == "<unknown>" || rawAlbum.isBlank()) {
                        ""
                    } else {
                        rawAlbum
                    }

                    // Construct the playback URI (content://media/external/audio/media/<id>)
                    val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Construct the album art URI if available
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val track = Track(
                        id = id.toString(),
                        title = rawTitle.ifBlank { "Track $id" },
                        artist = cleanArtist,
                        album = cleanAlbum,
                        durationMs = durationMs,
                        durationText = formatDuration(durationMs),
                        thumbnailUrl = albumArtUri,
                        isVideo = false,
                        isDownloaded = true,
                        isFavorite = false,
                        contentUri = trackUri.toString(),
                        sizeBytes = sizeBytes,
                    )

                    tracksList.add(track)
                }
            }
        } catch (e: Exception) {
            // Log error or gracefully handle security exceptions if permission was revoked
            e.printStackTrace()
        }

        return tracksList
    }

    /**
     * Calculates the total storage size in bytes occupied by all given tracks.
     *
     * @param tracks The list of tracks to sum up.
     * @return Total bytes as a Long.
     */
    fun calculateTotalStorageBytes(tracks: List<Track>): Long {
        return tracks.sumOf { it.sizeBytes }
    }

    companion object {
        /**
         * Converts milliseconds into a friendly "m:ss" time string.
         * Example: 212000L -> "3:32"
         */
        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return "0:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(Locale.US, minutes, seconds)
        }

        /**
         * Converts raw byte count into human-readable storage metrics for the UI badge.
         *
         * Examples:
         * - 512_000L -> "500 KB Used"
         * - 150_000_000L -> "143.0 MB Used"
         * - 1_500_000_000L -> "1.4 GB Used"
         */
        fun formatStorageSize(bytes: Long): String {
            if (bytes <= 0L) return "0 MB Used"

            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1f GB Used", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB Used", mb)
                else -> String.format(Locale.US, "%.0f KB Used", kb)
            }
        }
    }
}
