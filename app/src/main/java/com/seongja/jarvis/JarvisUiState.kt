package com.seongja.jarvis

data class JarvisUiState(
    val status: String = "SYSTEM ONLINE",
    val transcript: String = "Say a command, Sir.",
    val response: String = "Jarvis shell initialized.",
    val isListening: Boolean = false,
    val pulse: Float = 0f
)
