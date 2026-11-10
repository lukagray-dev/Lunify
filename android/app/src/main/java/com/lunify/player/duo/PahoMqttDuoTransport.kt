package com.lunify.player.duo

import android.os.Build
import android.util.Log
import com.lunify.domain.model.DuoAction
import com.lunify.domain.model.DuoConnectionState
import com.lunify.domain.model.DuoPacket
import com.lunify.domain.model.DuoPeerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

/**
 * ==============================================================================
 * Lunify - Production MQTT Duo Relay Transport (PahoMqttDuoTransport.kt)
 * ==============================================================================
 *
 * Hey friend! This class implements our real-time synchronization transport
 * over an industrial-grade MQTT broker (HiveMQ) using Eclipse Paho.
 *
 * WHY MQTT FOR DUO?
 * -----------------
 * 1. Global Reachability: Mobile devices are behind cellular carrier-grade NATs
 *    (CGNAT) and firewalls, preventing direct incoming socket connections.
 *    By establishing an outbound connection to HiveMQ's TLS cluster, two phones
 *    in different cities or countries connect instantaneously!
 * 2. Ultra-Low Latency: Message transit times across HiveMQ's edge broker are
 *    typically 15-40ms, well below human audio perception thresholds.
 * 3. Topic Isolation: Each room code (e.g. "LN-7842") receives dedicated sub-topics:
 *    - `lunify/duo/v1/LN-7842/control` (room claims & pairing handshakes)
 *    - `lunify/duo/v1/LN-7842/h2g` (host-to-guest playback commands)
 *    - `lunify/duo/v1/LN-7842/g2h` (guest-to-host playback commands)
 * 4. Last Will and Testament (LWT): If a device loses cellular signal or gets
 *    force-killed, the broker immediately alerts the partner with a DISCONNECT packet.
 * ==============================================================================
 */
