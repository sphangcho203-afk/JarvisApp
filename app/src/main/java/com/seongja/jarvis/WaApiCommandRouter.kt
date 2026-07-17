package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.Locale

class WaApiCommandRouter(context: Context) {
    private val appContext = context.applicationContext
    private val store = WaApiSecureStore(appContext)
    private val client = WaApiClient(store)

    private data class PendingMessage(
        val recipient: String,
        val message: String,
        val createdAtMs: Long
    )

    @Volatile
    private var pending: PendingMessage? = null

    @Volatile
    private var lastSentAtMs: Long = 0L

    fun statusLabel(): String = store.load().healthLabel()

    fun intercept(
        raw: String,
        memorySummary: String,
        onToken: ((String) -> Unit)? = null
    ): BrainResponse? {
        val input = raw.trim()
        if (input.isBlank()) return null
        val lower = input.lowercase(Locale.US)

        if (isConfigureCommand(lower)) {
            launchConfig()
            return response(
                spoken = "WaAPI configuration is open, Sir.",
                display = "WHATSAPP DIRECT // WAAPI CONFIGURATION OPEN\nTOKEN // ENCRYPTED ON DEVICE\nINSTANCE // AUTO-DISCOVERY AVAILABLE\nSTATUS // AWAITING OWNER CONFIGURATION",
                intent = "whatsapp/waapi_configure",
                mode = BrainMode.EXECUTING,
                trace = listOf("waapi_config_activity", "credentials=android_keystore"),
                memory = memorySummary,
                decision = "open_waapi_configuration"
            )
        }

        if (isStatusCommand(lower)) {
            if (!client.isConfigured()) {
                launchConfig()
                return response(
                    spoken = "WaAPI needs its bearer token, Sir. The configuration screen is open.",
                    display = "WHATSAPP DIRECT // CONFIGURATION REQUIRED\nTOKEN // MISSING OR DISABLED\nINSTANCE // NOT RESOLVED",
                    intent = "whatsapp/waapi_status",
                    mode = BrainMode.ALERT,
                    trace = listOf("waapi_status", "configured=false"),
                    memory = memorySummary,
                    decision = "request_waapi_configuration"
                )
            }
            JarvisOperationBus.publish("WHATSAPP DIRECT", "VERIFYING WAAPI CLIENT STATUS", .28f)
            onToken?.invoke("Checking the WhatsApp route, Sir. ")
            return runCatching { client.testConnection() }
                .fold(
                    onSuccess = { status ->
                        JarvisOperationBus.publish("WHATSAPP DIRECT", "INSTANCE ${status.instanceId} ${status.clientState}", .96f, false)
                        response(
                            spoken = "WaAPI is online, Sir. The connected WhatsApp client reports ${status.clientState}.",
                            display = "WHATSAPP DIRECT // ONLINE\nPROVIDER // WAAPI\nINSTANCE // ${status.instanceId}\nCLIENT // ${status.clientState.uppercase()}\nLATENCY // ${status.elapsedMs} MS",
                            intent = "whatsapp/waapi_status",
                            mode = BrainMode.ONLINE,
                            trace = listOf("waapi_status=online", "instance=${status.instanceId}", "http=${status.statusCode}"),
                            memory = memorySummary,
                            decision = "report_waapi_status"
                        )
                    },
                    onFailure = { error ->
                        JarvisOperationBus.publish("WHATSAPP DIRECT ERROR", error.message.orEmpty(), 1f, false)
                        response(
                            spoken = "The WaAPI connection check failed: ${error.message ?: error.javaClass.simpleName}",
                            display = "WHATSAPP DIRECT // CONNECTION FAILED\n${(error.message ?: error.javaClass.simpleName).take(360)}",
                            intent = "whatsapp/waapi_error",
                            mode = BrainMode.ALERT,
                            trace = listOf("waapi_status=failed", "error=${error.javaClass.simpleName}"),
                            memory = memorySummary,
                            decision = "report_waapi_failure"
                        )
                    }
                )
        }

        if (lower in CONFIRM_COMMANDS && pending != null) {
            return confirm(memorySummary, onToken)
        }
        if (lower in CANCEL_COMMANDS && pending != null) {
            pending = null
            return response(
                spoken = "WhatsApp send cancelled.",
                display = "WHATSAPP DIRECT // SEND CANCELLED",
                intent = "whatsapp/cancel",
                mode = BrainMode.ONLINE,
                trace = listOf("waapi_confirmation=cancelled"),
                memory = memorySummary,
                decision = "cancel_waapi_send"
            )
        }

        val parsed = parseSend(input) ?: return null
        if (!WaApiClient.supportsDirectRecipient(parsed.first)) return null
        if (!client.isConfigured()) {
            launchConfig()
            return response(
                spoken = "WaAPI is not configured yet, Sir. The secure setup screen is open.",
                display = "WHATSAPP DIRECT // CONFIGURATION REQUIRED\nRECIPIENT // ${parsed.first.take(80)}\nMESSAGE // NOT SENT",
                intent = "whatsapp/waapi_configure",
                mode = BrainMode.ALERT,
                trace = listOf("waapi_send=blocked", "reason=not_configured"),
                memory = memorySummary,
                decision = "request_waapi_configuration"
            )
        }

        pending = PendingMessage(parsed.first, parsed.second, SystemClock.elapsedRealtime())
        return response(
            spoken = "Ready to send a WhatsApp message to ${spokenRecipient(parsed.first)}. Say confirm to send, or cancel.",
            display = buildString {
                appendLine("WHATSAPP DIRECT // CONFIRMATION REQUIRED")
                appendLine("RECIPIENT // ${parsed.first.take(120)}")
                appendLine("MESSAGE // ${parsed.second.take(640)}")
                append("ACTION // SAY CONFIRM OR CANCEL")
            },
            intent = "whatsapp/waapi_confirm",
            mode = BrainMode.EXECUTING,
            trace = listOf("waapi_confirmation=required", "message_not_sent", "recipient=${parsed.first.take(80)}"),
            memory = memorySummary,
            decision = "stage_waapi_send"
        )
    }

