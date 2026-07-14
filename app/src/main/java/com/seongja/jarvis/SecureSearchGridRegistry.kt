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

class SecureSearchGridRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): SearchGridRegistry {
        val raw = decrypt(prefs.getString(KEY_REGISTRY, "").orEmpty())
        if (raw.isBlank()) return SearchGridRegistry()
        return runCatching { decode(raw) }.getOrElse { SearchGridRegistry() }
    }

    @Synchronized
    fun save(registry: SearchGridRegistry) {
        val normalized = normalize(registry)
        prefs.edit()
            .putString(KEY_REGISTRY, encrypt(encode(normalized)))
            .apply()
    }

    @Synchronized
    fun updateCredential(updated: SearchGridCredential) {
        val current = load()
        val credentials = current.credentials.map {
            if (it.provider == updated.provider) updated else it
        }
        save(current.copy(credentials = credentials))
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun normalize(registry: SearchGridRegistry): SearchGridRegistry {
        val byProvider = registry.credentials.associateBy { it.provider }
        return SearchGridRegistry(
            credentials = SearchGridProvider.entries.map { provider ->
                byProvider[provider]?.copy(provider = provider)
                    ?: SearchGridCredential(provider = provider)
            }
        )
    }

    private fun encode(registry: SearchGridRegistry): String = JSONObject().apply {
        put("credentials", JSONArray().apply {
            registry.credentials.forEach { credential ->
                put(JSONObject().apply {
                    put("provider", credential.provider.name)
                    put("apiKey", credential.apiKey)
                    put("enabled", credential.enabled)
                    put("successes", credential.successes)
                    put("failures", credential.failures)
                    put("requestCount", credential.requestCount)
                    put("lastLatencyMs", credential.lastLatencyMs)
                    put("lastUsedAtMs", credential.lastUsedAtMs)
                    put("cooldownUntilMs", credential.cooldownUntilMs)
                    put("lastStatusCode", credential.lastStatusCode)
                    put("lastError", credential.lastError)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): SearchGridRegistry {
        val root = JSONObject(raw)
        val array = root.optJSONArray("credentials") ?: JSONArray()
        val credentials = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val provider = runCatching {
                    SearchGridProvider.valueOf(item.optString("provider"))
                }.getOrNull() ?: continue
                add(
                    SearchGridCredential(
                        provider = provider,
                        apiKey = item.optString("apiKey"),
                        enabled = item.optBoolean("enabled", true),
                        successes = item.optInt("successes", 0),
                        failures = item.optInt("failures", 0),
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
        return normalize(SearchGridRegistry(credentials = credentials))
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
        private const val PREFS_NAME = "jarvis_search_grid_encrypted"
        private const val KEY_REGISTRY = "registry"
        private const val KEY_ALIAS = "jarvis_search_grid_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
