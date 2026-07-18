package com.jarvis.core.device

import java.util.Locale

/**
 * Native FRIDAY workspaces are routed by the cognitive capability layer, not by
 * Android's generic "open app" launcher. Keeping this parser pure makes the
 * precedence boundary unit-testable without an Android runtime.
 */
object FridayWorkspaceReservation {
    private val openPattern = Regex(
        "^(?:open(?: up)?|launch|start|run|bring up|take me to|go to|activate|show)\\s+(.+)$"
    )

    private val reservedTargets = setOf(
        "eye",
        "eyes",
        "x camera",
        "xcamera",
        "vision",
        "visual lab",
        "visual studio",
        "image lab",
        "image studio",
        "image synthesis",
        "private diary",
        "diary",
        "memory vault",
        "vault",
        "owner voice lab",
        "voice lab",
        "permission center",
        "control center",
        "weather core",
        "cortex mesh",
        "cortex setup",
        "api setup",
        "api settings"
    )

    private val directPhrases = setOf(
        "open your eyes",
        "open your eye",
        "close your eyes",
        "close your eye",
        "what can you see",
        "what do you see",
        "tell me what you see",
        "look at me",
        "look at this",
        "scan this",
        "inspect this"
    )

    fun shouldBypassGenericDeviceRouter(raw: String): Boolean {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return false
        if (normalized in directPhrases) return true

        val target = openPattern.matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.removePrefix("the ")
            ?.removePrefix("my ")
            ?.removePrefix("your ")
            ?.removeSuffix(" app")
            ?.removeSuffix(" application")
            ?.trim()
            ?: return false

        return target in reservedTargets
    }

    private fun normalize(value: String): String {
        var command = value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        command = command
            .replace(Regex("^(?:hey\\s+)?(?:friday|jarvis)\\s+"), "")
            .replace(Regex("^(?:please|kindly)\\s+"), "")
            .replace(Regex("^(?:can|could|would|will)\\s+(?:you|u)\\s+"), "")
            .replace(Regex("^(?:i\\s+want\\s+you\\s+to|i\\s+need\\s+you\\s+to)\\s+"), "")
            .replace(Regex("\\s+(?:for me|right now|now please|please)$"), "")
            .trim()
        return command
    }
}
