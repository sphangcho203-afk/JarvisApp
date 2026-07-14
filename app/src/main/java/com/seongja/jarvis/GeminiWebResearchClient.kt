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

class WebResearchException(message: String) : Exception(message)

data class WebSource(
    val title: String,
    val uri: String
)

data class WebResearchResult(
    val answer: String,
    val spokenSummary: String,
    val sources: List<WebSource>,
    val searchQueries: List<String>,
    val model: String,
    val profileLabel: String,
    val statusCode: Int,
    val elapsedMs: Long
) {
    fun displayText(): String = buildString {
        append(answer.trim())
        if (sources.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("SOURCES // ${sources.size}")
            sources.forEachIndexed { index, source ->
                appendLine("[${index + 1}] ${source.title}")
                appendLine(source.uri)
            }
        }
    }.trim()
}

object WebResearchIntent {
    private val currentTerms = Regex(
        "\\b(latest|today|tonight|current|currently|recent|recently|now|right now|this week|this month|this year|live|breaking|update|updates|newest|as of)\\b",
        RegexOption.IGNORE_CASE
    )

    private val changingTopics = Regex(
        "\\b(news|weather|forecast|price|prices|market|stock|crypto|score|scores|match|schedule|release date|outage|election|president|prime minister|ceo|law|laws|rule|rules|policy|availability|available|war|conflict|disaster|earthquake|storm|cyclone)\\b",
        RegexOption.IGNORE_CASE
    )

    private val explicitResearch = Regex(
        "\\b(search (?:the )?web|search online|browse (?:the )?web|look up online|find online|research|deep research|use the internet|check online|find sources|verify online)\\b",
        RegexOption.IGNORE_CASE
    )

    fun shouldUseWeb(input: String): Boolean {
        val clean = input.trim()
        if (clean.isBlank()) return false
        if (explicitResearch.containsMatchIn(clean)) return true
        if (currentTerms.containsMatchIn(clean)) return true
        if (changingTopics.containsMatchIn(clean) && looksLikeQuestion(clean)) return true

        val lower = clean.lowercase()
        return lower.contains("what is happening around the world") ||
            lower.contains("what's happening around the world") ||
            lower.contains("whats happening around the world") ||
            lower.contains("what happened around the world") ||
            lower.contains("world briefing") ||
            lower.contains("global briefing")
    }

    fun isWorldBrief(input: String): Boolean {
        val lower = input.lowercase()
        return lower.contains("around the world") ||
            lower.contains("world briefing") ||
            lower.contains("global briefing") ||
            lower.contains("world news") ||
            lower.contains("global news")
    }

    private fun looksLikeQuestion(value: String): Boolean {
        val lower = value.lowercase().trim()
        return value.contains('?') || lower.startsWith("what ") ||
            lower.startsWith("who ") || lower.startsWith("when ") ||
            lower.startsWith("where ") || lower.startsWith("why ") ||
            lower.startsWith("how ") || lower.startsWith("is ") ||
            lower.startsWith("are ") || lower.startsWith("did ") ||
            lower.startsWith("does ") || lower.startsWith("tell me ") ||
            lower.startsWith("give me ") || lower.startsWith("show me ")
    }
}

