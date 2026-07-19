package com.seongja.jarvis

import java.util.Locale

sealed interface XCameraVoiceCommand {
    data object Close : XCameraVoiceCommand
    data object SwitchLens : XCameraVoiceCommand
    data class UseFront(val question: String, val scan: Boolean) : XCameraVoiceCommand
    data class UseRear(val question: String, val scan: Boolean) : XCameraVoiceCommand
    data class Scan(val question: String) : XCameraVoiceCommand
    data object Ignore : XCameraVoiceCommand
}

object XCameraVoiceCommandParser {
    fun parse(raw: String): XCameraVoiceCommand {
        val clean = raw.trim()
        val normalized = normalize(clean)
        if (normalized.isBlank()) return XCameraVoiceCommand.Ignore

        if (
            containsAny(
                normalized,
                "close your eyes",
                "close your eye",
                "close x camera",
                "close xcamera",
                "turn off x camera",
                "stop the camera",
                "stop looking"
            )
        ) {
            return XCameraVoiceCommand.Close
        }

        if (containsAny(normalized, "switch lens", "switch camera", "change lens", "flip camera")) {
            return XCameraVoiceCommand.SwitchLens
        }

        if (
            containsAny(
                normalized,
                "look at me",
                "see me",
                "scan me",
                "front camera",
                "selfie camera",
                "use front camera",
                "use the front camera"
            )
        ) {
            return XCameraVoiceCommand.UseFront(
                question = clean.ifBlank { "Tell me what you can see through the front camera." },
                scan = shouldScan(normalized) || containsAny(normalized, "look at me", "see me", "scan me")
            )
        }

        if (
            containsAny(
                normalized,
                "rear camera",
                "back camera",
                "use rear camera",
                "use back camera",
                "use the rear camera",
                "use the back camera"
            )
        ) {
            return XCameraVoiceCommand.UseRear(
                question = clean.ifBlank { "Tell me what you can see through the rear camera." },
                scan = shouldScan(normalized)
            )
        }

        if (shouldScan(normalized)) {
            return XCameraVoiceCommand.Scan(
                clean.ifBlank {
                    "Tell me what you can see, including important objects, visible text, and anything requiring attention."
                }
            )
        }

        return XCameraVoiceCommand.Ignore
    }

    private fun shouldScan(normalized: String): Boolean = containsAny(
        normalized,
        "scan",
        "look again",
        "see again",
        "what can you see",
        "what do you see",
        "tell me what you see",
        "inspect",
        "read this",
        "read that",
        "describe this",
        "describe that"
    )

    private fun containsAny(value: String, vararg phrases: String): Boolean =
        phrases.any { value.contains(it) }

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
