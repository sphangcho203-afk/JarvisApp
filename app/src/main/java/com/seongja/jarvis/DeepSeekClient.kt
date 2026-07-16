package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class DeepSeekException(message: String) : Exception(message)

data class DeepSeekResult(
    val reply: String,
    val model: String,
    val statusCode: Int,
    val elapsedMs: Long
)

/** Optional DeepSeek reasoning route used only after the primary cortex mesh fails. */
class DeepSeekClient(private val store: SecureIntegrationRegistry) {
    fun isConfigured(): Boolean = store.load().isDeepSeekConfigured()

    fun ask(
        userInput: String,
        memoryContext: String,
        onToken: ((String) -> Unit)? = null
    ): DeepSeekResult {
        val settings = store.load()
        if (!settings.isDeepSeekConfigured()) {
            throw DeepSeekException("DeepSeek is not configured.")
        }

        val system = buildString {
            append(CortexRegistry.DEFAULT_SYSTEM_PROMPT)
            append("\n\nRelevant encrypted owner context:\n")
            append(memoryContext.take(5_000))
            append("\n\nReturn only the polished final answer. Do not expose hidden reasoning.")
        }
        val body = JSONObject().apply {
            put("model", settings.deepSeekModel.trim())
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", userInput.take(10_000)))
                }
            )
            put("stream", false)
            put("temperature", 0.72)
            put("max_tokens", 2_400)
        }

        val started = System.currentTimeMillis()
        val connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 70_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.deepSeekApiKey.trim()}")
            setRequestProperty("User-Agent", "Friday-Android/0.9.22")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            val status = connection.responseCode
            val responseText = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val message = apiError(responseText)
                store.recordDeepSeekFailure(message, status)
                throw DeepSeekException("DeepSeek HTTP $status: $message")
            }
            val root = JSONObject(responseText)
            val reply = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (reply.isBlank()) {
                store.recordDeepSeekFailure("Empty response", status)
                throw DeepSeekException("DeepSeek returned an empty response.")
            }
            val clean = JarvisResponseSanitizer.clean(reply)
            store.recordDeepSeekSuccess(elapsed, status)
            onToken?.invoke(clean)
            return DeepSeekResult(
                reply = clean,
                model = root.optString("model", settings.deepSeekModel),
                statusCode = status,
                elapsedMs = elapsed
            )
        } catch (error: DeepSeekException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            store.recordDeepSeekFailure(message)
            throw DeepSeekException(message)
        } finally {
            connection.disconnect()
        }
    }

    fun test(): DeepSeekResult = ask(
        userInput = "Reply with exactly: DEEPSEEK CORTEX ONLINE",
        memoryContext = "Connection diagnostic only."
    )

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun apiError(raw: String): String = runCatching {
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: raw.take(280)
    }.getOrDefault(raw.take(280)).ifBlank { "Unknown provider error" }

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    }
}
