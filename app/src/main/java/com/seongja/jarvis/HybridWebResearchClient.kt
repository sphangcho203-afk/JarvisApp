package com.seongja.jarvis

/**
 * Provider-resilient live research.
 *
 * Groq Compound is fast and often returns useful executed-tool metadata. Gemini
 * Google Search grounding is the independent fallback. A current-information
 * request only falls back to model memory after both real web routes fail.
 */
class HybridWebResearchClient(store: SecureCortexRegistry) {
    private val groq = GroqWebResearchClient(store)
    private val gemini = GeminiWebResearchClient(store)

    data class Result(
        val research: WebResearchResult,
        val provider: String,
        val attempts: List<String>
    )

    fun isConfigured(): Boolean = groq.isConfigured() || gemini.isConfigured()

    fun research(userInput: String, memoryContext: String): Result {
        val attempts = mutableListOf<String>()
        val failures = mutableListOf<String>()

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
                .onFailure { failures += "Groq: ${it.message ?: it.javaClass.simpleName}" }
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
                .onFailure { failures += "Gemini: ${it.message ?: it.javaClass.simpleName}" }
        }

        if (attempts.isEmpty()) {
            throw WebResearchException(
                "No configured Groq or Gemini node can perform live research."
            )
        }

        throw WebResearchException(
            "Every live research route failed. ${failures.joinToString(" | ").take(420)}"
        )
    }
}
