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

/** Stores the Cartesia key locally under Android Keystore AES-GCM encryption. */
class SecureVoiceRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): CartesiaVoiceSettings {
        val raw = decrypt(prefs.getString(KEY_SETTINGS, "").orEmpty())
        if (raw.isBlank()) return CartesiaVoiceSettings()
        return runCatching {
            val root = JSONObject(raw)
            CartesiaVoiceSettings(
                apiKey = root.optString("apiKey"),
                enabled = root.optBoolean("enabled", true),
                successes = root.optInt("successes", 0),
                failures = root.optInt("failures", 0),
                lastLatencyMs = root.optLong("lastLatencyMs", 0L),
                lastStatusCode = root.optInt("lastStatusCode", 0),
                lastError = root.optString("lastError")
            )
        }.getOrElse { CartesiaVoiceSettings() }
    }

    @Synchronized
    fun save(settings: CartesiaVoiceSettings) {
        val encoded = JSONObject().apply {
            put("apiKey", settings.apiKey.trim())
            put("enabled", settings.enabled)
            put("successes", settings.successes)
            put("failures", settings.failures)
            put("lastLatencyMs", settings.lastLatencyMs)
            put("lastStatusCode", settings.lastStatusCode)
            put("lastError", settings.lastError.take(240))
        }.toString()
        prefs.edit().putString(KEY_SETTINGS, encrypt(encoded)).apply()
    }

    @Synchronized
    fun recordSuccess(latencyMs: Long, statusCode: Int = 101) {
        val current = load()
        save(
            current.copy(
                successes = current.successes + 1,
                lastLatencyMs = latencyMs,
                lastStatusCode = statusCode,
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
                lastError = redact(message, current.apiKey)
            )
        )
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

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

    private fun redact(value: String, apiKey: String): String = value
        .replace(apiKey, "[redacted]", ignoreCase = false)
        .replace(Regex("(?i)x-api-key\\s*[:=]\\s*[^\\s,;]+"), "x-api-key=[redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)

    companion object {
        private const val PREFS_NAME = "jarvis_voice_mesh_encrypted"
        private const val KEY_SETTINGS = "cartesia"
        private const val KEY_ALIAS = "jarvis_voice_mesh_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
