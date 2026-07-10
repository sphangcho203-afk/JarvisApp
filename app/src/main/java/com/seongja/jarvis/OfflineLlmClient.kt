package com.seongja.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class OfflineLlmDecision(
    val reply: String,
    val tool: String,
    val argument: String,
    val memoryFact: String,
    val mode: String,
    val confidence: Float,
    val raw: String
)

internal class OfflineLlmClient(
    private val endpoint: String = "http://127.0.0.1:8080/v1/chat/completions"
) {
    @Volatile
    var lastError: String = "none"
        private set

    fun ask(userInput: String, memoryContext: String): OfflineLlmDecision? {
        lastError = "none"

        return runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3_000
                readTimeout = 300_000
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer local-only")
            }

            val body = requestBody(userInput, memoryContext).toString()
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${responseText.take(260)}")
            }
            if (responseText.isBlank()) {
                throw IllegalStateException("llama-server returned an empty HTTP body")
            }

            parseResponse(responseText)
        }.onFailure {
            lastError = (it.message ?: it.javaClass.simpleName).take(220)
        }.getOrNull()
    }

    private fun requestBody(userInput: String, memoryContext: String): JSONObject {
        val toolValues = JSONArray(
            listOf("none", "open_app", "open_settings", "remember", "set_identity", "set_mode")
        )

        val schema = JSONObject().apply {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", JSONObject().apply {
                put("reply", JSONObject().apply {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", 240)
                })
                put("tool", JSONObject().apply {
                    put("type", "string")
                    put("enum", toolValues)
                })
                put("argument", JSONObject().apply {
                    put("type", "string")
                    put("maxLength", 120)
                })
            })
            put("required", JSONArray(listOf("reply", "tool", "argument")))
        }

        val systemPrompt = """
            You are JARVIS, Seongja's fully offline Android assistant.
            Reply briefly and honestly. Address the user as Sir when natural.
            Return JSON with exactly: reply, tool, argument.
            Tools: none, open_app, open_settings, remember, set_identity, set_mode.
            Never claim an action succeeded before Android confirms it.
            No internet. Memory: ${memoryContext.take(240)}
        """.trimIndent()

        return JSONObject().apply {
            put("model", "jarvis-local")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userInput.take(400)))
            })
            put("temperature", 0.35)
            put("top_p", 0.9)
            put("repeat_penalty", 1.08)
            put("max_tokens", 128)
            put("stream", false)
            put("cache_prompt", true)
            put("reasoning_format", "none")
            put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            put("response_format", JSONObject().apply {
                put("type", "json_schema")
                put("schema", schema)
            })
        }
    }

    private fun parseResponse(responseText: String): OfflineLlmDecision {
        val envelope = JSONObject(responseText)
        val choices = envelope.optJSONArray("choices")
            ?: throw IllegalArgumentException("No choices array in llama-server response")

        if (choices.length() == 0) {
            throw IllegalArgumentException("llama-server returned zero choices")
        }

        val choice = choices.optJSONObject(0)
            ?: throw IllegalArgumentException("choices[0] was not an object")
        val message = choice.optJSONObject("message")

        val content = message?.optString("content", "")?.trim().orEmpty()
        val text = choice.optString("text", "").trim()
        val reasoning = message?.optString("reasoning_content", "")?.trim().orEmpty()

        val candidate = listOf(content, text, reasoning).firstOrNull { it.isNotBlank() }
            ?: throw IllegalArgumentException("Model returned empty content")

        val json = extractJsonObjectOrNull(candidate)

        val parsedReply = json?.let {
            firstNonBlank(
                it.optString("reply", ""),
                it.optString("answer", ""),
                it.optString("response", ""),
                it.optString("message", ""),
                it.optString("text", "")
            )
        }.orEmpty()

        val plainReply = if (json == null) cleanPlainText(candidate) else ""
        val reply = firstNonBlank(parsedReply, plainReply).take(240)

        if (reply.isBlank()) {
            throw IllegalArgumentException("Model JSON contained no usable reply: ${candidate.take(120)}")
        }

        val allowedTools = setOf(
            "none", "open_app", "open_settings", "remember", "set_identity", "set_mode"
        )

        val tool = json
            ?.optString("tool", "none")
            ?.lowercase(Locale.US)
            ?.let { if (it in allowedTools) it else "none" }
            ?: "none"

        val argument = json?.optString("argument", "")?.trim()?.take(120).orEmpty()
        val mode = if (tool == "set_mode" && argument.isNotBlank()) {
            argument.uppercase(Locale.US)
        } else {
            "ONLINE"
        }

        return OfflineLlmDecision(
            reply = reply,
            tool = tool,
            argument = argument,
            memoryFact = if (tool == "remember") argument else "",
            mode = mode,
            confidence = 0.86f,
            raw = candidate.take(1_000)
        )
    }

    private fun extractJsonObjectOrNull(text: String): JSONObject? {
        val clean = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return runCatching {
            JSONObject(clean.substring(start, end + 1))
        }.getOrNull()
    }

    private fun cleanPlainText(text: String): String {
        return text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
}
