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

    fun healthLabel(nowMs: Long = System.currentTimeMillis()): String = when {
        !enabled -> "DISABLED"
        apiKey.isBlank() -> "NOT CONFIGURED"
        isCoolingDown(nowMs) ->
            "COOLDOWN ${((cooldownUntilMs - nowMs) / 1_000L).coerceAtLeast(1L)}s"
        lastStatusCode in 200..299 -> "ONLINE ${lastLatencyMs}ms"
        lastError.isNotBlank() ->
            "ERROR ${lastStatusCode.takeIf { it > 0 } ?: "NET"}"
        else -> "READY"
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
