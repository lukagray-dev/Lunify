package com.lunify.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * ==============================================================================
 * Lunify - Duo Synchronization Domain Models (DuoSession.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the domain models powering our Lunify Duo feature.
 *
 * WHAT IS DUO?
 * -------------
 * Lunify Duo lets two friends listen to music together in real-time sync, even
 * if they are thousands of miles apart across different cities or countries!
 *
 * When either person presses Play, Pause, Seeks, or switches to another track,
 * a synchronization command packet is delivered to the other device with sub-frame
 * audio drift compensation.
 *
 * HOW DOES OFFLINE VS ONLINE WORK?
 * ---------------------------------
 * 1. Online Streaming (YouTube Music):
 *    Both devices fetch the audio stream from YouTube's global CDN using the
 *    shared track ID and metadata.
 * 2. Offline / Downloaded Tracks:
 *    To prevent playback failures where one device has a file stored on disk
 *    that the other device doesn't have, both devices exchange a lightweight
 *    catalog manifest of their downloaded songs when connecting.
 *    The library view switches to "Duo Library", showing only songs present
 *    on BOTH devices (the mathematical intersection).
 * ==============================================================================
 */

/**
 * The life-cycle states of a Lunify Duo connection.
 */
enum class DuoConnectionState {
    /** No active Duo session; local music plays normally. */
    DISCONNECTED,

    /** Connecting to the relay broker network. */
    CONNECTING,

    /** Host is waiting for a friend to enter the room pairing code. */
    WAITING_FOR_PEER,

    /** A guest has requested to connect; host must confirm or decline. */
    INCOMING_REQUEST,

    /** Devices are actively connected and ready to sync. */
    CONNECTED,

    /** Actively synchronizing playlist manifests or clock offsets. */
    SYNCING,

    /** A connection or pairing error occurred (e.g. room full or network timeout). */
    ERROR,
}

/**
 * Action verbs exchanged inside [DuoPacket] messages.
 */
object DuoAction {
    /** Host asserts ownership of a room code to detect collisions. */
    const val ROOM_CLAIM = "ROOM_CLAIM"

    /** Guest requests to join a host's room code. */
    const val JOIN_REQUEST = "JOIN_REQUEST"

    /** Host accepts guest's join request. */
    const val JOIN_ACCEPT = "JOIN_ACCEPT"

    /** Host rejects join request because room already has 2 participants (locked). */
    const val JOIN_REJECT = "JOIN_REJECT"

    /** Exchange of downloaded track signatures to compute mutual offline library. */
    const val CATALOG_MANIFEST = "CATALOG_MANIFEST"

    /** Command: start playing the specified track at the given timestamp. */
    const val PLAY = "PLAY"

    /** Command: pause playback at the given track position. */
    const val PAUSE = "PAUSE"

    /** Command: seek playback to the given position in milliseconds. */
    const val SEEK = "SEEK"

    /** Periodic ping/pong to calculate round-trip latency and clock drift. */
    const val SYNC_HEARTBEAT = "SYNC_HEARTBEAT"

    /** Graceful teardown of the Duo session. */
    const val DISCONNECT = "DISCONNECT"
}

/**
 * Metadata representing the connected friend in a Duo session.
 *
 * @property peerId User's pairing code (e.g., "LN-7842").
 * @property deviceName Friendly device name (e.g. "Redmi Note 11S" or "Pixel 8").
 * @property latencyMs Round-trip network latency in milliseconds.
 * @property isHost Whether this device is the host of the room.
 */
data class DuoPeerInfo(
    val peerId: String,
    val deviceName: String = "Lunify Partner",
    val latencyMs: Long = 0L,
    val isHost: Boolean = false,
)

/**
 * Lightweight fingerprint of an offline track used to calculate the intersection
 * of songs present on both devices.
 *
 * @property title Track song title.
 * @property artist Artist or channel name.
 * @property durationMs Track length in milliseconds (used for fuzzy matching).
 */
data class DuoTrackSignature(
    val title: String,
    val artist: String,
    val durationMs: Long,
) {
    /**
     * Converts this signature into a JSON object for network transmission.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("durationMs", durationMs)
        }
    }

    companion object {
        /**
         * Reconstructs a [DuoTrackSignature] from a JSON object.
         */
        fun fromJson(json: JSONObject): DuoTrackSignature {
            return DuoTrackSignature(
                title = json.optString("title", ""),
                artist = json.optString("artist", ""),
                durationMs = json.optLong("durationMs", 0L),
            )
        }
    }
}

