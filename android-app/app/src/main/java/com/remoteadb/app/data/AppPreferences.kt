package com.remoteadb.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _serverHost = MutableStateFlow(prefs.getString(KEY_SERVER_HOST, "127.0.0.1") ?: "127.0.0.1")
    val serverHost: StateFlow<String> = _serverHost

    private val _serverPort = MutableStateFlow(prefs.getInt(KEY_SERVER_PORT, 8080))
    val serverPort: StateFlow<Int> = _serverPort

    private val _deviceId = MutableStateFlow(getOrGenerateDeviceId())
    val deviceId: StateFlow<String> = _deviceId

    private val _pin = MutableStateFlow(getOrGeneratePin())
    val pin: StateFlow<String> = _pin

    private val _autoStartAdb = MutableStateFlow(prefs.getBoolean(KEY_AUTO_START_ADB, true))
    val autoStartAdb: StateFlow<Boolean> = _autoStartAdb

    private val _localAdbPort = MutableStateFlow(prefs.getInt(KEY_LOCAL_ADB_PORT, 5555))
    val localAdbPort: StateFlow<Int> = _localAdbPort

    private val _tunnelMode = MutableStateFlow(
        try {
            TunnelMode.valueOf(prefs.getString(KEY_TUNNEL_MODE, TunnelMode.PUBLIC_FREE.name) ?: TunnelMode.PUBLIC_FREE.name)
        } catch (_: Exception) {
            TunnelMode.PUBLIC_FREE
        }
    )
    val tunnelMode: StateFlow<TunnelMode> = _tunnelMode

    private val _ngrokToken = MutableStateFlow(prefs.getString(KEY_NGROK_TOKEN, "") ?: "")
    val ngrokToken: StateFlow<String> = _ngrokToken

    fun updateTunnelMode(mode: TunnelMode) {
        prefs.edit().putString(KEY_TUNNEL_MODE, mode.name).apply()
        _tunnelMode.value = mode
    }

    fun updateNgrokToken(token: String) {
        prefs.edit().putString(KEY_NGROK_TOKEN, token).apply()
        _ngrokToken.value = token
    }

    fun updateServerConfig(host: String, port: Int) {
        prefs.edit()
            .putString(KEY_SERVER_HOST, host)
            .putInt(KEY_SERVER_PORT, port)
            .apply()
        _serverHost.value = host
        _serverPort.value = port
    }

    fun updatePin(newPin: String) {
        prefs.edit().putString(KEY_PIN, newPin).apply()
        _pin.value = newPin
    }

    fun updateDeviceId(newId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        _deviceId.value = newId
    }

    fun updateAutoStartAdb(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_ADB, enabled).apply()
        _autoStartAdb.value = enabled
    }

    fun updateLocalAdbPort(port: Int) {
        prefs.edit().putInt(KEY_LOCAL_ADB_PORT, port).apply()
        _localAdbPort.value = port
    }

    private fun getOrGenerateDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = "android-" + UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    private fun getOrGeneratePin(): String {
        var pin = prefs.getString(KEY_PIN, null)
        if (pin.isNullOrBlank()) {
            pin = (100000..999999).random().toString()
            prefs.edit().putString(KEY_PIN, pin).apply()
        }
        return pin
    }

    companion object {
        private const val PREFS_NAME = "remote_adb_prefs"
        private const val KEY_SERVER_HOST = "server_host"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PIN = "pin"
        private const val KEY_AUTO_START_ADB = "auto_start_adb"
        private const val KEY_LOCAL_ADB_PORT = "local_adb_port"
        private const val KEY_TUNNEL_MODE = "tunnel_mode"
        private const val KEY_NGROK_TOKEN = "ngrok_token"
    }
}
