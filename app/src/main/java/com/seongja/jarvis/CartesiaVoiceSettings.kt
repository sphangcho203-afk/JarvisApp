package com.seongja.jarvis

/**
 * Encrypted Cartesia Sonic configuration with one primary and three automatic
 * backup routes. No key is exposed to HELIX, logs, or conversation memory.
 */
data class CartesiaVoiceSettings(
    val apiKey: String = "",
    val backupApiKeys: List<String> = emptyList(),
    val enabled: Boolean = true,
    val activeKeyIndex: Int = 0,
    val keyCooldownUntilMs: List<Long> = emptyList(),
    val successes: Int = 0,
    val failures: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastError: String = ""
) {
    fun configuredKeys(): List<String> = buildList {
        add(apiKey.trim())
        backupApiKeys.take(MAX_KEYS - 1).forEach { add(it.trim()) }
    }.filter { it.isNotBlank() }.distinct().take(MAX_KEYS)

    fun isConfigured(): Boolean = enabled && configuredKeys().isNotEmpty()

    fun normalizedCooldowns(): List<Long> =
        List(configuredKeys().size) { index -> keyCooldownUntilMs.getOrElse(index) { 0L } }

    fun activeKey(nowMs: Long = System.currentTimeMillis()): Pair<Int, String>? {
        val keys = configuredKeys()
        if (!enabled || keys.isEmpty()) return null
        val cooldowns = normalizedCooldowns()
        val preferred = activeKeyIndex.coerceIn(0, keys.lastIndex)
        if (cooldowns.getOrElse(preferred) { 0L } <= nowMs) return preferred to keys[preferred]
        keys.indices.firstOrNull { cooldowns.getOrElse(it) { 0L } <= nowMs }?.let {
            return it to keys[it]
        }
        return null
    }

    fun routeLabel(index: Int = activeKeyIndex): String = when (index) {
        0 -> "P1"
        1 -> "B1"
        2 -> "B2"
        3 -> "B3"
        else -> "K${index + 1}"
    }

    fun healthLabel(): String = when {
        !enabled -> "DISABLED"
        configuredKeys().isEmpty() -> "NOT CONFIGURED"
        activeKey() == null -> "ALL KEYS COOLING DOWN"
        lastStatusCode in 200..299 || lastStatusCode == 101 ->
            "ONLINE ${lastLatencyMs}ms // ${routeLabel()} // ${configuredKeys().size} KEYS"
        lastError.isNotBlank() ->
            "ERROR ${lastStatusCode.takeIf { it > 0 } ?: "NET"} // ${lastError.take(120)}"
        else -> "READY // ${configuredKeys().size} KEYS // FAILOVER ARMED"
    }

    companion object {
        const val MAX_KEYS = 4
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
