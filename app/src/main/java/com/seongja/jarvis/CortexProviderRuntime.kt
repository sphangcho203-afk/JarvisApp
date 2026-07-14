package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Verified model aliases used by the owner-bound cortex mesh.
 *
 * Gemini API keys created in AI Studio use Google's global Generative Language
 * endpoint. Vertex AI region names such as global or us-central1 belong to the
 * OAuth/project-based Vertex route, not to these API-key requests.
 */
object CortexModelCatalog {
    const val GEMINI_LOCATION = "global"
    const val GEMINI_35_FLASH = "gemini-3.5-flash"
    const val GEMINI_25_FLASH = "gemini-2.5-flash"
    const val GEMINI_31_FLASH_LITE = "gemini-3.1-flash-lite"

    const val GROQ_LLAMA_33_70B = "llama-3.3-70b-versatile"
    const val GROQ_LLAMA_31_8B = "llama-3.1-8b-instant"
    const val GROQ_LLAMA_4_SCOUT = "meta-llama/llama-4-scout-17b-16e-instruct"
    const val GROQ_QWEN3_32B = "qwen/qwen3-32b"

    val geminiActive: List<String> = listOf(
        GEMINI_35_FLASH,
        GEMINI_25_FLASH,
        GEMINI_31_FLASH_LITE
    )

    val groqActive: List<String> = listOf(
        GROQ_LLAMA_33_70B,
        GROQ_LLAMA_31_8B,
        GROQ_LLAMA_4_SCOUT,
        GROQ_QWEN3_32B
    )

    fun defaultModel(provider: CortexProvider, slotIndex: Int): String = when (provider) {
        CortexProvider.GEMINI -> when (slotIndex % 6) {
            0, 2, 4 -> GEMINI_35_FLASH
            1, 3 -> GEMINI_25_FLASH
            else -> GEMINI_31_FLASH_LITE
        }
        CortexProvider.GROQ -> when (slotIndex % 4) {
            0 -> GROQ_LLAMA_31_8B
            1 -> GROQ_LLAMA_33_70B
            2 -> GROQ_LLAMA_4_SCOUT
            else -> GROQ_QWEN3_32B
        }
    }

    fun migrate(provider: CortexProvider, rawModel: String, fallback: String): String {
        val clean = normalize(rawModel)
        if (clean.isBlank()) return fallback
        return when (provider) {
            CortexProvider.GEMINI -> when (clean) {
                "gemini-3.5-flash-latest" -> GEMINI_35_FLASH
                "gemini-2.5-flash-latest" -> GEMINI_25_FLASH
                else -> clean
            }
            CortexProvider.GROQ -> when (clean) {
                "llama-3.3-70b-specdec",
                "llama-3.3-70b",
                "llama3-70b-8192" -> GROQ_LLAMA_33_70B
                "llama3-8b-8192" -> GROQ_LLAMA_31_8B
                "groq/compound",
                "groq/compound-mini" -> fallback
                else -> clean
            }
        }
    }

    fun candidates(profile: CortexProfile, task: CortexTask): List<String> {
        val configured = migrate(
            provider = profile.provider,
            rawModel = profile.model,
            fallback = defaultModel(profile.provider, profile.slotIndex())
        )
        val ordered = when (profile.provider) {
            CortexProvider.GEMINI -> when (task) {
                CortexTask.FAST -> listOf(
                    GEMINI_31_FLASH_LITE,
                    GEMINI_35_FLASH,
                    GEMINI_25_FLASH
                )
                CortexTask.GENERAL -> listOf(
                    GEMINI_35_FLASH,
                    GEMINI_31_FLASH_LITE,
                    GEMINI_25_FLASH
                )
                CortexTask.REASONING,
                CortexTask.CODING -> listOf(
                    GEMINI_35_FLASH,
                    GEMINI_25_FLASH,
                    GEMINI_31_FLASH_LITE
                )
            }
            CortexProvider.GROQ -> when (task) {
                CortexTask.FAST -> listOf(
                    GROQ_LLAMA_31_8B,
                    GROQ_LLAMA_4_SCOUT,
                    GROQ_QWEN3_32B,
                    GROQ_LLAMA_33_70B
                )
                CortexTask.GENERAL -> listOf(
                    GROQ_LLAMA_33_70B,
                    GROQ_LLAMA_4_SCOUT,
                    GROQ_QWEN3_32B,
                    GROQ_LLAMA_31_8B
                )
                CortexTask.REASONING,
                CortexTask.CODING -> listOf(
                    GROQ_QWEN3_32B,
                    GROQ_LLAMA_33_70B,
                    GROQ_LLAMA_4_SCOUT,
                    GROQ_LLAMA_31_8B
                )
            }
        }
        return (listOf(configured) + ordered)
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun normalize(value: String): String = value
        .trim()
        .removePrefix("models/")
        .substringBefore(':')
        .trim()

    private fun CortexProfile.slotIndex(): Int = id
        .substringAfterLast('_')
        .toIntOrNull()
        ?.minus(1)
        ?.coerceAtLeast(0)
        ?: 0
}

enum class CortexFailureKind {
    AUTH,
    RATE_LIMIT,
    MODEL,
    SERVER,
    NETWORK,
    OTHER
}

internal class CortexProviderFailure(
    val provider: CortexProvider,
    val model: String,
    val statusCode: Int,
    val retryAfterMs: Long,
    val kind: CortexFailureKind,
    message: String
) : Exception(message)

internal data class CortexProviderResponse(
    val reply: String,
    val statusCode: Int,
    val elapsedMs: Long,
    val model: String
)

/** Provider-specific HTTP transport with bounded timeouts and redacted errors. */
internal class CortexProviderTransport {

