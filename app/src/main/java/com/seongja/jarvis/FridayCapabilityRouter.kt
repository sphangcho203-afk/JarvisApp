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
        append(" // diary encrypted")
        append(" // memory owner-controlled")
    }

    fun intercept(input: String, memorySummary: String, onToken: ((String) -> Unit)? = null): BrainResponse? {
        PrivateDiaryCommandParser.parse(input)?.let { return handleDiaryCommand(it, memorySummary, onToken) }
        MemoryVaultCommandParser.parse(input)?.let { return handleMemoryVaultCommand(it, memorySummary, onToken) }
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
        return runCatching { gmail.execute(command) }.fold(
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
                        .replace(Regex("\\s+"), " ").trim().take(480)
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

    private fun handleDiaryCommand(command: PrivateDiaryCommand, memorySummary: String, onToken: ((String) -> Unit)?): BrainResponse {
        val result = when (command) {
            PrivateDiaryCommand.Open -> {
                PrivateDiaryActivity.launch(appContext)
                RouteResult(
                    "The private diary is opening, Sir. Android owner authentication is required.",
                    "PRIVATE DIARY // AUTHENTICATION REQUIRED\nAI VISIBILITY // CONTROLLED PER ENTRY\nSCREEN CAPTURE // BLOCKED IN VAULT",
                    "diary/open",
                    "launch_private_diary"
                )
            }
            PrivateDiaryCommand.NewEntry -> {
                PrivateDiaryActivity.launch(appContext, newEntry = true)
                RouteResult(
                    "Opening a new encrypted diary entry, Sir.",
                    "PRIVATE DIARY // NEW ENTRY\nSTORAGE // LOCAL ENCRYPTED\nDEFAULT AI ACCESS // OWNER ONLY",
                    "diary/new",
                    "launch_new_diary_entry"
                )
            }
            is PrivateDiaryCommand.Search -> {
                PrivateDiaryActivity.launch(appContext, search = command.query)
                RouteResult(
                    "The private diary search is opening, Sir. I will not read protected pages.",
                    "PRIVATE DIARY // OWNER SEARCH\nQUERY // ${command.query.take(240)}\nPROTECTED CONTENT // NOT EXPOSED TO ASSISTANT",
                    "diary/search",
                    "launch_owner_diary_search"
                )
            }
            PrivateDiaryCommand.SecureVault -> {
                val secured = PrivateDiaryRuntime.secureActive("OWNER COMMAND")
                RouteResult(
                    if (secured) "Diary sealed. Temporary vault context has been cleared, Sir." else "The private diary is already sealed, Sir.",
                    if (secured) "PRIVATE DIARY // SEALED\nTEMPORARY CONTEXT // CLEARED" else "PRIVATE DIARY // ALREADY SEALED",
                    "diary/secure",
                    if (secured) "secure_active_diary" else "diary_already_secure"
                )
            }
        }
        JarvisOperationBus.publish("PRIVATE DIARY", result.display.lineSequence().firstOrNull().orEmpty(), 1f)
        onToken?.invoke(result.spoken)
        return BrainResponse(
            spoken = result.spoken,
            display = result.display,
            intent = result.intent,
            confidence = 1f,
            mode = BrainMode.EXECUTING,
            trace = listOf(
                "storage=android_keystore_aes_gcm",
                "authentication=android_owner",
                "screen_capture=flag_secure",
                "diary_memory_separation=enforced"
            ),
            memory = memorySummary,
            thoughts = listOf("The diary route protects entries unless the owner selects a readable mode."),
            entities = listOf("workspace=private_diary_vault"),
            decision = result.decision,
            action = BrainAction()
        )
    }

    private fun handleMemoryVaultCommand(command: MemoryVaultCommand, memorySummary: String, onToken: ((String) -> Unit)?): BrainResponse {
        val result = when (command) {
            MemoryVaultCommand.Open -> {
                MemoryVaultActivity.launch(appContext)
                RouteResult(
                    "Opening the Memory Vault, Sir. Owner authentication is required.",
                    "MEMORY VAULT // AUTHENTICATION REQUIRED\nCONTROL // REVIEW + CORRECT + DELETE + RETENTION + ENCRYPTED TRANSFER",
                    "memory/vault_open",
                    "launch_memory_vault"
                )
            }
            MemoryVaultCommand.ReviewPending -> {
                MemoryVaultActivity.launchPending(appContext)
                RouteResult(
                    "Opening the memory approval queue, Sir.",
                    "MEMORY VAULT // APPROVAL QUEUE\nDIARY CANDIDATES // OWNER CONFIRMATION REQUIRED",
                    "memory/vault_review",
                    "launch_memory_review_queue"
                )
            }
            MemoryVaultCommand.ConversationArchive -> {
                MemoryVaultActivity.launchArchive(appContext)
                RouteResult(
                    "Opening the encrypted conversation archive, Sir.",
                    "MEMORY VAULT // CONVERSATION ARCHIVE\nSTORAGE // LOCAL ENCRYPTED",
                    "memory/conversation_archive",
                    "launch_conversation_archive"
                )
            }
            is MemoryVaultCommand.Search -> {
                MemoryVaultActivity.launch(appContext, query = command.query)
                RouteResult(
                    "Opening the Memory Vault for your search, Sir.",
                    "MEMORY VAULT // SEARCH\nQUERY // ${command.query.take(240)}",
                    "memory/vault_search",
                    "launch_memory_search"
                )
            }
        }
        JarvisOperationBus.publish("MEMORY VAULT", result.display.lineSequence().firstOrNull().orEmpty(), .95f)
        onToken?.invoke(result.spoken)
        return BrainResponse(
            spoken = result.spoken,
            display = result.display,
            intent = result.intent,
            confidence = 1f,
            mode = BrainMode.EXECUTING,
            trace = listOf(
                "workspace=memory_vault",
                "storage=android_keystore_aes_gcm",
                "owner_review=required",
                "diary_transfer=approval_queue_only"
            ),
            memory = memorySummary,
            thoughts = listOf("Personal memory controls remain local and owner-authenticated."),
            entities = listOf("workspace=memory_vault"),
            decision = result.decision,
            action = BrainAction()
        )
    }

    private fun launchGmailAuthorization() {
        appContext.startActivity(Intent(appContext, GmailAuthorizationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun authorizationResponse(memorySummary: String, reason: String? = null): BrainResponse = BrainResponse(
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

    private data class RouteResult(val spoken: String, val display: String, val intent: String, val decision: String)
}

object ImageCommandIntent {
    private val creationVerb = Regex("\\b(create|generate|make|draw|design|render|produce|illustrate|paint)\\b", RegexOption.IGNORE_CASE)
    private val visualNoun = Regex("\\b(image|picture|photo|wallpaper|poster|logo|art|artwork|illustration|graphic|thumbnail|cover)\\b", RegexOption.IGNORE_CASE)

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