/**
 * Real-time synchronization event model for backwards compatibility.
 */
data class DuoSyncPacket(
    val action: String,
    val trackId: String,
    val positionMs: Long,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Production-grade network packet exchanged between devices in a Duo room.
 *
 * Designed with full forward compatibility and zero third-party reflection:
 * uses Android's built-in [JSONObject] for lightning-fast parsing.
 */
data class DuoPacket(
    val version: Int = 1,
    val action: String,
    val senderId: String,
    val senderDeviceName: String,
    val roomCode: String,
    val trackId: String? = null,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val trackDurationMs: Long = 0L,
    val trackArtworkUri: String? = null,
    val trackPlaybackSource: String? = null, // "ONLINE" or "LOCAL"
    val positionMs: Long = 0L,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val manifest: List<DuoTrackSignature>? = null,
    val errorMessage: String? = null,
) {
    /**
     * Encodes this packet into a compact JSON string for transmission over MQTT/WebSocket.
     */
    fun toJson(): String {
        val root = JSONObject()
        root.put("v", version)
        root.put("act", action)
        root.put("sid", senderId)
        root.put("sdn", senderDeviceName)
        root.put("room", roomCode)
        root.put("ts", timestampEpochMs)

        if (trackId != null) root.put("tid", trackId)
        if (trackTitle != null) root.put("tt", trackTitle)
        if (trackArtist != null) root.put("ta", trackArtist)
        if (trackDurationMs > 0L) root.put("td", trackDurationMs)
        if (trackArtworkUri != null) root.put("art", trackArtworkUri)
        if (trackPlaybackSource != null) root.put("src", trackPlaybackSource)
        if (positionMs > 0L) root.put("pos", positionMs)
        if (errorMessage != null) root.put("err", errorMessage)

        if (manifest != null) {
            val manifestArray = JSONArray()
            manifest.forEach { sig ->
                manifestArray.put(sig.toJson())
            }
            root.put("man", manifestArray)
        }

        return root.toString()
    }

    companion object {
        /**
         * Safely decodes a JSON string into a [DuoPacket], returning null if malformed.
         */
        fun fromJson(rawJson: String): DuoPacket? {
            return try {
                val obj = JSONObject(rawJson)
                val action = obj.getString("act")
                val senderId = obj.getString("sid")
                val senderDeviceName = obj.optString("sdn", "Lunify Peer")
                val roomCode = obj.getString("room")
                val version = obj.optInt("v", 1)
                val timestampEpochMs = obj.optLong("ts", System.currentTimeMillis())

                val trackId = if (obj.has("tid")) obj.getString("tid") else null
                val trackTitle = if (obj.has("tt")) obj.getString("tt") else null
                val trackArtist = if (obj.has("ta")) obj.getString("ta") else null
                val trackDurationMs = obj.optLong("td", 0L)
                val trackArtworkUri = if (obj.has("art")) obj.getString("art") else null
                val trackPlaybackSource = if (obj.has("src")) obj.getString("src") else null
                val positionMs = obj.optLong("pos", 0L)
                val errorMessage = if (obj.has("err")) obj.getString("err") else null

                val manifest = if (obj.has("man")) {
                    val arr = obj.getJSONArray("man")
                    val list = mutableListOf<DuoTrackSignature>()
                    for (i in 0 until arr.length()) {
                        list.add(DuoTrackSignature.fromJson(arr.getJSONObject(i)))
                    }
                    list
                } else null

                DuoPacket(
                    version = version,
                    action = action,
                    senderId = senderId,
                    senderDeviceName = senderDeviceName,
                    roomCode = roomCode,
                    trackId = trackId,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    trackDurationMs = trackDurationMs,
                    trackArtworkUri = trackArtworkUri,
                    trackPlaybackSource = trackPlaybackSource,
                    positionMs = positionMs,
                    timestampEpochMs = timestampEpochMs,
                    manifest = manifest,
                    errorMessage = errorMessage,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
