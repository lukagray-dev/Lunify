package com.lunify.domain.model

/**
 * ==============================================================================
 * Lunify - Synchronized Lyrics Domain Model (Lyrics.kt)
 * ==============================================================================
 *
 * Hey friend! This file defines models for line-by-line and word-by-word
 * synchronized lyrics.
 *
 * It parses and represents timestamps in milliseconds (compatible with standard
 * LRC files and LRCLIB responses) so the Compose lyrics engine can smoothly
 * highlight and auto-scroll the active line in real time as the song plays!
 * ==============================================================================
 */

/**
 * Represents a single line in a synchronized lyrics sheet.
 *
 * @property timestampMs Start time in milliseconds when this line begins.
 * @property text The actual lyrical line text.
 * @property translation Optional translated text if available.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val translation: String? = null,
)

/**
 * Full lyrics container holding all lines and metadata.
 *
 * @property trackId Associated track identifier.
 * @property lines Ordered list of synchronized lyric lines.
 * @property plainText Plain unsynced lyrics fallback.
 * @property isSynced Whether timing data is present.
 */
data class SyncedLyrics(
    val trackId: String,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String = "",
    val isSynced: Boolean = lines.isNotEmpty(),
) {
    /**
     * Determines which lyric line index is currently active based on playback position.
     *
     * @param currentPositionMs Current song position in milliseconds.
     * @return Index of the active line, or -1 if none match yet.
     */
    fun findActiveIndex(currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        
        // Find the last line whose timestamp is less than or equal to current position
        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= currentPositionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
