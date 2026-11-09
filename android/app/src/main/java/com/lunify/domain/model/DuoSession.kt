package com.lunify.domain.model

/**
 * ==============================================================================
 * Lunify - Duo Synchronization Domain Model (DuoSession.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the domain models powering our unique Duo feature.
 *
 * Duo allows two friends to connect over the internet (via WebRTC) or locally
 * over Wi-Fi Direct and listen to the same playlist in perfect time synchronization.
 * This file models connection states, peer metadata, and real-time sync packets.
 * ==============================================================================
 */

/**
 * State of the Duo pairing session.
 */
enum class DuoConnectionState {
    DISCONNECTED,
    CONNECTING,
    WAITING_FOR_PEER,
    INCOMING_REQUEST,
    CONNECTED,
    SYNCING,
}

/**
 * Metadata representing a connected peer in a Duo room.
 *
 * @property peerId User's pairing code (e.g., "LN-7842").
 * @property deviceName Device identifier or nickname.
 * @property latencyMs Round-trip network latency in milliseconds.
 * @property isHost Whether this peer is the active session host.
 */
data class DuoPeerInfo(
    val peerId: String,
    val deviceName: String = "Lunify Partner",
    val latencyMs: Long = 0L,
    val isHost: Boolean = false,
)

/**
 * Real-time synchronization event dispatched when a user plays, pauses, seeks,
 * or modifies the shared queue.
 *
 * @property action "PLAY", "PAUSE", "SEEK", or "QUEUE_CHANGE".
 * @property trackId ID of track being synced.
 * @property positionMs Position in milliseconds when action was triggered.
 * @property timestampEpochMs Client clock epoch timestamp used to compensate for network transit delay.
 */
data class DuoSyncPacket(
    val action: String,
    val trackId: String,
    val positionMs: Long,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)
