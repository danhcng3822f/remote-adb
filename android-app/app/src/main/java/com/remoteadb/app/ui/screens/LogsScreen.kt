package com.remoteadb.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteadb.app.data.AppLogger
import com.remoteadb.app.data.LogLevel

@Composable
fun LogsScreen() {
    val logs by AppLogger.logs.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Console Logs (${logs.size})",
                color = Color.White,
                fontSize = 16.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    val allText = logs.joinToString("\n") { "[${it.timestamp}][${it.tag}] ${it.message}" }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("App Logs", allText))
                    Toast.makeText(context, "Copied all logs!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray)
                }
                IconButton(onClick = { AppLogger.clear() }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray)
                }
            }
        }

        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No logs yet. Start service to see connection events.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    val textColor = when (log.level) {
                        LogLevel.DEBUG -> Color.Gray
                        LogLevel.INFO -> Color(0xFF4CAF50)
                        LogLevel.WARN -> Color(0xFFFFC107)
                        LogLevel.ERROR -> Color(0xFFFF5252)
                    }

                    Text(
                        text = "[${log.timestamp}] [${log.tag}] ${log.message}",
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
