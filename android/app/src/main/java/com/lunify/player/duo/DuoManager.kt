package com.lunify.player.duo

import android.content.Context
import android.os.Build
import android.util.Log
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.domain.model.DuoAction
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.DuoPacket
import com.lunify.domain.model.DuoPeerInfo
import com.lunify.domain.model.DuoTrackSignature
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.Track
import com.lunify.player.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ==============================================================================
 * Lunify - Duo Synchronization Coordinator (DuoManager.kt)
 * ==============================================================================
 *
 * Hey friend! This is the central brain coordinating Lunify Duo.
 *
 * RESPONSIBILITIES:
 * 1. Session Lifecycle:
 *    Hosting rooms, joining friends' rooms, and managing the pairing state machine.
 * 2. Shared Offline Library Calculation:
 *    When connected, collects local downloaded songs and exchanges manifests
 *    with the partner device. Filters and publishes [commonOfflineTracks], which
 *    powers the "Duo Library" UI view!
 * 3. Bidirectional Playback Synchronization:
 *    - Captures local Play / Pause / Seek events from [AudioPlayerManager] and
 *      broadcasts them over [DuoTransport].
 *    - Consumes incoming peer packets, calculates clock transit delays, and
 *      applies commands directly to [AudioPlayerManager] with anti-echo protection!
 * ==============================================================================
 */
