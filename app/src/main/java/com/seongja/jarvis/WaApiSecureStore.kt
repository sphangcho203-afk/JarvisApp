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

data class WaApiSettings(
    val apiToken: String = "",
    val instanceId: Long = 0L,
    val enabled: Boolean = true,
    val successes: Int = 0,
    val failures: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastClientState: String = "",
    val lastError: String = ""
) {
    fun isConfigured(): Boolean = enabled && apiToken.isNotBlank()

    fun healthLabel(): String = when {
        !enabled -> "DISABLED"
        apiToken.isBlank() -> "TOKEN REQUIRED"
        lastStatusCode in 200..299 && lastClientState.isNotBlank() ->
            "${lastClientState.uppercase()} ${lastLatencyMs}ms"
        lastStatusCode in 200..299 -> "ONLINE ${lastLatencyMs}ms"
        lastError.isNotBlank() -> "ERROR ${lastStatusCode.takeIf { it > 0 } ?: "NET"}"
        instanceId > 0L -> "READY // INSTANCE $instanceId"
        else -> "READY // DISCOVERY REQUIRED"
    }
}

class WaApiSecureStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): WaApiSettings {
        val raw = decrypt(prefs.getString(KEY_SETTINGS, "").orEmpty())
        if (raw.isBlank()) return WaApiSettings()
        return runCatching {
            val root = JSONObject(raw)
            WaApiSettings(
                apiToken = root.optString("apiToken"),
                instanceId = root.optLong("instanceId", 0L),
                enabled = root.optBoolean("enabled", true),
                successes = root.optInt("successes", 0),
                failures = root.optInt("failures", 0),
                lastLatencyMs = root.optLong("lastLatencyMs", 0L),
                lastStatusCode = root.optInt("lastStatusCode", 0),
                lastClientState = root.optString("lastClientState"),
                lastError = root.optString("lastError")
            )
        }.getOrElse { WaApiSettings() }
    }

    @Synchronized
    fun save(settings: WaApiSettings) {
        val root = JSONObject().apply {
            put("apiToken", settings.apiToken.trim())
            put("instanceId", settings.instanceId.coerceAtLeast(0L))
            put("enabled", settings.enabled)
            put("successes", settings.successes)
            put("failures", settings.failures)
            put("lastLatencyMs", settings.lastLatencyMs)
            put("lastStatusCode", settings.lastStatusCode)
            put("lastClientState", settings.lastClientState.take(80))
            put("lastError", settings.lastError.take(240))
        }
        prefs.edit().putString(KEY_SETTINGS, encrypt(root.toString())).apply()
    }

    @Synchronized
    fun recordSuccess(
        latencyMs: Long,
        statusCode: Int,
        clientState: String = "",
        resolvedInstanceId: Long? = null
    ) {
        val current = load()
        save(
            current.copy(
                instanceId = resolvedInstanceId?.takeIf { it > 0L } ?: current.instanceId,
                successes = current.successes + 1,
                lastLatencyMs = latencyMs.coerceAtLeast(0L),
                lastStatusCode = statusCode,
                lastClientState = clientState.trim(),
                lastError = ""
            )
        )
    }

    @Synchronized
    fun recordFailure(message: String, statusCode: Int = 0) {
        val current = load()
        save(
            current.copy(
                failures = current.failures + 1,
                lastStatusCode = statusCode,
                lastError = redact(message, current.apiToken)
            )
        )
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun redact(value: String, secret: String): String = value
        .replace(secret, "[redacted]", ignoreCase = false)
        .replace(Regex("(?i)(token|authorization|bearer)\\s*[:=]?\\s*[^\\s,;]+")) {
            "${it.groupValues[1]}=[redacted]"
        }
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
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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
        private const val PREFS_NAME = "friday_waapi_encrypted"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_ALIAS = "friday_waapi_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
