package com.remoteadb.app.data

enum class TunnelMode {
    CUSTOM_RELAY,   // Custom VPS / Dedicated Relay Server
    PUBLIC_FREE,    // Free public relay (No VPS required)
    NGROK           // Ngrok TCP tunnel
}
