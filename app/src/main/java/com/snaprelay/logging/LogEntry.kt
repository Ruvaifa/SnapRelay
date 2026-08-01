package com.snaprelay.logging

enum class LogLevel {
    INFO, WARN, ERROR
}

data class LogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val stage: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)
