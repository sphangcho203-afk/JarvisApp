package com.seongja.jarvis

import android.content.Context
import android.util.Base64
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Tasks
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

class GmailAccessRequiredException(message: String) : Exception(message)
class GmailApiException(message: String) : Exception(message)

data class GmailProfile(
    val emailAddress: String,
    val messagesTotal: Long,
    val threadsTotal: Long
)

data class GmailMessageSummary(
    val id: String,
    val from: String,
    val subject: String,
    val date: String,
    val snippet: String,
    val unread: Boolean
)

data class GmailExecutionResult(
    val spoken: String,
    val display: String,
    val trace: List<String>,
    val confidence: Float = .98f
)

sealed interface GmailCommand {
    data object Authorize : GmailCommand
    data object Status : GmailCommand
    data class Inbox(val unreadOnly: Boolean = false, val limit: Int = 5) : GmailCommand
    data class Search(val query: String, val limit: Int = 6) : GmailCommand
    data class Send(val to: String, val subject: String, val body: String) : GmailCommand
    data class Draft(val to: String, val subject: String, val body: String) : GmailCommand
}

object GmailCommandParser {
    private val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)

    fun parse(raw: String): GmailCommand? {
        val clean = raw.trim()
        val lower = clean.lowercase(Locale.US)
        if (!Regex("\\b(gmail|email|mailbox|inbox|emails)\\b").containsMatchIn(lower)) return null

        if (listOf("authorize", "authorise", "connect", "sign in", "login", "grant access").any(lower::contains)) {
            return GmailCommand.Authorize
        }
        if (lower.contains("gmail status") || lower.contains("mailbox status") || lower.contains("email status")) {
            return GmailCommand.Status
        }

        parseCompose(clean, lower, draft = true)?.let { return it }
        parseCompose(clean, lower, draft = false)?.let { return it }

        val searchPrefixes = listOf(
            "search gmail for ", "search my gmail for ", "search email for ",
            "find emails about ", "find email about ", "find mail about ",
            "look in gmail for ", "look through gmail for "
        )
        searchPrefixes.firstOrNull(lower::startsWith)?.let { prefix ->
            clean.drop(prefix.length).trim().takeIf(String::isNotBlank)?.let {
                return GmailCommand.Search(it)
            }
        }

        if (lower.contains("unread") && listOf("gmail", "email", "inbox", "mailbox").any(lower::contains)) {
            return GmailCommand.Inbox(unreadOnly = true)
        }
        if (listOf("latest", "recent", "inbox", "summarize", "summarise", "show", "read", "check").any(lower::contains)) {
            return GmailCommand.Inbox(unreadOnly = false)
        }
        return null
    }

    private fun parseCompose(clean: String, lower: String, draft: Boolean): GmailCommand? {
        val verbMatches = if (draft) {
            lower.startsWith("draft email") || lower.startsWith("compose email") || lower.startsWith("write an email")
        } else {
            lower.startsWith("send email") || lower.startsWith("send an email") || lower.startsWith("email ")
        }
        if (!verbMatches) return null
        val address = emailPattern.find(clean)?.value ?: return null
        val messageMarker = listOf(" saying ", " message ", " body ", " that says ")
            .map { marker -> marker to lower.indexOf(marker) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
        val body = messageMarker?.let { clean.substring(it.second + it.first.length).trim() }
            .orEmpty()
            .ifBlank { "Hello." }
        val subjectMatch = Regex("(?i)\\bsubject\\s+(.+?)(?=\\s+(?:saying|message|body|that says)\\s+|$)")
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val subject = subjectMatch.orEmpty().ifBlank { "Message from F.R.I.D.A.Y." }
        return if (draft) {
            GmailCommand.Draft(address, subject, body)
        } else {
            GmailCommand.Send(address, subject, body)
        }
    }
}

class GmailApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val authStore = GmailAuthStore(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    fun profile(): GmailProfile {
        val token = acquireAccessToken()
        return profileWithToken(token)
    }

    private fun profileWithToken(token: String): GmailProfile {
        val root = getJson("$BASE/users/me/profile", token)
        return GmailProfile(
            emailAddress = root.optString("emailAddress").trim(),
            messagesTotal = root.optLong("messagesTotal", 0L),
            threadsTotal = root.optLong("threadsTotal", 0L)
        ).also {
            if (it.emailAddress.isNotBlank()) {
                authStore.recordAuthorized(it.emailAddress, GmailScopes.requiredUris)
            }
        }
    }

    fun execute(command: GmailCommand): GmailExecutionResult = when (command) {
        GmailCommand.Authorize -> throw GmailAccessRequiredException("Open Gmail authorization to grant account consent.")
        GmailCommand.Status -> statusResult()
        is GmailCommand.Inbox -> inboxResult(command)
        is GmailCommand.Search -> searchResult(command)
        is GmailCommand.Send -> sendResult(command)
        is GmailCommand.Draft -> draftResult(command)
    }

    private fun statusResult(): GmailExecutionResult {
        val profile = profile()
        return GmailExecutionResult(
            spoken = "Gmail is authorized for ${profile.emailAddress}, Sir.",
            display = buildString {
                appendLine("GMAIL // AUTHORIZED")
                appendLine("ACCOUNT // ${profile.emailAddress}")
                appendLine("MESSAGES // ${profile.messagesTotal}")
                append("THREADS // ${profile.threadsTotal}")
            },
            trace = listOf("gmail_oauth=authorized", "gmail_profile=http_200")
        )
    }

    private fun inboxResult(command: GmailCommand.Inbox): GmailExecutionResult {
        val query = if (command.unreadOnly) "is:unread in:inbox" else "in:inbox"
        val messages = listMessages(query, command.limit)
        val label = if (command.unreadOnly) "UNREAD" else "LATEST"
        val spoken = when {
            messages.isEmpty() -> "There are no ${label.lowercase(Locale.US)} inbox messages to report, Sir."
            messages.size == 1 -> "I found one ${label.lowercase(Locale.US)} message, from ${spokenSender(messages.first().from)}, about ${messages.first().subject.ifBlank { "no subject" }}."
            else -> "I found ${messages.size} ${label.lowercase(Locale.US)} messages. The newest is from ${spokenSender(messages.first().from)}, about ${messages.first().subject.ifBlank { "no subject" }}."
        }
        return GmailExecutionResult(
            spoken = spoken,
            display = formatMessages("GMAIL // $label INBOX", messages),
            trace = listOf("gmail_messages_list", "query=$query", "results=${messages.size}")
        )
    }

    private fun searchResult(command: GmailCommand.Search): GmailExecutionResult {
        val messages = listMessages(command.query, command.limit)
        val spoken = if (messages.isEmpty()) {
            "I found no Gmail messages matching ${command.query}, Sir."
        } else {
            "I found ${messages.size} Gmail messages matching ${command.query}. The first is from ${spokenSender(messages.first().from)}, about ${messages.first().subject.ifBlank { "no subject" }}."
        }
        return GmailExecutionResult(
            spoken = spoken,
            display = formatMessages("GMAIL SEARCH // ${command.query.uppercase(Locale.US).take(72)}", messages),
            trace = listOf("gmail_messages_search", "query=${command.query.take(120)}", "results=${messages.size}")
        )
    }

    private fun sendResult(command: GmailCommand.Send): GmailExecutionResult {
        requireValidRecipient(command.to)
        val token = acquireAccessToken()
        val raw = encodeMime(command.to, command.subject, command.body)
        val response = postJson(
            "$BASE/users/me/messages/send",
            JSONObject().put("raw", raw),
            token
        )
        val id = response.optString("id").trim()
        return GmailExecutionResult(
            spoken = "Email sent to ${command.to}, Sir.",
            display = "GMAIL // MESSAGE SENT\nTO // ${command.to}\nSUBJECT // ${command.subject}\nMESSAGE ID // ${id.ifBlank { "CONFIRMED" }}",
            trace = listOf("gmail_send=http_200", "recipient=${command.to}", "message_id=${id.take(32)}")
        )
    }

    private fun draftResult(command: GmailCommand.Draft): GmailExecutionResult {
        requireValidRecipient(command.to)
        val token = acquireAccessToken()
        val raw = encodeMime(command.to, command.subject, command.body)
        val response = postJson(
            "$BASE/users/me/drafts",
            JSONObject().put("message", JSONObject().put("raw", raw)),
            token
        )
        val id = response.optString("id").trim()
        return GmailExecutionResult(
            spoken = "A Gmail draft is ready for ${command.to}, Sir.",
            display = "GMAIL // DRAFT CREATED\nTO // ${command.to}\nSUBJECT // ${command.subject}\nDRAFT ID // ${id.ifBlank { "CONFIRMED" }}",
            trace = listOf("gmail_draft=http_200", "recipient=${command.to}", "draft_id=${id.take(32)}")
        )
    }

    private fun listMessages(query: String, limit: Int): List<GmailMessageSummary> {
        val token = acquireAccessToken()
        val listUrl = "$BASE/users/me/messages".toHttpUrl().newBuilder()
            .addQueryParameter("maxResults", limit.coerceIn(1, 12).toString())
            .addQueryParameter("q", query)
            .build()
        val root = getJson(listUrl.toString(), token)
        val array = root.optJSONArray("messages") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val id = array.optJSONObject(index)?.optString("id").orEmpty()
                if (id.isBlank()) continue
                runCatching { messageMetadata(id, token) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun messageMetadata(id: String, token: String): GmailMessageSummary {
        val url = "$BASE/users/me/messages/$id".toHttpUrl().newBuilder()
            .addQueryParameter("format", "metadata")
            .addQueryParameter("metadataHeaders", "From")
            .addQueryParameter("metadataHeaders", "Subject")
            .addQueryParameter("metadataHeaders", "Date")
            .build()
        val root = getJson(url.toString(), token)
        val headers = root.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
        fun header(name: String): String {
            for (index in 0 until headers.length()) {
                val item = headers.optJSONObject(index) ?: continue
                if (item.optString("name").equals(name, ignoreCase = true)) {
                    return item.optString("value").replace(Regex("\\s+"), " ").trim().take(220)
                }
            }
            return ""
        }
        val labels = root.optJSONArray("labelIds") ?: JSONArray()
        var unread = false
        for (index in 0 until labels.length()) {
            if (labels.optString(index) == "UNREAD") unread = true
        }
        return GmailMessageSummary(
            id = id,
            from = header("From"),
            subject = header("Subject"),
            date = header("Date"),
            snippet = root.optString("snippet").replace(Regex("\\s+"), " ").trim().take(360),
            unread = unread
        )
    }

    private fun acquireAccessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(GmailScopes.required)
            .build()
        val result = runCatching {
            Tasks.await(
                Identity.getAuthorizationClient(appContext).authorize(request),
                25,
                TimeUnit.SECONDS
            )
        }.getOrElse { error ->
            authStore.recordFailure(error.message ?: error.javaClass.simpleName)
            throw GmailAccessRequiredException("Gmail authorization failed: ${safeError(error)}")
        }
        if (result.hasResolution()) {
            throw GmailAccessRequiredException("Gmail consent is required. Say authorize Gmail.")
        }
        val token = result.accessToken.orEmpty()
        val granted = result.grantedScopes.orEmpty().toSet()
        if (token.isBlank() || !GmailScopes.requiredUris.all(granted::contains)) {
            throw GmailAccessRequiredException("Gmail authorization is incomplete. Say authorize Gmail.")
        }

        val returnedAccount = result.toGoogleSignInAccount()?.email.orEmpty()
        val storedAccount = authStore.load().accountEmail
        val account = returnedAccount.ifBlank { storedAccount }
        if (account.isNotBlank()) {
            authStore.recordAuthorized(account, granted)
        } else {
            val recovered = profileWithToken(token)
            authStore.recordAuthorized(recovered.emailAddress, granted)
        }
        return token
    }

    private fun getJson(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return executeJson(request)
    }

    private fun postJson(url: String, body: JSONObject, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = runCatching {
                val error = JSONObject(raw).optJSONObject("error")
                error?.optString("message").orEmpty()
            }.getOrDefault("").ifBlank { response.message }
            if (response.code == 401 || response.code == 403) {
                authStore.recordFailure("HTTP ${response.code}: $detail")
            }
            throw GmailApiException("Gmail HTTP ${response.code}: ${detail.take(220)}")
        }
        if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun encodeMime(to: String, subject: String, body: String): String {
        val cleanTo = to.replace(Regex("[\\r\\n]"), "").trim()
        val cleanSubject = subject.replace(Regex("[\\r\\n]"), " ").trim().take(180)
        val mime = buildString {
            append("To: ").append(cleanTo).append("\r\n")
            append("Subject: ").append(cleanSubject).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 8bit\r\n\r\n")
            append(body.trim())
        }
        return Base64.encodeToString(
            mime.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun requireValidRecipient(value: String) {
        if (!Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE).matches(value)) {
            throw GmailApiException("A valid recipient email address is required.")
        }
    }

    private fun formatMessages(title: String, messages: List<GmailMessageSummary>): String = buildString {
        appendLine(title)
        if (messages.isEmpty()) {
            append("NO MATCHING MESSAGES")
            return@buildString
        }
        messages.forEachIndexed { index, message ->
            appendLine()
            appendLine("${index + 1}. ${if (message.unread) "UNREAD // " else ""}${message.subject.ifBlank { "(NO SUBJECT)" }}")
            appendLine("FROM // ${message.from.ifBlank { "UNKNOWN" }}")
            if (message.date.isNotBlank()) appendLine("DATE // ${message.date}")
            append(message.snippet.ifBlank { "No preview available." })
            if (index != messages.lastIndex) appendLine()
        }
    }.trim()

    private fun spokenSender(value: String): String = value
        .substringBefore('<')
        .trim()
        .trim('"')
        .ifBlank { value.substringAfter('<').substringBefore('>').ifBlank { "an unknown sender" } }
        .take(80)

    private fun safeError(error: Throwable): String = (error.message ?: error.javaClass.simpleName)
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer [redacted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(220)

    companion object {
        private const val BASE = "https://gmail.googleapis.com/gmail/v1"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
