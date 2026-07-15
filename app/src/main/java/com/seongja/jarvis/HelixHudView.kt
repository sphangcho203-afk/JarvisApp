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
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jarvis.core.device.DeviceTelemetry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

/**
 * Android host for the embedded React / React Three Fiber HELIX interface.
 *
 * The WebGL layer renders presentation only. Android remains the source of
 * truth for microphone state, telemetry, actions, countdowns, and verified
 * results. No API keys or device secrets cross this JavaScript bridge.
 */
@SuppressLint("SetJavaScriptEnabled")
class HelixHudView(context: Context) : WebView(context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val telemetry = DeviceTelemetry(appContext)
    private val pendingPayloads = ArrayDeque<String>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    private var pageReady = false
    private var released = false
    private var telemetryRunning = false
    private var currentMode = "IDLE"
    private var cloudConfigured = false
    private var voiceSource = "LOCAL"
    private var coreTapListener: (() -> Unit)? = null
    private var lastAudioDispatchAt = 0L

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
            userAgentString = "$userAgentString JarvisHelix/0.9.12"
            @Suppress("DEPRECATION")
            saveFormData = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        addJavascriptInterface(AndroidBridge(), BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = request?.url?.scheme != "file"

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.startsWith(HELIX_ASSET_URL) == true) {
                    dispatchEvent("HELIX DOCUMENT -> LOADED", "SYS")
                }
            }
        }

        startTelemetry()
        loadUrl(HELIX_ASSET_URL)
    }

    fun setCoreTapListener(listener: () -> Unit) {
        coreTapListener = listener
    }

    fun setCloudConfigured(configured: Boolean) {
        cloudConfigured = configured
        dispatchTelemetry()
    }

    fun setVoiceState(state: VoiceLoop.State) {
        when (state) {
            VoiceLoop.State.LISTENING -> updateMode("LISTENING")
            VoiceLoop.State.PROCESSING -> updateMode("PROCESSING")
            VoiceLoop.State.ERROR,
            VoiceLoop.State.UNAVAILABLE -> updateMode("ERROR")
            VoiceLoop.State.READY -> {
                if (currentMode == "LISTENING" || currentMode == "PROCESSING") {
                    updateMode("IDLE")
                }
            }
        }
    }

    fun setVoiceAmplitude(value: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAudioDispatchAt < AUDIO_DISPATCH_INTERVAL_MS) return
        lastAudioDispatchAt = now
        dispatch(
            JSONObject()
                .put("type", "audio")
                .put("rms", value.coerceIn(0f, 1f).toDouble())
        )
    }

    fun setProcessing(processing: Boolean) {
        if (processing) {
            updateMode("PROCESSING")
        } else if (currentMode == "PROCESSING") {
            updateMode("IDLE")
        }
    }

    fun setTranscript(value: String) {
        dispatch(
            JSONObject()
                .put("type", "transcript")
                .put("text", value.take(MAX_TEXT_CHARS))
        )
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
        dispatch(
            JSONObject()
                .put("type", "response")
                .put("spoken", response.spoken.take(MAX_TEXT_CHARS))
                .put("display", response.display.take(MAX_TEXT_CHARS))
                .put("intent", response.intent)
                .put("confidence", response.confidence.toDouble())
                .put("trace", JSONArray(response.trace.takeLast(10)))
                .put("entities", JSONArray(response.entities.takeLast(8)))
                .put("decision", response.decision)
        )
        updateMode(if (response.mode == BrainMode.ALERT) "ERROR" else "SPEAKING")
    }

    fun pushEvent(event: String) {
        val clean = event.take(MAX_EVENT_CHARS)
        val upper = clean.uppercase(Locale.US)

        when {
            "ON-DEVICE" in upper -> voiceSource = "ON-DEVICE"
            "PREMIUM" in upper || "PCM STREAM" in upper -> voiceSource = "PREMIUM PCM"
        }
        if ("CORTEX MESH -> READY" in upper) cloudConfigured = true

        when {
            "VOICE -> COMPLETE" in upper || "VOICE OUTPUT -> COMPLETE" in upper -> updateMode("IDLE")
            "VOICE OUTPUT ->" in upper || "VOICE -> STREAM" in upper || "VOICE -> SPEAKING" in upper -> updateMode("SPEAKING")
            "LISTENING" in upper || "MIC ->" in upper -> updateMode("LISTENING")
            "ROUTING REQUEST" in upper || "PROCESSING" in upper || "SYNTHESIZING" in upper -> updateMode("PROCESSING")
            "ERROR" in upper || "FAILED" in upper || "DENIED" in upper -> updateMode("ERROR")
        }

        dispatchEvent(clean, eventChannel(upper))
        dispatchTelemetry()
    }

    fun release() {
        if (released) return
        released = true
        stopTelemetry()
        handler.removeCallbacksAndMessages(null)
        coreTapListener = null
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
                )
        )
    }

    private fun updateMode(mode: String) {
        if (mode == currentMode) return
        currentMode = mode
        dispatch(
            JSONObject()
                .put("type", "state")
                .put("mode", mode)
        )
    }

    private fun dispatchEvent(text: String, channel: String) {
        dispatch(
            JSONObject()
                .put("type", "event")
                .put("channel", channel)
                .put("text", text)
        )
    }

    private fun eventChannel(upper: String): String = when {
        "ERROR" in upper || "FAILED" in upper || "DENIED" in upper -> "WARN"
        "VOICE" in upper || "MIC" in upper -> "VOICE"
        "ACTION" in upper || "SYSTEM" in upper || "DEVICE" in upper -> "SYS"
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
            evaluateJavascript(
                "window.jarvisHelix&&window.jarvisHelix.receive(JSON.parse($encoded));",
                null
            )
        }
    }

    private fun flushPending() {
        while (pendingPayloads.isNotEmpty() && pageReady && !released) {
            val encoded = pendingPayloads.removeFirst()
            evaluateJavascript(
                "window.jarvisHelix&&window.jarvisHelix.receive(JSON.parse($encoded));",
                null
            )
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onHelixReady() {
            post {
                if (released) return@post
                pageReady = true
                dispatch(JSONObject().put("type", "ready"))
                flushPending()
                dispatchTelemetry()
                dispatchEvent("HELIX WEBGL -> NATIVE BRIDGE LOCKED", "SYS")
            }
        }

        @JavascriptInterface
        fun onCoreTap() {
            post {
                if (released) return@post
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                coreTapListener?.invoke()
            }
        }
    }

    companion object {
        private const val BRIDGE_NAME = "JarvisAndroid"
        private const val HELIX_ASSET_URL = "file:///android_asset/helix/index.html"
        private const val TELEMETRY_INTERVAL_MS = 1_000L
        private const val AUDIO_DISPATCH_INTERVAL_MS = 45L
        private const val MAX_PENDING_PAYLOADS = 96
        private const val MAX_TEXT_CHARS = 12_000
        private const val MAX_EVENT_CHARS = 180
    }
}
