package com.example.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class DiagnosticLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class DiagnosticLogEntry(
    val id: Long,
    val timestamp: Long,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object TrackingDiagnosticLogger {
    private const val MAX_LOGS = 100
    private val idCounter = AtomicLong(1L)
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    init {
        log(DiagnosticLevel.INFO, "System", "TrackingDiagnosticLogger initialized")
    }

    fun log(level: DiagnosticLevel, tag: String, message: String) {
        val entry = DiagnosticLogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_LOGS) {
            _logs.value = current.take(MAX_LOGS)
        } else {
            _logs.value = current
        }
    }

    fun logInfo(tag: String, message: String) = log(DiagnosticLevel.INFO, tag, message)
    fun logSuccess(tag: String, message: String) = log(DiagnosticLevel.SUCCESS, tag, message)
    fun logWarn(tag: String, message: String) = log(DiagnosticLevel.WARNING, tag, message)
    fun logError(tag: String, message: String) = log(DiagnosticLevel.ERROR, tag, message)

    fun clear() {
        _logs.value = emptyList()
        log(DiagnosticLevel.INFO, "System", "Diagnostic logs cleared")
    }
}
