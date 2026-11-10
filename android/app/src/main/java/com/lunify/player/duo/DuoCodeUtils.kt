package com.lunify.player.duo

import com.lunify.domain.model.DuoTrackSignature
import com.lunify.domain.model.Track
import java.security.SecureRandom
import kotlin.math.abs

/**
 * ==============================================================================
 * Lunify - Duo Code Generator & Offline Matching Utilities (DuoCodeUtils.kt)
 * ==============================================================================
 *
 * Hey friend! This utility file provides three vital algorithms for Lunify Duo:
 *
 * 1. Unambiguous Pairing Codes:
 *    Generates clean 6-character room codes formatted as "LN-XXXX".
 *    We exclude visually ambiguous characters like '0' (zero), 'O' (oh),
 *    '1' (one), 'I' (eye), and 'L' (el) so users never struggle reading codes out loud!
 *
 * 2. Permissive Code Sanitization:
 *    Users might type "ln 7842", "LN-7842", or just "7842". We automatically
 *    sanitize and format it into canonical "LN-7842".
 *
 * 3. Offline Library Reconciliation:
 *    When two devices connect, they want to share downloaded offline music.
 *    Because file names and ID tags can differ slightly depending on when or how
 *    they were downloaded, we use smart normalization:
 *    - Strips noisy suffixes like "(Official Video)", "[Audio]", "(Lyric Video)"
 *    - Matches normalized artist names
 *    - Allows a +/- 3000ms duration tolerance for MP3 header/padding variations.
 * ==============================================================================
 */
object DuoCodeUtils {

    /**
     * Base-27 unambiguous character alphabet.
     * Excludes 0, O, 1, I, and L to avoid voice and font confusion.
     */
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_BODY_LENGTH = 4
    private const val PREFIX = "LN-"

    private val secureRandom = SecureRandom()

    /**
     * Generates a secure, readable pairing code (e.g. "LN-7842", "LN-9K4W").
     */
    fun generateRoomCode(): String {
        val sb = java.lang.StringBuilder(PREFIX)
        for (i in 0 until CODE_BODY_LENGTH) {
            val index = secureRandom.nextInt(ALPHABET.length)
            sb.append(ALPHABET[index])
        }
        return sb.toString()
    }

    /**
     * Sanitizes user input into canonical "LN-XXXX" format.
     * Handles inputs like "7842", "ln-7842", "LN 7842", "ln7842".
     *
     * @param input Raw text typed by user.
     * @return Canonical formatted code or the cleaned uppercase string if invalid.
     */
    fun normalizeRoomCode(input: String): String {
        val cleaned = input.trim()
            .uppercase()
            .replace(" ", "")
            .replace("-", "")

        return if (cleaned.startsWith("LN") && cleaned.length >= 6) {
            "LN-" + cleaned.substring(2, 6)
        } else if (cleaned.length == 4) {
            "LN-$cleaned"
        } else {
            cleaned
        }
    }

    /**
     * Validates whether a room code matches the official "LN-XXXX" structure.
     */
    fun isValidRoomCode(code: String): Boolean {
        if (!code.startsWith(PREFIX) || code.length != 7) return false
        val body = code.substring(PREFIX.length)
        return body.all { it in ALPHABET }
    }

    /**
     * Cleans up song titles for fuzzy matching across devices.
     * Strips bracketed metadata like "(Official Music Video)" or "[Audio]".
     */
    fun normalizeTrackTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("""\((official|audio|video|lyrics|hd|4k|hq|remastered|explicit).*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(official|audio|video|lyrics|hd|4k|hq|remastered|explicit).*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""-\s*\d{4}\s*remaster.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Normalizes artist names by removing "feat.", "ft.", and special characters.
     */
    fun normalizeArtistName(artist: String): String {
        return artist.lowercase()
            .replace(Regex("""(feat\.|ft\.|featuring).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Checks if a local track matches a remote track signature.
     *
     * Matches if:
     * 1. The titles match when normalized, OR
     * 2. The local track title contains or is contained in remote title with matching artist, AND
     * 3. The duration is within +/- 3000ms (3 seconds) of each other.
     */
    fun areTracksMatching(local: Track, remote: DuoTrackSignature): Boolean {
        // Direct ID match (e.g. YouTube video ID)
        if (local.id.isNotBlank() && remote.title.contains(local.id)) {
            return true
        }

        // Duration check: if both have positive duration, enforce 3-second tolerance
        if (local.durationMs > 0 && remote.durationMs > 0) {
            val diff = abs(local.durationMs - remote.durationMs)
            if (diff > 3000L) {
                return false
            }
        }

        val normLocalTitle = normalizeTrackTitle(local.title)
        val normRemoteTitle = normalizeTrackTitle(remote.title)

        val titlesMatch = normLocalTitle == normRemoteTitle ||
                (normLocalTitle.isNotEmpty() && normRemoteTitle.isNotEmpty() &&
                        (normLocalTitle.contains(normRemoteTitle) || normRemoteTitle.contains(normLocalTitle)))

        if (!titlesMatch) return false

        val normLocalArtist = normalizeArtistName(local.artist)
        val normRemoteArtist = normalizeArtistName(remote.artist)

        // If either artist is generic or empty, accept title match
        if (normLocalArtist.isBlank() || normRemoteArtist.isBlank() ||
            normLocalArtist == "unknown" || normRemoteArtist == "unknown") {
            return true
        }

        return normLocalArtist == normRemoteArtist ||
                normLocalArtist.contains(normRemoteArtist) ||
                normRemoteArtist.contains(normLocalArtist)
    }

    /**
     * Filters a list of local offline tracks, returning only those that exist
     * in the partner's catalog manifest (the mutual shared intersection).
     *
     * Automatically deduplicates to guarantee every matching song appears
     * strictly once in the shared Duo Library.
     */
    fun filterCommonOfflineTracks(
        localTracks: List<Track>,
        remoteManifest: List<DuoTrackSignature>,
    ): List<Track> {
        if (remoteManifest.isEmpty()) return emptyList()

        val matched = localTracks.filter { localTrack ->
            remoteManifest.any { remoteSig ->
                areTracksMatching(localTrack, remoteSig)
            }
        }

        // Deduplicate by normalized title and artist so identical tracks never repeat
        return matched.distinctBy { track ->
            val normTitle = normalizeTrackTitle(track.title)
            val normArtist = normalizeArtistName(track.artist)
            "$normTitle|$normArtist"
        }
    }
}