    private fun confirm(memorySummary: String, onToken: ((String) -> Unit)?): BrainResponse {
        val staged = pending ?: return response(
            spoken = "There is no pending WaAPI message.",
            display = "WHATSAPP DIRECT // NO PENDING MESSAGE",
            intent = "whatsapp/waapi_confirm",
            mode = BrainMode.ALERT,
            trace = listOf("waapi_confirmation=empty"),
            memory = memorySummary,
            decision = "report_no_pending_message"
        )
        if (SystemClock.elapsedRealtime() - staged.createdAtMs > CONFIRMATION_TTL_MS) {
            pending = null
            return response(
                spoken = "That WhatsApp confirmation expired. State the message again.",
                display = "WHATSAPP DIRECT // CONFIRMATION EXPIRED\nMESSAGE // NOT SENT",
                intent = "whatsapp/waapi_confirm",
                mode = BrainMode.ALERT,
                trace = listOf("waapi_confirmation=expired"),
                memory = memorySummary,
                decision = "expire_waapi_send"
            )
        }
        val elapsedSinceSend = SystemClock.elapsedRealtime() - lastSentAtMs
        if (lastSentAtMs > 0L && elapsedSinceSend < MIN_SEND_INTERVAL_MS) {
            val waitSeconds = ((MIN_SEND_INTERVAL_MS - elapsedSinceSend + 999L) / 1000L).coerceAtLeast(1L)
            return response(
                spoken = "WaAPI safety pacing is active. Wait $waitSeconds seconds, then say confirm again.",
                display = "WHATSAPP DIRECT // SAFETY PACING\nRETRY IN // $waitSeconds SECONDS\nMESSAGE // STILL PENDING",
                intent = "whatsapp/waapi_rate_limit",
                mode = BrainMode.ALERT,
                trace = listOf("waapi_send=delayed", "wait_seconds=$waitSeconds"),
                memory = memorySummary,
                decision = "pace_waapi_send"
            )
        }

        JarvisOperationBus.publish("WHATSAPP DIRECT", "RESOLVING CHAT ID AND SENDING", .35f)
        onToken?.invoke("Sending through WaAPI, Sir. ")
        return runCatching { client.sendText(staged.recipient, staged.message) }
            .fold(
                onSuccess = { result ->
                    pending = null
                    lastSentAtMs = SystemClock.elapsedRealtime()
                    JarvisOperationBus.publish("WHATSAPP SENT", "INSTANCE ${result.instanceId} // ${result.chatId}", .98f, false)
                    response(
                        spoken = "WhatsApp message sent successfully, Sir.",
                        display = buildString {
                            appendLine("WHATSAPP DIRECT // SENT")
                            appendLine("PROVIDER // WAAPI")
                            appendLine("INSTANCE // ${result.instanceId}")
                            appendLine("CHAT // ${result.chatId}")
                            appendLine("HTTP // ${result.statusCode}")
                            append("LATENCY // ${result.elapsedMs} MS")
                        },
                        intent = "whatsapp/waapi_send",
                        mode = BrainMode.ONLINE,
                        trace = listOf("waapi_send=success", "http=${result.statusCode}", "chat=${result.chatId}"),
                        memory = memorySummary,
                        decision = "send_whatsapp_via_waapi"
                    )
                },
                onFailure = { error ->
                    JarvisOperationBus.publish("WHATSAPP SEND ERROR", error.message.orEmpty(), 1f, false)
                    response(
                        spoken = "The WhatsApp message was not sent: ${error.message ?: error.javaClass.simpleName}",
                        display = "WHATSAPP DIRECT // SEND FAILED\nMESSAGE // NOT SENT\nERROR // ${(error.message ?: error.javaClass.simpleName).take(360)}",
                        intent = "whatsapp/waapi_error",
                        mode = BrainMode.ALERT,
                        trace = listOf("waapi_send=failed", "error=${error.javaClass.simpleName}"),
                        memory = memorySummary,
                        decision = "report_waapi_send_failure"
                    )
                }
            )
    }

