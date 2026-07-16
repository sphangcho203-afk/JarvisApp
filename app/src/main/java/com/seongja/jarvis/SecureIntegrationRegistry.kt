package com.seongja.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class IntegrationSettings(
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = "deepseek-chat",
    val deepSeekEnabled: Boolean = true,
    val deepSeekSuccesses: Int = 0,
    val deepSeekFailures: Int = 0,
    val deepSeekLastLatencyMs: Long = 0L,
    val deepSeekLastStatusCode: Int = 0,
    val deepSeekLastError: String = "",
    val youtubeApiKey: String = "",
    val youtubeEnabled: Boolean = true,
    val youtubeSuccesses: Int = 0,
    val youtubeFailures: Int = 0,
    val youtubeLastLatencyMs: Long = 0L,
    val youtubeLastStatusCode: Int = 0,
    val youtubeLastError: String = "",
    val gmailApiKey: String = "",
    val gmailOAuthClientId: String = "",
    val gmailEnabled: Boolean = true
) {
    fun isDeepSeekConfigured(): Boolean =
        deepSeekEnabled && deepSeekApiKey.isNotBlank() && deepSeekModel.isNotBlank()

    fun isYouTubeConfigured(): Boolean = youtubeEnabled && youtubeApiKey.isNotBlank()

    fun deepSeekHealthLabel(): String = when {
        !deepSeekEnabled -> "DISABLED"
        deepSeekApiKey.isBlank() -> "NOT CONFIGURED"
        deepSeekLastStatusCode in 200..299 -> "ONLINE ${deepSeekLastLatencyMs}ms"
        deepSeekLastError.isNotBlank() ->
            "ERROR ${deepSeekLastStatusCode.takeIf { it > 0 } ?: "NET"}"
        else -> "READY"
    }

    fun youtubeHealthLabel(): String = when {
        !youtubeEnabled -> "DISABLED"
        youtubeApiKey.isBlank() -> "NOT CONFIGURED"
        youtubeLastStatusCode in 200..299 -> "ONLINE ${youtubeLastLatencyMs}ms"
        youtubeLastError.isNotBlank() ->
            "ERROR ${youtubeLastStatusCode.takeIf { it > 0 } ?: "NET"}"
        else -> "READY"
    }

    fun gmailHealthLabel(): String = when {
        !gmailEnabled -> "DISABLED"
        gmailApiKey.isBlank() && gmailOAuthClientId.isBlank() -> "NOT CONFIGURED"
        gmailOAuthClientId.isBlank() -> "PROJECT KEY STORED // OAUTH REQUIRED"
        else -> "OAUTH CLIENT READY // USER CONSENT REQUIRED"
    }
}

