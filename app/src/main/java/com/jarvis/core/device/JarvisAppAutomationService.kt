package com.jarvis.core.device

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-demand app automation bridge.
 *
 * The service does not continuously log screen content. It reads the current
 * accessibility tree only while an explicit automation request is active, or
 * when the operator explicitly asks Jarvis to describe the current screen.
 */
class JarvisAppAutomationService : AccessibilityService() {

    private data class Pending(
        val request: AppAutomationRequest,
        val startedAt: Long,
        val callback: (AppAutomationOutcome) -> Unit,
        var attempts: Int = 0,
        var stage: Int = 0,
        var clickedSend: Boolean = false,
        var contactSelected: Boolean = false
    )

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Pending>()
    private val processing = AtomicBoolean(false)
    private var active: Pending? = null
    private var lastEventText: String = ""

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
        drainQueue()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val text = event?.text
            ?.joinToString(" ")
            .orEmpty()
            .plus(" ")
            .plus(event?.contentDescription?.toString().orEmpty())
            .trim()
        if (text.isNotBlank()) lastEventText = normalize(text)

        if (active != null) {
            handler.removeCallbacks(processRunnable)
            handler.postDelayed(processRunnable, EVENT_SETTLE_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        active?.let { pending ->
            finish(
                pending,
                DeviceActionStatus.FAILED,
                "App automation stopped before the action completed.",
                "service_destroyed"
            )
        }
        queue.clear()
        super.onDestroy()
    }

    private val processRunnable = Runnable { processActive() }

    private fun enqueue(
        request: AppAutomationRequest,
        callback: (AppAutomationOutcome) -> Unit
    ): Boolean {
        if (queue.size >= MAX_QUEUE) return false
        queue.addLast(
            Pending(
                request = request,
                startedAt = SystemClock.elapsedRealtime(),
                callback = callback
            )
        )
        drainQueue()
        return true
    }

    private fun drainQueue() {
        if (active != null || processing.getAndSet(true)) return
        val next = if (queue.isEmpty()) null else queue.removeFirst()
        if (next == null) {
            processing.set(false)
            return
        }
        active = next
        lastEventText = ""

        val launched = when (val request = next.request) {
            is AppAutomationRequest.SendEmail -> launchEmail(request)
            is AppAutomationRequest.SendWhatsApp -> launchWhatsApp(request)
        }
        if (!launched) {
            finish(
                next,
                DeviceActionStatus.FAILED,
                when (next.request) {
                    is AppAutomationRequest.SendEmail -> "Gmail could not be opened."
                    is AppAutomationRequest.SendWhatsApp -> "WhatsApp could not be opened."
                },
                "target_app_launch_failed"
            )
            return
        }

        handler.postDelayed(processRunnable, INITIAL_APP_DELAY_MS)
        handler.postDelayed({ timeout(next) }, COMMAND_TIMEOUT_MS)
    }

    private fun launchEmail(request: AppAutomationRequest.SendEmail): Boolean {
        val uri = Uri.Builder()
            .scheme("mailto")
            .opaquePart(request.to)
            .build()
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            setPackage(GMAIL_PACKAGE)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(request.to))
            putExtra(Intent.EXTRA_SUBJECT, request.subject)
            putExtra(Intent.EXTRA_TEXT, request.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun launchWhatsApp(request: AppAutomationRequest.SendWhatsApp): Boolean {
        val digits = request.recipient.filter(Char::isDigit)
        val intent = if (digits.length >= 7) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$digits?text=${Uri.encode(request.message)}")
            ).apply {
                setPackage(WHATSAPP_PACKAGE)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(WHATSAPP_PACKAGE)
                putExtra(Intent.EXTRA_TEXT, request.message)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun processActive() {
        val pending = active ?: return
        pending.attempts++
        val root = rootInActiveWindow
        if (root == null) {
            retryOrFail(pending, "active_window_unavailable")
            return
        }

        when (val request = pending.request) {
            is AppAutomationRequest.SendEmail -> processEmail(pending, request, root)
            is AppAutomationRequest.SendWhatsApp -> processWhatsApp(pending, request, root)
        }
    }

    private fun processEmail(
        pending: Pending,
        request: AppAutomationRequest.SendEmail,
        root: AccessibilityNodeInfo
    ) {
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName != GMAIL_PACKAGE) {
            retryOrFail(pending, "gmail_window_not_active")
            return
        }

        val screenText = collectText(root, limit = 220)
        if (pending.clickedSend) {
            val sentConfirmed = lastEventText.contains("sent") ||
                Regex("\\bsent\\b").containsMatchIn(screenText)
            if (sentConfirmed) {
                finish(
                    pending,
                    DeviceActionStatus.SUCCESS,
                    "Email sent to ${request.to}.",
                    "gmail_sent_confirmation_visible"
                )
                return
            }
            if (pending.attempts >= MAX_VERIFY_ATTEMPTS) {
                finish(
                    pending,
                    DeviceActionStatus.EXECUTED_UNVERIFIED,
                    "Gmail accepted the send command, but Android did not expose a final sent confirmation.",
                    "gmail_send_clicked_confirmation_unavailable"
                )
                return
            }
            handler.postDelayed(processRunnable, VERIFY_RETRY_MS)
            return
        }

        val recipientVisible = normalize(screenText).contains(normalize(request.to))
        if (!recipientVisible && pending.attempts < MAX_SEARCH_ATTEMPTS) {
            handler.postDelayed(processRunnable, SEARCH_RETRY_MS)
            return
        }

        val send = findBestActionNode(
            root,
            labels = listOf("send", "send email"),
            viewIdTokens = listOf("send")
        )
        val clicked = send?.let(::performClick) == true
        if (!clicked) {
            retryOrFail(pending, "gmail_send_button_not_found")
            return
        }

        pending.clickedSend = true
        pending.attempts = 0
        handler.postDelayed(processRunnable, VERIFY_DELAY_MS)
    }

    private fun processWhatsApp(
        pending: Pending,
        request: AppAutomationRequest.SendWhatsApp,
        root: AccessibilityNodeInfo
    ) {
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName != WHATSAPP_PACKAGE) {
            retryOrFail(pending, "whatsapp_window_not_active")
            return
        }

        val messageField = findEditableNode(root)
        if (messageField == null) {
            if (!pending.contactSelected && request.recipient.filter(Char::isDigit).length < 7) {
                val contact = findTextNode(root, request.recipient)
                if (contact?.let(::performClick) == true) {
                    pending.contactSelected = true
                    pending.attempts = 0
                    handler.postDelayed(processRunnable, CONTACT_OPEN_DELAY_MS)
                    return
                }
            }
            retryOrFail(pending, "whatsapp_chat_not_ready")
            return
        }

        if (pending.clickedSend) {
            val currentInput = messageField.text?.toString().orEmpty().trim()
            val screenText = collectText(root, limit = 240)
            val messageVisible = normalize(screenText).contains(normalize(request.message))
            if (currentInput.isBlank() && messageVisible) {
                finish(
                    pending,
                    DeviceActionStatus.SUCCESS,
                    "WhatsApp message sent to ${request.recipient}.",
                    "whatsapp_message_visible_and_input_cleared"
                )
                return
            }
            if (pending.attempts >= MAX_VERIFY_ATTEMPTS) {
                finish(
                    pending,
                    DeviceActionStatus.EXECUTED_UNVERIFIED,
                    "WhatsApp accepted the send command, but Android did not expose a complete delivery confirmation.",
                    "whatsapp_send_clicked_confirmation_unavailable"
                )
                return
            }
            handler.postDelayed(processRunnable, VERIFY_RETRY_MS)
            return
        }

        val currentText = messageField.text?.toString().orEmpty()
        if (normalize(currentText) != normalize(request.message)) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    request.message
                )
            }
            val set = messageField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!set) {
                finish(
                    pending,
                    DeviceActionStatus.FAILED,
                    "WhatsApp opened, but Android blocked message entry.",
                    "whatsapp_set_text_blocked"
                )
                return
            }
            handler.postDelayed(processRunnable, TEXT_SETTLE_MS)
            return
        }

        val send = findBestActionNode(
            root,
            labels = listOf("send", "send message"),
            viewIdTokens = listOf("send")
        )
        val clicked = send?.let(::performClick) == true
        if (!clicked) {
            retryOrFail(pending, "whatsapp_send_button_not_found")
            return
        }

        pending.clickedSend = true
        pending.attempts = 0
        handler.postDelayed(processRunnable, VERIFY_DELAY_MS)
    }

    private fun retryOrFail(pending: Pending, trace: String) {
        if (pending.attempts < MAX_SEARCH_ATTEMPTS) {
            handler.postDelayed(processRunnable, SEARCH_RETRY_MS)
            return
        }
        finish(
            pending,
            DeviceActionStatus.FAILED,
            when (pending.request) {
                is AppAutomationRequest.SendEmail ->
                    "Gmail opened, but Jarvis could not verify the compose controls."
                is AppAutomationRequest.SendWhatsApp ->
                    "WhatsApp opened, but Jarvis could not verify the target chat and send controls."
            },
            trace
        )
    }

    private fun timeout(pending: Pending) {
        if (active !== pending) return
        finish(
            pending,
            DeviceActionStatus.FAILED,
            "The app automation request timed out.",
            "automation_timeout"
        )
    }

    private fun finish(
        pending: Pending,
        status: DeviceActionStatus,
        message: String,
        terminalTrace: String
    ) {
        if (active !== pending) return
        handler.removeCallbacks(processRunnable)
        active = null
        processing.set(false)
        runCatching {
            pending.callback(
                AppAutomationOutcome(
                    request = pending.request,
                    status = status,
                    message = message,
                    latencyMs = (SystemClock.elapsedRealtime() - pending.startedAt)
                        .coerceAtLeast(0L),
                    trace = listOf(
                        "android_app_automation_bridge",
                        "request=${pending.request.requestId}",
                        "target=${pending.request.targetLabel}",
                        terminalTrace
                    )
                )
            )
        }
        handler.postDelayed({ drainQueue() }, 180L)
    }

    private fun captureSnapshot(): ScreenContextSnapshot? {
        val root = rootInActiveWindow ?: return null
        val visible = linkedSetOf<String>()
        val clickable = linkedSetOf<String>()
        val editable = linkedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_SNAPSHOT_NODES) {
            val node = queue.removeFirst()
            visited++
            val label = rawNodeText(node)
            if (label.isNotBlank()) {
                if (node.isVisibleToUser) visible += label.take(MAX_LABEL_CHARS)
                if (node.isClickable) clickable += label.take(MAX_LABEL_CHARS)
                if (node.isEditable) editable += label.take(MAX_LABEL_CHARS)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        return ScreenContextSnapshot(
            packageName = root.packageName?.toString().orEmpty().ifBlank { "unknown" },
            windowClass = root.className?.toString().orEmpty().ifBlank { "unknown" },
            visibleText = visible.toList().take(MAX_VISIBLE_ITEMS),
            clickableLabels = clickable.toList().take(MAX_CLICKABLE_ITEMS),
            editableLabels = editable.toList().take(MAX_EDITABLE_ITEMS),
            capturedAtMs = System.currentTimeMillis()
        )
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isVisibleToUser) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun findTextNode(
        root: AccessibilityNodeInfo,
        target: String
    ): AccessibilityNodeInfo? {
        val needle = normalize(target)
        if (needle.isBlank()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var partial: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = normalize(rawNodeText(node))
            if (text == needle) return node
            if (partial == null && text.contains(needle)) partial = node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return partial
    }

    private fun findBestActionNode(
        root: AccessibilityNodeInfo,
        labels: List<String>,
        viewIdTokens: List<String>
    ): AccessibilityNodeInfo? {
        val normalizedLabels = labels.map(::normalize)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = normalize(rawNodeText(node))
            val viewId = normalize(node.viewIdResourceName.orEmpty())
            var score = 0
            if (normalizedLabels.any { text == it }) score += 100
            if (normalizedLabels.any { text.startsWith(it) }) score += 70
            if (viewIdTokens.any { viewId.contains(normalize(it)) }) score += 80
            if (node.isClickable) score += 20
            if (score > bestScore) {
                best = node
                bestScore = score
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return best?.takeIf { bestScore >= 80 }
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val target = current ?: return false
            if (
                target.isClickable ||
                target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            ) {
                return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = target.parent
        }
        return false
    }

    private fun collectText(root: AccessibilityNodeInfo, limit: Int): String {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val parts = mutableListOf<String>()
        while (queue.isNotEmpty() && parts.size < limit) {
            val node = queue.removeFirst()
            val text = rawNodeText(node)
            if (text.isNotBlank()) parts += text
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return normalize(parts.joinToString(" "))
    }

    private fun rawNodeText(node: AccessibilityNodeInfo): String = listOfNotNull(
        node.text?.toString(),
        node.contentDescription?.toString(),
        node.hintText?.toString(),
        node.paneTitle?.toString()
    )
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9@+._ -]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val MAX_QUEUE = 4
        private const val MAX_SEARCH_ATTEMPTS = 12
        private const val MAX_VERIFY_ATTEMPTS = 8
        private const val COMMAND_TIMEOUT_MS = 22_000L
        private const val INITIAL_APP_DELAY_MS = 900L
        private const val CONTACT_OPEN_DELAY_MS = 700L
        private const val EVENT_SETTLE_MS = 120L
        private const val SEARCH_RETRY_MS = 300L
        private const val VERIFY_DELAY_MS = 600L
        private const val VERIFY_RETRY_MS = 450L
        private const val TEXT_SETTLE_MS = 250L
        private const val MAX_SNAPSHOT_NODES = 320
        private const val MAX_LABEL_CHARS = 160
        private const val MAX_VISIBLE_ITEMS = 64
        private const val MAX_CLICKABLE_ITEMS = 32
        private const val MAX_EDITABLE_ITEMS = 16

        @Volatile
        private var instance: JarvisAppAutomationService? = null

        fun isConnected(): Boolean = instance != null

        fun request(
            request: AppAutomationRequest,
            callback: (AppAutomationOutcome) -> Unit
        ): Boolean = instance?.enqueue(request, callback) ?: false

        fun snapshot(): ScreenContextSnapshot? = instance?.captureSnapshot()
    }
}
