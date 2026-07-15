package com.jarvis.core.device

import java.util.UUID

sealed class AppAutomationRequest(
    open val requestId: String,
    open val targetLabel: String
) {
    data class SendEmail(
        val to: String,
        val subject: String,
        val body: String,
        override val requestId: String = UUID.randomUUID().toString()
    ) : AppAutomationRequest(requestId, to)

    data class SendWhatsApp(
        val recipient: String,
        val message: String,
        override val requestId: String = UUID.randomUUID().toString()
    ) : AppAutomationRequest(requestId, recipient)
}

data class AppAutomationOutcome(
    val request: AppAutomationRequest,
    val status: DeviceActionStatus,
    val message: String,
    val latencyMs: Long,
    val trace: List<String>
) {
    fun toDeviceActionResult(): DeviceActionResult = DeviceActionResult(
        actionId = when (request) {
            is AppAutomationRequest.SendEmail -> "gmail_send"
            is AppAutomationRequest.SendWhatsApp -> "whatsapp_send"
        },
        target = request.targetLabel,
        status = status,
        spoken = message,
        latencyMs = latencyMs,
        trace = trace
    )
}

data class ScreenContextSnapshot(
    val packageName: String,
    val windowClass: String,
    val visibleText: List<String>,
    val clickableLabels: List<String>,
    val editableLabels: List<String>,
    val capturedAtMs: Long
) {
    fun spokenSummary(): String {
        val app = packageName.substringAfterLast('.').ifBlank { "the current app" }
        val text = visibleText
            .filter { it.length in 2..120 }
            .distinct()
            .take(8)
            .joinToString(". ")
        return if (text.isBlank()) {
            "I can see $app, but Android exposed no readable text on this screen."
        } else {
            "Current screen: $app. $text"
        }
    }

    fun displayText(): String = buildString {
        appendLine("SCREEN CONTEXT // ON-DEMAND")
        appendLine("PACKAGE // $packageName")
        appendLine("WINDOW // $windowClass")
        appendLine("CAPTURE // ACCESSIBILITY TREE ONLY")
        appendLine()
        appendLine("VISIBLE TEXT")
        visibleText.take(24).forEach { appendLine("- $it") }
        if (clickableLabels.isNotEmpty()) {
            appendLine()
            appendLine("CLICKABLE CONTROLS")
            clickableLabels.take(16).forEach { appendLine("- $it") }
        }
        if (editableLabels.isNotEmpty()) {
            appendLine()
            appendLine("EDITABLE FIELDS")
            editableLabels.take(12).forEach { appendLine("- $it") }
        }
    }.trim()
}
