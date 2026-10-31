package com.remoteadb.app.tunnel

import android.util.Log
import com.remoteadb.app.data.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class TunnelState {
    object Disconnected : TunnelState()
    object Connecting : TunnelState()
    data class Connected(val assignedPort: Int) : TunnelState()
    data class Error(val message: String) : TunnelState()
}

class TunnelClient(
    private val serverHost: String,
    private val serverPort: Int,
    private val deviceId: String,
    private val pin: String,
    private val localAdbPort: Int = 5555
) {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var bridge: LocalAdbBridge? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null
    private var isUserStopped = false

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState

    fun connect() {
        isUserStopped = false
        reconnectJob?.cancel()
        _tunnelState.value = TunnelState.Connecting

        bridge = LocalAdbBridge(
            localPort = localAdbPort,
            queueSizeProvider = { webSocket?.queueSize() ?: 0L }
        ) { json ->
            webSocket?.send(json.toString())
        }

        val isSsl = serverPort == 443 || serverHost.contains("pinggy") || serverHost.contains("ngrok") || serverHost.contains("loca.lt")
        val protocol = if (isSsl) "wss" else "ws"
        val portSuffix = if (serverPort == 80 || serverPort == 443) "" else ":$serverPort"
        val wsUrl = "$protocol://$serverHost$portSuffix/ws/device"
        AppLogger.i(TAG, "Connecting to: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                AppLogger.i(TAG, "WebSocket connected! Registering device: $deviceId")
                val regMsg = JSONObject().apply {
                    put("type", "register")
                    put("deviceId", deviceId)
                    put("pin", pin)
                }
                ws.send(regMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                AppLogger.d(TAG, "Received: $text")
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                AppLogger.w(TAG, "Closing: $reason ($code)")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                AppLogger.w(TAG, "Closed ($code: $reason)")
                bridge?.closeAll()
                if (!isUserStopped) {
                    _tunnelState.value = TunnelState.Disconnected
                    scheduleReconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val body = try { response?.body?.string() } catch (_: Exception) { null }
                val errorDetails = "Failure (HTTP $code): ${t.message}${if (!body.isNullOrBlank()) " | Body: $body" else ""}"
                AppLogger.e(TAG, errorDetails, t)
                bridge?.closeAll()
                if (!isUserStopped) {
                    _tunnelState.value = TunnelState.Error(t.message ?: "Connection failed")
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.getString("type")) {
                "register_ack" -> {
                    val status = json.getString("status")
                    if (status == "ok") {
                        val port = json.getInt("assignedPort")
                        AppLogger.i(TAG, "Registration SUCCESS! Assigned Port: $port")
                        _tunnelState.value = TunnelState.Connected(port)
                    } else {
                        val msg = json.optString("message", "Registration failed")
                        AppLogger.e(TAG, "Registration REJECTED: $msg")
                        _tunnelState.value = TunnelState.Error(msg)
                    }
                }
                "stream_open" -> {
                    val streamId = json.getString("streamId")
                    bridge?.handleStreamOpen(streamId)
                }
                "stream_data" -> {
                    val streamId = json.getString("streamId")
                    val data = json.getString("data")
                    bridge?.handleStreamData(streamId, data)
                }
                "stream_close" -> {
                    val streamId = json.getString("streamId")
                    bridge?.closeStream(streamId)
                }
                "ping" -> {
                    val pong = JSONObject().apply {
                        put("type", "pong")
                        put("timestamp", System.currentTimeMillis())
                    }
                    webSocket?.send(pong.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    private fun scheduleReconnect() {
        if (isUserStopped) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            Log.i(TAG, "Attempting reconnect...")
            connect()
        }
    }

    fun disconnect() {
        isUserStopped = true
        reconnectJob?.cancel()
        bridge?.closeAll()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _tunnelState.value = TunnelState.Disconnected
    }

    companion object {
        private const val TAG = "TunnelClient"
    }
}
