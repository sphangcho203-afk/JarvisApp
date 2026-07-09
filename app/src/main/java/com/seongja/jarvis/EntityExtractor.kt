package com.seongja.jarvis

class EntityExtractor(private val normalizer: InputNormalizer = InputNormalizer()) {
    private val knownApps = mapOf(
        "youtube" to "YouTube",
        "chrome" to "Chrome",
        "google" to "Chrome",
        "spotify" to "Spotify",
        "discord" to "Discord",
        "telegram" to "Telegram",
        "whatsapp" to "WhatsApp",
        "gmail" to "Gmail",
        "play store" to "Play Store",
        "settings" to "Settings",
        "camera" to "Camera",
        "files" to "Files",
        "clock" to "Clock",
        "calculator" to "Calculator"
    )

    fun extract(input: String, signal: IntentSignal): EntityBundle {
        val clean = normalizer.clean(input)
        val lower = normalizer.lower(input)
        val targetApp = knownApps.entries.firstOrNull { lower.contains(it.key) }?.value
        val mode = when {
            lower.contains("red alert") || lower.contains("alert mode") -> BrainMode.ALERT
            lower.contains("tactical") || lower.contains("combat") -> BrainMode.TACTICAL
            lower.contains("stealth") -> BrainMode.STEALTH
            lower.contains("security") -> BrainMode.SECURITY
            lower.contains("normal") || lower.contains("online") -> BrainMode.ONLINE
            else -> null
        }

        val identityName = when {
            lower.startsWith("call me ") -> clean.substringAfter("call me", "").trim()
            lower.startsWith("my name is ") -> clean.substringAfter("my name is", "").trim()
            lower.startsWith("i am ") -> clean.substringAfter("i am", "").trim()
            lower.startsWith("i'm ") -> clean.substringAfter("I'm", "").ifBlank { clean.substringAfter("i'm", "") }.trim()
            lower.startsWith("im ") -> clean.substringAfter("im", "").trim()
            else -> null
        }?.takeIf { it.isNotBlank() }?.take(40)

        val memoryPayload = when (signal.type) {
            IntentType.MEMORY_WRITE -> when {
                lower.startsWith("remember that ") -> clean.substringAfter("remember that", "").trim()
                lower.startsWith("remember ") -> clean.substringAfter("remember", "").trim()
                lower.startsWith("store ") -> clean.substringAfter("store", "").trim()
                lower.startsWith("save this") -> clean.substringAfter("save this", "").trim()
                identityName != null -> "Operator identity is $identityName"
                else -> null
            }
            else -> null
        }?.takeIf { it.isNotBlank() }?.take(220)

        val searchQuery = when {
            lower.startsWith("google ") -> clean.substringAfter("google", "").trim()
            lower.startsWith("search for ") -> clean.substringAfter("search for", "").trim()
            lower.startsWith("search ") -> clean.substringAfter("search", "").trim()
            lower.contains("look up ") -> clean.substringAfter("look up", "").trim()
            else -> null
        }?.takeIf { it.isNotBlank() }?.take(180)

        return EntityBundle(
            targetApp = targetApp,
            memoryPayload = memoryPayload,
            identityName = identityName,
            searchQuery = searchQuery,
            modeTarget = mode,
            rawSubject = clean.take(180)
        )
    }
}