class DuoManager(
    private val context: Context,
    private val localAudioRepository: LocalAudioRepository,
    private val audioPlayerManager: AudioPlayerManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    companion object {
        private const val TAG = "DuoManager"
    }

    private val myDeviceId = "dev_" + UUID.randomUUID().toString().take(8)
    private val myDeviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    // Pluggable transport layer (Paho MQTT Relay)
    private val transport: DuoTransport = PahoMqttDuoTransport(scope)

    // Current room code (e.g., "LN-7842")
    private val _myRoomCode = MutableStateFlow(DuoCodeUtils.generateRoomCode())
    val myRoomCode: StateFlow<String> = _myRoomCode.asStateFlow()

    // Observable connection state driving DuoSheet and LibraryScreen
    val connectionState: StateFlow<DuoConnectionState> = transport.connectionState
    val peerInfo: StateFlow<DuoPeerInfo?> = transport.peerInfo
    val incomingRequest: StateFlow<DuoPacket?> = transport.incomingRequest

    // Shared common offline tracks present on BOTH devices
    private val _commonOfflineTracks = MutableStateFlow<List<Track>>(emptyList())
    val commonOfflineTracks: StateFlow<List<Track>> = _commonOfflineTracks.asStateFlow()

    // Friendly error message for UI snackbars / alerts
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Flag indicating if we are the host of the active session
    private var isHost: Boolean = false

    init {
        // 1. Observe incoming sync packets from transport
        scope.launch {
            transport.incomingPackets.collectLatest { packet ->
                handleIncomingPacket(packet)
            }
        }

        // 2. Observe connection state transitions
        scope.launch {
            transport.connectionState.collectLatest { state ->
                when (state) {
                    DuoConnectionState.CONNECTED -> {
                        Log.i(TAG, "Duo paired! Exchanging catalog manifests...")
                        exchangeCatalogManifest()
                    }
                    DuoConnectionState.DISCONNECTED -> {
                        _commonOfflineTracks.value = emptyList()
                    }
                    else -> Unit
                }
            }
        }

        // 3. Register our playback listener with AudioPlayerManager
        audioPlayerManager.duoSyncDispatcher = { action, track, positionMs ->
            if (connectionState.value == DuoConnectionState.CONNECTED) {
                dispatchLocalPlaybackAction(action, track, positionMs)
            }
        }
    }

    /**
     * Re-rolls a fresh random pairing code.
     */
    fun refreshRoomCode() {
        if (connectionState.value == DuoConnectionState.DISCONNECTED) {
            _myRoomCode.value = DuoCodeUtils.generateRoomCode()
        }
    }

    /**
     * Starts hosting a session with our room code.
     */
    fun hostSession(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        _errorMessage.value = null
        isHost = true
        val code = _myRoomCode.value

        transport.startHosting(
            roomCode = code,
            onReady = onReady,
            onError = { err ->
                _errorMessage.value = err
                onError(err)
            }
        )
    }

    /**
     * Joins a friend's hosted room using their pairing code.
     */
    fun joinSession(
        targetCode: String,
        onConnected: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        _errorMessage.value = null
        isHost = false
        val normalized = DuoCodeUtils.normalizeRoomCode(targetCode)

        if (!DuoCodeUtils.isValidRoomCode(normalized)) {
            val err = "Invalid room code format. Expected LN-XXXX"
            _errorMessage.value = err
            onError(err)
            return
        }

        _myRoomCode.value = normalized

        transport.joinRoom(
            roomCode = normalized,
            onConnected = onConnected,
            onError = { err ->
                _errorMessage.value = err
                onError(err)
            }
        )
    }

    /**
     * Confirms and accepts an incoming pairing request from a friend.
     */
    fun acceptPairing() {
        transport.acceptIncomingRequest()
    }

    /**
     * Rejects an incoming pairing request from a friend.
     */
    fun declinePairing() {
        transport.declineIncomingRequest()
    }

    /**
     * Disconnects from the current Duo session and restores standalone playback.
     */
    fun disconnect() {
        transport.disconnect()
        _commonOfflineTracks.value = emptyList()
        _errorMessage.value = null
        // Re-roll room code for the next session
        _myRoomCode.value = DuoCodeUtils.generateRoomCode()
    }

    /**
     * Clears the active error message after UI display.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Scans local offline tracks and broadcasts our catalog manifest to the peer.
     */
    private fun exchangeCatalogManifest() {
        scope.launch(Dispatchers.IO) {
            val localTracks = localAudioRepository.getLocalAudioTracks()
            val signatures = localTracks
                .distinctBy {
                    "${DuoCodeUtils.normalizeTrackTitle(it.title)}|${DuoCodeUtils.normalizeArtistName(it.artist)}"
                }
                .map { track ->
                    DuoTrackSignature(
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                    )
                }

            val manifestPacket = DuoPacket(
                action = DuoAction.CATALOG_MANIFEST,
                senderId = myDeviceId,
                senderDeviceName = myDeviceName,
                roomCode = myRoomCode.value,
                manifest = signatures,
            )

            transport.sendPacket(manifestPacket)
        }
    }

    /**
     * Dispatches a local playback event (Play, Pause, Seek) to the connected peer.
     */
    private fun dispatchLocalPlaybackAction(
        action: String,
        track: Track?,
        positionMs: Long,
    ) {
        val currentRoom = myRoomCode.value
        val packet = DuoPacket(
            action = action,
            senderId = myDeviceId,
            senderDeviceName = myDeviceName,
            roomCode = currentRoom,
            trackId = track?.id,
            trackTitle = track?.title,
            trackArtist = track?.artist,
            trackDurationMs = track?.durationMs ?: 0L,
            trackArtworkUri = track?.thumbnailUrl,
            trackPlaybackSource = if (track?.isDownloaded == true) "LOCAL" else "ONLINE",
            positionMs = positionMs,
            timestampEpochMs = System.currentTimeMillis(),
        )

        transport.sendPacket(packet)
    }

    /**
     * Handles an incoming playback or catalog packet from the peer.
     */
    private fun handleIncomingPacket(packet: DuoPacket) {
        when (packet.action) {
            DuoAction.CATALOG_MANIFEST -> {
                handleRemoteManifest(packet.manifest ?: emptyList())
            }

            DuoAction.PLAY -> {
                handleRemotePlay(packet)
            }

            DuoAction.PAUSE -> {
                handleRemotePause(packet)
            }

            DuoAction.SEEK -> {
                handleRemoteSeek(packet)
            }
        }
    }

    /**
     * Reconciles remote catalog manifest with local downloaded tracks.
     */
    private fun handleRemoteManifest(remoteManifest: List<DuoTrackSignature>) {
        scope.launch(Dispatchers.IO) {
            val localTracks = localAudioRepository.getLocalAudioTracks()
            val common = DuoCodeUtils.filterCommonOfflineTracks(localTracks, remoteManifest)

            Log.i(TAG, "Reconciled offline libraries: ${common.size} common songs identified.")
            _commonOfflineTracks.value = common
        }
    }

    /**
     * Executes an incoming remote Play command with network latency compensation.
     */
    private fun handleRemotePlay(packet: DuoPacket) {
        val now = System.currentTimeMillis()
        val networkTransitMs = (now - packet.timestampEpochMs).coerceAtLeast(0L)
        val compensatedPositionMs = packet.positionMs + networkTransitMs

        scope.launch(Dispatchers.Main) {
            if (packet.trackPlaybackSource == "LOCAL") {
                // Find matching local downloaded track
                val localMatch = commonOfflineTracks.value.firstOrNull { localTrack ->
                    DuoCodeUtils.areTracksMatching(
                        localTrack,
                        DuoTrackSignature(
                            title = packet.trackTitle ?: "",
                            artist = packet.trackArtist ?: "",
                            durationMs = packet.trackDurationMs,
                        )
                    )
                } ?: localAudioRepository.getLocalAudioTracks().firstOrNull { localTrack ->
                    DuoCodeUtils.areTracksMatching(
                        localTrack,
                        DuoTrackSignature(
                            title = packet.trackTitle ?: "",
                            artist = packet.trackArtist ?: "",
                            durationMs = packet.trackDurationMs,
                        )
                    )
                }

                if (localMatch != null) {
                    audioPlayerManager.playTrack(
                        track = localMatch,
                        isRemoteSync = true,
                        startPositionMs = compensatedPositionMs,
                    )
                } else {
                    Log.w(TAG, "Local offline track not found on this device: ${packet.trackTitle}")
                }
            } else {
                // Online YouTube stream
                val onlineTrack = Track(
                    id = packet.trackId ?: "",
                    title = packet.trackTitle ?: "Unknown Track",
                    artist = packet.trackArtist ?: "Unknown Artist",
                    durationMs = packet.trackDurationMs,
                    thumbnailUrl = packet.trackArtworkUri ?: "",
                    isDownloaded = false,
                )

                audioPlayerManager.playTrack(
                    track = onlineTrack,
                    isRemoteSync = true,
                    startPositionMs = compensatedPositionMs,
                )
            }
        }
    }

    /**
     * Executes an incoming remote Pause command.
     */
    private fun handleRemotePause(packet: DuoPacket) {
        scope.launch(Dispatchers.Main) {
            audioPlayerManager.pause(isRemoteSync = true)
        }
    }

    /**
     * Executes an incoming remote Seek command with latency compensation.
     */
    private fun handleRemoteSeek(packet: DuoPacket) {
        val now = System.currentTimeMillis()
        val networkTransitMs = (now - packet.timestampEpochMs).coerceAtLeast(0L)
        val compensatedPositionMs = packet.positionMs + networkTransitMs

        scope.launch(Dispatchers.Main) {
            audioPlayerManager.seekTo(
                positionMs = compensatedPositionMs,
                isRemoteSync = true,
            )
        }
    }
}
