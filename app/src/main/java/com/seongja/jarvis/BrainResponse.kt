package com.seongja.jarvis

enum class BrainMode {
    BOOT,
    ONLINE,
    LISTENING,
    PROCESSING,
    THINKING,
    TACTICAL,
    STEALTH,
    SECURITY,
    ALERT
}

data class BrainResponse(
    val spoken: String,
    val display: String,
    val intent: String,
    val confidence: Float,
    val mode: BrainMode,
    val trace: List<String>,
    val memory: String
)
