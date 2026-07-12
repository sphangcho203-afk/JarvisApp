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
        private const val MAX_REQUEST_BYTES = 8_192
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    }

    fun isConfigured(): Boolean = bestProfile() != null

    fun research(userInput: String, memoryContext: String): WebResearchResult {
        val profile = bestProfile()
            ?: throw WebResearchException(
                "Live web research needs at least one configured Groq cortex node."
            )

        val worldBrief = WebResearchIntent.isWorldBrief(userInput)
        val primaryModel = if (worldBrief || isDeepResearch(userInput)) {
            "groq/compound"
        } else {
            "groq/compound-mini"
        }

        val primaryPrompt = buildResearchPrompt(
            userInput = userInput,
            memoryContext = memoryContext,
            worldBrief = worldBrief
        )

        val primary = request(profile, primaryModel, primaryPrompt)
        if (primary.statusCode != 413) return primary.toResult(profile.label)

        // A 413 means Groq rejected the request body size. Retry with the exact
        // minimal one-message shape from Groq's Compound quickstart.
        val compactPrompt = buildCompactPrompt(userInput, worldBrief)
        val retry = request(profile, "groq/compound-mini", compactPrompt)
        if (retry.statusCode == 413) {
            throw WebResearchException(
                "Groq rejected both compact research requests as too large " +
                    "(${retry.requestBytes} bytes on retry)."
            )
        }
        if (retry.statusCode !in 200..299) {
            throw WebResearchException(
                "Groq live research failed after compact retry: ${retry.errorMessage}"
            )
        }
        return retry.toResult(profile.label)
    }

    private data class RequestResult(
        val answer: String,
        val sources: List<WebSource>,
        val searchQueries: List<String>,
        val model: String,
        val statusCode: Int,
        val elapsedMs: Long,
        val requestBytes: Int,
        val errorMessage: String
    ) {
        fun toResult(profileLabel: String): WebResearchResult {
            if (statusCode !in 200..299) {
                throw WebResearchException("Groq live research failed: $errorMessage")
            }
            if (answer.isBlank()) {
                throw WebResearchException("Groq live research returned an empty answer.")
            }
            return WebResearchResult(
                answer = answer,
                spokenSummary = groqSpokenSummary(answer),
                sources = sources.take(MAX_SOURCES),
                searchQueries = searchQueries,
                model = model,
                profileLabel = profileLabel,
                statusCode = statusCode,
                elapsedMs = elapsedMs
            )
        }
    }

    private fun request(
        profile: CortexProfile,
        model: String,
        prompt: String
    ): RequestResult {
        val body = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            )
        }.toString()

        val payload = body.toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_REQUEST_BYTES) {
            throw WebResearchException(
                "Jarvis blocked an oversized research request before transmission " +
                    "(${payload.size} bytes)."
            )
        }

        val started = System.currentTimeMillis()
        val connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setFixedLengthStreamingMode(payload.size)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${profile.apiKey.trim()}")
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.3")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload)
                stream.flush()
            }

            val status = connection.responseCode
            val raw = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started

            if (status !in 200..299) {
                return RequestResult(
                    answer = "",
                    sources = emptyList(),
                    searchQueries = emptyList(),
                    model = model,
                    statusCode = status,
                    elapsedMs = elapsed,
                    requestBytes = payload.size,
                    errorMessage = extractApiError(raw).ifBlank { "HTTP $status" }
                )
            }

            val parsed = parseResponse(raw)
            return RequestResult(
                answer = parsed.answer,
                sources = parsed.sources,
                searchQueries = parsed.searchQueries,
                model = model,
                statusCode = status,
                elapsedMs = elapsed,
                requestBytes = payload.size,
                errorMessage = ""
            )
        } catch (error: SocketTimeoutException) {
            throw WebResearchException("Live web research timed out. Please try again.")
        } catch (error: WebResearchException) {
            throw error
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
        appendLine("Time: ${ZonedDateTime.now()}")
        appendLine("Request: ${userInput.take(1_200)}")
        appendLine()

        if (worldBrief) {
            appendLine("Use live web search. Create a world intelligence brief with 5-7 important developments.")
            appendLine("For each: WHAT HAPPENED, WHY IT MATTERS, WHAT TO WATCH NEXT.")
            appendLine("Use exact dates, distinguish facts from uncertainty, and keep citations.")
        } else {
            appendLine("Use live web search when needed. Answer directly in clear sections.")
            appendLine("Cross-check important claims, use exact dates, and keep citations.")
        }

        appendLine("Never invent facts, URLs, quotes, or access to private content.")
        if (memoryContext.isNotBlank()) {
            appendLine("Relevant operator context: ${memoryContext.take(500)}")
        }
    }.take(3_500)

    private fun buildCompactPrompt(userInput: String, worldBrief: Boolean): String {
        val instruction = if (worldBrief) {
            "Use web search. Give a concise current world briefing with exact dates and citations."
        } else {
            "Use web search. Answer with current facts, exact dates, and citations."
        }
        return "$instruction\nQuestion: ${userInput.take(700)}"
    }

    private data class ParsedResponse(
        val answer: String,
        val sources: List<WebSource>,
        val searchQueries: List<String>
    )

    private fun parseResponse(raw: String): ParsedResponse {
        val root = JSONObject(raw)
        val message = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
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
        value.optJSONObject("arguments")?.let { arguments ->
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
                value.optJSONArray("results")?.let { collectResultArray(it, sources) }
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

private fun groqSpokenSummary(answer: String): String {
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
        window.take(sentenceEnd + 1) +
            " I have placed the full intelligence report and sources on screen."
    } else {
        window.trimEnd() +
            "... I have placed the full intelligence report and sources on screen."
    }
}
