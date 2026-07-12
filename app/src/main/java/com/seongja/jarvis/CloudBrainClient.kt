package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CloudBrainException(message: String) : Exception(message)

data class CloudBrainResult(
    val reply: String,
    val statusCode: Int,
    val elapsedMs: Long,
    val model: String
)

class CloudBrainClient(private val configStore: SecureCloudConfigStore) {

    fun ask(userInput: String, memoryContext: String): CloudBrainResult {
        val config = configStore.load()
        validate(config)

        val requestBody = JSONObject().apply {
            put("model", config.model)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                config.systemPrompt + "\n\nOperator memory:\n" + memoryContext.take(5_000)
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userInput.take(8_000))
                        }
                    )
                }
            )
        }

        val started = System.currentTimeMillis()
        val connection = (URL(config.endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Jarvis-Android/0.8.2")
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            val status = connection.responseCode
            val responseText = readResponse(connection, status)
            val elapsed = System.currentTimeMillis() - started

            if (status !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw CloudBrainException(
                    if (apiMessage.isNotBlank()) "API $status: $apiMessage" else "API request failed with HTTP $status."
                )
            }

            val reply = parseReply(responseText)
            if (reply.isBlank()) throw CloudBrainException("The cloud model returned an empty response.")
            return CloudBrainResult(reply, status, elapsed, config.model)
        } catch (error: CloudBrainException) {
            throw error
        } catch (error: Exception) {
            throw CloudBrainException(error.message ?: error.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    fun testConnection(): CloudBrainResult = ask(
        userInput = "Reply with exactly: CLOUD CORTEX ONLINE",
        memoryContext = "Connection diagnostic only."
    )

    private fun validate(config: CloudConfig) {
        if (!config.isConfigured()) {
            throw CloudBrainException("Cloud API is not configured.")
        }

        val uri = runCatching { URI(config.endpoint) }
            .getOrElse { throw CloudBrainException("The API endpoint is invalid.") }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw CloudBrainException("Only HTTPS cloud endpoints are allowed.")
        }

        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank()) throw CloudBrainException("The API endpoint has no valid host.")
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1") {
            throw CloudBrainException("Localhost endpoints are disabled in cloud-only mode.")
        }
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun parseReply(raw: String): String {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.optJSONObject(0)
            val messageText = first
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (messageText.isNotBlank()) return messageText

            val text = first?.optString("text").orEmpty().trim()
            if (text.isNotBlank()) return text
        }

        val direct = root.optString("output_text").trim()
        if (direct.isNotBlank()) return direct

        throw CloudBrainException("Unsupported API response format. Use an OpenAI-compatible chat-completions endpoint.")
    }
}
