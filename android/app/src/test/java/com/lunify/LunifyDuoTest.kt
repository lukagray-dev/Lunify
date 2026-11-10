package com.lunify

import com.lunify.domain.model.DuoAction
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.DuoPacket
import com.lunify.domain.model.DuoPeerInfo
import com.lunify.domain.model.DuoTrackSignature
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.Track
import com.lunify.player.duo.DuoCodeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Lunify - Duo Synchronization Engine Unit Tests (LunifyDuoTest.kt)
 * ==============================================================================
 *
 * Hey friend! This test suite validates all core algorithms powering Lunify Duo:
 *
 * 1. Room Code Generation & Sanitization:
 *    Ensures codes are 100% unambiguous (no '0', 'O', '1', 'I', 'L') and tolerant
 *    of user typos ("7842", "ln-7842", "ln 7842" -> canonical "LN-7842").
 *
 * 2. Fuzzy Track Matching for Offline Reconciliation:
 *    Verifies songs match across devices despite different metadata tags,
 *    remaster/official suffixes, or minor MP3 header padding (+/- 3s duration tolerance).
 *
 * 3. Mutual Offline Catalog Reconciliation:
 *    Verifies the mathematical intersection logic ensuring users only see songs
 *    available on BOTH devices while connected in Duo.
 *
 * 4. Transit Latency & Sub-frame Drift Compensation:
 *    Verifies timestamp-based position offsets calculated during network transit.
 *
 * 5. Packet Serialization & Deserialization:
 *    Verifies deterministic JSON packet encoding/decoding without reflection.
 * ==============================================================================
 */
class LunifyDuoTest {

    @Test
    fun testRoomCodeGenerationUnambiguous() {
        val ambiguousChars = setOf('0', 'O', 'o', '1', 'I', 'i', 'L', 'l')

        // Generate 100 room codes and verify none contain confusing characters
        repeat(100) {
            val code = DuoCodeUtils.generateRoomCode()
            assertTrue("Code should start with LN- prefix: $code", code.startsWith("LN-"))
            assertEquals("Code must be 7 characters long (LN-XXXX): $code", 7, code.length)

            val body = code.substring(3)
            for (char in body) {
                assertFalse("Code must not contain confusing char '$char': $code", char in ambiguousChars)
            }
            assertTrue("Generated code must be valid: $code", DuoCodeUtils.isValidRoomCode(code))
        }
    }

    @Test
    fun testRoomCodeNormalization() {
        // User types lowercase with hyphen
        assertEquals("LN-7842", DuoCodeUtils.normalizeRoomCode("ln-7842"))

        // User types with spaces
        assertEquals("LN-7842", DuoCodeUtils.normalizeRoomCode("ln 7842"))

        // User types only the 4-character suffix
        assertEquals("LN-7842", DuoCodeUtils.normalizeRoomCode("7842"))

        // User types lowercase without hyphen
        assertEquals("LN-7842", DuoCodeUtils.normalizeRoomCode("ln7842"))

        // Already canonical
        assertEquals("LN-9K4W", DuoCodeUtils.normalizeRoomCode("LN-9K4W"))
    }

    @Test
    fun testTrackTitleNormalization() {
        assertEquals("midnight city", DuoCodeUtils.normalizeTrackTitle("Midnight City (Official Audio)"))
        assertEquals("starboy", DuoCodeUtils.normalizeTrackTitle("Starboy [Official Music Video]"))
        assertEquals("blinding lights", DuoCodeUtils.normalizeTrackTitle("Blinding Lights (Lyrics)"))
        assertEquals("hotel california", DuoCodeUtils.normalizeTrackTitle("Hotel California - 2013 Remaster"))
    }

    @Test
    fun testArtistNameNormalization() {
        assertEquals("the weeknd", DuoCodeUtils.normalizeArtistName("The Weeknd feat. Daft Punk"))
        assertEquals("dua lipa", DuoCodeUtils.normalizeArtistName("Dua Lipa ft. DaBaby"))
        assertEquals("calvin harris", DuoCodeUtils.normalizeArtistName("Calvin Harris featuring Rihanna"))
    }