    private fun launchConfig() {
        appContext.startActivity(
            Intent(appContext, WaApiConfigActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isConfigureCommand(lower: String): Boolean =
        lower in setOf(
            "configure whatsapp api",
            "setup whatsapp api",
            "set up whatsapp api",
            "configure waapi",
            "open waapi settings",
            "open whatsapp api settings"
        ) || (lower.contains("waapi") && lower.contains("config"))

    private fun isStatusCommand(lower: String): Boolean = lower in setOf(
        "waapi status",
        "whatsapp api status",
        "whatsapp connection status",
        "is waapi connected",
        "is whatsapp api connected",
        "check waapi",
        "test waapi"
    )

    private fun parseSend(raw: String): Pair<String, String>? {
        val clean = raw
            .replace(Regex("(?i)^\\s*(hey\\s+)?(friday|jarvis)[, ]*"), "")
            .trim()
        SEND_PATTERNS.forEach { pattern ->
            val match = pattern.matchEntire(clean) ?: return@forEach
            val recipient = match.groupValues[1].trim().trim(',', ':')
            val message = match.groupValues[2].trim()
            if (recipient.isNotBlank() && message.isNotBlank()) return recipient to message
        }
        return null
    }

    private fun spokenRecipient(raw: String): String = WaApiClient.normalizePhoneNumber(raw)
        .takeIf(String::isNotBlank)
        ?.chunked(3)
        ?.joinToString(" ")
        ?: "the selected chat"

    private fun response(
        spoken: String,
        display: String,
        intent: String,
        mode: BrainMode,
        trace: List<String>,
        memory: String,
        decision: String
    ): BrainResponse = BrainResponse(
        spoken = spoken,
        display = display,
        intent = intent,
        confidence = 1f,
        mode = mode,
        trace = trace + "provider=waapi",
        memory = memory,
        thoughts = listOf("The WhatsApp action was routed through the owner-configured WaAPI instance."),
        entities = emptyList(),
        decision = decision,
        action = BrainAction()
    )

    companion object {
        private const val CONFIRMATION_TTL_MS = 90_000L
        private const val MIN_SEND_INTERVAL_MS = 15_000L
        private val CONFIRM_COMMANDS = setOf("confirm", "confirm send", "send it", "yes send it", "proceed")
        private val CANCEL_COMMANDS = setOf("cancel", "cancel send", "do not send", "don't send", "stop")
        private val SEND_PATTERNS = listOf(
            Regex("(?i)^(?:send|send a|send the)\\s+(?:whatsapp\\s+)?(?:message\\s+)?to\\s+(.+?)\\s+(?:saying|that says|with message|message)\\s+(.+)$"),
            Regex("(?i)^(?:whatsapp|message)\\s+(.+?)\\s+(?:saying|that says|with message)\\s+(.+)$"),
            Regex("(?i)^send\\s+(.+?)\\s+on\\s+whatsapp\\s+(?:saying|that says|with message)\\s+(.+)$")
        )
    }
}
