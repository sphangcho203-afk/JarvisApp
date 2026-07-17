package com.seongja.jarvis

import java.util.Locale

sealed class OwnerVoiceCommand {
    data object OpenLab : OwnerVoiceCommand()
    data object Enroll : OwnerVoiceCommand()
    data object Status : OwnerVoiceCommand()
}

object OwnerVoiceCommandParser {
    fun parse(raw: String): OwnerVoiceCommand? {
        val clean = raw.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return null
        val mentionsVoiceIdentity = Regex(
            "\\b(owner voice|voice identity|voice profile|voice recognition|recognize my voice|learn my voice|voice lab)\\b"
        ).containsMatchIn(clean)
        if (!mentionsVoiceIdentity) return null
        return when {
            Regex("\\b(status|how well|what do you know)\\b").containsMatchIn(clean) -> OwnerVoiceCommand.Status
            Regex("\\b(enroll|train|learn|record|set up|setup)\\b").containsMatchIn(clean) -> OwnerVoiceCommand.Enroll
            else -> OwnerVoiceCommand.OpenLab
        }
    }
}
