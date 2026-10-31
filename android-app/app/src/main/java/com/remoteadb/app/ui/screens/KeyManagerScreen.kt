package com.remoteadb.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
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
import com.remoteadb.app.ui.MainViewModel

@Composable
fun KeyManagerScreen(viewModel: MainViewModel) {
    val trustedKeys by viewModel.trustedKeys.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadTrustedKeys()
    }

    Scaffold(
        floatingActionButton = {
            if (rootStatus?.isRooted == true) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add PC Key")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Trusted PC Keys", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "Adding your PC public key (adbkey.pub) directly bypasses the 'Allow USB Debugging' authorization prompt.",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (trustedKeys.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No trusted keys found.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trustedKeys) { key ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = key,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }
                                IconButton(onClick = { viewModel.removePublicKey(key) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add PC Public Key") },
            text = {
                Column {
                    Text("Paste the content of your PC's ~/.android/adbkey.pub file:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ssh-rsa AAAAB3NzaC1... user@hostname") },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            viewModel.addPublicKey(keyInput)
                            keyInput = ""
                            showAddDialog = false
                            Toast.makeText(context, "Public key added!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
