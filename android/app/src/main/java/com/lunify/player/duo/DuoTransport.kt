package com.lunify.player.duo

import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.DuoPacket
import com.lunify.domain.model.DuoPeerInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ==============================================================================
 * Lunify - Duo Network Transport Contract (DuoTransport.kt)
 * ==============================================================================
 *
 * Hey friend! This interface establishes the transport boundary for Lunify Duo.
 *
 * By isolating network transport behind this interface:
 * 1. Paho MQTT Relay (global TLS pub/sub across different cities/networks)
 * 2. Future Wi-Fi Direct (local peer-to-peer when both devices are in the same room)
 *
 * both implement this EXACT contract, allowing [DuoManager] to remain 100%
 * decoupled from transport-specific sockets or protocols!
 * ==============================================================================
 */
interface DuoTransport {

    /** Current state of the transport connection. */
    val connectionState: StateFlow<DuoConnectionState>

    /** Metadata regarding the currently connected partner peer, if any. */
    val peerInfo: StateFlow<DuoPeerInfo?>

    /** Stream of validated incoming packets received from the peer or relay. */
    val incomingPackets: SharedFlow<DuoPacket>

    /** Active room code currently bound to this transport session. */
    val activeRoomCode: StateFlow<String?>

    /** Pending join request packet awaiting host confirmation, if any. */
    val incomingRequest: StateFlow<DuoPacket?>

    /**
     * Confirms and accepts an incoming pairing request from a guest.
     */
    fun acceptIncomingRequest()

    /**
     * Rejects an incoming pairing request from a guest.
     */
    fun declineIncomingRequest()

    /**
     * Initiates a room as Host.
     *
     * @param roomCode The 6-character room code (e.g. "LN-7842").
     * @param onReady Called when room is claimed and waiting for peer.
     * @param onError Called if network connection or room claim fails.
     */
    fun startHosting(
        roomCode: String,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    )

    /**
     * Joins an existing host's room as Guest.
     *
     * @param roomCode The 6-character room code entered by the user.
     * @param onConnected Called when successfully paired with the host.
     * @param onError Called if room doesn't exist, is locked/full, or fails.
     */
    fun joinRoom(
        roomCode: String,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
    )

    /**
     * Transmits a synchronization packet to the connected peer.
     */
    fun sendPacket(packet: DuoPacket)

    /**
     * Gracefully disconnects, tears down MQTT topics or sockets, and resets state.
     */
    fun disconnect()
}
