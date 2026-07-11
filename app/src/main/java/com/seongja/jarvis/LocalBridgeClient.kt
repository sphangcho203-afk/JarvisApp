package com.seongja.jarvis

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class BridgePairResult(
    val ok: Boolean,
    val message: String
)

internal data class LocalBridgeDecision(
    val reply: String,
    val intent: String,
    val arguments: String,
    val executionOk: Boolean,
    val elapsedMs: Long,
    val raw: String
)

internal class LocalBridgeClient(
    context: Context,
    private val baseUrl: String = "http://127.0.0.1:8765"
) {
    private val preferences = context.getSharedPreferences(
        "jarvis_local_bridge",
        Context.MODE_PRIVATE
    )

    @Volatile
    var lastError: String = "none"
        private set

    fun isPaired(): Boolean = !preferences.getString(TOKEN_KEY, null).isNullOrBlank()

    fun clearPairing() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }

    fun pair(code: String): BridgePairResult {
        lastError = "none"
        val normalizedCode = code.filter(Char::isDigit).take(6)

        if (normalizedCode.length != 6) {
            lastError = "Pair code must contain six digits."
            return BridgePairResult(false, lastError)
        }

        return runCatching {
            val response = post(
                path = "/pair",
                payload = JSONObject().put("code", normalizedCode),
                token = null,
                readTimeoutMs = 8_000
            )

            val envelope = JSONObject(response.body.ifBlank { "{}" })
            if (response.code !in 200..299 || !envelope.optBoolean("ok", false)) {
                val error = envelope.optString("error", "HTTP ${response.code}")
                throw IllegalStateException(error.replace('_', ' '))
            }

            val token = envelope.optString("token", "").trim()
            if (token.length < 32) {
                throw IllegalStateException("Bridge returned an invalid token")
            }

            preferences.edit().putString(TOKEN_KEY, token).apply()
            BridgePairResult(true, "Secure local bridge paired, Sir.")
        }.onFailure {
            lastError = (it.message ?: it.javaClass.simpleName).take(180)
        }.getOrElse {
            BridgePairResult(false, "Pairing failed: $lastError")
        }
    }

    fun ask(userInput: String): LocalBridgeDecision? {
        lastError = "none"
        val token = preferences.getString(TOKEN_KEY, null)

        if (token.isNullOrBlank()) {
            lastError = "pairing_required"
            return null
        }

        return runCatching {
            val response = post(
                path = "/command",
                payload = JSONObject()
                    .put("text", userInput.take(500))
                    .put("speak", false),
                token = token,
                readTimeoutMs = 120_000
            )

            if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                clearPairing()
                throw IllegalStateException("pairing_required")
            }

            val envelope = JSONObject(response.body.ifBlank { "{}" })
            if (response.code !in 200..299 || !envelope.optBoolean("ok", false)) {
                val error = envelope.optString("error", "HTTP ${response.code}")
                throw IllegalStateException(error.replace('_', ' '))
            }

            val decision = envelope.optJSONObject("decision")
                ?: throw IllegalStateException("Bridge response contained no decision")
            val execution = decision.optJSONObject("execution")
            val reply = decision.optString("response", "").trim()

            if (reply.isBlank()) {
                throw IllegalStateException("Bridge returned an empty response")
            }

            LocalBridgeDecision(
                reply = reply.take(900),
                intent = decision.optString("intent", "UNKNOWN").trim(),
                arguments = decision.optJSONObject("arguments")?.toString().orEmpty(),
                executionOk = execution?.optBoolean("ok", false) ?: false,
                elapsedMs = envelope.optLong("elapsed_ms", -1L),
                raw = response.body.take(2_000)
            )
        }.onFailure {
            lastError = (it.message ?: it.javaClass.simpleName).take(180)
        }.getOrNull()
    }

    private fun post(
        path: String,
        payload: JSONObject,
        token: String?,
        readTimeoutMs: Int
    ): HttpResponse {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2_500
            readTimeout = readTimeoutMs
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("X-Jarvis-Token", token)
            }
        }

        return try {
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { output ->
                output.write(bytes)
                output.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    companion object {
        private const val TOKEN_KEY = "bridge_token"
    }
}
