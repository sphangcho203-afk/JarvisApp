package com.seongja.jarvis

import java.util.Locale

sealed class MemoryVaultCommand {
    data object Open : MemoryVaultCommand()
    data object ReviewPending : MemoryVaultCommand()
    data object ConversationArchive : MemoryVaultCommand()
    data class Search(val query: String) : MemoryVaultCommand()
}

object MemoryVaultCommandParser {
    fun parse(raw: String): MemoryVaultCommand? {
        val clean = raw.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return null

        val namesVault = Regex(
            "\\b(memory vault|memory center|memory settings|saved memories|long term memory|memory queue)\\b"
        ).containsMatchIn(clean)
        if (!namesVault) return null

        if (Regex("\\b(conversation archive|conversation history|old conversations)\\b").containsMatchIn(clean)) {
            return MemoryVaultCommand.ConversationArchive
        }
        if (Regex("\\b(review|approve|pending|queue)\\b").containsMatchIn(clean)) {
            return MemoryVaultCommand.ReviewPending
        }
        val search = Regex(
            "(?:find|search|show)\\s+(?:my\\s+)?(?:memory vault|saved memories|long term memory)\\s+(?:for|about|containing)\\s+(.+)"
        ).find(clean)
        if (search != null) return MemoryVaultCommand.Search(search.groupValues[1].trim().take(240))
        return MemoryVaultCommand.Open
    }
}
