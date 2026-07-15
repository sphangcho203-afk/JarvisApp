package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** Streams Gemini and Groq SSE text deltas without waiting for the full reply. */
internal class CortexStreamingTransport {

    fun request(
        profile: CortexProfile,
        model: String,
        systemEnvelope: String,
        userInput: String,
        temperature: Double,
        maxOutputTokens: Int,
        deadlineMs: Long,
        onToken: (String) -> Unit
    ): CortexProviderResponse = when (profile.provider) {
        CortexProvider.GEMINI -> requestGemini(
            profile,
            model,
            systemEnvelope,
            userInput,
            temperature,
            maxOutputTokens,
            deadlineMs,
            onToken
        )
        CortexProvider.GROQ -> requestGroq(
            profile,
            model,
            systemEnvelope,
            userInput,
            temperature,
            maxOutputTokens,
            deadlineMs,
            onToken
        )
    }

    private fun requestGemini(
        profile: CortexProfile,
        model: String,
        systemEnvelope: String,
        userInput: String,
        temperature: Double,
        maxOutputTokens: Int,
        deadlineMs: Long,
        onToken: (String) -> Unit
    ): CortexProviderResponse {
        val encodedModel = URLEncoder.encode(
            CortexModelCatalog.normalize(model),
            StandardCharsets.UTF_8.name()
        )
        val endpoint = "$GEMINI_BASE_ENDPOINT/$encodedModel:streamGenerateContent?alt=sse"
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemEnvelope))
                )
            )
            put("safetySettings", geminiSafetySettings())
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", userInput.take(MAX_USER_INPUT_CHARS))
                            )
                        )
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", temperature)
                    .put("maxOutputTokens", maxOutputTokens)
            )
        }
        return executeSse(
            provider = CortexProvider.GEMINI,
            model = model,
            endpoint = endpoint,
            apiKey = profile.apiKey,
            headers = mapOf("x-goog-api-key" to profile.apiKey.trim()),
            body = body,
            deadlineMs = deadlineMs,
            tokenParser = ::parseGeminiToken,
            onToken = onToken
        )
    }

    private fun requestGroq(
        profile: CortexProfile,
        model: String,
        systemEnvelope: String,
        userInput: String,
        temperature: Double,
        maxOutputTokens: Int,
        deadlineMs: Long,
        onToken: (String) -> Unit
    ): CortexProviderResponse {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", JarvisRuntimeConfig.GROQ_FRIDAY_TEMPERATURE)
            put("max_tokens", maxOutputTokens)
            put("stream", true)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemEnvelope))
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", userInput.take(MAX_USER_INPUT_CHARS))
                    )
            )
        }
        return executeSse(
            provider = CortexProvider.GROQ,
            model = model,
            endpoint = GROQ_ENDPOINT,
            apiKey = profile.apiKey,
            headers = mapOf("Authorization" to "Bearer ${profile.apiKey.trim()}"),
            body = body,
            deadlineMs = deadlineMs,
            tokenParser = ::parseGroqToken,
            onToken = onToken
        )
    }

    private fun executeSse(
        provider: CortexProvider,
        model: String,
        endpoint: String,
        apiKey: String,
        headers: Map<String, String>,
        body: JSONObject,
        deadlineMs: Long,
        tokenParser: (JSONObject) -> String,
        onToken: (String) -> Unit
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
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", "Jarvis-Android/0.9.18")
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
            if (status !in 200..299) {
                val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = redact(extractApiError(raw).ifBlank { "HTTP $status" }, apiKey)
                throw CortexProviderFailure(
                    provider = provider,
                    model = model,
                    statusCode = status,
                    retryAfterMs = retryAfterMs(connection, status),
                    kind = classifyFailure(status, message),
                    message = message.take(MAX_ERROR_CHARS)
                )
            }

            val answer = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (System.currentTimeMillis() >= deadlineMs) {
                        throw SocketTimeoutException("Cortex streaming deadline reached")
                    }
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    val token = runCatching { tokenParser(JSONObject(data)) }.getOrDefault("")
                    if (token.isNotEmpty()) {
                        answer.append(token)
                        onToken(token)
                    }
                }
            }

            val reply = answer.toString().trim()
            if (reply.isBlank()) {
                throw CortexProviderFailure(
                    provider = provider,
                    model = model,
                    statusCode = status,
                    retryAfterMs = 10_000L,
                    kind = CortexFailureKind.OTHER,
                    message = "Empty streaming model response"
                )
            }
            return CortexProviderResponse(
                reply = reply,
                statusCode = status,
                elapsedMs = System.currentTimeMillis() - started,
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
                message = "Streaming network timeout"
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

    private fun parseGeminiToken(root: JSONObject): String {
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotEmpty()) append(text)
            }
        }
    }

    private fun parseGroqToken(root: JSONObject): String =
        root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
            .orEmpty()

    private fun geminiSafetySettings(): JSONArray = JSONArray().apply {
        GEMINI_HARM_CATEGORIES.forEach { category ->
            put(
                JSONObject()
                    .put("category", category)
                    .put("threshold", JarvisRuntimeConfig.GEMINI_SAFETY_THRESHOLD)
            )
        }
    }

    private fun extractApiError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: root.optString("message").takeIf { it.isNotBlank() }
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

    private fun retryAfterMs(connection: HttpsURLConnection, status: Int): Long {
        connection.getHeaderField("Retry-After")
            ?.trim()
            ?.toDoubleOrNull()
            ?.let { return (it * 1_000.0).toLong().coerceIn(1_000L, MAX_RETRY_MS) }
        return when {
            status == 429 -> 60_000L
            status in 500..599 -> 20_000L
            else -> 15_000L
        }
    }

    private fun redact(value: String, apiKey: String): String = value
        .replace(apiKey, "[redacted]", ignoreCase = false)
        .replace(Regex("AIza[A-Za-z0-9_-]{20,}"), "[redacted]")
        .replace(Regex("gsk_[A-Za-z0-9_-]{20,}"), "[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val GEMINI_BASE_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private const val GROQ_ENDPOINT =
            "https://api.groq.com/openai/v1/chat/completions"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val READ_TIMEOUT_MS = 65_000L
        private const val MIN_TIMEOUT_MS = 3_000L
        private const val MAX_RETRY_MS = 15 * 60_000L
        private const val MAX_USER_INPUT_CHARS = 12_000
        private const val MAX_ERROR_CHARS = 320
        private val GEMINI_HARM_CATEGORIES = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        )
        private val MODEL_ERROR_TERMS = listOf(
            "model", "not found", "unsupported", "decommission", "permission",
            "not available", "region", "location", "does not exist"
        )
    }
}
