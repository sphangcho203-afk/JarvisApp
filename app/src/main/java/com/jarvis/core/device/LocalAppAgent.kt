package com.jarvis.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.seongja.jarvis.JarvisWakeService
import java.util.Locale

/**
 * Deterministic natural-language layer for local app actions.
 *
 * Draft creation is immediate. Sending email or WhatsApp messages always
 * requires a separate confirmation utterance before the accessibility executor
 * receives the request.
 */
class LocalAppAgent(
    context: Context,
    private val onDeferredResult: (DeviceActionResult) -> Unit
) {
    private val appContext = context.applicationContext

    private data class PendingSend(
        val request: AppAutomationRequest,
        val preview: String,
        val createdAtMs: Long
    )

    @Volatile
    private var pendingSend: PendingSend? = null

    fun handle(rawCommand: String, startedAtMs: Long): DeviceActionResult? {
        val command = cleanCommand(rawCommand)
        if (command.isBlank()) return null
        val lower = command.lowercase(Locale.US)

        if (lower in WAKE_STATUS_COMMANDS) return wakeStatusResult(startedAtMs)
        if (lower in ENABLE_WAKE_COMMANDS) return enableWakeResult(startedAtMs)
        if (lower in DISABLE_WAKE_COMMANDS) return disableWakeResult(startedAtMs)
        if (lower in STATUS_COMMANDS) return statusResult(startedAtMs)
        if (lower in ENABLE_COMMANDS) return enableResult(startedAtMs)
        if (lower in SCREEN_COMMANDS) return screenResult(startedAtMs)
        if (lower in CANCEL_COMMANDS) return cancelResult(startedAtMs)
        if (lower in CONFIRM_COMMANDS) return confirmResult(startedAtMs)

        parseEmail(command)?.let { parsed ->
            return when (parsed) {
                is ParsedCommand.ComposeEmail -> openEmailDraft(parsed, startedAtMs)
                is ParsedCommand.SendEmail -> stageSend(
                    request = AppAutomationRequest.SendEmail(
                        to = parsed.to,
                        subject = parsed.subject,
                        body = parsed.body
                    ),
                    preview = "Send email to ${parsed.to}: ${parsed.body.take(PREVIEW_CHARS)}",
                    startedAtMs = startedAtMs
                )
                else -> null
            }
        }

        parseWhatsApp(command)?.let { parsed ->
            if (parsed is ParsedCommand.SendWhatsApp) {
                return stageSend(
                    request = AppAutomationRequest.SendWhatsApp(
                        recipient = parsed.recipient,
                        message = parsed.message
                    ),
                    preview = "Send WhatsApp message to ${parsed.recipient}: ${parsed.message.take(PREVIEW_CHARS)}",
                    startedAtMs = startedAtMs
                )
            }
        }
        return null
    }

    private fun stageSend(
        request: AppAutomationRequest,
        preview: String,
        startedAtMs: Long
    ): DeviceActionResult {
        pendingSend = PendingSend(
            request = request,
            preview = preview,
            createdAtMs = SystemClock.elapsedRealtime()
        )
        return result(
            actionId = "communication_confirmation",
            target = request.targetLabel,
            status = DeviceActionStatus.USER_CONFIRMATION_REQUIRED,
            spoken = "$preview. Say confirm to send, or cancel.",
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_app_agent",
                "confirmation_gate",
                "request=${request.requestId}",
                "send_not_executed"
            )
        )
    }

    private fun confirmResult(startedAtMs: Long): DeviceActionResult {
        val pending = pendingSend
            ?: return result(
                actionId = "communication_confirm",
                target = "none",
                status = DeviceActionStatus.NEEDS_CLARIFICATION,
                spoken = "There is no pending message to send.",
                startedAtMs = startedAtMs,
                trace = listOf("local_app_agent", "no_pending_send")
            )

        if (SystemClock.elapsedRealtime() - pending.createdAtMs > CONFIRMATION_TTL_MS) {
            pendingSend = null
            return result(
                actionId = "communication_confirm",
                target = pending.request.targetLabel,
                status = DeviceActionStatus.FAILED,
                spoken = "That send confirmation expired. State the message again.",
                startedAtMs = startedAtMs,
                trace = listOf("local_app_agent", "confirmation_expired")
            )
        }

        if (!AppAutomationAccess.isEnabled(appContext)) {
            val opened = AppAutomationAccess.openSettings(appContext)
            return result(
                actionId = "app_automation_setup",
                target = "accessibility settings",
                status = if (opened) {
                    DeviceActionStatus.PERMISSION_REQUIRED
                } else {
                    DeviceActionStatus.FAILED
                },
                spoken = if (opened) {
                    "Enable Jarvis App Automation once, return to Jarvis, then say confirm again."
                } else {
                    "I could not open the App Automation permission screen."
                },
                startedAtMs = startedAtMs,
                trace = listOf(
                    "local_app_agent",
                    "app_automation_permission_required",
                    "send_not_executed"
                )
            )
        }

        if (!JarvisAppAutomationService.isConnected()) {
            return result(
                actionId = "app_automation_connect",
                target = pending.request.targetLabel,
                status = DeviceActionStatus.FAILED,
                spoken = "App Automation is enabled but not connected. Reopen Jarvis and say confirm again.",
                startedAtMs = startedAtMs,
                trace = listOf(
                    "local_app_agent",
                    "accessibility_service_not_connected",
                    "send_not_executed"
                )
            )
        }

        val accepted = JarvisAppAutomationService.request(pending.request) { outcome ->
            onDeferredResult(outcome.toDeviceActionResult())
        }
        if (accepted) pendingSend = null

        return result(
            actionId = when (pending.request) {
                is AppAutomationRequest.SendEmail -> "gmail_send"
                is AppAutomationRequest.SendWhatsApp -> "whatsapp_send"
            },
            target = pending.request.targetLabel,
            status = if (accepted) DeviceActionStatus.IN_PROGRESS else DeviceActionStatus.FAILED,
            spoken = if (accepted) {
                "Confirmed. Executing the send action."
            } else {
                "App Automation is busy. Try confirm again in a moment."
            },
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_app_agent",
                "confirmation_received",
                "request=${pending.request.requestId}",
                if (accepted) "automation_queued" else "automation_queue_rejected"
            )
        )
    }

    private fun cancelResult(startedAtMs: Long): DeviceActionResult {
        val pending = pendingSend
        pendingSend = null
        return result(
            actionId = "communication_cancel",
            target = pending?.request?.targetLabel ?: "none",
            status = DeviceActionStatus.SUCCESS,
            spoken = if (pending == null) {
                "There was no pending message."
            } else {
                "Send cancelled."
            },
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_app_agent",
                if (pending == null) "no_pending_send" else "pending_send_cleared"
            )
        )
    }

    private fun screenResult(startedAtMs: Long): DeviceActionResult {
        if (!AppAutomationAccess.isEnabled(appContext)) {
            val opened = AppAutomationAccess.openSettings(appContext)
            return result(
                actionId = "screen_context_setup",
                target = "accessibility settings",
                status = if (opened) DeviceActionStatus.PERMISSION_REQUIRED else DeviceActionStatus.FAILED,
                spoken = if (opened) {
                    "Enable Jarvis App Automation once. Screen context is read only when you ask for it."
                } else {
                    "I could not open the screen-context permission settings."
                },
                startedAtMs = startedAtMs,
                trace = listOf(
                    "local_app_agent",
                    "screen_context_permission_required",
                    "no_screen_content_read"
                )
            )
        }

        if (!JarvisAppAutomationService.isConnected()) {
            return result(
                actionId = "screen_context",
                target = "active window",
                status = DeviceActionStatus.FAILED,
                spoken = "App Automation is enabled but not connected. Reopen Jarvis and ask again.",
                startedAtMs = startedAtMs,
                trace = listOf("local_app_agent", "screen_service_not_connected")
            )
        }

        val snapshot = JarvisAppAutomationService.snapshot()
            ?: return result(
                actionId = "screen_context",
                target = "active window",
                status = DeviceActionStatus.FAILED,
                spoken = "Android did not expose a readable active window.",
                startedAtMs = startedAtMs,
                trace = listOf("local_app_agent", "active_window_unavailable")
            )

        return result(
            actionId = "screen_context",
            target = snapshot.packageName,
            status = DeviceActionStatus.SUCCESS,
            spoken = snapshot.spokenSummary(),
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_app_agent",
                "on_demand_accessibility_snapshot",
                "package=${snapshot.packageName}",
                "items=${snapshot.visibleText.size}",
                "snapshot_not_persisted"
            )
        )
    }

    private fun wakeStatusResult(startedAtMs: Long): DeviceActionResult {
        val capable = JarvisWakeService.canRun(appContext)
        val running = JarvisWakeService.isRunning()
        return result(
            actionId = "wake_listener_status",
            target = "wake up jarvis",
            status = when {
                running -> DeviceActionStatus.SUCCESS
                capable -> DeviceActionStatus.USER_CONFIRMATION_REQUIRED
                else -> DeviceActionStatus.FAILED
            },
            spoken = when {
                running -> "The local wake listener is active."
                capable -> "The local wake listener is available but stopped. Say enable wake listener."
                else -> "Android on-device speech recognition is unavailable or microphone permission is missing."
            },
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_wake_listener",
                "capable=$capable",
                "running=$running"
            )
        )
    }

    private fun enableWakeResult(startedAtMs: Long): DeviceActionResult {
        val started = JarvisWakeService.start(appContext)
        return result(
            actionId = "wake_listener_enable",
            target = "wake up jarvis",
            status = if (started) DeviceActionStatus.SUCCESS else DeviceActionStatus.FAILED,
            spoken = if (started) {
                "Local wake listener enabled. A visible microphone notification will remain active."
            } else {
                "The wake listener could not start. Android requires microphone permission and an on-device recognizer."
            },
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_wake_listener",
                if (started) "foreground_service_started" else "start_failed"
            )
        )
    }

    private fun disableWakeResult(startedAtMs: Long): DeviceActionResult {
        val stopped = JarvisWakeService.stop(appContext)
        return result(
            actionId = "wake_listener_disable",
            target = "wake up jarvis",
            status = if (stopped) DeviceActionStatus.SUCCESS else DeviceActionStatus.FAILED,
            spoken = if (stopped) "Local wake listener stopped." else "The wake listener could not be stopped.",
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_wake_listener",
                if (stopped) "service_stop_requested" else "stop_failed"
            )
        )
    }

    private fun statusResult(startedAtMs: Long): DeviceActionResult {
        val enabled = AppAutomationAccess.isEnabled(appContext)
        val connected = JarvisAppAutomationService.isConnected()
        val message = when {
            enabled && connected ->
                "App Automation is online. Gmail, WhatsApp, and on-demand screen context are ready."
            enabled ->
                "App Automation is enabled and reconnecting."
            else ->
                "App Automation is disabled. Say enable app automation to open the one-time setup."
        }
        return result(
            actionId = "app_automation_status",
            target = "local app agent",
            status = if (enabled) DeviceActionStatus.SUCCESS else DeviceActionStatus.PERMISSION_REQUIRED,
            spoken = message,
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_app_agent",
                "enabled=$enabled",
                "connected=$connected"
            )
        )
    }

    private fun enableResult(startedAtMs: Long): DeviceActionResult {
        val opened = AppAutomationAccess.openSettings(appContext)
        return result(
            actionId = "app_automation_setup",
            target = "accessibility settings",
            status = if (opened) DeviceActionStatus.PERMISSION_REQUIRED else DeviceActionStatus.FAILED,
            spoken = if (opened) {
                "Enable Jarvis App Automation. It acts only on explicit commands and does not store screen content."
            } else {
                "I could not open the App Automation setup."
            },
            startedAtMs = startedAtMs,
            trace = listOf("local_app_agent", "open_accessibility_settings")
        )
    }

    private fun openEmailDraft(
        command: ParsedCommand.ComposeEmail,
        startedAtMs: Long
    ): DeviceActionResult {
        val uri = Uri.Builder()
            .scheme("mailto")
            .opaquePart(command.to)
            .build()
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            setPackage(GMAIL_PACKAGE)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(command.to))
            if (command.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, command.subject)
            if (command.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, command.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)

        return result(
            actionId = "gmail_compose",
            target = command.to,
            status = if (opened) DeviceActionStatus.SUCCESS else DeviceActionStatus.FAILED,
            spoken = if (opened) {
                "Gmail draft opened for ${command.to}."
            } else {
                "Gmail could not be opened for ${command.to}."
            },
            startedAtMs = startedAtMs,
            trace = listOf(
                "local_app_agent",
                "android_sendto_mailto",
                "target=${command.to}",
                if (opened) "draft_opened" else "draft_open_failed",
                "message_not_sent"
            )
        )
    }

    private fun parseEmail(command: String): ParsedCommand? {
        val direct = EMAIL_DIRECT_PATTERN.find(command)
        if (direct != null) {
            val to = direct.groupValues[1].trim()
            val body = direct.groupValues.getOrNull(2).orEmpty().trim().trimQuotes()
            val explicitSend = command.trim().startsWith("send", ignoreCase = true)
            return if (body.isBlank() && !explicitSend) {
                ParsedCommand.ComposeEmail(to = to)
            } else if (body.isBlank()) {
                ParsedCommand.ComposeEmail(to = to)
            } else {
                ParsedCommand.SendEmail(to = to, subject = "", body = body)
            }
        }

        val compose = EMAIL_COMPOSE_PATTERN.find(command) ?: return null
        val to = compose.groupValues[1].trim()
        val body = compose.groupValues.getOrNull(2).orEmpty().trim().trimQuotes()
        return if (body.isBlank()) {
            ParsedCommand.ComposeEmail(to = to)
        } else {
            ParsedCommand.SendEmail(to = to, subject = "", body = body)
        }
    }

    private fun parseWhatsApp(command: String): ParsedCommand? {
        WHATSAPP_TO_SAYING_PATTERN.find(command)?.let { match ->
            val recipient = match.groupValues[1].trim().trimQuotes()
            val message = match.groupValues[2].trim().trimQuotes()
            if (recipient.isNotBlank() && message.isNotBlank()) {
                return ParsedCommand.SendWhatsApp(recipient, message)
            }
        }

        WHATSAPP_MESSAGE_TO_PATTERN.find(command)?.let { match ->
            val message = match.groupValues[1].trim().trimQuotes()
            val recipient = match.groupValues[2]
                .replace(Regex("(?i)\\s+(?:on|using)\\s+whatsapp$"), "")
                .trim()
                .trimQuotes()
            if (recipient.isNotBlank() && message.isNotBlank()) {
                return ParsedCommand.SendWhatsApp(recipient, message)
            }
        }
        return null
    }

    private fun cleanCommand(value: String): String {
        var command = value.trim()
        command = command.replace(
            Regex("(?i)^(?:hey\\s+|wake\\s+up\\s+)?jarvis[, ]+"),
            ""
        )
        command = command.replace(Regex("(?i)^(?:please|kindly)\\s+"), "")
        return command.trim()
    }

    private fun result(
        actionId: String,
        target: String,
        status: DeviceActionStatus,
        spoken: String,
        startedAtMs: Long,
        trace: List<String>
    ): DeviceActionResult = DeviceActionResult(
        actionId = actionId,
        target = target,
        status = status,
        spoken = spoken,
        latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L),
        trace = trace
    )

    private fun String.trimQuotes(): String = trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .removePrefix("“")
        .removeSuffix("”")
        .removePrefix("'")
        .removeSuffix("'")
        .trim()

    private sealed interface ParsedCommand {
        data class ComposeEmail(
            val to: String,
            val subject: String = "",
            val body: String = ""
        ) : ParsedCommand

        data class SendEmail(
            val to: String,
            val subject: String,
            val body: String
        ) : ParsedCommand

        data class SendWhatsApp(
            val recipient: String,
            val message: String
        ) : ParsedCommand
    }

    companion object {
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        private const val PREVIEW_CHARS = 140
        private const val CONFIRMATION_TTL_MS = 120_000L

        private val EMAIL = "([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})"
        private val EMAIL_DIRECT_PATTERN = Regex(
            "(?i)^(?:send\\s+(?:an\\s+)?|compose\\s+(?:an\\s+)?|write\\s+(?:an\\s+)?|open\\s+gmail\\s+and\\s+)?(?:email|mail)(?:\\s+to)?\\s+$EMAIL(?:\\s+(?:saying|with(?:\\s+the)?\\s+message|message|body)\\s+(.+))?$"
        )
        private val EMAIL_COMPOSE_PATTERN = Regex(
            "(?i)^open\\s+gmail\\s+and\\s+(?:email|mail)\\s+$EMAIL(?:\\s+(?:saying|message|body)\\s+(.+))?$"
        )
        private val WHATSAPP_TO_SAYING_PATTERN = Regex(
            "(?i)^(?:send\\s+(?:a\\s+)?whatsapp\\s+message|whatsapp|message)\\s+to\\s+(.+?)\\s+(?:saying|with(?:\\s+the)?\\s+message)\\s+(.+)$"
        )
        private val WHATSAPP_MESSAGE_TO_PATTERN = Regex(
            "(?i)^(?:send|message|whatsapp)\\s+[\\\"“']?(.+?)[\\\"”']?\\s+to\\s+(.+?)$"
        )

        private val WAKE_STATUS_COMMANDS = setOf(
            "wake listener status",
            "wake word status",
            "is wake up jarvis enabled"
        )
        private val ENABLE_WAKE_COMMANDS = setOf(
            "enable wake listener",
            "enable wake word",
            "start wake listener",
            "always listen for wake up jarvis"
        )
        private val DISABLE_WAKE_COMMANDS = setOf(
            "disable wake listener",
            "disable wake word",
            "stop wake listener"
        )
        private val STATUS_COMMANDS = setOf(
            "app automation status",
            "automation status",
            "screen access status"
        )
        private val ENABLE_COMMANDS = setOf(
            "enable app automation",
            "open app automation",
            "app automation setup",
            "enable screen access"
        )
        private val SCREEN_COMMANDS = setOf(
            "what is on my screen",
            "whats on my screen",
            "what's on my screen",
            "describe my screen",
            "read my screen",
            "describe the current screen",
            "screen context"
        )
        private val CONFIRM_COMMANDS = setOf(
            "confirm",
            "confirmed",
            "yes",
            "yes send it",
            "send it",
            "proceed"
        )
        private val CANCEL_COMMANDS = setOf(
            "cancel",
            "cancel send",
            "do not send",
            "don't send",
            "stop send"
        )
    }
}
