package com.remoteadb.app.tunnel

import android.util.Base64
import com.remoteadb.app.data.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges relay streams to the local adbd TCP endpoint.
 *
 * Ordering guarantees matter here: adb is a framed binary protocol, so a single
 * out-of-order or dropped chunk corrupts the whole session (this is what broke
 * `adb push` / `adb install`, which send long bursts of back-to-back chunks).
 * Therefore every stream owns one unbounded channel plus exactly one writer
 * coroutine:
 *  - [handleStreamData] enqueues synchronously, preserving WebSocket arrival order.
 *  - The writer drains that channel sequentially, so writes never interleave.
 *  - Chunks arriving before the local socket finishes connecting stay queued
 *    instead of being dropped.
 *
 * The reader direction applies backpressure via [queueSizeProvider] so a fast
 * `adb pull` cannot overflow the OkHttp outgoing frame queue.
 */
class LocalAdbBridge(
    private val localPort: Int = 5555,
    private val queueSizeProvider: () -> Long = { 0L },
    private val sendToWebSocket: (JSONObject) -> Unit
) {

    private class StreamContext {
        val queue = Channel<ByteArray>(Channel.UNLIMITED)
        @Volatile var socket: Socket? = null
        @Volatile var closed = false
    }

    private val streams = ConcurrentHashMap<String, StreamContext>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun handleStreamOpen(streamId: String) {
        // Register the queue synchronously so data arriving immediately after
        // stream_open is buffered rather than discarded.
        val ctx = StreamContext()
        streams[streamId] = ctx

        scope.launch {
            try {
                AppLogger.d(TAG, "Stream $streamId: connecting to 127.0.0.1:$localPort")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress("127.0.0.1", localPort), CONNECT_TIMEOUT_MS)
                ctx.socket = socket
                AppLogger.d(TAG, "Stream $streamId: local adbd connected")

                // Writer: drains queued chunks in strict arrival order.
                val writerJob = launch { runWriter(streamId, ctx, socket.getOutputStream()) }

                // Reader: forwards adbd output back to the relay.
                val input: InputStream = socket.getInputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (isActive && !socket.isClosed) {
                    val readBytes = input.read(buffer)
                    if (readBytes == -1) break

                    // Backpressure so a fast device->PC transfer cannot blow up
                    // the OkHttp outgoing frame queue.
                    var waited = 0L
                    while (isActive && queueSizeProvider() > WS_QUEUE_HIGH_WATER && waited < MAX_DRAIN_WAIT_MS) {
                        delay(DRAIN_POLL_MS)
                        waited += DRAIN_POLL_MS
                    }

                    val dataMsg = JSONObject().apply {
                        put("type", "stream_data")
                        put("streamId", streamId)
                        put("data", Base64.encodeToString(buffer, 0, readBytes, Base64.NO_WRAP))
                    }
                    sendToWebSocket(dataMsg)
                }
                writerJob.cancel()
            } catch (e: Exception) {
                if (!ctx.closed) {
                    AppLogger.w(TAG, "Stream $streamId error: ${e.message}")
                }
            } finally {
                val wasOpen = !ctx.closed
                closeStream(streamId)
                if (wasOpen) {
                    sendToWebSocket(JSONObject().apply {
                        put("type", "stream_close")
                        put("streamId", streamId)
                        put("reason", "local_adbd_closed")
                    })
                }
            }
        }
    }

    private suspend fun runWriter(streamId: String, ctx: StreamContext, out: OutputStream) {
        try {
            for (chunk in ctx.queue) {
                out.write(chunk)
                out.flush()
            }
        } catch (e: Exception) {
            if (!ctx.closed) {
                AppLogger.w(TAG, "Stream $streamId write failed: ${e.message}")
                closeStream(streamId)
            }
        }
    }

    fun handleStreamData(streamId: String, base64Data: String) {
        val ctx = streams[streamId]
        if (ctx == null || ctx.closed) {
            AppLogger.w(TAG, "Dropping data for unknown/closed stream $streamId")
            return
        }
        try {
            val raw = Base64.decode(base64Data, Base64.NO_WRAP)
            val result = ctx.queue.trySend(raw)
            if (result.isFailure) {
                AppLogger.e(TAG, "Stream $streamId queue rejected ${raw.size} bytes")
                closeStream(streamId)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Stream $streamId base64 decode failed: ${e.message}")
            closeStream(streamId)
        }
    }

    fun closeStream(streamId: String) {
        val ctx = streams.remove(streamId) ?: return
        ctx.closed = true
        ctx.queue.close()
        try {
            ctx.socket?.close()
        } catch (_: Exception) {
        }
    }

    fun closeAll() {
        for (streamId in streams.keys.toList()) {
            closeStream(streamId)
        }
        streams.clear()
    }

    fun shutdown() {
        closeAll()
        scope.cancel()
    }

    companion object {
        private const val TAG = "LocalAdbBridge"
        private const val BUFFER_SIZE = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val WS_QUEUE_HIGH_WATER = 4L * 1024 * 1024
        private const val DRAIN_POLL_MS = 15L
        private const val MAX_DRAIN_WAIT_MS = 30_000L
    }
}
