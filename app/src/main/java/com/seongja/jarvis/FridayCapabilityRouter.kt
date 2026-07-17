package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import java.util.Locale

class FridayCapabilityRouter(context: Context) {
    private val appContext = context.applicationContext
    private val gmail = GmailApiClient(appContext)
    private val gmailAuth = GmailAuthStore(appContext)
    private val images = GeminiImageClient(appContext)
    private val waapi = WaApiCommandRouter(appContext)

    fun statusLabel(): String = buildString {
        append("Gmail ")
        append(if (gmailAuth.load().isAuthorized()) "authorized" else "consent")
        append(" // image ")
        append(if (images.isConfigured()) "ready" else "key required")
        append(" // WhatsApp ")
        append(waapi.statusLabel())
    }

    fun intercept(
        input: String,
        memorySummary: String,
        onToken: ((String) -> Unit)? = null
    ): BrainResponse? {
        waapi.intercept(input, memorySummary, onToken)?.let { return it }

        ImageCommandIntent.promptFor(input)?.let { prompt ->
            JarvisOperationBus.publish("IMAGE SYNTHESIS", "OPENING GEMINI VISUAL STUDIO", .08f)
            onToken?.invoke("Opening image synthesis, Sir. ")
            ImageGenerationActivity.launch(appContext, prompt)
            return BrainResponse(
                spoken = "Image synthesis is underway, Sir. The visual studio is open on screen.",
                display = buildString {
                    appendLine("IMAGE SYNTHESIS // GEMINI VISUAL STUDIO")
                    appendLine("PROMPT // ${prompt.take(320)}")
                    appendLine("ROUTE // AUTOMATIC IMAGE MODEL FAILOVER")
                    append("OUTPUT // PREVIEW + GALLERY + SHARE")
                },
                intent = "image/generate",
                confidence = 1f,
                mode = BrainMode.EXECUTING,
                trace = listOf(
                    "route=gemini_image_generation",
                    "models=${GeminiImageClient.IMAGE_MODELS.joinToString(">")}",
                    "prompt_chars=${prompt.length}",
                    "activity=image_generation_studio"
                ),
                memory = memorySummary,
                thoughts = listOf("The image prompt was routed to the dedicated visual synthesis surface."),
                entities = listOf("aspect_ratio=${GeminiImageClient.inferAspectRatio(prompt)}"),
                decision = "launch_image_synthesis",
                action = BrainAction()
            )
        }

        val command = GmailCommandParser.parse(input) ?: return null
        if (command == GmailCommand.Authorize) {
            launchGmailAuthorization()
            return authorizationResponse(memorySummary)
        }

        JarvisOperationBus.publish("GMAIL PRIVATE DATA", "ACQUIRING AUTHORIZED MAILBOX TOKEN", .12f)
        onToken?.invoke("Accessing Gmail, Sir. ")
        return runCatching { gmail.execute(command) }
            .fold(
                onSuccess = { result ->
                    JarvisOperationBus.publish("GMAIL OPERATION VERIFIED", result.trace.firstOrNull().orEmpty(), .94f)
                    BrainResponse(
                        spoken = result.spoken,
                        display = result.display,
                        intent = "gmail/${command.javaClass.simpleName.lowercase(Locale.US)}",
                        confidence = result.confidence,
                        mode = BrainMode.ONLINE,
                        trace = result.trace + "oauth=google_identity_services",
                        memory = memorySummary,
                        thoughts = listOf("Gmail data was handled through the user-authorized Google API scope set."),
                        entities = listOf("gmail_account=${gmailAuth.load().accountEmail}"),
                        decision = "execute_authorized_gmail_command",
                        action = BrainAction()
                    )
                },
                onFailure = { error ->
                    if (error is GmailAccessRequiredException) {
                        launchGmailAuthorization()
                        authorizationResponse(memorySummary, error.message)
                    } else {
                        val message = (error.message ?: error.javaClass.simpleName)
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(480)
                        JarvisOperationBus.publish("GMAIL OPERATION ERROR", message, 1f, false)
                        BrainResponse(
                            spoken = "The Gmail operation failed: $message",
                            display = "GMAIL // OPERATION FAILED\n$message",
                            intent = "gmail/error",
                            confidence = 0f,
                            mode = BrainMode.ALERT,
                            trace = listOf("gmail_operation=failed", "error=${error.javaClass.simpleName}"),
                            memory = memorySummary,
                            thoughts = listOf("The mailbox request failed without exposing credentials."),
                            entities = emptyList(),
                            decision = "report_gmail_failure",
                            action = BrainAction()
                        )
                    }
                }
            ).also { JarvisOperationBus.clear("GMAIL CYCLE COMPLETE") }
    }

    private fun launchGmailAuthorization() {
        appContext.startActivity(
            Intent(appContext, GmailAuthorizationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun authorizationResponse(memorySummary: String, reason: String? = null): BrainResponse =
        BrainResponse(
            spoken = "Google Gmail authorization is open, Sir. Select your account and approve the requested permissions.",
            display = buildString {
                appendLine("GMAIL // GOOGLE AUTHORIZATION OPEN")
                appendLine("SCOPES // MODIFY + SEND")
                appendLine("ACCOUNT // SELECT ON GOOGLE CONSENT SCREEN")
                if (!reason.isNullOrBlank()) append("STATUS // ${reason.take(220)}") else append("STATUS // AWAITING OWNER CONSENT")
            },
            intent = "gmail/authorize",
            confidence = 1f,
            mode = BrainMode.EXECUTING,
            trace = listOf("google_identity_services", "gmail_oauth_consent", "token_not_persisted"),
            memory = memorySummary,
            thoughts = listOf("Google must obtain explicit account consent before Gmail data can be accessed."),
            entities = listOf("scopes=gmail.modify+gmail.send"),
            decision = "launch_gmail_authorization",
            action = BrainAction()
        )
}

object ImageCommandIntent {
    private val creationVerb = Regex(
        "\\b(create|generate|make|draw|design|render|produce|illustrate|paint)\\b",
        RegexOption.IGNORE_CASE
    )
    private val visualNoun = Regex(
        "\\b(image|picture|photo|wallpaper|poster|logo|art|artwork|illustration|graphic|thumbnail|cover)\\b",
        RegexOption.IGNORE_CASE
    )

    fun promptFor(raw: String): String? {
        val clean = raw.trim()
        if (!creationVerb.containsMatchIn(clean) || !visualNoun.containsMatchIn(clean)) return null
        val stripped = clean
            .replace(Regex("(?i)^\\s*(hey\\s+)?(friday|jarvis)[, ]*"), "")
            .replace(Regex("(?i)^\\s*(please\\s+)?(create|generate|make|draw|design|render|produce|illustrate|paint)\\s+"), "")
            .replace(Regex("(?i)^(an?|the)\\s+(image|picture|photo|wallpaper|poster|logo|artwork?|illustration|graphic|thumbnail|cover)\\s+(of|for|showing)?\\s*"), "")
            .trim()
        return stripped.ifBlank { clean }.take(4_000)
    }
}
