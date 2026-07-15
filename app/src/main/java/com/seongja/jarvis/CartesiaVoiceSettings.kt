package com.seongja.jarvis

/**
 * Encrypted Cartesia Sonic configuration.
 *
 * Gemma is an en-GB voice. Cartesia's current TTS protocol accepts the base
 * language code `en`; the selected voice carries the British accent.
 */
data class CartesiaVoiceSettings(
    val apiKey: String = "",
    val enabled: Boolean = true,
    val successes: Int = 0,
    val failures: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastError: String = ""
) {
    fun isConfigured(): Boolean = enabled && apiKey.isNotBlank()

    fun healthLabel(): String = when {
        !enabled -> "DISABLED"
        apiKey.isBlank() -> "NOT CONFIGURED"
        lastStatusCode in 200..299 || lastStatusCode == 101 ->
            "ONLINE ${lastLatencyMs}ms"
        lastError.isNotBlank() ->
            "ERROR ${lastStatusCode.takeIf { it > 0 } ?: "NET"} // ${lastError.take(120)}"
        else -> "READY"
    }

    companion object {
        const val MODEL_ID = "sonic-3"
        const val VOICE_ID = "62ae83ad-4f6a-430b-af41-a9bede9286ca"
        const val VOICE_LABEL = "GEMMA // EN-GB"
        const val LANGUAGE = "en"
        const val SPEED = 1.10
        const val SAMPLE_RATE_HZ = 44_100
        const val MAX_BUFFER_DELAY_MS = 180
        const val API_VERSION = "2026-03-01"
        const val WEBSOCKET_ENDPOINT = "wss://api.cartesia.ai/tts/websocket"
    }
}
