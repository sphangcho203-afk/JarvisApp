package com.seongja.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GmailAuthState(
    val accountEmail: String = "",
    val grantedScopes: Set<String> = emptySet(),
    val authorizedAtMs: Long = 0L,
    val lastVerifiedAtMs: Long = 0L,
    val lastError: String = ""
) {
    fun isAuthorized(): Boolean =
        accountEmail.isNotBlank() && GmailScopes.requiredUris.all(grantedScopes::contains)

    fun statusLabel(): String = when {
        isAuthorized() -> "AUTHORIZED // $accountEmail"
        lastError.isNotBlank() -> "AUTH ERROR // ${lastError.take(120)}"
        else -> "CONSENT REQUIRED"
    }
}

object GmailScopes {
    const val MODIFY = "https://www.googleapis.com/auth/gmail.modify"
    const val SEND = "https://www.googleapis.com/auth/gmail.send"

    val requiredUris: List<String> = listOf(MODIFY, SEND)
    val required: List<Scope> = requiredUris.map(::Scope)
}

class GmailAuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): GmailAuthState {
        val raw = decrypt(prefs.getString(KEY_STATE, "").orEmpty())
        if (raw.isBlank()) return GmailAuthState()
        return runCatching {
            val root = JSONObject(raw)
            val scopesArray = root.optJSONArray("grantedScopes") ?: JSONArray()
            val scopes = buildSet {
                for (index in 0 until scopesArray.length()) {
                    scopesArray.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
            GmailAuthState(
                accountEmail = root.optString("accountEmail").trim(),
                grantedScopes = scopes,
                authorizedAtMs = root.optLong("authorizedAtMs", 0L),
                lastVerifiedAtMs = root.optLong("lastVerifiedAtMs", 0L),
                lastError = root.optString("lastError").take(240)
            )
        }.getOrElse { GmailAuthState(lastError = "Encrypted authorization state could not be read.") }
    }

    @Synchronized
    fun recordAuthorized(email: String, scopes: Collection<String>) {
        val now = System.currentTimeMillis()
        save(
            GmailAuthState(
                accountEmail = email.trim(),
                grantedScopes = scopes.map(String::trim).filter(String::isNotBlank).toSet(),
                authorizedAtMs = load().authorizedAtMs.takeIf { it > 0L } ?: now,
                lastVerifiedAtMs = now,
                lastError = ""
            )
        )
    }

    @Synchronized
    fun recordFailure(message: String) {
        save(load().copy(lastError = redact(message), lastVerifiedAtMs = System.currentTimeMillis()))
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun save(state: GmailAuthState) {
        val root = JSONObject().apply {
            put("accountEmail", state.accountEmail)
            put("grantedScopes", JSONArray(state.grantedScopes.toList()))
            put("authorizedAtMs", state.authorizedAtMs)
            put("lastVerifiedAtMs", state.lastVerifiedAtMs)
            put("lastError", state.lastError.take(240))
        }
        prefs.edit().putString(KEY_STATE, encrypt(root.toString())).apply()
    }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(access[_ -]?token|authorization)\\s*[:=]\\s*[^\\s,;]+")) {
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
        private const val PREFS_NAME = "friday_gmail_auth_encrypted"
        private const val KEY_STATE = "state"
        private const val KEY_ALIAS = "friday_gmail_auth_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
