package com.seongja.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the Cartesia voice mesh locally under Android Keystore AES-GCM encryption. */
class SecureVoiceRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): CartesiaVoiceSettings {
        val raw = decrypt(prefs.getString(KEY_SETTINGS, "").orEmpty())
        if (raw.isBlank()) return CartesiaVoiceSettings()
        return runCatching {
            val root = JSONObject(raw)
            val backups = root.optJSONArray("backupApiKeys").toStringList()
            val cooldowns = root.optJSONArray("keyCooldownUntilMs").toLongList()
            CartesiaVoiceSettings(
                apiKey = root.optString("apiKey"),
                backupApiKeys = backups,
                enabled = root.optBoolean("enabled", true),
                activeKeyIndex = root.optInt("activeKeyIndex", 0),
                keyCooldownUntilMs = cooldowns,
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
        val normalized = settings.copy(
            apiKey = settings.apiKey.trim(),
            backupApiKeys = settings.backupApiKeys
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(CartesiaVoiceSettings.MAX_KEYS - 1),
            activeKeyIndex = settings.activeKeyIndex.coerceAtLeast(0),
            lastError = settings.lastError.take(240)
        )
        val encoded = JSONObject().apply {
            put("apiKey", normalized.apiKey)
            put("backupApiKeys", JSONArray(normalized.backupApiKeys))
            put("enabled", normalized.enabled)
            put("activeKeyIndex", normalized.activeKeyIndex)
            put("keyCooldownUntilMs", JSONArray(normalized.keyCooldownUntilMs))
            put("successes", normalized.successes)
            put("failures", normalized.failures)
            put("lastLatencyMs", normalized.lastLatencyMs)
            put("lastStatusCode", normalized.lastStatusCode)
            put("lastError", normalized.lastError)
        }.toString()
        prefs.edit().putString(KEY_SETTINGS, encrypt(encoded)).apply()
    }

    @Synchronized
    fun selectActiveKey(nowMs: Long = System.currentTimeMillis()): Pair<Int, String>? {
        val current = load()
        val selected = current.activeKey(nowMs) ?: return null
        if (selected.first != current.activeKeyIndex) {
            save(current.copy(activeKeyIndex = selected.first))
        }
        return selected
    }

    @Synchronized
    fun recordSuccess(keyIndex: Int, latencyMs: Long, statusCode: Int = 101) {
        val current = load()
        val cooldowns = current.normalizedCooldowns().toMutableList()
        if (keyIndex in cooldowns.indices) cooldowns[keyIndex] = 0L
        save(
            current.copy(
                activeKeyIndex = keyIndex,
                keyCooldownUntilMs = cooldowns,
                successes = current.successes + 1,
                lastLatencyMs = latencyMs,
                lastStatusCode = statusCode,
                lastError = ""
            )
        )
    }

    @Synchronized
    fun recordFailure(
        keyIndex: Int,
        message: String,
        statusCode: Int = 0,
        rotate: Boolean = true
    ): Pair<Int, String>? {
        val current = load()
        val keys = current.configuredKeys()
        val cooldowns = current.normalizedCooldowns().toMutableList()
        if (keyIndex in cooldowns.indices) {
            cooldowns[keyIndex] = System.currentTimeMillis() + cooldownFor(statusCode, message)
        }
        val next = if (rotate && keys.isNotEmpty()) {
            keys.indices
                .map { (keyIndex + 1 + it) % keys.size }
                .firstOrNull { candidate -> cooldowns.getOrElse(candidate) { 0L } <= System.currentTimeMillis() }
        } else {
            null
        }
        save(
            current.copy(
                activeKeyIndex = next ?: current.activeKeyIndex,
                keyCooldownUntilMs = cooldowns,
                failures = current.failures + 1,
                lastStatusCode = statusCode,
                lastError = redact(message, keys)
            )
        )
        return next?.let { it to keys[it] }
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun cooldownFor(statusCode: Int, message: String): Long {
        val lower = message.lowercase()
        return when {
            statusCode == 401 || statusCode == 403 -> 24 * 60 * 60_000L
            statusCode == 402 || statusCode == 429 ||
                "credit" in lower || "quota" in lower || "rate limit" in lower -> 30 * 60_000L
            statusCode >= 500 -> 90_000L
            else -> 30_000L
        }
    }

    private fun redact(value: String, apiKeys: List<String>): String {
        var clean = value
        apiKeys.filter(String::isNotBlank).forEach { key -> clean = clean.replace(key, "[redacted]") }
        return clean
            .replace(Regex("(?i)x-api-key\\s*[:=]\\s*[^\\s,;]+"), "x-api-key=[redacted]")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun JSONArray?.toLongList(): List<Long> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(optLong(index, 0L))
        }
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

    companion object {
        private const val PREFS_NAME = "jarvis_voice_mesh_encrypted"
        private const val KEY_SETTINGS = "cartesia"
        private const val KEY_ALIAS = "jarvis_voice_mesh_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
