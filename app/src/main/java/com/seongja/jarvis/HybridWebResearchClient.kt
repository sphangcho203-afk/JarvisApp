package com.seongja.jarvis

/**
 * Provider-resilient live research.
 *
 * The dedicated Search Grid is preferred because Tavily and Exa return explicit
 * evidence payloads that Jarvis can deduplicate and synthesize. Groq Compound
 * and Gemini Google Search remain independent fallbacks.
 */
class HybridWebResearchClient(
    private val cortexStore: SecureCortexRegistry
) {
    private val searchStore = SecureSearchGridRegistry(cortexStore.appContext)
    private val searchGrid = SearchGridResearchClient(searchStore, cortexStore)
    private val groq = GroqWebResearchClient(cortexStore)
    private val gemini = GeminiWebResearchClient(cortexStore)

    data class Result(
        val research: WebResearchResult,
        val provider: String,
        val attempts: List<String>
    )

    fun isConfigured(): Boolean =
        searchGrid.isConfigured() || groq.isConfigured() || gemini.isConfigured()

    fun configuredSearchProviders(): List<SearchGridProvider> =
        searchGrid.configuredProviders()

    fun research(
        userInput: String,
        memoryContext: String,
        onToken: ((String) -> Unit)? = null
    ): Result {
        val attempts = mutableListOf<String>()
        val failures = mutableListOf<String>()

        if (searchGrid.isConfigured()) {
            attempts += "Tavily + Exa Search Grid"
            runCatching { searchGrid.research(userInput, memoryContext, onToken) }
                .onSuccess { result ->
                    val providers = result.providers
                        .joinToString(" + ") { it.displayName }
                        .ifBlank { "Search Grid" }
                    return Result(
                        research = result.research,
                        provider = "$providers evidence + Cortex synthesis",
                        attempts = attempts.toList()
                    )
                }
                .onFailure {
                    failures += "Search Grid: ${it.message ?: it.javaClass.simpleName}"
                }
        }

        if (groq.isConfigured()) {
            attempts += "Groq Compound"
            runCatching { groq.research(userInput, memoryContext) }
                .onSuccess {
                    return Result(
                        research = it,
                        provider = "Groq Compound web search",
                        attempts = attempts.toList()
                    )
                }
                .onFailure {
                    failures += "Groq: ${it.message ?: it.javaClass.simpleName}"
                }
        }

        if (gemini.isConfigured()) {
            attempts += "Gemini Google Search"
            runCatching { gemini.research(userInput, memoryContext) }
                .onSuccess {
                    return Result(
                        research = it,
                        provider = "Gemini Google Search grounding",
                        attempts = attempts.toList()
                    )
                }
                .onFailure {
                    failures += "Gemini: ${it.message ?: it.javaClass.simpleName}"
                }
        }

        if (attempts.isEmpty()) {
            throw WebResearchException(
                "No Tavily, Exa, Groq, or Gemini live research route is configured."
            )
        }

        throw WebResearchException(
            "Every live research route failed. ${failures.joinToString(" | ").take(560)}"
        )
    }
}
