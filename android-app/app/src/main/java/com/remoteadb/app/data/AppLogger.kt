package com.remoteadb.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogItem(
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel
)

object AppLogger {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val maxLogs = 500

    private val _logs = MutableStateFlow<List<LogItem>>(emptyList())
    val logs: StateFlow<List<LogItem>> = _logs

    fun d(tag: String, message: String) = add(tag, message, LogLevel.DEBUG)
    fun i(tag: String, message: String) = add(tag, message, LogLevel.INFO)
    fun w(tag: String, message: String) = add(tag, message, LogLevel.WARN)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val extra = if (throwable != null) "\n${throwable.stackTraceToString()}" else ""
        add(tag, message + extra, LogLevel.ERROR)
    }

    private fun add(tag: String, message: String, level: LogLevel) {
        val item = LogItem(
            timestamp = dateFormat.format(Date()),
            tag = tag,
            message = message,
            level = level
        )
        val current = _logs.value.toMutableList()
        current.add(item)
        if (current.size > maxLogs) {
            current.removeAt(0)
        }
        _logs.value = current
        android.util.Log.println(
            when (level) {
                LogLevel.DEBUG -> android.util.Log.DEBUG
                LogLevel.INFO -> android.util.Log.INFO
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.ERROR -> android.util.Log.ERROR
            },
            tag,
            message
        )
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
