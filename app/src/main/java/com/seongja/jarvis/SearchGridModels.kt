package com.seongja.jarvis

import java.util.Locale

enum class SearchGridProvider(
    val displayName: String,
    val endpoint: String
) {
    TAVILY(
        displayName = "Tavily",
        endpoint = "https://api.tavily.com/search"
    ),
    EXA(
        displayName = "Exa",
        endpoint = "https://api.exa.ai/search"
    )
}

data class SearchGridCredential(
    val provider: SearchGridProvider,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val successes: Int = 0,
    val failures: Int = 0,
    val requestCount: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val lastUsedAtMs: Long = 0L,
    val cooldownUntilMs: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastError: String = ""
) {
    fun isConfigured(): Boolean = enabled && apiKey.isNotBlank()

    fun isCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean =
        cooldownUntilMs > nowMs

    fun healthLabel(nowMs: Long = System.currentTimeMillis()): String {
        val status = lastStatusCode.takeIf { it > 0 }?.toString() ?: "NET"
        val safeError = lastError
            .replace(Regex("tvly-[A-Za-z0-9_-]+"), "[redacted]")
            .replace(Regex("(?i)(authorization|x-api-key)\\s*[:=]\\s*[^\\s,;]+"), "$1=[redacted]")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(150)

        return when {
            !enabled -> "DISABLED"
            apiKey.isBlank() -> "NOT CONFIGURED"
            isCoolingDown(nowMs) -> buildString {
                append("COOLDOWN ")
                append(((cooldownUntilMs - nowMs) / 1_000L).coerceAtLeast(1L))
                append("s")
                if (lastStatusCode > 0 || safeError.isNotBlank()) {
                    append(" // HTTP ")
                    append(status)
                }
                if (safeError.isNotBlank()) {
                    append(" // ")
                    append(safeError)
                }
            }
            lastStatusCode in 200..299 -> "ONLINE ${lastLatencyMs}ms"
            safeError.isNotBlank() -> "ERROR $status // $safeError"
            else -> "READY"
        }
    }
}

data class SearchGridRegistry(
    val credentials: List<SearchGridCredential> = SearchGridDefaults.credentials()
) {
    fun configuredCredentials(): List<SearchGridCredential> =
        credentials.filter { it.isConfigured() }

    fun credential(provider: SearchGridProvider): SearchGridCredential? =
        credentials.firstOrNull { it.provider == provider }
}

object SearchGridDefaults {
    fun credentials(): List<SearchGridCredential> =
        SearchGridProvider.entries.map { SearchGridCredential(provider = it) }
}

data class SearchEvidence(
    val title: String,
    val url: String,
    val snippet: String,
    val publishedDate: String,
    val author: String,
    val provider: SearchGridProvider,
    val relevance: Double
) {
    fun label(index: Int): String = buildString {
        append("[")
        append(index)
        append("] ")
        append(title.ifBlank { "Untitled source" })
        if (publishedDate.isNotBlank()) {
            append(" // ")
            append(publishedDate)
        }
        if (author.isNotBlank()) {
            append(" // ")
            append(author)
        }
        append(" // ")
        append(provider.displayName.uppercase(Locale.US))
    }
}

data class SearchProviderTestResult(
    val provider: SearchGridProvider,
    val statusCode: Int,
    val elapsedMs: Long,
    val resultCount: Int
)

data class SearchGridResult(
    val research: WebResearchResult,
    val providers: List<SearchGridProvider>,
    val evidenceCount: Int
)
