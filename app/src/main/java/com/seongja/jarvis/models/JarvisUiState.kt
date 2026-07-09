package com.seongja.jarvis.models

data class JarvisUiState(
    val mode: JarvisMode = JarvisMode.ONLINE,
    val statusLine: String = "CORE ONLINE",
    val transcript: String = "Awaiting command authorization.",
    val lastResponse: String = "Systems nominal.",
    val telemetry: TelemetryState = TelemetryState(),
    val bootProgress: Float = 1f,
    val isBooting: Boolean = false,
    val audioLevel: Float = 0f,
    val eventLog: List<String> = emptyList()
)
