package com.seongja.jarvis

import android.os.SystemClock

class JarvisEngine {
    private val listeners = mutableSetOf<(JarvisUiState) -> Unit>()
    private val bootTime = SystemClock.elapsedRealtime()

    var state: JarvisUiState = JarvisUiState()
        private set

    fun addListener(listener: (JarvisUiState) -> Unit) {
        listeners += listener
        listener(state.copy(uptimeSeconds = uptimeSeconds()))
    }

    fun removeListener(listener: (JarvisUiState) -> Unit) {
        listeners -= listener
    }

    fun update(
        status: String = state.status,
        transcript: String = state.transcript,
        response: String = state.response,
        listening: Boolean = state.listening,
        energy: Float = state.energy,
        mode: String = state.mode,
        commandCount: Int = state.commandCount,
        lastCommand: String = state.lastCommand,
        signal: String = state.signal
    ) {
        state = JarvisUiState(
            status = status,
            transcript = transcript,
            response = response,
            listening = listening,
            energy = energy.coerceIn(0f, 1f),
            mode = mode,
            commandCount = commandCount,
            lastCommand = lastCommand,
            signal = signal,
            uptimeSeconds = uptimeSeconds()
        )
        listeners.toList().forEach { it(state) }
    }

    private fun uptimeSeconds(): Long {
        return (SystemClock.elapsedRealtime() - bootTime) / 1000L
    }
}