    fun request(
        profile: CortexProfile,
        model: String,
        systemEnvelope: String,
        userInput: String,
        temperature: Double,
        maxOutputTokens: Int,
        deadlineMs: Long
    ): CortexProviderResponse = when (profile.provider) {
        CortexProvider.GEMINI -> requestGemini(
            profile = profile,
            model = model,
            systemEnvelope = systemEnvelope,
            userInput = userInput,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            deadlineMs = deadlineMs
        )
        CortexProvider.GROQ -> requestGroq(
            profile = profile,
            model = model,
            systemEnvelope = systemEnvelope,
            userInput = userInput,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            deadlineMs = deadlineMs
        )
    }

    private fun requestGemini(
        profile: CortexProfile,
        model: String,
        systemEnvelope: String,
        userInput: String,
        temperature: Double,
        maxOutputTokens: Int,
        deadlineMs: Long
    ): CortexProviderResponse {
        val encodedModel = URLEncoder.encode(
            CortexModelCatalog.normalize(model),
            StandardCharsets.UTF_8.name()
        )
        val endpoint = "$GEMINI_BASE_ENDPOINT/$encodedModel:generateContent"
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemEnvelope))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", userInput.take(MAX_USER_INPUT_CHARS))
                            )
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", temperature)
                    put("maxOutputTokens", maxOutputTokens)
                }
            )
        }

        return executeJson(
            provider = CortexProvider.GEMINI,
            model = model,
            endpoint = endpoint,
            apiKey = profile.apiKey,
            headers = mapOf("x-goog-api-key" to profile.apiKey.trim()),
            body = body,
            deadlineMs = deadlineMs,
            parser = ::parseGeminiReply
        )
    }

    private fun requestGroq(
        profile: CortexProfile,
        model: String,
        systemEnvelope: String,
        userInput: String,
        temperature: Double,
        maxOutputTokens: Int,
        deadlineMs: Long
    ): CortexProviderResponse {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxOutputTokens)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemEnvelope))
                    put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", userInput.take(MAX_USER_INPUT_CHARS))
                    )
                }
            )
        }

        return executeJson(
            provider = CortexProvider.GROQ,
            model = model,
            endpoint = GROQ_ENDPOINT,
            apiKey = profile.apiKey,
            headers = mapOf("Authorization" to "Bearer ${profile.apiKey.trim()}"),
            body = body,
            deadlineMs = deadlineMs,
            parser = ::parseGroqReply
        )
    }

    private fun executeJson(
        provider: CortexProvider,
        model: String,
        endpoint: String,
        apiKey: String,
        headers: Map<String, String>,
        body: JSONObject,
        deadlineMs: Long,
        parser: (String) -> String
    ): CortexProviderResponse {
        val started = System.currentTimeMillis()
        val remainingMs = (deadlineMs - started).coerceAtLeast(MIN_TIMEOUT_MS)
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS.coerceAtMost(remainingMs).toInt()
            readTimeout = READ_TIMEOUT_MS.coerceAtMost(remainingMs).toInt()
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.7")
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
            val raw = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val message = redact(
                    extractApiError(raw).ifBlank { "HTTP $status" },
                    apiKey
                )
                throw CortexProviderFailure(
                    provider = provider,
                    model = model,
                    statusCode = status,
                    retryAfterMs = retryAfterMs(connection, status),
                    kind = classifyFailure(status, message),
                    message = message.take(MAX_ERROR_CHARS)
                )
            }

            val reply = parser(raw).trim()
            if (reply.isBlank()) {
                throw CortexProviderFailure(
                    provider = provider,
                    model = model,
                    statusCode = status,
                    retryAfterMs = 10_000L,
                    kind = CortexFailureKind.OTHER,
                    message = "Empty model response"
                )
            }
            return CortexProviderResponse(
                reply = reply,
                statusCode = status,
                elapsedMs = elapsed,
                model = model
            )
        } catch (error: CortexProviderFailure) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw CortexProviderFailure(
                provider = provider,
                model = model,
                statusCode = 0,
                retryAfterMs = 20_000L,
                kind = CortexFailureKind.NETWORK,
                message = "Network timeout"
            )
        } catch (error: Exception) {
            throw CortexProviderFailure(
                provider = provider,
                model = model,
                statusCode = 0,
                retryAfterMs = 15_000L,
                kind = CortexFailureKind.NETWORK,
                message = redact(error.message ?: error.javaClass.simpleName, apiKey)
                    .take(MAX_ERROR_CHARS)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGeminiReply(raw: String): String {
        val root = JSONObject(raw)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            ?: return root.optString("output_text")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: return ""
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text").orEmpty().trim()
                if (text.isNotBlank()) {
                    if (isNotEmpty()) appendLine()
                    append(text)
                }
            }
        }
    }

    private fun parseGroqReply(raw: String): String {
        val root = JSONObject(raw)
        return root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText().take(MAX_RESPONSE_CHARS)
        }
    }

    private fun extractApiError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: root.optString("message")
                .takeIf { it.isNotBlank() }
            ?: root.optString("detail")
    }.getOrDefault("")

    private fun classifyFailure(status: Int, message: String): CortexFailureKind {
        val lower = message.lowercase(Locale.US)
        return when {
            status == 401 -> CortexFailureKind.AUTH
            status == 429 -> CortexFailureKind.RATE_LIMIT
            status in 500..599 -> CortexFailureKind.SERVER
            status in setOf(400, 403, 404) && MODEL_ERROR_TERMS.any(lower::contains) ->
                CortexFailureKind.MODEL
            else -> CortexFailureKind.OTHER
        }
    }

    private fun retryAfterMs(connection: HttpURLConnection, status: Int): Long {
        connection.getHeaderField("Retry-After")
            ?.trim()
            ?.toDoubleOrNull()
            ?.let { return (it * 1_000.0).toLong().coerceIn(1_000L, MAX_RETRY_MS) }

        parseResetDuration(connection.getHeaderField("x-ratelimit-reset-tokens"))
            ?.let { return it.coerceIn(1_000L, MAX_RETRY_MS) }

        return when {
            status == 429 -> 60_000L
            status in 500..599 -> 20_000L
            else -> 15_000L
        }
    }

    private fun parseResetDuration(value: String?): Long? {
        val clean = value?.trim()?.lowercase(Locale.US).orEmpty()
        if (clean.isBlank()) return null
        var totalMs = 0.0
        val matches = Regex("(\\d+(?:\\.\\d+)?)(ms|s|m|h)").findAll(clean).toList()
        if (matches.isEmpty()) return clean.toDoubleOrNull()?.times(1_000.0)?.toLong()
        matches.forEach { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            totalMs += when (match.groupValues[2]) {
                "ms" -> amount
                "s" -> amount * 1_000.0
                "m" -> amount * 60_000.0
                "h" -> amount * 3_600_000.0
                else -> 0.0
            }
        }
        return totalMs.toLong().takeIf { it > 0L }
    }

    private fun redact(value: String, apiKey: String): String = value
        .replace(apiKey, "[redacted]", ignoreCase = false)
        .replace(Regex("AIza[A-Za-z0-9_-]{20,}"), "[redacted]")
        .replace(Regex("gsk_[A-Za-z0-9_-]{20,}"), "[redacted]")
        .replace(Regex("(?i)authorization\\s*[:=]\\s*[^\\s,;]+"), "authorization=[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val GEMINI_BASE_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private const val GROQ_ENDPOINT =
            "https://api.groq.com/openai/v1/chat/completions"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val READ_TIMEOUT_MS = 45_000L
        private const val MIN_TIMEOUT_MS = 3_000L
        private const val MAX_RETRY_MS = 15 * 60_000L
        private const val MAX_USER_INPUT_CHARS = 12_000
        private const val MAX_RESPONSE_CHARS = 500_000
        private const val MAX_ERROR_CHARS = 320
        private val MODEL_ERROR_TERMS = listOf(
            "model", "not found", "unsupported", "decommission", "permission",
            "not available", "region", "location", "does not exist"
        )
    }
}
