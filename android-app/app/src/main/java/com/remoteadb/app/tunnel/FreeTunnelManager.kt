package com.remoteadb.app.tunnel

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class FreeTunnelManager(
    private val localPort: Int = 5555
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var tunnelJob: Job? = null
    private var isRunning = false

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state

    private val _publicAddress = MutableStateFlow<String>("")
    val publicAddress: StateFlow<String> = _publicAddress

    fun startPublicTunnel() {
        stopTunnel()
        isRunning = true
        _state.value = TunnelState.Connecting

        tunnelJob = scope.launch {
            try {
                // Use Pinggy TCP tunnel (No sign-up, free public relay for TCP)
                // Pinggy exposes TCP tunnels via SSH reverse port forwarding
                Log.d(TAG, "Starting Pinggy public tunnel for 127.0.0.1:$localPort...")
                
                // Check if ssh binary is available or use root shell to launch ssh tunnel
                val sshCmd = "ssh -p 443 -R0:localhost:$localPort -o StrictHostKeyChecking=no -o ServerAliveInterval=10 a.pinggy.io"
                val process = Shell.cmd(sshCmd).exec()
                
                // Fallback: If ssh not on device, we can connect to a public WebSocket relay server
                Log.d(TAG, "SSH command executed: ${process.isSuccess}")
            } catch (e: Exception) {
                Log.e(TAG, "Free tunnel error: ${e.message}", e)
                _state.value = TunnelState.Error(e.message ?: "Failed to establish public tunnel")
            }
        }
    }

    fun startNgrok(authtoken: String) {
        stopTunnel()
        isRunning = true
        _state.value = TunnelState.Connecting

        tunnelJob = scope.launch {
            try {
                Log.d(TAG, "Starting Ngrok TCP tunnel on port $localPort...")
                // Execute ngrok if binary present, or configure ngrok token
                if (authtoken.isNotBlank()) {
                    Shell.cmd("ngrok config add-authtoken $authtoken").exec()
                }
                val result = Shell.cmd("ngrok tcp $localPort --log=stdout").exec()
                Log.d(TAG, "Ngrok result: ${result.isSuccess}")
            } catch (e: Exception) {
                _state.value = TunnelState.Error(e.message ?: "Ngrok tunnel failed")
            }
        }
    }

    fun stopTunnel() {
        isRunning = false
        tunnelJob?.cancel()
        Shell.cmd("pkill -f pinggy", "pkill -f ngrok").exec()
        _state.value = TunnelState.Disconnected
        _publicAddress.value = ""
    }

    companion object {
        private const val TAG = "FreeTunnelManager"
    }
}
