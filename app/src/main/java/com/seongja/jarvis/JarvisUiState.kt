package com.seongja.jarvis

data class JarvisUiState(
    val status: String = "BOOTING",
    val transcript: String = "",
    val response: String = "Awaiting initialization.",
    val listening: Boolean = false,
    val energy: Float = 0.2f,
    val mode: String = "STANDBY",
    val commandCount: Int = 0,
    val lastCommand: String = "NONE",
    val signal: String = "LOCAL",
    val uptimeSeconds: Long = 0L
)
