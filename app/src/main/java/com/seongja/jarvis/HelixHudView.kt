package com.seongja.jarvis

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import com.jarvis.core.device.DeviceTelemetry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

/** Android source-of-truth host for the embedded HELIX operations interface. */
@SuppressLint("SetJavaScriptEnabled")
class HelixHudView(context: Context) : WebView(context) {
    private data class ServiceSnapshot(
        val cortexConfigured: Int = 0,
        val cortexOnline: Int = 0,
        val searchConfigured: Int = 0,
        val searchOnline: Int = 0,
        val cartesiaKeys: Int = 0,
        val cartesiaRoute: String = "--",
        val cartesiaStatus: String = "NOT CONFIGURED",
        val deepSeekStatus: String = "NOT CONFIGURED",
        val youtubeStatus: String = "NOT CONFIGURED",
        val gmailStatus: String = "NOT CONFIGURED"
    )

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val telemetry = DeviceTelemetry(appContext)
    private val networkHealth = NetworkHealthMonitor(appContext)
    private val cortexStore = SecureCortexRegistry(appContext)
    private val searchStore = SecureSearchGridRegistry(appContext)
    private val voiceStore = SecureVoiceRegistry(appContext)
    private val integrationStore = SecureIntegrationRegistry(appContext)
    private val pendingPayloads = ArrayDeque<String>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val sessionStartedAt = SystemClock.elapsedRealtime()

    private var pageReady = false
    private var released = false
    private var telemetryRunning = false
    private var currentMode = "IDLE"
    private var cloudConfigured = false
    private var voiceSource = "ON-DEVICE"
    private var coreTapListener: (() -> Unit)? = null
    private var workspaceListener: ((String) -> Unit)? = null
    private var lastAudioDispatchAt = 0L
    private var bridgeTimeout: Runnable? = null
    private var lastServiceRefreshAt = 0L
    private var serviceSnapshot = ServiceSnapshot()

    private val operationListener: (JarvisOperationSignal) -> Unit = { signal ->
        post {
            if (!released) {
                dispatch(
                    JSONObject()
                        .put("type", "operation")
                        .put("stage", signal.stage)
                        .put("detail", signal.detail)
                        .put("progress", signal.progress.toDouble())
                        .put("active", signal.active)
                )
                dispatchEvent(
                    "${signal.stage} -> ${signal.detail}",
                    if (signal.active) "CORE" else "SYS"
                )
                if (signal.active) updateMode("PROCESSING")
            }
        }
    }

    private val telemetryTicker = object : Runnable {
        override fun run() {
            if (!telemetryRunning || released) return
            dispatchTelemetry()
            handler.postDelayed(this, TELEMETRY_INTERVAL_MS)
        }
    }

