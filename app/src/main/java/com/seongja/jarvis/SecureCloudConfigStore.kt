package com.seongja.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudConfig(
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    fun isConfigured(): Boolean = endpoint.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Jarvis, Seongja's capable phone assistant. Respond clearly, intelligently, and concisely. " +
                "Use the supplied memory only when relevant. Never claim an action succeeded unless the Android app confirms it."
    }
}

class SecureCloudConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CloudConfig = CloudConfig(
        endpoint = decrypt(KEY_ENDPOINT),
        model = decrypt(KEY_MODEL),
        apiKey = decrypt(KEY_API_KEY),
        systemPrompt = decrypt(KEY_SYSTEM_PROMPT).ifBlank { CloudConfig.DEFAULT_SYSTEM_PROMPT }
    )

    fun save(config: CloudConfig) {
        prefs.edit()
            .putString(KEY_ENDPOINT, encrypt(config.endpoint.trim()))
            .putString(KEY_MODEL, encrypt(config.model.trim()))
            .putString(KEY_API_KEY, encrypt(config.apiKey.trim()))
            .putString(KEY_SYSTEM_PROMPT, encrypt(config.systemPrompt.trim().ifBlank { CloudConfig.DEFAULT_SYSTEM_PROMPT }))
            .apply()
    }

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

    private fun decrypt(key: String): String {
        val encoded = prefs.getString(key, "").orEmpty()
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
        private const val PREFS_NAME = "jarvis_cloud_config_encrypted"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_ALIAS = "jarvis_cloud_config_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