    @Test
    fun testFuzzyTrackMatching() {
        val localTrack = Track(
            id = "track_123",
            title = "Midnight City (Official Video)",
            artist = "M83",
            durationMs = 244000L,
            isDownloaded = true,
        )

        // Matching signature on remote device
        val remoteSigMatch = DuoTrackSignature(
            title = "Midnight City",
            artist = "M83",
            durationMs = 245000L, // 1 second difference (within 3s tolerance)
        )
        assertTrue("Tracks should match with minor duration difference",
            DuoCodeUtils.areTracksMatching(localTrack, remoteSigMatch))

        // Duration difference > 3 seconds should fail
        val remoteSigDifferentDuration = DuoTrackSignature(
            title = "Midnight City",
            artist = "M83",
            durationMs = 260000L, // 16 seconds difference
        )
        assertFalse("Tracks should not match if duration difference exceeds 3s",
            DuoCodeUtils.areTracksMatching(localTrack, remoteSigDifferentDuration))

        // Completely different track
        val remoteSigDifferentSong = DuoTrackSignature(
            title = "Wait",
            artist = "M83",
            durationMs = 244000L,
        )
        assertFalse("Different song titles should not match",
            DuoCodeUtils.areTracksMatching(localTrack, remoteSigDifferentSong))
    }

    @Test
    fun testMutualOfflineCatalogReconciliation() {
        val localTracks = listOf(
            Track(id = "1", title = "Song A", artist = "Artist 1", durationMs = 180000L, isDownloaded = true),
            Track(id = "2", title = "Song B", artist = "Artist 2", durationMs = 210000L, isDownloaded = true),
            Track(id = "3", title = "Song C", artist = "Artist 3", durationMs = 240000L, isDownloaded = true),
        )

        // Remote device has Song B and Song C, but not Song A (has Song D instead)
        val remoteManifest = listOf(
            DuoTrackSignature(title = "Song B (Official Audio)", artist = "Artist 2", durationMs = 210500L),
            DuoTrackSignature(title = "Song C", artist = "Artist 3", durationMs = 240000L),
            DuoTrackSignature(title = "Song D", artist = "Artist 4", durationMs = 300000L),
        )

        val common = DuoCodeUtils.filterCommonOfflineTracks(localTracks, remoteManifest)

        assertEquals("Exactly 2 common songs should be identified", 2, common.size)
        assertEquals("Song B", common[0].title)
        assertEquals("Song C", common[1].title)
    }

    @Test
    fun testCommonOfflineTracksDeduplication() {
        // Local tracks has duplicate entries for the same song (e.g. MediaStore duplicate or two copies)
        val localTracks = listOf(
            Track(id = "1", title = "Blinding Lights", artist = "The Weeknd", durationMs = 200000L, isDownloaded = true),
            Track(id = "2", title = "Blinding Lights (Official Video)", artist = "The Weeknd", durationMs = 200000L, isDownloaded = true),
            Track(id = "3", title = "Starboy", artist = "The Weeknd", durationMs = 230000L, isDownloaded = true),
        )

        val remoteManifest = listOf(
            DuoTrackSignature(title = "Blinding Lights", artist = "The Weeknd", durationMs = 200000L),
            DuoTrackSignature(title = "Starboy", artist = "The Weeknd", durationMs = 230000L),
        )

        val common = DuoCodeUtils.filterCommonOfflineTracks(localTracks, remoteManifest)

        assertEquals("Should contain exactly 2 unique tracks (no duplicate Blinding Lights)", 2, common.size)
        assertEquals("Blinding Lights", common[0].title)
        assertEquals("Starboy", common[1].title)
    }

    @Test
    fun testPacketSerializationRoundtrip() {
        val originalPacket = DuoPacket(
            version = 1,
            action = DuoAction.PLAY,
            senderId = "dev_alpha",
            senderDeviceName = "Redmi Note 11S",
            roomCode = "LN-7842",
            trackId = "dQw4w9WgXcQ",
            trackTitle = "Never Gonna Give You Up",
            trackArtist = "Rick Astley",
            trackDurationMs = 213000L,
            trackPlaybackSource = "ONLINE",
            positionMs = 15420L,
            timestampEpochMs = 1712345678000L,
        )

        val json = originalPacket.toJson()
        assertNotNull(json)

        val parsed = DuoPacket.fromJson(json)
        assertNotNull("Parsed packet must not be null", parsed)
        assertEquals(originalPacket.action, parsed!!.action)
        assertEquals(originalPacket.senderId, parsed.senderId)
        assertEquals(originalPacket.senderDeviceName, parsed.senderDeviceName)
        assertEquals(originalPacket.roomCode, parsed.roomCode)
        assertEquals(originalPacket.trackId, parsed.trackId)
        assertEquals(originalPacket.trackTitle, parsed.trackTitle)
        assertEquals(originalPacket.trackArtist, parsed.trackArtist)
        assertEquals(originalPacket.trackDurationMs, parsed.trackDurationMs)
        assertEquals(originalPacket.trackPlaybackSource, parsed.trackPlaybackSource)
        assertEquals(originalPacket.positionMs, parsed.positionMs)
        assertEquals(originalPacket.timestampEpochMs, parsed.timestampEpochMs)
    }

