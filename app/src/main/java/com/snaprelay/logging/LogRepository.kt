package com.snaprelay.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRepository(private val maxEntries: Int = 500) {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(stage: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            timestampMs = System.currentTimeMillis(),
            stage = stage,
            message = message,
            level = level
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Add newest at top
        if (currentList.size > maxEntries) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    fun formatTime(timestampMs: Long): String {
        return timeFormatter.format(Date(timestampMs))
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
