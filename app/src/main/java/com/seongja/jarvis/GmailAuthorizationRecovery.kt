package com.seongja.jarvis

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Resolves the mailbox identity from Gmail itself.
 *
 * Google Identity Services may return a valid Gmail access token and granted
 * scopes without attaching an email address to toGoogleSignInAccount(). The
 * Gmail profile endpoint is the authoritative identity for this authorization.
 */
object GmailAuthorizationRecovery {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun profile(accessToken: String): GmailProfile {
        require(accessToken.isNotBlank()) { "Google returned no Gmail access token." }
        val request = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/profile")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("").ifBlank { response.message }
                throw GmailApiException("Gmail profile HTTP ${response.code}: ${detail.take(220)}")
            }
            val root = JSONObject(raw)
            val email = root.optString("emailAddress").trim()
            require(email.isNotBlank()) { "Gmail profile did not return a mailbox address." }
            GmailProfile(
                emailAddress = email,
                messagesTotal = root.optLong("messagesTotal", 0L),
                threadsTotal = root.optLong("threadsTotal", 0L)
            )
        }
    }
}