/** Android-Keystore encrypted storage for optional provider credentials. */
class SecureIntegrationRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): IntegrationSettings {
        val raw = decrypt(prefs.getString(KEY_SETTINGS, "").orEmpty())
        if (raw.isBlank()) return IntegrationSettings()
        return runCatching {
            val root = JSONObject(raw)
            IntegrationSettings(
                deepSeekApiKey = root.optString("deepSeekApiKey"),
                deepSeekModel = root.optString("deepSeekModel", "deepseek-chat")
                    .ifBlank { "deepseek-chat" },
                deepSeekEnabled = root.optBoolean("deepSeekEnabled", true),
                deepSeekSuccesses = root.optInt("deepSeekSuccesses", 0),
                deepSeekFailures = root.optInt("deepSeekFailures", 0),
                deepSeekLastLatencyMs = root.optLong("deepSeekLastLatencyMs", 0L),
                deepSeekLastStatusCode = root.optInt("deepSeekLastStatusCode", 0),
                deepSeekLastError = root.optString("deepSeekLastError"),
                youtubeApiKey = root.optString("youtubeApiKey"),
                youtubeEnabled = root.optBoolean("youtubeEnabled", true),
                youtubeSuccesses = root.optInt("youtubeSuccesses", 0),
                youtubeFailures = root.optInt("youtubeFailures", 0),
                youtubeLastLatencyMs = root.optLong("youtubeLastLatencyMs", 0L),
                youtubeLastStatusCode = root.optInt("youtubeLastStatusCode", 0),
                youtubeLastError = root.optString("youtubeLastError"),
                gmailApiKey = root.optString("gmailApiKey"),
                gmailOAuthClientId = root.optString("gmailOAuthClientId"),
                gmailEnabled = root.optBoolean("gmailEnabled", true)
            )
        }.getOrElse { IntegrationSettings() }
    }

    @Synchronized
    fun save(settings: IntegrationSettings) {
        val root = JSONObject().apply {
            put("deepSeekApiKey", settings.deepSeekApiKey.trim())
            put("deepSeekModel", settings.deepSeekModel.trim().ifBlank { "deepseek-chat" })
            put("deepSeekEnabled", settings.deepSeekEnabled)
            put("deepSeekSuccesses", settings.deepSeekSuccesses)
            put("deepSeekFailures", settings.deepSeekFailures)
            put("deepSeekLastLatencyMs", settings.deepSeekLastLatencyMs)
            put("deepSeekLastStatusCode", settings.deepSeekLastStatusCode)
            put("deepSeekLastError", settings.deepSeekLastError.take(240))
            put("youtubeApiKey", settings.youtubeApiKey.trim())
            put("youtubeEnabled", settings.youtubeEnabled)
            put("youtubeSuccesses", settings.youtubeSuccesses)
            put("youtubeFailures", settings.youtubeFailures)
            put("youtubeLastLatencyMs", settings.youtubeLastLatencyMs)
            put("youtubeLastStatusCode", settings.youtubeLastStatusCode)
            put("youtubeLastError", settings.youtubeLastError.take(240))
            put("gmailApiKey", settings.gmailApiKey.trim())
            put("gmailOAuthClientId", settings.gmailOAuthClientId.trim())
            put("gmailEnabled", settings.gmailEnabled)
        }
        prefs.edit().putString(KEY_SETTINGS, encrypt(root.toString())).apply()
    }

    @Synchronized
    fun recordDeepSeekSuccess(latencyMs: Long, statusCode: Int) {
        val current = load()
        save(
            current.copy(
                deepSeekSuccesses = current.deepSeekSuccesses + 1,
                deepSeekLastLatencyMs = latencyMs,
                deepSeekLastStatusCode = statusCode,
                deepSeekLastError = ""
            )
        )
    }

    @Synchronized
    fun recordDeepSeekFailure(message: String, statusCode: Int = 0) {
        val current = load()
        save(
            current.copy(
                deepSeekFailures = current.deepSeekFailures + 1,
                deepSeekLastStatusCode = statusCode,
                deepSeekLastError = redact(message, current.deepSeekApiKey)
            )
        )
    }

    @Synchronized
    fun recordYouTubeSuccess(latencyMs: Long, statusCode: Int) {
        val current = load()
        save(
            current.copy(
                youtubeSuccesses = current.youtubeSuccesses + 1,
                youtubeLastLatencyMs = latencyMs,
                youtubeLastStatusCode = statusCode,
                youtubeLastError = ""
            )
        )
    }

    @Synchronized
    fun recordYouTubeFailure(message: String, statusCode: Int = 0) {
        val current = load()
        save(
            current.copy(
                youtubeFailures = current.youtubeFailures + 1,
                youtubeLastStatusCode = statusCode,
                youtubeLastError = redact(message, current.youtubeApiKey)
            )
        )
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun redact(value: String, secret: String): String = value
        .replace(secret, "[redacted]", ignoreCase = false)
        .replace(Regex("(?i)(key|token|authorization)\\s*[:=]\\s*[^\\s,;]+"), "$1=[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + ciphertext.size)
        System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
        System.arraycopy(ciphertext, 0, payload, cipher.iv.size, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_SIZE)
            val iv = payload.copyOfRange(0, IV_SIZE)
            val ciphertext = payload.copyOfRange(IV_SIZE, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "friday_integrations_encrypted"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_ALIAS = "friday_integrations_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
