package com.remoteadb.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remoteadb.app.data.AppPreferences
import com.remoteadb.app.root.AdbManager
import com.remoteadb.app.root.RootController
import com.remoteadb.app.root.RootStatus
import com.remoteadb.app.service.TunnelForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = AppPreferences(application)

    private val _rootStatus = MutableStateFlow<RootStatus?>(null)
    val rootStatus: StateFlow<RootStatus?> = _rootStatus

    private val _isAdbEnabled = MutableStateFlow(false)
    val isAdbEnabled: StateFlow<Boolean> = _isAdbEnabled

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val _trustedKeys = MutableStateFlow<List<String>>(emptyList())
    val trustedKeys: StateFlow<List<String>> = _trustedKeys

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _rootStatus.value = RootController.checkRoot()
            _isAdbEnabled.value = AdbManager.isAdbTcpEnabled()
            loadTrustedKeys()
        }
    }

    fun toggleAdb(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                val success = AdbManager.enableAdbTcp(prefs.localAdbPort.value)
                _isAdbEnabled.value = success
                _statusMessage.value = if (success) "ADB TCP/IP Enabled on ${prefs.localAdbPort.value}" else "Failed to enable ADB (Root required)"
            } else {
                val success = AdbManager.disableAdbTcp()
                _isAdbEnabled.value = !success
                _statusMessage.value = if (success) "ADB TCP/IP Disabled" else "Failed to disable ADB"
            }
        }
    }

    fun toggleTunnelService(start: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, TunnelForegroundService::class.java).apply {
            action = if (start) TunnelForegroundService.ACTION_START else TunnelForegroundService.ACTION_STOP
        }
        if (start) {
            context.startForegroundService(intent)
            _isServiceRunning.value = true
        } else {
            context.startService(intent)
            _isServiceRunning.value = false
        }
    }

    fun loadTrustedKeys() {
        viewModelScope.launch {
            _trustedKeys.value = AdbManager.getTrustedKeys()
        }
    }

    fun addPublicKey(key: String) {
        viewModelScope.launch {
            val success = AdbManager.injectPublicKey(key)
            if (success) {
                _statusMessage.value = "Added public key successfully"
                loadTrustedKeys()
            } else {
                _statusMessage.value = "Failed to add public key"
            }
        }
    }

    fun removePublicKey(key: String) {
        viewModelScope.launch {
            val success = AdbManager.removePublicKey(key)
            if (success) {
                _statusMessage.value = "Removed key"
                loadTrustedKeys()
            } else {
                _statusMessage.value = "Failed to remove key"
            }
        }
    }
}
