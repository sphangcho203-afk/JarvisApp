package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.ZonedDateTime
import javax.net.ssl.HttpsURLConnection

class GroqWebResearchClient(private val store: SecureCortexRegistry) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_SOURCES = 10
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val RESEARCH_SYSTEM_PROMPT =
            "You are Jarvis's live web intelligence engine. Use web search for time-sensitive facts. " +
                "Prefer authoritative primary sources and reputable reporting. Cross-check important claims. " +
                "State uncertainty, avoid sensationalism, distinguish facts from analysis, and never claim that stale model memory is live evidence."
    }

    fun isConfigured(): Boolean = bestProfile() != null

    fun research(userInput: String, memoryContext: String): WebResearchResult {
        val profile = bestProfile()
            ?: throw WebResearchException(
                "Live web research needs at least one configured Groq cortex node."
            )

        val worldBrief = WebResearchIntent.isWorldBrief(userInput)
        val model = if (worldBrief || isDeepResearch(userInput)) {
            "groq/compound"
        } else {
            "groq/compound-mini"
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", RESEARCH_SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            buildResearchPrompt(
                                userInput = userInput,
                                memoryContext = memoryContext,
                                worldBrief = worldBrief
                            )
                        )
                    })
                }
            )
        }

        val started = System.currentTimeMillis()
        val connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${profile.apiKey}")
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.3")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            val status = connection.responseCode
            val raw = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started

            if (status !in 200..299) {
                val message = extractApiError(raw).ifBlank { "HTTP $status" }
                throw WebResearchException("Groq live research failed: $message")
            }

            val parsed = parseResponse(raw)
            if (parsed.answer.isBlank()) {
                throw WebResearchException("Groq live research returned an empty answer.")
            }

            return WebResearchResult(
                answer = parsed.answer,
                spokenSummary = createSpokenSummary(parsed.answer),
                sources = parsed.sources.take(MAX_SOURCES),
                searchQueries = parsed.searchQueries,
                model = model,
                profileLabel = profile.label,
                statusCode = status,
                elapsedMs = elapsed
            )
        } catch (error: WebResearchException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw WebResearchException("Live web research timed out. Please try again.")
        } catch (error: Exception) {
            throw WebResearchException(
                "Live web research failed: ${error.message ?: error.javaClass.simpleName}"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun bestProfile(): CortexProfile? {
        val now = System.currentTimeMillis()
        return store.load().configuredProfiles()
            .asSequence()
            .filter { it.provider == CortexProvider.GROQ }
            .filterNot { it.isCoolingDown(now) }
            .sortedByDescending { CortexMath.score(it, CortexTask.GENERAL, now) }
            .firstOrNull()
    }

    private fun isDeepResearch(input: String): Boolean {
        val lower = input.lowercase()
        return lower.contains("deep research") ||
            lower.contains("research everything") ||
            lower.contains("full report") ||
            lower.contains("comprehensive research") ||
            lower.contains("around the world") ||
            lower.contains("world briefing") ||
            lower.contains("global briefing")
    }

    private fun buildResearchPrompt(
        userInput: String,
        memoryContext: String,
        worldBrief: Boolean
    ): String = buildString {
        appendLine("Current device time: ${ZonedDateTime.now()}")
        appendLine("Operator request: $userInput")
        appendLine()

        if (worldBrief) {
            appendLine("Create a WORLD INTELLIGENCE BRIEF using current web-search results.")
            appendLine("Choose five to seven genuinely important global developments, not filler.")
            appendLine("For each item include: WHAT HAPPENED, WHY IT MATTERS, and WHAT TO WATCH NEXT.")
            appendLine("Cover geopolitics, economy, science and technology, climate or disasters, and health only when significant.")
            appendLine("Use exact dates and distinguish confirmed facts, disputed claims, and uncertainty.")
            appendLine("End with a compact OVERALL ASSESSMENT.")
        } else {
            appendLine("Research this request using current web results whenever useful.")
            appendLine("Answer directly, explain it in plain language, and break complex information into clear sections.")
            appendLine("Use exact dates for time-sensitive claims and compare multiple reliable sources when possible.")
            appendLine("If credible sources disagree, describe the disagreement instead of choosing silently.")
        }

        appendLine("Keep citations supplied by the research system in the answer.")
        appendLine("Do not invent facts, URLs, quotations, or access to private or paywalled material.")
        appendLine("Keep the answer information-dense and readable on a phone.")

        if (memoryContext.isNotBlank()) {
            appendLine()
            appendLine("Relevant operator context, use only when useful:")
            appendLine(memoryContext.take(2_500))
        }
    }

    private data class ParsedResponse(
        val answer: String,
        val sources: List<WebSource>,
        val searchQueries: List<String>
    )

    private fun parseResponse(raw: String): ParsedResponse {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices")
            ?: throw WebResearchException("No response choice was returned by Groq.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw WebResearchException("No response message was returned by Groq.")

        val answer = message.optString("content").trim()
        val sources = mutableListOf<WebSource>()
        val queries = mutableListOf<String>()

        val tools = message.optJSONArray("executed_tools")
        if (tools != null) {
            for (index in 0 until tools.length()) {
                val tool = tools.optJSONObject(index) ?: continue
                collectQuery(tool, queries)
                collectSearchResults(tool.opt("search_results"), sources, queries)
            }
        }

        return ParsedResponse(
            answer = answer,
            sources = sources.distinctBy { it.uri },
            searchQueries = queries.distinct()
        )
    }

    private fun collectQuery(value: JSONObject, output: MutableList<String>) {
        listOf("query", "search_query", "q").forEach { key ->
            value.optString(key).trim().takeIf { it.isNotBlank() }?.let(output::add)
        }
        val arguments = value.optJSONObject("arguments")
        if (arguments != null) {
            listOf("query", "search_query", "q").forEach { key ->
                arguments.optString(key).trim().takeIf { it.isNotBlank() }?.let(output::add)
            }
        }
    }

    private fun collectSearchResults(
        value: Any?,
        sources: MutableList<WebSource>,
        queries: MutableList<String>
    ) {
        when (value) {
            is JSONObject -> {
                collectQuery(value, queries)
                val results = value.optJSONArray("results")
                if (results != null) collectResultArray(results, sources)
            }
            is JSONArray -> collectResultArray(value, sources)
        }
    }

    private fun collectResultArray(array: JSONArray, sources: MutableList<WebSource>) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uri = item.optString("url").ifBlank { item.optString("uri") }.trim()
            if (uri.isBlank()) continue
            val title = item.optString("title").trim().ifBlank { "Web source" }
            sources += WebSource(title = title, uri = uri)
        }
    }

    private fun createSpokenSummary(answer: String): String {
        val cleaned = answer
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace("**", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.length <= 850) return cleaned
        val window = cleaned.take(850)
        val sentenceEnd = maxOf(
            window.lastIndexOf(". "),
            window.lastIndexOf("! "),
            window.lastIndexOf("? ")
        )
        return if (sentenceEnd >= 420) {
            window.take(sentenceEnd + 1) + " I have placed the full intelligence report and sources on screen."
        } else {
            window.trimEnd() + "... I have placed the full intelligence report and sources on screen."
        }
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun extractApiError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message")
            ?: root.optString("message")
    }.getOrNull().orEmpty()
}