class GeminiWebResearchClient(private val store: SecureCortexRegistry) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val RESEARCH_SYSTEM_PROMPT =
            "You are Jarvis's live web intelligence engine. Ground time-sensitive claims in Google Search. " +
                "Prefer authoritative primary sources and reputable reporting. Never pretend that model memory is live web evidence. " +
                "State uncertainty, avoid sensationalism, and separate facts from analysis."
    }

    fun isConfigured(): Boolean = bestProfile() != null

    fun research(userInput: String, memoryContext: String): WebResearchResult {
        val profile = bestProfile()
            ?: throw WebResearchException(
                "Live web research needs at least one configured Gemini cortex node."
            )

        val registry = store.load()
        val model = normalizeModel(profile.model)
        if (model.isBlank()) throw WebResearchException("The selected Gemini model ID is empty.")

        val prompt = buildResearchPrompt(
            userInput = userInput,
            memoryContext = memoryContext,
            worldBrief = WebResearchIntent.isWorldBrief(userInput)
        )
        val systemEnvelope = OwnerIdentityCore.researchEnvelope(
            editablePrompt = registry.systemPrompt,
            researchDirective = RESEARCH_SYSTEM_PROMPT + "\n\n" + JarvisDirective.RESEARCH
        )

        val requestBody = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("text", systemEnvelope)
                    )
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    }
                )
            )
            put(
                "tools",
                JSONArray().put(
                    JSONObject().put("google_search", JSONObject())
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.20)
                    put("maxOutputTokens", 2_400)
                }
            )
        }

        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val started = System.currentTimeMillis()
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = JarvisDirective.PROVIDER_READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-goog-api-key", profile.apiKey)
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.6")
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
                throw WebResearchException("Google-grounded research failed: $message")
            }

            val parsed = parseResponse(raw)
            if (parsed.answer.isBlank()) {
                throw WebResearchException("Google-grounded research returned an empty answer.")
            }

            return WebResearchResult(
                answer = parsed.answer,
                spokenSummary = createSpokenSummary(parsed.answer),
                sources = parsed.sources.take(JarvisDirective.MAX_RESEARCH_SOURCES),
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
            .filter { it.provider == CortexProvider.GEMINI }
            .filterNot { it.isCoolingDown(now) }
            .sortedByDescending { CortexMath.score(it, CortexTask.GENERAL, now) }
            .firstOrNull()
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
            appendLine("Create a WORLD INTELLIGENCE BRIEF using current Google Search results.")
            appendLine("Choose the five to seven most important global developments, not filler.")
            appendLine("For each development include: WHAT HAPPENED, WHY IT MATTERS, and WHAT TO WATCH NEXT.")
            appendLine("Cover geopolitics, economy, science/technology, climate/disasters, and health only when genuinely significant.")
            appendLine("Use exact dates and clearly distinguish confirmed facts, disputed claims, and uncertainty.")
            appendLine("End with a compact one-paragraph OVERALL ASSESSMENT.")
        } else {
            appendLine("Research this request using current Google Search results whenever useful.")
            appendLine("Answer directly, explain it in plain language, and break complex information into clear sections.")
            appendLine("Use exact dates for time-sensitive claims. Cross-check important claims across multiple sources when possible.")
            appendLine("If reliable sources disagree, describe the disagreement rather than choosing silently.")
        }

        appendLine("Do not invent facts, URLs, quotations, or access to private/paywalled content.")
        appendLine("Do not append a Sources section or raw URLs; the Android client attaches verified source metadata.")
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
        val candidates = root.optJSONArray("candidates")
            ?: throw WebResearchException("No candidate was returned by Gemini.")
        val candidate = candidates.optJSONObject(0)
            ?: throw WebResearchException("No candidate was returned by Gemini.")

        val answer = buildString {
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            if (parts != null) {
                for (index in 0 until parts.length()) {
                    val text = parts.optJSONObject(index)?.optString("text").orEmpty().trim()
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) appendLine()
                        append(text)
                    }
                }
            }
        }.trim()

        val metadata = candidate.optJSONObject("groundingMetadata")
        val sources = mutableListOf<WebSource>()
        val chunks = metadata?.optJSONArray("groundingChunks")
        if (chunks != null) {
            for (index in 0 until chunks.length()) {
                val web = chunks.optJSONObject(index)?.optJSONObject("web") ?: continue
                val uri = web.optString("uri").trim()
                if (uri.isBlank()) continue
                val title = web.optString("title").trim().ifBlank { "Web source" }
                sources += WebSource(title = title, uri = uri)
            }
        }

        val queries = mutableListOf<String>()
        val queryArray = metadata?.optJSONArray("webSearchQueries")
        if (queryArray != null) {
            for (index in 0 until queryArray.length()) {
                queryArray.optString(index).trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(queries::add)
            }
        }

        return ParsedResponse(
            answer = answer,
            sources = sources.distinctBy { it.uri },
            searchQueries = queries.distinct()
        )
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
            window.take(sentenceEnd + 1) +
                " I have placed the full intelligence report and sources on screen."
        } else {
            window.trimEnd() +
                "... I have placed the full intelligence report and sources on screen."
        }
    }

    private fun normalizeModel(value: String): String = value
        .trim()
        .removePrefix("models/")
        .substringBefore(':')
        .trim()

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
