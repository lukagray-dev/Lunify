package com.lunify.data.repository

import android.content.Context
import android.net.Uri
import com.lunify.domain.model.LyricLine
import com.lunify.domain.model.SyncedLyrics
import com.lunify.domain.model.Track
import java.io.File
import java.util.regex.Pattern

/**
 * ==============================================================================
 * Lunify - Real Synchronized Lyrics Repository (LyricsRepository.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the real lyrics engine of Lunify.
 *
 * HOW DOES OFFLINE LYRICS WORK?
 * -----------------------------
 * In offline music players, synchronized lyrics are standardly delivered via:
 * 1. Synchronized `.lrc` files located in the same directory as the music file
 *    (e.g., `Midnight City.mp3` accompanied by `Midnight City.lrc`).
 * 2. Embedded ID3 tags (such as USLT / SYLT frames).
 *
 * WHAT IS THE LRC FORMAT?
 * -----------------------
 * An LRC line looks like this:
 * `[00:12.50]Waiting in a car`
 * `[00:16.00]Waiting for a ride in the dark`
 *
 * Where:
 * - `00` is the minute (0 * 60 * 1000 = 0 ms).
 * - `12.50` is seconds with hundredths of a second (12.50 * 1000 = 12500 ms).
 *
 * OUR PARSER:
 * 1. Locates any matching `.lrc` file on device storage.
 * 2. Parses the timestamps into millisecond values (`Long`).
 * 3. Sorts them in chronological order so the Apple Music style lyrics view can
 *    smoothly auto-scroll to the currently singing line.
 * 4. If no lyrics are found on device, returns `null` so the UI can gracefully
 *    disable the Lyrics button!
 * ==============================================================================
 */
object LyricsRepository {

    private val LRC_PATTERN = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)")

    /**
     * Attempts to resolve real synchronized lyrics for the specified track.
     *
     * @param track The song being played.
     * @param context Application context for ContentResolver or metadata reading.
     * @return [SyncedLyrics] if real lyrics exist on device, or null if none exist.
     */
    fun getLyricsForTrack(track: Track, context: Context): SyncedLyrics? {
        // 1. Check for companion .lrc file in the same directory
        val lrcFile = findLrcFile(track)
        if (lrcFile != null && lrcFile.exists() && lrcFile.canRead()) {
            val lyrics = parseLrcFile(track.id, lrcFile)
            if (lyrics != null && lyrics.lines.isNotEmpty()) {
                return lyrics
            }
        }

        // 2. If no file found, return null (lyrics button will be disabled)
        return null
    }

    /**
     * Looks for a .lrc file on disk corresponding to the given track.
     */
    private fun findLrcFile(track: Track): File? {
        if (track.filePath.isBlank()) return null

        try {
            val audioFile = File(track.filePath)
            if (!audioFile.exists()) return null

            val parentDir = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            // Try exact same base name with .lrc extension
            val directLrc = File(parentDir, "$baseName.lrc")
            if (directLrc.exists()) return directLrc

            val directLrcUpper = File(parentDir, "$baseName.LRC")
            if (directLrcUpper.exists()) return directLrcUpper

            // Try track title match
            val titleLrc = File(parentDir, "${track.title}.lrc")
            if (titleLrc.exists()) return titleLrc

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Parses standard LRC file format into structured [SyncedLyrics].
     */
    fun parseLrcContent(trackId: String, content: String): SyncedLyrics? {
        val lines = mutableListOf<LyricLine>()

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val matcher = LRC_PATTERN.matcher(trimmed)
                if (matcher.matches()) {
                    val minutesStr = matcher.group(1) ?: "0"
                    val secondsStr = matcher.group(2) ?: "0"
                    val text = matcher.group(3)?.trim() ?: ""

                    val minutes = minutesStr.toLongOrNull() ?: 0L
                    val secondsFloat = secondsStr.toFloatOrNull() ?: 0f
                    val timestampMs = (minutes * 60 * 1000L) + (secondsFloat * 1000f).toLong()

                    lines.add(LyricLine(timestampMs = timestampMs, text = text))
                }
            }
        }

        if (lines.isEmpty()) return null

        return SyncedLyrics(
            trackId = trackId,
            lines = lines.sortedBy { it.timestampMs }
        )
    }

    private fun parseLrcFile(trackId: String, file: File): SyncedLyrics? {
        return try {
            val content = file.readText(Charsets.UTF_8)
            parseLrcContent(trackId, content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