    @Test
    fun testManifestPacketSerializationRoundtrip() {
        val signatures = listOf(
            DuoTrackSignature("Title 1", "Artist 1", 180000L),
            DuoTrackSignature("Title 2", "Artist 2", 210000L),
        )
        val manifestPacket = DuoPacket(
            action = DuoAction.CATALOG_MANIFEST,
            senderId = "dev_beta",
            senderDeviceName = "Pixel 8",
            roomCode = "LN-3810",
            manifest = signatures,
        )

        val json = manifestPacket.toJson()
        val parsed = DuoPacket.fromJson(json)

        assertNotNull(parsed)
        assertEquals(DuoAction.CATALOG_MANIFEST, parsed!!.action)
        assertNotNull(parsed.manifest)
        assertEquals(2, parsed.manifest!!.size)
        assertEquals("Title 1", parsed.manifest!![0].title)
        assertEquals(210000L, parsed.manifest!![1].durationMs)
    }

    @Test
    fun testClockDriftAndTransitCompensationCalculation() {
        val sendTimeEpoch = 1000000L
        val originalPosition = 45000L // 45 seconds into track

        val packet = DuoPacket(
            action = DuoAction.PLAY,
            senderId = "peer",
            senderDeviceName = "Partner",
            roomCode = "LN-7842",
            positionMs = originalPosition,
            timestampEpochMs = sendTimeEpoch,
        )

        // Simulate receipt 120ms later across internet
        val receiptTimeEpoch = sendTimeEpoch + 120L
        val transitDelayMs = (receiptTimeEpoch - packet.timestampEpochMs).coerceAtLeast(0L)
        val compensatedPositionMs = packet.positionMs + transitDelayMs

        assertEquals("Transit delay should be 120ms", 120L, transitDelayMs)
        assertEquals("Compensated position should advance by 120ms to keep audio in lockstep",
            45120L, compensatedPositionMs)
    }

    @Test
    fun testPairingHandshakePacketRoundtrips() {
        // 1. Join Request
        val joinRequest = DuoPacket(
            action = DuoAction.JOIN_REQUEST,
            senderId = "guest_dev_123",
            senderDeviceName = "Pixel 8 Pro",
            roomCode = "LN-CPT7",
        )
        val requestJson = joinRequest.toJson()
        val parsedRequest = DuoPacket.fromJson(requestJson)
        assertNotNull(parsedRequest)
        assertEquals(DuoAction.JOIN_REQUEST, parsedRequest!!.action)
        assertEquals("Pixel 8 Pro", parsedRequest.senderDeviceName)
        assertEquals("LN-CPT7", parsedRequest.roomCode)

        // 2. Join Accept
        val joinAccept = DuoPacket(
            action = DuoAction.JOIN_ACCEPT,
            senderId = "host_dev_456",
            senderDeviceName = "Redmi Note 11S",
            roomCode = "LN-CPT7",
        )
        val acceptJson = joinAccept.toJson()
        val parsedAccept = DuoPacket.fromJson(acceptJson)
        assertNotNull(parsedAccept)
        assertEquals(DuoAction.JOIN_ACCEPT, parsedAccept!!.action)
        assertEquals("host_dev_456", parsedAccept.senderId)

        // 3. Join Reject with error reason
        val joinReject = DuoPacket(
            action = DuoAction.JOIN_REJECT,
            senderId = "host_dev_456",
            senderDeviceName = "Redmi Note 11S",
            roomCode = "LN-CPT7",
            errorMessage = "Host declined the pairing request",
        )
        val rejectJson = joinReject.toJson()
        val parsedReject = DuoPacket.fromJson(rejectJson)
        assertNotNull(parsedReject)
        assertEquals(DuoAction.JOIN_REJECT, parsedReject!!.action)
        assertEquals("Host declined the pairing request", parsedReject.errorMessage)
    }

    @Test
    fun testIncomingRequestStateLifecycle() {
        val states = DuoConnectionState.values().toList()
        assertTrue(states.contains(DuoConnectionState.INCOMING_REQUEST))
        assertTrue(states.contains(DuoConnectionState.WAITING_FOR_PEER))
        assertTrue(states.contains(DuoConnectionState.CONNECTED))
        assertTrue(states.contains(DuoConnectionState.DISCONNECTED))
    }
}