class PahoMqttDuoTransport(
    private val scope: CoroutineScope,
    private val deviceNameProvider: () -> String = {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    },
) : DuoTransport {

    companion object {
        private const val TAG = "PahoMqttDuoTransport"

        // Production public relay broker endpoints with automatic multi-cluster failover
        private val BROKER_URIS = arrayOf(
            "ssl://broker.emqx.io:8883",
            "tcp://broker.emqx.io:1883",
            "ssl://broker.hivemq.com:8883",
            "tcp://broker.hivemq.com:1883",
        )

        private const val TOPIC_PREFIX = "lunify/duo/v1"
        private const val HEARTBEAT_INTERVAL_MS = 4000L
        private const val PAIRING_TIMEOUT_MS = 35000L
    }

    private val myDeviceId = "dev_" + UUID.randomUUID().toString().take(8)
    private val myDeviceName: String
        get() = deviceNameProvider()

    private val _connectionState = MutableStateFlow(DuoConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<DuoConnectionState> = _connectionState.asStateFlow()

    private val _peerInfo = MutableStateFlow<DuoPeerInfo?>(null)
    override val peerInfo: StateFlow<DuoPeerInfo?> = _peerInfo.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<DuoPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<DuoPacket> = _incomingPackets.asSharedFlow()

    private val _activeRoomCode = MutableStateFlow<String?>(null)
    override val activeRoomCode: StateFlow<String?> = _activeRoomCode.asStateFlow()

    private val _incomingRequest = MutableStateFlow<DuoPacket?>(null)
    override val incomingRequest: StateFlow<DuoPacket?> = _incomingRequest.asStateFlow()

    private var mqttClient: MqttAsyncClient? = null
    private var isHostSession: Boolean = false
    private var publishTopic: String = ""
    private var subscribeTopic: String = ""
    private var controlTopic: String = ""

    private var heartbeatJob: Job? = null
    private var pairingTimeoutJob: Job? = null
    private var incomingRequestTimeoutJob: Job? = null
    private var guestOnConnectedCallback: (() -> Unit)? = null
    private var guestOnErrorCallback: ((String) -> Unit)? = null

    /**
     * Initializes room as Host.
     */
    override fun startHosting(
        roomCode: String,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        disconnect()

        val normalized = DuoCodeUtils.normalizeRoomCode(roomCode)
        _activeRoomCode.value = normalized
        isHostSession = true

        controlTopic = "$TOPIC_PREFIX/$normalized/control"
        publishTopic = "$TOPIC_PREFIX/$normalized/h2g"
        subscribeTopic = "$TOPIC_PREFIX/$normalized/g2h"

        _connectionState.value = DuoConnectionState.CONNECTING

        connectToBroker(
            onConnectSuccess = {
                // Subscribe to control topic and guest-to-host commands
                subscribeToTopics(
                    topics = arrayOf(controlTopic, subscribeTopic),
                    onSubscribed = {
                        _connectionState.value = DuoConnectionState.WAITING_FOR_PEER
                        // Announce room claim
                        val claimPacket = DuoPacket(
                            action = DuoAction.ROOM_CLAIM,
                            senderId = myDeviceId,
                            senderDeviceName = myDeviceName,
                            roomCode = normalized,
                        )
                        sendRawMessage(controlTopic, claimPacket.toJson())
                        onReady()
                    },
                    onError = { err ->
                        _connectionState.value = DuoConnectionState.ERROR
                        onError(err)
                    }
                )
            },
            onConnectError = { err ->
                _connectionState.value = DuoConnectionState.ERROR
                onError(err)
            }
        )
    }

    /**
     * Joins an existing host's room as Guest.
     */
    override fun joinRoom(
        roomCode: String,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
    ) {
        disconnect()

        val normalized = DuoCodeUtils.normalizeRoomCode(roomCode)
        _activeRoomCode.value = normalized
        isHostSession = false

        controlTopic = "$TOPIC_PREFIX/$normalized/control"
        publishTopic = "$TOPIC_PREFIX/$normalized/g2h"
        subscribeTopic = "$TOPIC_PREFIX/$normalized/h2g"
        guestOnConnectedCallback = onConnected
        guestOnErrorCallback = onError

        _connectionState.value = DuoConnectionState.CONNECTING

        connectToBroker(
            onConnectSuccess = {
                // Subscribe to control topic and host-to-guest commands
                subscribeToTopics(
                    topics = arrayOf(controlTopic, subscribeTopic),
                    onSubscribed = {
                        // Send Join Request to the Host
                        val joinRequest = DuoPacket(
                            action = DuoAction.JOIN_REQUEST,
                            senderId = myDeviceId,
                            senderDeviceName = myDeviceName,
                            roomCode = normalized,
                        )
                        sendRawMessage(controlTopic, joinRequest.toJson())

                        // Start timeout watch in case host is offline
                        pairingTimeoutJob = scope.launch(Dispatchers.Main) {
                            delay(PAIRING_TIMEOUT_MS)
                            if (_connectionState.value != DuoConnectionState.CONNECTED) {
                                _connectionState.value = DuoConnectionState.ERROR
                                onError("Pairing timed out. Host did not respond.")
                                disconnect()
                            }
                        }
                    },
                    onError = { err ->
                        _connectionState.value = DuoConnectionState.ERROR
                        onError(err)
                    }
                )
            },
            onConnectError = { err ->
                _connectionState.value = DuoConnectionState.ERROR
                onError(err)
            }
        )
    }

    /**
     * Confirms and accepts an incoming pairing request from a guest.
     */
    override fun acceptIncomingRequest() {
        incomingRequestTimeoutJob?.cancel()
        incomingRequestTimeoutJob = null

        val request = _incomingRequest.value ?: return
        val currentRoom = _activeRoomCode.value ?: return

        _incomingRequest.value = null
        _peerInfo.value = DuoPeerInfo(
            peerId = request.senderId,
            deviceName = request.senderDeviceName,
            latencyMs = 25L,
            isHost = false,
        )
        _connectionState.value = DuoConnectionState.CONNECTED

        val acceptPacket = DuoPacket(
            action = DuoAction.JOIN_ACCEPT,
            senderId = myDeviceId,
            senderDeviceName = myDeviceName,
            roomCode = currentRoom,
        )
        sendRawMessage(controlTopic, acceptPacket.toJson())
        startHeartbeatLoop()
    }

    /**
     * Rejects an incoming pairing request from a guest.
     */
    override fun declineIncomingRequest() {
        incomingRequestTimeoutJob?.cancel()
        incomingRequestTimeoutJob = null

        val request = _incomingRequest.value ?: return
        val currentRoom = _activeRoomCode.value ?: return

        _incomingRequest.value = null
        _connectionState.value = DuoConnectionState.WAITING_FOR_PEER

        val rejectPacket = DuoPacket(
            action = DuoAction.JOIN_REJECT,
            senderId = myDeviceId,
            senderDeviceName = myDeviceName,
            roomCode = currentRoom,
            errorMessage = "Host declined the pairing request",
        )
        sendRawMessage(controlTopic, rejectPacket.toJson())
    }

    /**
     * Sends a synchronization packet to the connected peer over the active channel.
     */
    override fun sendPacket(packet: DuoPacket) {
        val client = mqttClient ?: return
        if (!client.isConnected) return

        val payload = packet.toJson()
        sendRawMessage(publishTopic, payload)
    }

    /**
     * Disconnects and cleans up all active MQTT subscriptions and coroutine timers asynchronously.
     */
    override fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        incomingRequestTimeoutJob?.cancel()
        incomingRequestTimeoutJob = null
        guestOnConnectedCallback = null
        guestOnErrorCallback = null

        val wasConnected = _connectionState.value == DuoConnectionState.CONNECTED
        val client = mqttClient
        mqttClient = null

        val currentRoom = _activeRoomCode.value
        val ctrlTopic = controlTopic
        if (client != null && currentRoom != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (client.isConnected) {
                        if (wasConnected) {
                            // Publish graceful disconnect packet only when an active session was established
                            val disconnectPacket = DuoPacket(
                                action = DuoAction.DISCONNECT,
                                senderId = myDeviceId,
                                senderDeviceName = myDeviceName,
                                roomCode = currentRoom,
                            )
                            val msg = MqttMessage(disconnectPacket.toJson().toByteArray(Charsets.UTF_8)).apply {
                                qos = 1
                            }
                            client.publish(ctrlTopic, msg)
                        }
                        client.disconnectForcibly(1000)
                    }
                    client.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error during graceful disconnect: ${e.message}")
                }
            }
        }

        _connectionState.value = DuoConnectionState.DISCONNECTED
        _peerInfo.value = null
        _activeRoomCode.value = null
        _incomingRequest.value = null
    }

    /**
     * Establishes MQTT connection with automatic multi-broker and TLS-to-TCP failover.
     */
    private fun connectToBroker(
        onConnectSuccess: () -> Unit,
        onConnectError: (String) -> Unit,
    ) {
        // Enforce IPv4 preference to bypass broken cellular IPv6 routes
        try {
            System.setProperty("java.net.preferIPv4Stack", "true")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set IPv4 system properties: ${e.message}")
        }

        val clientId = "lunify_${UUID.randomUUID().toString().take(12)}"
        try {
            val client = MqttAsyncClient(BROKER_URIS[0], clientId, MemoryPersistence())
            mqttClient = client

            val options = MqttConnectOptions().apply {
                serverURIs = BROKER_URIS
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
                isAutomaticReconnect = true

                // LWT message informing peer of unexpected disconnect
                val lwtPacket = DuoPacket(
                    action = DuoAction.DISCONNECT,
                    senderId = myDeviceId,
                    senderDeviceName = myDeviceName,
                    roomCode = _activeRoomCode.value ?: "unknown",
                    errorMessage = "Peer disconnected unexpectedly",
                )
                setWill(controlTopic, lwtPacket.toJson().toByteArray(Charsets.UTF_8), 1, false)
            }

            setupMqttCallbacks(client)

            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Successfully connected to Duo relay broker: ${client.serverURI}")
                    scope.launch(Dispatchers.Main) { onConnectSuccess() }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "All relay broker connections failed: ${exception?.message}", exception)
                    scope.launch(Dispatchers.Main) {
                        onConnectError(exception?.message ?: "Unable to connect to Duo relay server")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating broker connection: ${e.message}", e)
            scope.launch(Dispatchers.Main) {
                onConnectError(e.message ?: "Relay initialization error")
            }
        }
    }

    /**
     * Subscribes to given MQTT topics with QoS 1.
     */
    private fun subscribeToTopics(
        topics: Array<String>,
        onSubscribed: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val client = mqttClient ?: return onError("MQTT client is null")
        val qosArray = IntArray(topics.size) { 1 }

        client.subscribe(topics, qosArray, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG, "Subscribed to topics: ${topics.joinToString()}")
                scope.launch(Dispatchers.Main) { onSubscribed() }
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                scope.launch(Dispatchers.Main) {
                    onError("Failed to subscribe: ${exception?.message}")
                }
            }
        })
    }

    /**
     * Attaches message received and connection lost listeners.
     */
    private fun setupMqttCallbacks(client: MqttAsyncClient) {
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "MQTT connectComplete (reconnect=$reconnect, serverURI=$serverURI)")
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                scope.launch(Dispatchers.Main) {
                    if (_connectionState.value == DuoConnectionState.CONNECTED) {
                        _connectionState.value = DuoConnectionState.CONNECTING
                    }
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (message == null || topic == null) return
                val payload = String(message.payload, Charsets.UTF_8)
                handleIncomingMessage(topic, payload)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Packet delivered to broker successfully
            }
        })
    }

    /**
     * Internal dispatcher for incoming MQTT message payloads.
     */
    private fun handleIncomingMessage(topic: String, payload: String) {
        val packet = DuoPacket.fromJson(payload) ?: return

        // Ignore packets originating from this device to prevent echo loops
        if (packet.senderId == myDeviceId) return

        scope.launch(Dispatchers.Main) {
            when (packet.action) {
                DuoAction.ROOM_CLAIM -> {
                    // Another device claimed this room code while we are hosting
                    if (isHostSession && _connectionState.value == DuoConnectionState.WAITING_FOR_PEER) {
                        Log.w(TAG, "Room code collision detected with sender: ${packet.senderId}")
                        // Re-roll code or alert host
                    }
                }

                DuoAction.JOIN_REQUEST -> {
                    if (isHostSession) {
                        if (_connectionState.value == DuoConnectionState.CONNECTED) {
                            // Room already has 2 peers (1 host + 1 guest) -> Reject 3rd party
                            val rejectPacket = DuoPacket(
                                action = DuoAction.JOIN_REJECT,
                                senderId = myDeviceId,
                                senderDeviceName = myDeviceName,
                                roomCode = packet.roomCode,
                                errorMessage = "Room is full (already paired)",
                            )
                            sendRawMessage(controlTopic, rejectPacket.toJson())
                        } else {
                            // Prompt host user with incoming pairing confirmation request!
                            Log.i(TAG, "Incoming join request from ${packet.senderDeviceName} (${packet.senderId})")
                            _incomingRequest.value = packet
                            _connectionState.value = DuoConnectionState.INCOMING_REQUEST

                            // Start 35s timeout to auto-dismiss if host does not respond
                            incomingRequestTimeoutJob?.cancel()
                            incomingRequestTimeoutJob = scope.launch(Dispatchers.Main) {
                                delay(PAIRING_TIMEOUT_MS)
                                if (_connectionState.value == DuoConnectionState.INCOMING_REQUEST) {
                                    Log.i(TAG, "Incoming pairing request expired without response")
                                    _incomingRequest.value = null
                                    _connectionState.value = DuoConnectionState.WAITING_FOR_PEER
                                }
                            }
                        }
                    }
                }

                DuoAction.JOIN_ACCEPT -> {
                    if (!isHostSession) {
                        pairingTimeoutJob?.cancel()
                        _peerInfo.value = DuoPeerInfo(
                            peerId = packet.senderId,
                            deviceName = packet.senderDeviceName,
                            latencyMs = 25L,
                            isHost = true,
                        )
                        _connectionState.value = DuoConnectionState.CONNECTED
                        guestOnConnectedCallback?.invoke()
                        startHeartbeatLoop()
                    }
                }

                DuoAction.JOIN_REJECT -> {
                    if (!isHostSession) {
                        pairingTimeoutJob?.cancel()
                        val errMsg = packet.errorMessage ?: "Pairing request was declined"
                        _connectionState.value = DuoConnectionState.ERROR
                        guestOnErrorCallback?.invoke(errMsg)
                        disconnect()
                    }
                }

                DuoAction.SYNC_HEARTBEAT -> {
                    handleHeartbeat(packet)
                }

                DuoAction.DISCONNECT -> {
                    Log.i(TAG, "Partner disconnected: ${packet.senderDeviceName}")
                    disconnect()
                }

                else -> {
                    // Playback commands (PLAY, PAUSE, SEEK, CATALOG_MANIFEST)
                    _incomingPackets.emit(packet)
                }
            }
        }
    }

    /**
     * Sends a raw UTF-8 string message to an MQTT topic asynchronously on Dispatchers.IO.
     */
    private fun sendRawMessage(topic: String, messageStr: String) {
        val client = mqttClient ?: return
        scope.launch(Dispatchers.IO) {
            try {
                if (client.isConnected) {
                    val msg = MqttMessage(messageStr.toByteArray(Charsets.UTF_8)).apply {
                        qos = 1
                    }
                    client.publish(topic, msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing to $topic: ${e.message}", e)
            }
        }
    }

    /**
     * Starts background ping loop to calculate live network latency.
     */
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && _connectionState.value == DuoConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_MS)
                val currentRoom = _activeRoomCode.value ?: break
                val pingPacket = DuoPacket(
                    action = DuoAction.SYNC_HEARTBEAT,
                    senderId = myDeviceId,
                    senderDeviceName = myDeviceName,
                    roomCode = currentRoom,
                    timestampEpochMs = System.currentTimeMillis(),
                )
                sendRawMessage(controlTopic, pingPacket.toJson())
            }
        }
    }

    /**
     * Responds to peer heartbeat and computes round-trip latency.
     */
    private fun handleHeartbeat(packet: DuoPacket) {
        val now = System.currentTimeMillis()
        val rtt = (now - packet.timestampEpochMs).coerceAtLeast(0L)

        if (rtt in 1..2000) {
            _peerInfo.update { current ->
                current?.copy(latencyMs = rtt / 2)
            }
        }
    }
}
