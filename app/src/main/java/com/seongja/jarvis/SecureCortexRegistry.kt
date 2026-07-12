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

class SecureCortexRegistry(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): CortexRegistry {
        val raw = decrypt(prefs.getString(KEY_REGISTRY, "").orEmpty())
        if (raw.isBlank()) return CortexRegistry()

        return runCatching { decode(raw) }.getOrElse { CortexRegistry() }
    }

    @Synchronized
    fun save(registry: CortexRegistry) {
        val encoded = encode(normalize(registry))
        prefs.edit().putString(KEY_REGISTRY, encrypt(encoded)).apply()
    }

    @Synchronized
    fun updateProfile(updated: CortexProfile) {
        val current = load()
        val profiles = current.profiles.map { if (it.id == updated.id) updated else it }
        save(current.copy(profiles = profiles))
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun normalize(registry: CortexRegistry): CortexRegistry {
        val current = registry.profiles.associateBy { it.id }
        val normalized = CortexDefaults.profiles().map { default ->
            current[default.id]?.copy(
                id = default.id,
                label = default.label,
                provider = default.provider
            ) ?: default
        }
        return registry.copy(
            profiles = normalized,
            systemPrompt = registry.systemPrompt.trim().ifBlank { CortexRegistry.DEFAULT_SYSTEM_PROMPT }
        )
    }

    private fun encode(registry: CortexRegistry): String = JSONObject().apply {
        put("systemPrompt", registry.systemPrompt)
        put("profiles", JSONArray().apply {
            registry.profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("id", profile.id)
                    put("label", profile.label)
                    put("provider", profile.provider.name)
                    put("model", profile.model)
                    put("apiKey", profile.apiKey)
                    put("enabled", profile.enabled)
                    put("priority", profile.priority)
                    put("successes", profile.successes)
                    put("failures", profile.failures)
                    put("failureStreak", profile.failureStreak)
                    put("requestCount", profile.requestCount)
                    put("lastLatencyMs", profile.lastLatencyMs)
                    put("lastUsedAtMs", profile.lastUsedAtMs)
                    put("cooldownUntilMs", profile.cooldownUntilMs)
                    put("lastStatusCode", profile.lastStatusCode)
                    put("lastError", profile.lastError)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): CortexRegistry {
        val root = JSONObject(raw)
        val array = root.optJSONArray("profiles") ?: JSONArray()
        val profiles = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val provider = runCatching {
                    CortexProvider.valueOf(item.optString("provider"))
                }.getOrNull() ?: continue
                add(
                    CortexProfile(
                        id = item.optString("id"),
                        label = item.optString("label"),
                        provider = provider,
                        model = item.optString("model"),
                        apiKey = item.optString("apiKey"),
                        enabled = item.optBoolean("enabled", true),
                        priority = item.optInt("priority", 5),
                        successes = item.optInt("successes", 0),
                        failures = item.optInt("failures", 0),
                        failureStreak = item.optInt("failureStreak", 0),
                        requestCount = item.optLong("requestCount", 0L),
                        lastLatencyMs = item.optLong("lastLatencyMs", 0L),
                        lastUsedAtMs = item.optLong("lastUsedAtMs", 0L),
                        cooldownUntilMs = item.optLong("cooldownUntilMs", 0L),
                        lastStatusCode = item.optInt("lastStatusCode", 0),
                        lastError = item.optString("lastError")
                    )
                )
            }
        }

        return normalize(
            CortexRegistry(
                profiles = profiles,
                systemPrompt = root.optString("systemPrompt").ifBlank { CortexRegistry.DEFAULT_SYSTEM_PROMPT }
            )
        )
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
        private const val PREFS_NAME = "jarvis_cortex_mesh_encrypted"
        private const val KEY_REGISTRY = "registry"
        private const val KEY_ALIAS = "jarvis_cortex_mesh_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
