package com.lunify.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lunify.data.local.LibraryStorage
import com.lunify.domain.model.Playlist
import com.lunify.domain.model.Track
import com.lunify.domain.model.TrackSortOption
import java.io.File
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
 * KEY RESPONSIBILITIES:
 * 1. Querying music files: Pulling id, title, artist, album, duration, file size,
 *    album cover art, filesystem path, folder name, and creation timestamp.
 * 2. Grouping folders as playlists: Automatically recognizing folders containing
 *    music (e.g. "Download", "Music", "Albums") and presenting them as backward-
 *    compatible playlists without modifying or copying files!
 * 3. Sorting engine: Supporting sorting by Name, Time created, Times played, and Duration.
 * 4. File Deletion: Real deletion of offline tracks from device storage & MediaStore.
 * 5. Calculating storage metrics: Summing up exact byte sizes of music tracks.
 * ==============================================================================
 */
class LocalAudioRepository(private val context: Context) {

    /**
     * Scans and retrieves all available local audio tracks from the device's MediaStore,
     * seamlessly merging any offline tracks downloaded directly by Lunify.
     *
     * @param libraryStorage Optional [LibraryStorage] instance to merge app-downloaded tracks.
     * @return A list of [Track] objects ready to be queued into our audio player.
     */
    fun getLocalAudioTracks(libraryStorage: LibraryStorage? = null): List<Track> {
        val tracksList = mutableListOf<Track>()

        val audioUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        // Filter out clips shorter than 5 seconds (5000 ms) and non-music audio
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
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
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val rawTitle = cursor.getString(titleColumn) ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val rawAlbum = cursor.getString(albumColumn) ?: ""
                    val durationMs = cursor.getLong(durationColumn)
                    val sizeBytes = cursor.getLong(sizeColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val rawPath = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                    val dateAdded = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) * 1000L else 0L

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

                    // Extract parent folder name for folder playlists
                    val folderName = if (rawPath.isNotBlank()) {
                        try {
                            val parent = File(rawPath).parentFile
                            parent?.name?.ifBlank { "Music" } ?: "Music"
                        } catch (e: Exception) {
                            "Music"
                        }
                    } else {
                        "Music"
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
                        filePath = rawPath,
                        folderName = folderName,
                        dateAdded = dateAdded,
                    )

                    tracksList.add(track)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Seamlessly merge app-downloaded tracks from LibraryStorage
        if (libraryStorage != null) {
            try {
                val existingIds = tracksList.map { it.id }.toSet()
                val existingPaths = tracksList.map { it.filePath }.toSet()
                val appDownloads = libraryStorage.getDownloadedTracks()
                for (download in appDownloads) {
                    if (download.id !in existingIds && download.filePath !in existingPaths) {
                        val file = File(download.filePath)
                        if (file.exists() && file.length() > 0) {
                            tracksList.add(download.copy(isDownloaded = true))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return tracksList
    }

    /**
     * Groups local tracks into backward-compatible folder playlists based on the
     * directories where music files reside on device storage.
     *
     * @param tracks Scanned list of local tracks.
     * @return List of [Playlist] representing folders found.
     */
    fun getFolderPlaylists(tracks: List<Track>): List<Playlist> = Companion.getFolderPlaylists(tracks)

    /**
     * Sorts tracks according to the requested [TrackSortOption].
     */
    fun sortTracks(tracks: List<Track>, sortOption: TrackSortOption): List<Track> = Companion.sortTracks(tracks, sortOption)

    /**
     * Deletes an audio track permanently from the device storage, MediaStore,
     * and local library persistent storage.
     *
     * @param track The track to delete.
     * @param libraryStorage Optional [LibraryStorage] to unregister app downloads.
     * @return True if deleted successfully, false otherwise.
     */
    fun deleteTrack(track: Track, libraryStorage: LibraryStorage? = null): Boolean {
        var isDeleted = false

        // 1. Remove from app's local downloaded tracks storage
        if (libraryStorage != null) {
            if (libraryStorage.deleteDownloadedTrack(track.id)) {
                isDeleted = true
            }
        }

        // 2. Delete via ContentResolver (MediaStore)
        if (track.contentUri.isNotBlank()) {
            try {
                val uri = Uri.parse(track.contentUri)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) isDeleted = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Direct file fallback if file exists on disk
        if (track.filePath.isNotBlank()) {
            try {
                val file = File(track.filePath)
                if (file.exists() && file.delete()) {
                    isDeleted = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return isDeleted
    }

    /**
     * Calculates the total storage size in bytes occupied by all given tracks.
     */
    fun calculateTotalStorageBytes(tracks: List<Track>): Long = Companion.calculateTotalStorageBytes(tracks)

    companion object {
        /**
         * Groups local tracks into folder playlists based on directory path.
         */
        fun getFolderPlaylists(tracks: List<Track>): List<Playlist> {
            val folderGroups = tracks.groupBy { it.folderName }

            return folderGroups.map { (folderName, tracksInFolder) ->
                val firstFilePath = tracksInFolder.firstOrNull()?.filePath ?: ""
                val folderPath = try {
                    File(firstFilePath).parent ?: folderName
                } catch (e: Exception) {
                    folderName
                }

                Playlist(
                    id = "folder_${folderName.lowercase(Locale.ROOT).replace(" ", "_")}",
                    name = folderName,
                    trackIds = tracksInFolder.map { it.id },
                    isFolderPlaylist = true,
                    folderPath = folderPath,
                    createdAt = tracksInFolder.maxOfOrNull { it.dateAdded } ?: System.currentTimeMillis(),
                )
            }.sortedBy { it.name.lowercase(Locale.ROOT) }
        }

        /**
         * Sorts tracks according to the requested [TrackSortOption].
         */
        fun sortTracks(tracks: List<Track>, sortOption: TrackSortOption): List<Track> {
            return when (sortOption) {
                TrackSortOption.NAME -> tracks.sortedBy { it.title.lowercase(Locale.ROOT) }
                TrackSortOption.TIME_CREATED -> tracks.sortedByDescending { it.dateAdded }
                TrackSortOption.TIMES_PLAYED -> tracks.sortedByDescending { it.playCount }
                TrackSortOption.DURATION -> tracks.sortedByDescending { it.durationMs }
            }
        }

        fun calculateTotalStorageBytes(tracks: List<Track>): Long {
            return tracks.sumOf { it.sizeBytes }
        }

        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return "0:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(Locale.US, minutes, seconds)
        }

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
