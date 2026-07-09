package com.seongja.jarvis.core

import com.seongja.jarvis.models.JarvisMode
import com.seongja.jarvis.models.JarvisUiState
import com.seongja.jarvis.models.TelemetryState

class JarvisStateManager {
    private val log = ArrayDeque<String>()
    var state: JarvisUiState = JarvisUiState(isBooting = true, bootProgress = 0f)
        private set

    fun setBootProgress(progress: Float) {
        state = state.copy(
            isBooting = progress < 1f,
            bootProgress = progress.coerceIn(0f, 1f),
            statusLine = if (progress < 1f) "INITIALIZING CORE ${(progress * 100).toInt()}%" else "CORE ONLINE"
        )
    }

    fun setMode(mode: JarvisMode, status: String = mode.label) {
        pushLog("MODE -> ${mode.label}")
        state = state.copy(mode = mode, statusLine = status, eventLog = log.toList())
    }

    fun setTranscript(transcript: String) {
        state = state.copy(transcript = transcript)
    }

    fun setResponse(response: String) {
        pushLog(response.take(72))
        state = state.copy(lastResponse = response, eventLog = log.toList())
    }

    fun setTelemetry(telemetry: TelemetryState) {
        state = state.copy(telemetry = telemetry)
    }

    fun setAudioLevel(level: Float) {
        state = state.copy(audioLevel = level.coerceIn(0f, 1f))
    }

    private fun pushLog(value: String) {
        if (value.isBlank()) return
        log.addFirst(value)
        while (log.size > 8) log.removeLast()
    }
}
