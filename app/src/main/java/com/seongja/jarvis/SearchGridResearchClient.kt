package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.time.ZonedDateTime
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class SearchGridException(message: String) : Exception(message)

private data class SearchPlan(
    val originalRequest: String,
    val queries: List<String>,
    val deep: Boolean,
    val news: Boolean,
    val technical: Boolean,
    val maxEvidence: Int
)

private data class ProviderSearchResponse(
    val statusCode: Int,
    val elapsedMs: Long,
    val evidence: List<SearchEvidence>
)

/**
 * JARVIS Search Grid
 *
 * Tavily handles current web discovery and news. Exa handles semantic retrieval
 * and deep-page evidence. Results are normalized, deduplicated, then passed to
 * the existing cortex mesh for grounded synthesis with numbered citations.
 */
class SearchGridResearchClient(
    private val searchStore: SecureSearchGridRegistry,
    cortexStore: SecureCortexRegistry
) {
    private val cortexMesh = CortexMeshClient(cortexStore)

    fun isConfigured(): Boolean =
        searchStore.load().configuredCredentials().isNotEmpty()

    fun configuredProviders(): List<SearchGridProvider> =
        searchStore.load().configuredCredentials().map { it.provider }

    fun testProvider(provider: SearchGridProvider): SearchProviderTestResult {
        val credential = searchStore.load().credential(provider)
            ?.takeIf { it.isConfigured() }
            ?: throw SearchGridException("${provider.displayName} is not configured.")

        val plan = SearchPlan(
            originalRequest = "Android official developer documentation",
            queries = listOf("Android official developer documentation"),
            deep = false,
            news = false,
            technical = true,
            maxEvidence = 2
        )
        val response = executeProvider(credential, plan)
        return SearchProviderTestResult(
            provider = provider,
            statusCode = response.statusCode,
            elapsedMs = response.elapsedMs,
            resultCount = response.evidence.size
        )
    }

    fun research(userInput: String, memoryContext: String): SearchGridResult {
        val started = System.currentTimeMillis()
        val plan = createPlan(userInput)
        val now = System.currentTimeMillis()
        val configured = searchStore.load().configuredCredentials()
            .filterNot { it.isCoolingDown(now) }

        if (configured.isEmpty()) {
            throw SearchGridException("No healthy Tavily or Exa search provider is configured.")
        }

        val collected = mutableListOf<SearchEvidence>()
        val successfulProviders = mutableListOf<SearchGridProvider>()
        val failures = mutableListOf<String>()

        configured.forEach { credential ->
            runCatching { executeProvider(credential, plan) }
                .onSuccess { response ->
                    if (response.evidence.isNotEmpty()) {
                        collected += response.evidence
                        successfulProviders += credential.provider
                    }
                }
                .onFailure { error ->
                    failures += "${credential.provider.displayName}: ${redact(error.message.orEmpty())}"
                }
        }

        val evidence = deduplicate(collected)
            .sortedWith(
                compareByDescending<SearchEvidence> { it.relevance }
                    .thenByDescending { it.publishedDate }
            )
            .take(plan.maxEvidence)

        if (evidence.isEmpty()) {
            throw SearchGridException(
                "Search providers returned no usable evidence. ${failures.joinToString(" | ").take(360)}"
            )
        }

        val synthesisPrompt = buildSynthesisPrompt(plan, evidence)
        val mesh = cortexMesh.ask(synthesisPrompt, memoryContext)
        val cleanAnswer = JarvisResponseSanitizer.clean(mesh.reply, groundedResearch = true)
        val elapsed = System.currentTimeMillis() - started
        val sources = evidence.map {
            WebSource(
                title = it.title.ifBlank { it.url },
                uri = it.url
            )
        }

        return SearchGridResult(
            research = WebResearchResult(
                answer = cleanAnswer,
                spokenSummary = createSpokenSummary(cleanAnswer),
                sources = sources,
                searchQueries = plan.queries,
                model = mesh.model,
                profileLabel = "SEARCH GRID -> ${mesh.profileLabel}",
                statusCode = 200,
                elapsedMs = elapsed
            ),
            providers = successfulProviders.distinct(),
            evidenceCount = evidence.size
        )
    }

    private fun executeProvider(
        credential: SearchGridCredential,
        plan: SearchPlan
    ): ProviderSearchResponse {
        return try {
            val result = when (credential.provider) {
                SearchGridProvider.TAVILY -> searchTavily(credential, plan)
                SearchGridProvider.EXA -> searchExa(credential, plan)
            }
            markSuccess(credential, result)
            result
        } catch (error: SearchGridException) {
            markFailure(credential, error)
            throw error
        } catch (error: Exception) {
            val wrapped = SearchGridException(
                redact(error.message ?: error.javaClass.simpleName)
            )
            markFailure(credential, wrapped)
            throw wrapped
        }
    }

    private fun searchTavily(
        credential: SearchGridCredential,
        plan: SearchPlan
    ): ProviderSearchResponse {
        val started = System.currentTimeMillis()
        var finalStatus = 0
        val evidence = mutableListOf<SearchEvidence>()
        val queries = if (plan.deep) plan.queries.take(2) else plan.queries.take(1)

        queries.forEach { query ->
            val body = JSONObject().apply {
                put("query", query)
                put("search_depth", if (plan.deep) "advanced" else "basic")
                put("max_results", if (plan.deep) 8 else 6)
                put("topic", if (plan.news) "news" else "general")
                put("include_answer", false)
                put("include_raw_content", false)
                put("include_images", false)
                put("include_favicon", false)
                put("auto_parameters", false)
                put("safe_search", true)
                if (plan.news) put("time_range", if (plan.deep) "week" else "day")
                if (plan.deep) put("chunks_per_source", 3)
            }

            val response = postJson(
                endpoint = SearchGridProvider.TAVILY.endpoint,
                body = body,
                headers = mapOf(
                    "Authorization" to "Bearer ${credential.apiKey.trim()}"
                )
            )
            finalStatus = response.first
            val raw = response.second
            if (finalStatus !in 200..299) {
                throw SearchGridException(
                    "Tavily HTTP $finalStatus: ${extractTavilyError(raw)}"
                )
            }

            val root = JSONObject(raw)
            val results = root.optJSONArray("results") ?: JSONArray()
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (!isHttpUrl(url)) continue
                val title = item.optString("title").trim().ifBlank { domainLabel(url) }
                val snippet = cleanSnippet(
                    item.optString("content").ifBlank {
                        item.optString("raw_content")
                    }
                )
                evidence += SearchEvidence(
                    title = title,
                    url = url,
                    snippet = snippet,
                    publishedDate = item.optString("published_date")
                        .ifBlank { item.optString("publishedDate") }
                        .trim(),
                    author = item.optString("author").trim(),
                    provider = SearchGridProvider.TAVILY,
                    relevance = item.optDouble("score", 0.55).coerceIn(0.0, 1.0)
                )
            }
        }

        return ProviderSearchResponse(
            statusCode = finalStatus.takeIf { it > 0 } ?: 200,
            elapsedMs = System.currentTimeMillis() - started,
            evidence = evidence
        )
    }

    private fun searchExa(
        credential: SearchGridCredential,
        plan: SearchPlan
    ): ProviderSearchResponse {
        val started = System.currentTimeMillis()
        val body = JSONObject().apply {
            put("query", plan.queries.first())
            put("numResults", if (plan.deep) 10 else 7)
            put("type", if (plan.deep) "deep-lite" else "auto")
            put("moderation", true)
            if (plan.news) put("category", "news")
            if (plan.deep && plan.queries.size > 1) {
                put("additionalQueries", JSONArray(plan.queries.drop(1).take(4)))
            }
            put(
                "contents",
                JSONObject().apply {
                    put("highlights", true)
                }
            )
            put(
                "systemPrompt",
                "Prefer official, primary, technically authoritative, recent, and non-duplicate sources."
            )
        }

        val response = postJson(
            endpoint = SearchGridProvider.EXA.endpoint,
            body = body,
            headers = mapOf("x-api-key" to credential.apiKey.trim())
        )
        val status = response.first
        val raw = response.second
        if (status !in 200..299) {
            throw SearchGridException("Exa HTTP $status: ${extractExaError(raw)}")
        }

        val root = JSONObject(raw)
        val results = root.optJSONArray("results") ?: JSONArray()
        val evidence = mutableListOf<SearchEvidence>()
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (!isHttpUrl(url)) continue
            val highlights = item.optJSONArray("highlights")
            val highlightText = buildString {
                if (highlights != null) {
                    for (highlightIndex in 0 until highlights.length()) {
                        val value = highlights.optString(highlightIndex).trim()
                        if (value.isNotBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(value)
                        }
                    }
                }
            }
            val snippet = cleanSnippet(
                highlightText.ifBlank {
                    item.optString("summary").ifBlank { item.optString("text") }
                }
            )
            evidence += SearchEvidence(
                title = item.optString("title").trim().ifBlank { domainLabel(url) },
                url = url,
                snippet = snippet,
                publishedDate = item.optString("publishedDate").trim(),
                author = item.optString("author").trim(),
                provider = SearchGridProvider.EXA,
                relevance = exaRelevance(item, index)
            )
        }

        return ProviderSearchResponse(
            statusCode = status,
            elapsedMs = System.currentTimeMillis() - started,
            evidence = evidence
        )
    }

    private fun createPlan(input: String): SearchPlan {
        val clean = input.trim()
        val lower = clean.lowercase(Locale.US)
        val deep = JarvisDirective.isDeepResearch(clean) ||
            lower.contains("comprehensive") ||
            lower.contains("investigate") ||
            lower.contains("full report")
        val news = WebResearchIntent.isWorldBrief(clean) ||
            Regex("\\b(news|latest|today|current|recent|breaking|update)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(clean)
        val technical = Regex(
            "\\b(api|android|kotlin|java|python|code|documentation|library|framework|research paper|arxiv|github|software|technical)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(clean)

        val queries = buildList {
            add(clean)
            if (news) {
                add("$clean exact dates primary sources latest verified developments")
            }
            if (technical) {
                add("$clean official documentation primary technical sources")
            }
            if (deep) {
                add("$clean evidence counterarguments limitations independent verification")
            }
        }
            .map { it.replace(Regex("\\s+"), " ").trim().take(500) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)

        return SearchPlan(
            originalRequest = clean,
            queries = queries.ifEmpty { listOf(clean) },
            deep = deep,
            news = news,
            technical = technical,
            maxEvidence = if (deep) 12 else 8
        )
    }

    private fun buildSynthesisPrompt(
        plan: SearchPlan,
        evidence: List<SearchEvidence>
    ): String = buildString {
        appendLine("LIVE SEARCH GRID SYNTHESIS")
        appendLine("Current device time: ${ZonedDateTime.now()}")
        appendLine("Seongja asked: ${plan.originalRequest}")
        appendLine()
        appendLine(JarvisDirective.RESEARCH)
        appendLine(JarvisDirective.SUMMARIZATION)
        appendLine()
        appendLine("Use only the evidence packet below for current factual claims.")
        appendLine("Cite supporting sources inline as [1], [2], and so on.")
        appendLine("Do not invent a citation number, URL, quotation, date, or retrieved fact.")
        appendLine("When evidence conflicts, describe the conflict and reduce confidence.")
        appendLine("Do not print raw URLs because the Android client attaches them below the answer.")
        appendLine("Speak directly to Seongja as you or Sir. Never call him the user or operator.")
        appendLine("Return only the final intelligence brief. Never output <think>, <analysis>, scratchpad, or preparation text.")
        appendLine("Start with the actual answer. Do not say that you can provide a summary or mention a knowledge cutoff.")
        appendLine()
        appendLine("EVIDENCE PACKET")
        evidence.forEachIndexed { index, source ->
            appendLine(source.label(index + 1))
            appendLine("URL: ${source.url}")
            appendLine(
                "EXCERPT: ${source.snippet.ifBlank { "No extract returned; use title and metadata only." }.take(760)}"
            )
            appendLine()
        }
    }.take(MAX_SYNTHESIS_PROMPT_CHARS)

    private fun deduplicate(input: List<SearchEvidence>): List<SearchEvidence> {
        val byUrl = linkedMapOf<String, SearchEvidence>()
        input.forEach { candidate ->
            val key = canonicalUrl(candidate.url)
            val previous = byUrl[key]
            if (previous == null || candidate.relevance > previous.relevance ||
                candidate.snippet.length > previous.snippet.length
            ) {
                byUrl[key] = candidate
            }
        }

        val seenTitles = mutableSetOf<String>()
        return byUrl.values.filter { source ->
            val titleKey = source.title
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            titleKey.isBlank() || seenTitles.add(titleKey)
        }
    }

    private fun markSuccess(
        credential: SearchGridCredential,
        result: ProviderSearchResponse
    ) {
        searchStore.updateCredential(
            credential.copy(
                successes = credential.successes + 1,
                requestCount = credential.requestCount + 1,
                lastLatencyMs = result.elapsedMs,
                lastUsedAtMs = System.currentTimeMillis(),
                cooldownUntilMs = 0L,
                lastStatusCode = result.statusCode,
                lastError = ""
            )
        )
    }

    private fun markFailure(
        credential: SearchGridCredential,
        error: SearchGridException
    ) {
        val message = redact(error.message.orEmpty()).take(240)
        val status = Regex("HTTP\\s+(\\d{3})")
            .find(message)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        val cooldown = when {
            status == 429 -> 90_000L
            status == 401 || status == 403 -> 300_000L
            status in 500..599 -> 30_000L
            else -> 15_000L
        }
        val now = System.currentTimeMillis()
        searchStore.updateCredential(
            credential.copy(
                failures = credential.failures + 1,
                requestCount = credential.requestCount + 1,
                lastUsedAtMs = now,
                cooldownUntilMs = now + cooldown,
                lastStatusCode = status,
                lastError = message
            )
        )
    }

    private fun postJson(
        endpoint: String,
        body: JSONObject,
        headers: Map<String, String>
    ): Pair<Int, String> {
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.5")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

        try {
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { stream ->
                stream.write(payload)
                stream.flush()
            }
            val status = connection.responseCode
            return status to readResponse(connection, status)
        } catch (error: SocketTimeoutException) {
            throw SearchGridException("Network timeout while contacting the search provider.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText().take(MAX_PROVIDER_RESPONSE_CHARS)
        }
    }

    private fun extractTavilyError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optJSONObject("detail")?.optString("error")
            ?: root.optJSONObject("error")?.optString("message")
            ?: root.optString("message")
    }.getOrNull().orEmpty().ifBlank { "request rejected" }.let(::redact)

    private fun extractExaError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optString("error")
            .ifBlank { root.optString("message") }
            .ifBlank { root.optJSONObject("error")?.optString("message").orEmpty() }
    }.getOrNull().orEmpty().ifBlank { "request rejected" }.let(::redact)

    private fun exaRelevance(item: JSONObject, index: Int): Double {
        val explicit = item.optDouble("score", Double.NaN)
        if (!explicit.isNaN()) return explicit.coerceIn(0.0, 1.0)
        return (0.88 - index * 0.045).coerceIn(0.35, 0.88)
    }

    private fun cleanSnippet(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("[...]", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_SNIPPET_CHARS)

    private fun canonicalUrl(value: String): String = runCatching {
        val uri = URI(value.trim())
        val host = uri.host.orEmpty().lowercase(Locale.US).removePrefix("www.")
        val path = uri.path.orEmpty().trimEnd('/').lowercase(Locale.US)
        "$host$path"
    }.getOrDefault(value.lowercase(Locale.US).substringBefore('#').substringBefore('?'))

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun domainLabel(value: String): String = runCatching {
        URI(value).host.orEmpty().removePrefix("www.")
    }.getOrDefault("Web source").ifBlank { "Web source" }

    private fun redact(value: String): String = value
        .replace(Regex("tvly-[A-Za-z0-9_-]+"), "[redacted]")
        .replace(Regex("(?i)(x-api-key|authorization)\\s*[:=]\\s*[^\\s,;]+"), "$1=[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun createSpokenSummary(answer: String): String {
        val cleaned = answer
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\[(\\d+)]"), "")
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace("**", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length <= 900) return cleaned

        val window = cleaned.take(900)
        val sentenceEnd = maxOf(
            window.lastIndexOf(". "),
            window.lastIndexOf("! "),
            window.lastIndexOf("? ")
        )
        return if (sentenceEnd >= 420) {
            window.take(sentenceEnd + 1) +
                " The complete intelligence brief and source grid are on screen."
        } else {
            window.trimEnd() +
                "... The complete intelligence brief and source grid are on screen."
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 75_000
        private const val MAX_PROVIDER_RESPONSE_CHARS = 900_000
        private const val MAX_SNIPPET_CHARS = 1_600
        private const val MAX_SYNTHESIS_PROMPT_CHARS = 11_600
    }
}
