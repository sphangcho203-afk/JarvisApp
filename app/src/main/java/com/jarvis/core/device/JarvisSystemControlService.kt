package com.jarvis.core.device

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Narrow Accessibility bridge used only for Android SystemUI quick-setting tiles.
 *
 * The service does not read messages, type text, inspect other apps, or execute
 * arbitrary gestures. It opens Quick Settings, locates an allow-listed tile,
 * verifies the visible state when Android exposes it, and presses that tile.
 */
class JarvisSystemControlService : AccessibilityService() {

    private data class Pending(
        val request: SystemControlRequest,
        val startedAt: Long,
        val callback: (SystemControlOutcome) -> Unit,
        var searchAttempt: Int = 0,
        var pageAttempt: Int = 0,
        var clicked: Boolean = false
    )

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Pending>()
    private var active: Pending? = null
    private val processing = AtomicBoolean(false)

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
        drainQueue()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (active == null) return
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName == SYSTEM_UI_PACKAGE) {
            handler.removeCallbacks(searchRunnable)
            handler.postDelayed(searchRunnable, 90L)
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
                "System control stopped before ${pending.request.toggle.displayName} could be changed.",
                "service_destroyed"
            )
        }
        queue.clear()
        super.onDestroy()
    }

    private val searchRunnable = Runnable { searchAndAct() }

    private fun enqueue(request: SystemControlRequest, callback: (SystemControlOutcome) -> Unit): Boolean {
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
        val opened = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        if (!opened) {
            finish(
                next,
                DeviceActionStatus.FAILED,
                "Android did not open Quick Settings for ${next.request.toggle.displayName}.",
                "quick_settings_open_failed"
            )
            return
        }
        handler.postDelayed(searchRunnable, INITIAL_OPEN_DELAY_MS)
        handler.postDelayed({ timeout(next) }, COMMAND_TIMEOUT_MS)
    }

    private fun searchAndAct() {
        val pending = active ?: return
        pending.searchAttempt++

        val roots = windows
            .mapNotNull { it.root }
            .filter { root -> root.packageName?.toString() == SYSTEM_UI_PACKAGE }
            .ifEmpty {
                rootInActiveWindow
                    ?.takeIf { it.packageName?.toString() == SYSTEM_UI_PACKAGE }
                    ?.let(::listOf)
                    .orEmpty()
            }

        if (pending.clicked && tryConfirmSystemDialog(pending, roots) == true) {
            handler.postDelayed(searchRunnable, VERIFY_DELAY_MS)
            return
        }

        val match = roots
            .asSequence()
            .mapNotNull { root -> bestTileMatch(root, pending.request.toggle) }
            .maxByOrNull { it.score }

        if (match == null) {
            if (pending.searchAttempt <= MAX_SEARCH_ATTEMPTS) {
                handler.postDelayed(searchRunnable, SEARCH_RETRY_MS)
                return
            }
            if (pending.pageAttempt < MAX_PAGE_ATTEMPTS && scrollQuickSettingsForward(roots)) {
                pending.pageAttempt++
                pending.searchAttempt = 0
                handler.postDelayed(searchRunnable, PAGE_RETRY_MS)
                return
            }
            finish(
                pending,
                DeviceActionStatus.FAILED,
                "${pending.request.toggle.displayName} tile was not found. Place it on the first Quick Settings pages and try again.",
                "tile_not_found"
            )
            return
        }

        if (!pending.clicked) {
            val visibleState = readState(match.node, match.combinedText)
            if (visibleState != null && visibleState == pending.request.desiredEnabled) {
                finish(
                    pending,
                    DeviceActionStatus.SUCCESS,
                    "${pending.request.toggle.displayName} is already ${onOff(visibleState)}.",
                    "state_already_correct"
                )
                return
            }

            val clickable = findClickable(match.node)
            val clicked = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!clicked) {
                finish(
                    pending,
                    DeviceActionStatus.FAILED,
                    "Jarvis found ${pending.request.toggle.displayName}, but Android blocked the tile press.",
                    "tile_click_blocked"
                )
                return
            }

            pending.clicked = true
            pending.searchAttempt = 0
            handler.postDelayed(searchRunnable, VERIFY_DELAY_MS)
            return
        }

        val newState = readState(match.node, match.combinedText)
        when {
            newState == pending.request.desiredEnabled -> finish(
                pending,
                DeviceActionStatus.SUCCESS,
                "${pending.request.toggle.displayName} ${onOff(newState)}.",
                "state_verified"
            )
            pending.searchAttempt <= MAX_VERIFY_ATTEMPTS -> {
                handler.postDelayed(searchRunnable, VERIFY_RETRY_MS)
            }
            newState != null -> finish(
                pending,
                DeviceActionStatus.FAILED,
                "${pending.request.toggle.displayName} stayed ${onOff(newState)}.",
                "state_mismatch"
            )
            else -> finish(
                pending,
                DeviceActionStatus.EXECUTED_UNVERIFIED,
                "${pending.request.toggle.displayName} tile was pressed, but Android did not expose the final state.",
                "state_unavailable"
            )
        }
    }

    private data class TileMatch(
        val node: AccessibilityNodeInfo,
        val combinedText: String,
        val score: Int
    )

    private fun bestTileMatch(root: AccessibilityNodeInfo, toggle: SystemToggle): TileMatch? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: TileMatch? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val combined = nodeText(node)
            val score = matchScore(combined, toggle)
            if (score > (best?.score ?: 0)) {
                best = TileMatch(node, combined, score)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return best?.takeIf { it.score >= MIN_TILE_SCORE }
    }

    private fun matchScore(text: String, toggle: SystemToggle): Int {
        if (text.isBlank()) return 0
        return toggle.tileLabels.maxOf { label ->
            val needle = normalize(label)
            when {
                text == needle -> 100
                text.startsWith("$needle ") -> 92
                text.contains(" $needle ") -> 88
                text.contains(needle) -> 78
                else -> 0
            }
        }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String = normalize(
        listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription?.toString() else null,
            node.viewIdResourceName
        ).joinToString(" ")
    )

    private fun readState(node: AccessibilityNodeInfo, text: String): Boolean? {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            val target = current ?: return@repeat
            if (target.isCheckable) return target.isChecked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                parseState(target.stateDescription?.toString().orEmpty())?.let { return it }
            }
            parseState(target.contentDescription?.toString().orEmpty())?.let { return it }
            current = target.parent
        }
        return parseState(text)
    }

    private fun parseState(value: String): Boolean? {
        val normalized = normalize(value)
        val token = Regex("\\b(on|off|enabled|disabled|active|inactive|connected|disconnected)\\b")
            .find(normalized)
            ?.value
            ?: return null
        return token in setOf("on", "enabled", "active", "connected")
    }

    private fun findClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val target = current ?: return null
            if (target.isClickable || target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                return target
            }
            current = target.parent
        }
        return null
    }

    private fun scrollQuickSettingsForward(roots: List<AccessibilityNodeInfo>): Boolean {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        roots.forEach { root ->
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isScrollable) candidates += node
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }
        return candidates.any { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
    }

    private fun tryConfirmSystemDialog(
        pending: Pending,
        roots: List<AccessibilityNodeInfo>
    ): Boolean? {
        val targetAliases = pending.request.toggle.tileLabels.map(::normalize)
        val desired = pending.request.desiredEnabled
        val confirmLabels = if (desired) {
            listOf("turn on", "enable", "ok", "allow")
        } else {
            listOf("turn off", "disable", "ok")
        }

        val allText = roots.joinToString(" ") { root -> collectText(root, limit = 160) }
        if (targetAliases.none { allText.contains(it) }) return null

        roots.forEach { root ->
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val text = nodeText(node)
                if (confirmLabels.any { label -> text == normalize(label) || text.startsWith(normalize(label)) }) {
                    val clickable = findClickable(node)
                    if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }
        return false
    }

    private fun collectText(root: AccessibilityNodeInfo, limit: Int): String {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val parts = mutableListOf<String>()
        while (queue.isNotEmpty() && parts.size < limit) {
            val node = queue.removeFirst()
            val text = nodeText(node)
            if (text.isNotBlank()) parts += text
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return parts.joinToString(" ")
    }

    private fun timeout(pending: Pending) {
        if (active !== pending) return
        finish(
            pending,
            DeviceActionStatus.FAILED,
            "${pending.request.toggle.displayName} control timed out.",
            "control_timeout"
        )
    }

    private fun finish(
        pending: Pending,
        status: DeviceActionStatus,
        message: String,
        terminalTrace: String
    ) {
        if (active !== pending) return
        handler.removeCallbacks(searchRunnable)
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        active = null
        processing.set(false)
        runCatching {
            pending.callback(
                SystemControlOutcome(
                    request = pending.request,
                    status = status,
                    message = message,
                    latencyMs = (SystemClock.elapsedRealtime() - pending.startedAt).coerceAtLeast(0L),
                    trace = listOf(
                        "android_system_control_bridge",
                        "systemui_quick_settings",
                        "target=${pending.request.toggle.id}",
                        "desired=${onOff(pending.request.desiredEnabled)}",
                        terminalTrace
                    )
                )
            )
        }
        handler.postDelayed({ drainQueue() }, 180L)
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val MAX_QUEUE = 4
        private const val MAX_SEARCH_ATTEMPTS = 5
        private const val MAX_PAGE_ATTEMPTS = 2
        private const val MAX_VERIFY_ATTEMPTS = 4
        private const val MIN_TILE_SCORE = 78
        private const val INITIAL_OPEN_DELAY_MS = 620L
        private const val SEARCH_RETRY_MS = 260L
        private const val PAGE_RETRY_MS = 500L
        private const val VERIFY_DELAY_MS = 620L
        private const val VERIFY_RETRY_MS = 260L
        private const val COMMAND_TIMEOUT_MS = 7_500L

        @Volatile
        private var instance: JarvisSystemControlService? = null

        fun isConnected(): Boolean = instance != null

        fun request(
            request: SystemControlRequest,
            callback: (SystemControlOutcome) -> Unit
        ): Boolean = instance?.enqueue(request, callback) == true
    }
}
