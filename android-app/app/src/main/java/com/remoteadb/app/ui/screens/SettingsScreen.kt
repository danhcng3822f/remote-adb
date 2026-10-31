package com.remoteadb.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteadb.app.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val prefs = viewModel.prefs
    val currentHost by prefs.serverHost.collectAsState()
    val currentPort by prefs.serverPort.collectAsState()
    val currentDeviceId by prefs.deviceId.collectAsState()
    val currentPin by prefs.pin.collectAsState()
    val currentLocalPort by prefs.localAdbPort.collectAsState()
    val autoStartAdb by prefs.autoStartAdb.collectAsState()

    var hostInput by remember(currentHost) { mutableStateOf(currentHost) }
    var portInput by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    var deviceIdInput by remember(currentDeviceId) { mutableStateOf(currentDeviceId) }
    var pinInput by remember(currentPin) { mutableStateOf(currentPin) }
    var localPortInput by remember(currentLocalPort) { mutableStateOf(currentLocalPort.toString()) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Relay Server Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            label = { Text("Server Host (IP or Domain)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = portInput,
            onValueChange = { portInput = it },
            label = { Text("Server HTTP/WS Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Device Credentials", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        OutlinedTextField(
            value = deviceIdInput,
            onValueChange = { deviceIdInput = it },
            label = { Text("Device ID") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = pinInput,
            onValueChange = { pinInput = it },
            label = { Text("Security PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("ADB Configuration", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        OutlinedTextField(
            value = localPortInput,
            onValueChange = { localPortInput = it },
            label = { Text("Local ADB Port (Default: 5555)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto-start ADB when Tunnel starts", modifier = Modifier.weight(1f))
            Switch(
                checked = autoStartAdb,
                onCheckedChange = { prefs.updateAutoStartAdb(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val parsedPort = portInput.toIntOrNull() ?: 8080
                val parsedLocalPort = localPortInput.toIntOrNull() ?: 5555

                prefs.updateServerConfig(hostInput.trim(), parsedPort)
                prefs.updateDeviceId(deviceIdInput.trim())
                prefs.updatePin(pinInput.trim())
                prefs.updateLocalAdbPort(parsedLocalPort)

                Toast.makeText(context, "Settings Saved!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save All Settings")
        }
    }
}