    init {
        setBackgroundColor(Color.rgb(2, 6, 10))
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            setGeolocationEnabled(false)
            databaseEnabled = false
            userAgentString = "$userAgentString FridayHelix/0.10.0"
            @Suppress("DEPRECATION") saveFormData = false
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        addJavascriptInterface(AndroidBridge(), BRIDGE_NAME)
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage?.message()?.trim().orEmpty()
                if (message.isNotBlank() && consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    reportRuntimeError("HELIX JS -> ${message.take(MAX_EVENT_CHARS)}")
                }
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                !isAllowedNavigation(request?.url?.toString())

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                !isAllowedNavigation(url)

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == HELIX_ASSET_URL) {
                    dispatchEvent("HELIX DOCUMENT -> LOADED", "SYS")
                    armBridgeTimeout()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showNativeFallback(
                        "HELIX document failed to load (${error?.errorCode ?: -1}). " +
                            error?.description?.toString().orEmpty().take(120)
                    )
                }
            }
        }

        JarvisOperationBus.addListener(operationListener)
        networkHealth.start()
        startTelemetry()
        loadUrl(HELIX_ASSET_URL)
    }

    fun setCoreTapListener(listener: () -> Unit) {
        coreTapListener = listener
    }

    fun setWorkspaceListener(listener: (String) -> Unit) {
        workspaceListener = listener
    }

    fun setCloudConfigured(configured: Boolean) {
        cloudConfigured = configured
        dispatchTelemetry()
    }

    fun setVoiceState(state: VoiceLoop.State) {
        when (state) {
            VoiceLoop.State.LISTENING -> updateMode("LISTENING")
            VoiceLoop.State.PROCESSING -> updateMode("PROCESSING")
            VoiceLoop.State.ERROR, VoiceLoop.State.UNAVAILABLE -> updateMode("ERROR")
            VoiceLoop.State.READY -> if (currentMode == "LISTENING" || currentMode == "PROCESSING") updateMode("IDLE")
        }
    }

    fun setVoiceAmplitude(value: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAudioDispatchAt < AUDIO_DISPATCH_INTERVAL_MS) return
        lastAudioDispatchAt = now
        dispatch(JSONObject().put("type", "audio").put("rms", value.coerceIn(0f, 1f).toDouble()))
    }

    fun setProcessing(processing: Boolean) {
        if (processing) updateMode("PROCESSING") else if (currentMode == "PROCESSING") updateMode("IDLE")
    }

    fun setTranscript(value: String) {
        dispatch(JSONObject().put("type", "transcript").put("text", value.take(MAX_TEXT_CHARS)))
    }

    internal fun setCountdown(snapshot: CountdownSnapshot) {
        dispatch(
            JSONObject()
                .put("type", "countdown")
                .put(
                    "countdown",
                    JSONObject()
                        .put("active", snapshot.active)
                        .put("label", snapshot.label)
                        .put("remainingMs", snapshot.remainingMs)
                        .put("totalMs", snapshot.totalMs)
                        .put("progress", snapshot.progress.toDouble())
                )
        )
    }

    fun submitBrainResponse(response: BrainResponse) {
        val spoken = JarvisResponseSanitizer.spoken(response.spoken)
        val display = JarvisResponseSanitizer.clean(response.display)
        dispatch(
            JSONObject()
                .put("type", "response")
                .put("spoken", spoken.take(MAX_TEXT_CHARS))
                .put("display", display.take(MAX_TEXT_CHARS))
                .put("intent", response.intent)
                .put("confidence", response.confidence.toDouble())
                .put("trace", JSONArray(response.trace.takeLast(10)))
                .put("entities", JSONArray(response.entities.takeLast(8)))
                .put("decision", response.decision)
        )
        dispatch(
            JSONObject()
                .put("type", "operation")
                .put("stage", "RESPONSE VERIFIED")
                .put("detail", response.intent.uppercase(Locale.US).take(120))
                .put("progress", 1.0)
                .put("active", false)
        )
        updateMode(if (response.mode == BrainMode.ALERT) "ERROR" else "SPEAKING")
    }

    fun pushEvent(event: String) {
        val clean = event.take(MAX_EVENT_CHARS)
        val upper = clean.uppercase(Locale.US)
        when {
            "CARTESIA" in upper -> {
                val route = Regex("CARTESIA\\s+(P1|B1|B2|B3)").find(upper)?.groupValues?.getOrNull(1)
                voiceSource = if (route == null) "CARTESIA" else "CARTESIA $route"
            }
            "ON-DEVICE" in upper -> voiceSource = "ON-DEVICE"
            "PREMIUM" in upper || "PCM STREAM" in upper -> voiceSource = "PREMIUM PCM"
        }
        if ("CORTEX MESH -> READY" in upper) cloudConfigured = true
        when {
            "VOICE -> COMPLETE" in upper || "VOICE OUTPUT -> COMPLETE" in upper -> updateMode("IDLE")
            "VOICE OUTPUT ->" in upper || "VOICE -> STREAM" in upper || "VOICE -> SPEAKING" in upper -> updateMode("SPEAKING")
            "LISTENING" in upper || "MIC ->" in upper -> updateMode("LISTENING")
            "ROUTING REQUEST" in upper || "PROCESSING" in upper || "SYNTHESIZING" in upper || "SEARCH" in upper -> updateMode("PROCESSING")
            "ERROR" in upper || "FAILED" in upper || "DENIED" in upper -> updateMode("ERROR")
        }
        dispatchEvent(clean, eventChannel(upper))
        dispatchTelemetry()
    }

    fun release() {
        if (released) return
        released = true
        JarvisOperationBus.removeListener(operationListener)
        networkHealth.close()
        cancelBridgeTimeout()
        stopTelemetry()
        handler.removeCallbacksAndMessages(null)
        coreTapListener = null
        workspaceListener = null
        pendingPayloads.clear()
        removeJavascriptInterface(BRIDGE_NAME)
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        destroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTelemetry()
    }

    override fun onDetachedFromWindow() {
        stopTelemetry()
        super.onDetachedFromWindow()
    }

    private fun startTelemetry() {
        if (telemetryRunning || released) return
        telemetryRunning = true
        handler.post(telemetryTicker)
    }

    private fun stopTelemetry() {
        telemetryRunning = false
        handler.removeCallbacks(telemetryTicker)
    }

    private fun dispatchTelemetry() {
        if (released) return
        val runtime = Runtime.getRuntime()
        val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val battery = runCatching { telemetry.battery().percent }.getOrDefault(0)
        val network = runCatching { telemetry.network().label }.getOrDefault("UNKNOWN")
        val health = networkHealth.snapshot()
        val now = SystemClock.elapsedRealtime()
        if (now - lastServiceRefreshAt >= SERVICE_REFRESH_INTERVAL_MS) {
            lastServiceRefreshAt = now
            serviceSnapshot = readServices()
        }
        val services = serviceSnapshot
        dispatch(
            JSONObject()
                .put("type", "telemetry")
                .put(
                    "telemetry",
                    JSONObject()
                        .put("time", LocalTime.now().format(timeFormatter))
                        .put("battery", battery)
                        .put("network", network)
                        .put("heapMb", heapMb)
                        .put("device", Build.MODEL.take(34))
                        .put("voiceSource", voiceSource)
                        .put("cloudConfigured", cloudConfigured)
                        .put("latencyMs", health.latencyMs)
                        .put("jitterMs", health.jitterMs)
                        .put("packetLossPercent", health.packetLossPercent)
                        .put("downlinkMbps", health.downlinkMbps)
                        .put("uplinkMbps", health.uplinkMbps)
                        .put("networkQuality", health.quality)
                        .put("uptimeSeconds", (now - sessionStartedAt).coerceAtLeast(0L) / 1_000L)
                        .put("cortexConfigured", services.cortexConfigured)
                        .put("cortexOnline", services.cortexOnline)
                        .put("searchConfigured", services.searchConfigured)
                        .put("searchOnline", services.searchOnline)
                        .put("cartesiaKeys", services.cartesiaKeys)
                        .put("cartesiaRoute", services.cartesiaRoute)
                        .put("cartesiaStatus", services.cartesiaStatus)
                        .put("deepSeekStatus", services.deepSeekStatus)
                        .put("youtubeStatus", services.youtubeStatus)
                        .put("gmailStatus", services.gmailStatus)
                )
        )
    }

    private fun readServices(): ServiceSnapshot = runCatching {
        val cortex = cortexStore.load().configuredProfiles()
        val search = searchStore.load().configuredCredentials()
        val voice = voiceStore.load()
        val integrations = integrationStore.load()
        ServiceSnapshot(
            cortexConfigured = cortex.size,
            cortexOnline = cortex.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() },
            searchConfigured = search.size,
            searchOnline = search.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() },
            cartesiaKeys = voice.configuredKeys().size,
            cartesiaRoute = voice.routeLabel(),
            cartesiaStatus = voice.healthLabel(),
            deepSeekStatus = integrations.deepSeekHealthLabel(),
            youtubeStatus = integrations.youtubeHealthLabel(),
            gmailStatus = integrations.gmailHealthLabel()
        )
    }.getOrDefault(serviceSnapshot)

    private fun updateMode(mode: String) {
        if (mode == currentMode) return
        currentMode = mode
        dispatch(JSONObject().put("type", "state").put("mode", mode))
    }

    private fun dispatchEvent(text: String, channel: String) {
        dispatch(JSONObject().put("type", "event").put("channel", channel).put("text", text))
    }

    private fun eventChannel(upper: String): String = when {
        "ERROR" in upper || "FAILED" in upper || "DENIED" in upper || "DEGRADED" in upper -> "WARN"
        "VOICE" in upper || "MIC" in upper || "CARTESIA" in upper -> "VOICE"
        "ACTION" in upper || "SYSTEM" in upper || "DEVICE" in upper || "WEATHER" in upper -> "SYS"
        else -> "CORE"
    }

    private fun dispatch(payload: JSONObject) {
        if (released) return
        val encoded = JSONObject.quote(payload.toString())
        post {
            if (released) return@post
            if (!pageReady) {
                if (pendingPayloads.size >= MAX_PENDING_PAYLOADS) pendingPayloads.removeFirst()
                pendingPayloads.addLast(encoded)
                return@post
            }
            evaluateJavascript("window.jarvisHelix&&window.jarvisHelix.receive(JSON.parse($encoded));", null)
        }
    }

    private fun flushPending() {
        while (pendingPayloads.isNotEmpty() && pageReady && !released) {
            val encoded = pendingPayloads.removeFirst()
            evaluateJavascript("window.jarvisHelix&&window.jarvisHelix.receive(JSON.parse($encoded));", null)
        }
    }

    private fun armBridgeTimeout() {
        cancelBridgeTimeout()
        bridgeTimeout = Runnable {
            if (!pageReady && !released) showNativeFallback("The HELIX native bridge did not initialize within 12 seconds.")
        }.also { handler.postDelayed(it, BRIDGE_READY_TIMEOUT_MS) }
    }

    private fun cancelBridgeTimeout() {
        bridgeTimeout?.let(handler::removeCallbacks)
        bridgeTimeout = null
    }

    private fun reportRuntimeError(message: String) {
        post {
            if (!released) {
                currentMode = "ERROR"
                dispatchEvent(message.take(MAX_EVENT_CHARS), "WARN")
            }
        }
    }

    private fun showNativeFallback(reason: String) {
        post {
            if (released) return@post
            cancelBridgeTimeout()
            pageReady = false
            pendingPayloads.clear()
            currentMode = "ERROR"
            loadDataWithBaseURL(HELIX_ASSET_ROOT, fallbackHtml(reason), "text/html", "UTF-8", null)
        }
    }

    private fun fallbackHtml(reason: String): String {
        val safeReason = reason
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .take(320)
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
            <style>*{box-sizing:border-box}html,body{width:100%;height:100%;margin:0;background:#02060a;color:#e9fbff;font-family:monospace}body{display:grid;place-items:center;padding:24px;background:radial-gradient(circle at 50% 35%,rgba(60,130,255,.16),transparent 34%),#02060a}main{width:min(680px,100%);border:1px solid rgba(62,135,255,.45);padding:28px;text-align:center;box-shadow:inset 0 0 50px rgba(62,135,255,.06),0 0 35px rgba(62,135,255,.09)}.eyebrow{color:#4f8cff;font-size:11px;letter-spacing:.28em}.title{font-size:18px;letter-spacing:.15em;margin:18px 0 10px}.copy{color:rgba(255,255,255,.58);font-size:12px;line-height:1.8}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08);color:rgba(255,255,255,.48);font-size:10px;padding:12px;margin:20px 0}.actions{display:flex;gap:10px;justify-content:center}button{font:10px monospace;letter-spacing:.16em;padding:11px 15px}button:first-child{border:0;background:#4f8cff;color:#021018}button:last-child{border:1px solid rgba(79,140,255,.45);background:transparent;color:#72b4ff}</style></head>
            <body><main><div class="eyebrow">F.R.I.D.A.Y. // HELIX OPS</div><div class="title">NATIVE FALLBACK ONLINE</div><p class="copy">The visual document could not initialize. FRIDAY's native command core remains operational.</p><pre>$safeReason</pre><div class="actions"><button onclick="location.href='index.html'">RETRY HELIX</button><button onclick="window.JarvisAndroid&&window.JarvisAndroid.onCoreTap()">RECALIBRATE VOICE</button></div></main></body></html>
        """.trimIndent()
    }

    private fun isAllowedNavigation(url: String?): Boolean =
        url == "about:blank" || url?.startsWith(HELIX_ASSET_ROOT) == true

    inner class AndroidBridge {
        @JavascriptInterface
        fun onHelixReady() {
            post {
                if (released) return@post
                cancelBridgeTimeout()
                pageReady = true
                dispatch(JSONObject().put("type", "ready"))
                flushPending()
                dispatchTelemetry()
                dispatchEvent("HELIX OPERATIONS BRIDGE -> SECURED", "SYS")
            }
        }

        @JavascriptInterface
        fun onHelixError(message: String) {
            reportRuntimeError("HELIX RENDER -> ${message.replace(Regex("\\s+"), " ").take(MAX_EVENT_CHARS)}")
        }

        @JavascriptInterface
        fun onCoreTap() {
            post {
                if (!released) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    coreTapListener?.invoke()
                }
            }
        }

        @JavascriptInterface
        fun openWorkspace(workspace: String) {
            val clean = workspace.replace(Regex("[^A-Za-z0-9_-]"), "").take(32)
            if (clean.isBlank()) return
            post {
                if (!released) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    dispatchEvent("WORKSPACE -> ${clean.uppercase(Locale.US)}", "SYS")
                    workspaceListener?.invoke(clean)
                }
            }
        }
    }

    companion object {
        private const val BRIDGE_NAME = "JarvisAndroid"
        private const val HELIX_ASSET_ROOT = "file:///android_asset/helix/"
        private const val HELIX_ASSET_URL = "${HELIX_ASSET_ROOT}index.html"
        private const val TELEMETRY_INTERVAL_MS = 1_000L
        private const val SERVICE_REFRESH_INTERVAL_MS = 5_000L
        private const val AUDIO_DISPATCH_INTERVAL_MS = 45L
        private const val BRIDGE_READY_TIMEOUT_MS = 12_000L
        private const val MAX_PENDING_PAYLOADS = 128
        private const val MAX_TEXT_CHARS = 12_000
        private const val MAX_EVENT_CHARS = 180
    }
}
