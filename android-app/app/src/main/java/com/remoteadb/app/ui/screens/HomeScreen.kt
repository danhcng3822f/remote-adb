package com.remoteadb.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteadb.app.root.RootProvider
import com.remoteadb.app.ui.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val rootStatus by viewModel.rootStatus.collectAsState()
    val isAdbEnabled by viewModel.isAdbEnabled.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val serverHost by viewModel.prefs.serverHost.collectAsState()
    val serverPort by viewModel.prefs.serverPort.collectAsState()
    val deviceId by viewModel.prefs.deviceId.collectAsState()
    val pin by viewModel.prefs.pin.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Root Status Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (rootStatus?.isRooted == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Root Status",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (rootStatus?.isRooted == true) "Root Access Granted" else "No Root Detected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = when (rootStatus?.provider) {
                            RootProvider.MAGISK -> "Magisk (${rootStatus?.versionInfo})"
                            RootProvider.KERNEL_SU -> "KernelSU (${rootStatus?.versionInfo})"
                            RootProvider.APATCH -> "APatch (${rootStatus?.versionInfo})"
                            RootProvider.SUPER_SU -> "SuperSU"
                            RootProvider.UNKNOWN -> "Root: ${rootStatus?.versionInfo}"
                            else -> "App requires Root to toggle ADB without cables"
                        },
                        fontSize = 14.sp
                    )
                }
                IconButton(onClick = { viewModel.refreshAll() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // ADB TCP Control Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ADB Daemon (TCP:5555)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            if (isAdbEnabled) "Active and listening on 127.0.0.1:5555" else "Inactive",
                            fontSize = 12.sp,
                            color = if (isAdbEnabled) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isAdbEnabled,
                        onCheckedChange = { viewModel.toggleAdb(it) },
                        enabled = rootStatus?.isRooted == true
                    )
                }
            }
        }

        // Remote Tunnel Service Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Internet Tunnel Relay", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                // Mode Selector
                val currentMode by viewModel.prefs.tunnelMode.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentMode == com.remoteadb.app.data.TunnelMode.PUBLIC_FREE,
                        onClick = { viewModel.prefs.updateTunnelMode(com.remoteadb.app.data.TunnelMode.PUBLIC_FREE) },
                        label = { Text("🌐 Public Free") }
                    )
                    FilterChip(
                        selected = currentMode == com.remoteadb.app.data.TunnelMode.CUSTOM_RELAY,
                        onClick = { viewModel.prefs.updateTunnelMode(com.remoteadb.app.data.TunnelMode.CUSTOM_RELAY) },
                        label = { Text("🖥️ Custom VPS") }
                    )
                }

                if (currentMode == com.remoteadb.app.data.TunnelMode.CUSTOM_RELAY) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Server:", color = Color.Gray)
                        Text("$serverHost:$serverPort", fontWeight = FontWeight.Medium)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Relay:", color = Color.Gray)
                        Text("Public Free Tunnel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Device ID:", color = Color.Gray)
                    Text(deviceId, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PIN Code:", color = Color.Gray)
                    Text(pin, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { viewModel.toggleTunnelService(!isServiceRunning) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isServiceRunning) "Stop Tunnel Relay" else "Start Tunnel Relay")
                }
            }
        }

        // PC Command Quick Copy Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PC Connect Command", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                
                val isTunnelDomain = serverHost.contains("loca.lt") || serverHost.contains("pinggy") || serverHost.contains("ngrok")
                val connectTarget = if (isTunnelDomain) "127.0.0.1:15000" else "$serverHost:15000"
                val connectCmd = "adb connect $connectTarget"

                Text(
                    text = connectCmd,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isTunnelDomain) {
                    Text(
                        text = "💡 Server đang chạy trên PC của bạn (chuyển tiếp qua $serverHost). Trên PC chỉ cần gõ connect tới 127.0.0.1.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ADB Connect Command", connectCmd)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied command to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Command")
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
