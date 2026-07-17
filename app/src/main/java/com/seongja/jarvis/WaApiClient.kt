package com.seongja.jarvis

import android.os.SystemClock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WaApiStatusResult(
    val instanceId: Long,
    val clientState: String,
    val statusCode: Int,
    val elapsedMs: Long
)

data class WaApiSendResult(
    val instanceId: Long,
    val chatId: String,
    val statusCode: Int,
    val elapsedMs: Long,
    val messageId: String = ""
)

class WaApiClient(
    private val store: WaApiSecureStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isConfigured(): Boolean = store.load().isConfigured()

    fun statusLabel(): String = store.load().healthLabel()

    fun testConnection(): WaApiStatusResult {
        val settings = store.load()
        require(settings.enabled) { "WaAPI is disabled." }
        require(settings.apiToken.isNotBlank()) { "WaAPI bearer token is required." }
        val instanceId = resolveInstanceId(settings)
        val started = SystemClock.elapsedRealtime()
        val request = Request.Builder()
            .url("$BASE_URL/instances/$instanceId/client/status")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${settings.apiToken}")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return execute(request) { code, body ->
            val elapsed = SystemClock.elapsedRealtime() - started
            val root = parseJson(body)
            val state = findString(root, setOf("clientStatus", "status", "state"))
                .ifBlank { if (code in 200..299) "READY" else "UNKNOWN" }
            if (code !in 200..299) throw WaApiException(errorMessage(code, root, body), code)
            store.recordSuccess(elapsed, code, state, instanceId)
            WaApiStatusResult(instanceId, state, code, elapsed)
        }
    }

    fun sendText(recipient: String, message: String): WaApiSendResult {
        val settings = store.load()
        require(settings.enabled) { "WaAPI is disabled." }
        require(settings.apiToken.isNotBlank()) { "WaAPI bearer token is required." }
        require(message.isNotBlank()) { "WhatsApp message is empty." }
        val instanceId = resolveInstanceId(settings)
        val chatId = resolveChatId(instanceId, settings.apiToken, recipient)
        val payload = JSONObject()
            .put("chatId", chatId)
            .put("message", message.trim())
            .put("previewLink", true)
            .toString()
            .toRequestBody(JSON)
        val started = SystemClock.elapsedRealtime()
        val request = Request.Builder()
            .url("$BASE_URL/instances/$instanceId/client/action/send-message")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${settings.apiToken}")
            .header("User-Agent", USER_AGENT)
            .post(payload)
            .build()
        return execute(request) { code, body ->
            val elapsed = SystemClock.elapsedRealtime() - started
            val root = parseJson(body)
            if (code !in 200..299) throw WaApiException(errorMessage(code, root, body), code)
            val messageId = findString(root, setOf("messageId", "id", "_serialized"))
            store.recordSuccess(elapsed, code, "READY", instanceId)
            WaApiSendResult(instanceId, chatId, code, elapsed, messageId)
        }
    }

    private fun resolveInstanceId(settings: WaApiSettings): Long {
        if (settings.instanceId > 0L) return settings.instanceId
        val request = Request.Builder()
            .url("$BASE_URL/instances")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${settings.apiToken}")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return execute(request) { code, body ->
            val root = parseJson(body)
            if (code !in 200..299) throw WaApiException(errorMessage(code, root, body), code)
            val instanceId = findInstanceId(root)
                ?: throw WaApiException("WaAPI returned no connected instance. Pair an account in the WaAPI dashboard first.", code)
            val current = store.load()
            store.save(current.copy(instanceId = instanceId, lastError = "", lastStatusCode = code))
            instanceId
        }
    }

    private fun resolveChatId(instanceId: Long, token: String, recipient: String): String {
        val clean = recipient.trim()
        if (clean.endsWith("@c.us", true) || clean.endsWith("@g.us", true) || clean.endsWith("@newsletter", true)) {
            return clean
        }
        val number = normalizePhoneNumber(clean)
        require(number.length in 7..18) {
            "WaAPI direct sending needs an international phone number or a full chat ID."
        }
        val payload = JSONObject().put("number", number).toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/instances/$instanceId/client/action/get-number-id")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", USER_AGENT)
            .post(payload)
            .build()
        return execute(request) { code, body ->
            val root = parseJson(body)
            if (code !in 200..299) throw WaApiException(errorMessage(code, root, body), code)
            findString(root, setOf("chatId", "serialized", "_serialized", "id"))
                .takeIf { it.contains('@') }
                ?: "$number@c.us"
        }
    }

    private fun <T> execute(request: Request, transform: (Int, String) -> T): T {
        return try {
            client.newCall(request).execute().use { response ->
                transform(response.code, response.body?.string().orEmpty())
            }
        } catch (error: WaApiException) {
            store.recordFailure(error.message ?: "WaAPI request failed.", error.statusCode)
            throw error
        } catch (error: Throwable) {
            val message = "WaAPI network failure: ${error.message ?: error.javaClass.simpleName}"
            store.recordFailure(message)
            throw WaApiException(message, 0, error)
        }
    }

    private fun parseJson(raw: String): Any? = runCatching {
        val trimmed = raw.trim()
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> null
        }
    }.getOrNull()

    private fun findInstanceId(root: Any?): Long? {
        when (root) {
            is JSONArray -> {
                for (index in 0 until root.length()) {
                    findInstanceId(root.opt(index))?.let { return it }
                }
            }
            is JSONObject -> {
                listOf("id", "instanceId").forEach { key ->
                    val value = root.optLong(key, 0L)
                    if (value > 0L) return value
                }
                val keys = root.keys()
                while (keys.hasNext()) {
                    findInstanceId(root.opt(keys.next()))?.let { return it }
                }
            }
        }
        return null
    }

    private fun findString(root: Any?, candidates: Set<String>): String {
        when (root) {
            is JSONArray -> {
                for (index in 0 until root.length()) {
                    findString(root.opt(index), candidates).takeIf(String::isNotBlank)?.let { return it }
                }
            }
            is JSONObject -> {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = root.opt(key)
                    if (candidates.any { it.equals(key, true) } && value !is JSONObject && value !is JSONArray) {
                        value?.toString()?.takeIf(String::isNotBlank)?.let { return it }
                    }
                }
                val nestedKeys = root.keys()
                while (nestedKeys.hasNext()) {
                    findString(root.opt(nestedKeys.next()), candidates).takeIf(String::isNotBlank)?.let { return it }
                }
            }
        }
        return ""
    }

    private fun errorMessage(code: Int, root: Any?, raw: String): String {
        val detail = findString(root, setOf("message", "error", "detail", "reason"))
            .ifBlank { raw.replace(Regex("\\s+"), " ").trim().take(220) }
        return "WaAPI HTTP $code${if (detail.isNotBlank()) ": $detail" else ""}"
    }

    companion object {
        const val BASE_URL = "https://waapi.app/api/v1"
        private const val USER_AGENT = "Friday-Android/0.9.26"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun normalizePhoneNumber(raw: String): String {
            var value = raw.trim().replace(Regex("[^0-9+]"), "")
            while (value.startsWith("00")) value = value.drop(2)
            value = value.removePrefix("+")
            return value.filter(Char::isDigit)
        }

        fun supportsDirectRecipient(raw: String): Boolean {
            val clean = raw.trim()
            if (clean.endsWith("@c.us", true) || clean.endsWith("@g.us", true) || clean.endsWith("@newsletter", true)) return true
            return normalizePhoneNumber(clean).length in 7..18 && clean.any(Char::isDigit)
        }
    }
}

class WaApiException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
