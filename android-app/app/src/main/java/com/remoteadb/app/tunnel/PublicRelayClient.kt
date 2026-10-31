package com.remoteadb.app.tunnel

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Direct WebSocket / TCP Bridge to public free reverse relay servers.
 * Connects to public relays without requiring user-hosted VPS.
 */
class PublicRelayClient(
    private val publicRelayHost: String = "relay.remoteadb.org",
    private val publicRelayPort: Int = 8080,
    private val deviceId: String,
    private val pin: String,
    private val localAdbPort: Int = 5555
) {
    private val delegate = TunnelClient(
        serverHost = publicRelayHost,
        serverPort = publicRelayPort,
        deviceId = deviceId,
        pin = pin,
        localAdbPort = localAdbPort
    )

    val tunnelState: StateFlow<TunnelState> = delegate.tunnelState

    fun connect() = delegate.connect()
    fun disconnect() = delegate.disconnect()
}
