package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import com.jarvis.core.device.DeviceActionResult
import com.jarvis.core.device.DeviceActionStatus
import com.jarvis.core.device.DeviceCommandRouter
import com.jarvis.core.device.SystemControlAccess
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private lateinit var hud: HelixHudView
    private lateinit var brain: JarvisBrain
    private lateinit var voiceLoop: VoiceLoop
    private lateinit var countdown: JarvisCountdownController
    private lateinit var soundEngine: JarvisSoundEngine

    private var resumed = false
    private var announcedOnline = false
    private var setupOpenedThisSession = false
    private val brainBusy = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestGeneration = AtomicInteger(0)
    private var processingTimeout: Runnable? = null
    private var ttsResumeWatchdog: Runnable? = null
    private var lastTtsFinishedAt = 0L
    private val weatherListener: (WeatherSnapshot) -> Unit = { snapshot ->
        runOnUiThread {
            if (::hud.isInitialized) {
                hud.pushEvent("WEATHER -> ${snapshot.compactLabel()}")
            }
            if (resumed && !brainBusy.get() && ::voiceLoop.isInitialized) {
                WeatherRuntime.consumeAdvisory(snapshot)?.let { advisory ->
                    mainHandler.postDelayed({
                        if (resumed && !brainBusy.get()) speak(advisory)
                    }, 550L)
                }
            }
        }
    }
    private val deviceCommandRouter by lazy {
        DeviceCommandRouter(applicationContext, ::handleDeferredDeviceResult)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        hud = HelixHudView(this)
        brain = JarvisBrain(this)
        soundEngine = JarvisSoundEngine()
        countdown = JarvisCountdownController(
            onTick = hud::setCountdown,
            onFinished = ::handleCountdownFinished
        )
        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = ::handlePartialSpeech,
            onRms = hud::setVoiceAmplitude,
            onState = ::handleVoiceState,
            onDiagnostic = { message -> runOnUiThread { hud.pushEvent(message) } }
        )
        WeatherRuntime.addListener(weatherListener)
        WeatherRuntime.refresh()

        setContentView(hud)
        hud.setCloudConfigured(brain.isCloudConfigured())
        if (intent?.getBooleanExtra(JarvisWakeService.EXTRA_WAKE_DETECTED, false) == true) {
            hud.pushEvent("WAKE PHRASE -> DETECTED // LOCAL SUMMON")
        }
        hud.pushEvent("PHASE 11 -> HELIX WEBGL COGNITIVE INTERFACE")
        hud.pushEvent("HELIX WEBGL -> REACT THREE FIBER / BLOOM / NATIVE BRIDGE")
        hud.pushEvent("ON-DEVICE SPEECH -> API-KEY-FREE COMMAND FALLBACK")
        hud.pushEvent("LOCAL ANDROID SPEECH OUTPUT -> DISABLED")
        hud.pushEvent("MICROPHONE -> RAW PCM / ON-DEVICE HANDOFF")
        hud.pushEvent("PREMIUM VOICE OUTPUT -> OPTIONAL LOCAL RUNTIME")
        hud.pushEvent("CLOUD CORTEX -> OPTIONAL")
        hud.pushEvent("APP AUTOMATION -> GMAIL / WHATSAPP / SCREEN CONTEXT")
        hud.pushEvent("SYSTEM CONTROL -> QUICK SETTINGS EXECUTOR")
        hud.pushEvent("WEATHER CORE -> WEATHERAPI / FORECAST / ALERTS")
        hud.pushEvent("TAP -> RECALIBRATE VOICE ARRAY")

        hud.postDelayed({ soundEngine.boot() }, 350L)

        hud.setCoreTapListener {
            when {
                brainBusy.get() -> abortActiveRequest("USER CANCELLED ACTIVE REQUEST")
                hasMicPermission() -> {
                    hud.pushEvent("USER -> VOICE ARRAY RECALIBRATION")
                    voiceLoop.manualRestart()
                }
                else -> {
                    hud.pushEvent("AUTH -> REQUESTING MICROPHONE")
                    requestMicPermission()
                }
            }
        }

        hud.setWorkspaceListener(::openWorkspace)
        hud.isLongClickable = false
        if (!hasMicPermission()) requestMicPermission()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(JarvisWakeService.EXTRA_WAKE_DETECTED, false) == true) {
            announcedOnline = false
            if (::hud.isInitialized) hud.pushEvent("WAKE PHRASE -> DETECTED // LOCAL SUMMON")
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        JarvisWakeService.pause(this)
        enterImmersiveMode()
        if (::brain.isInitialized) {
            hud.setCloudConfigured(brain.isCloudConfigured())
            hud.pushEvent(
                if (brain.isCloudConfigured()) {
                    "CORTEX MESH -> READY // ${brain.configuredModel()}"
                } else {
                    "LOCAL AGENT -> READY // CLOUD CORTEX OPTIONAL"
                }
            )
            hud.pushEvent(
                if (SystemControlAccess.isEnabled(this)) {
                    "SYSTEM CONTROL -> ENABLED"
                } else {
                    "SYSTEM CONTROL -> SAY ENABLE SYSTEM CONTROL"
                }
            )
        }
        WeatherRuntime.refresh()
        consumeXCameraResult()
        if (hasMicPermission() && !brainBusy.get()) {
            voiceLoop.resume()
            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)
        }
    }

    override fun onPause() {
        resumed = false
        if (::voiceLoop.isInitialized) voiceLoop.stop()
        if (JarvisWakeService.isEnabled(this)) JarvisWakeService.resume(this)
        super.onPause()
    }

    private fun openWorkspace(workspace: String) {
        when (workspace.trim().lowercase(Locale.US)) {
            "xcamera", "vision", "eyes" -> XCameraActivity.launch(this, autoScan = false)
            "image", "visual", "studio" -> ImageGenerationActivity.launch(this, "")
            "diary" -> PrivateDiaryActivity.launch(this)
            "memory", "vault" -> MemoryVaultActivity.launch(this)
            "control", "permissions" -> startActivity(Intent(this, PermissionCenterActivity::class.java))
            "apis", "cortex" -> startActivity(Intent(this, CloudConfigActivity::class.java))
            else -> hud.pushEvent("WORKSPACE -> UNKNOWN ${workspace.take(32).uppercase(Locale.US)}")
        }
    }

    private fun consumeXCameraResult() {
        val result = XCameraRuntime.consume() ?: return
        val response = BrainResponse(
            spoken = result.description,
            display = buildString {
                appendLine(if (result.isError) "X-CAMERA // DEGRADED" else "X-CAMERA // VISUAL ANALYSIS VERIFIED")
                appendLine("QUESTION // ${result.question.take(320)}")
                appendLine("MODEL // ${result.model}")
                appendLine("TIME // ${result.elapsedMs}MS")
                append(result.description)
            },
            intent = if (result.isError) "vision/error" else "vision/result",
            confidence = if (result.isError) 0f else .97f,
            mode = if (result.isError) BrainMode.ALERT else BrainMode.ONLINE,
            trace = listOf(
                "workspace=x_camera",
                "model=${result.model}",
                "latency=${result.elapsedMs}ms",
                "capture_persistence=disabled"
            ),
            memory = brain.memorySnapshot(),
            thoughts = listOf("A temporary camera frame was analyzed and discarded."),
            entities = listOf("sensor=xcamera"),
            decision = if (result.isError) "report_xcamera_error" else "return_visual_analysis",
            action = BrainAction()
        )
        hud.submitBrainResponse(response)
        hud.pushEvent(if (result.isError) "X-CAMERA -> DEGRADED" else "X-CAMERA -> RESULT VERIFIED")
        if (!result.isError && !result.spokenInWorkspace) {
            mainHandler.postDelayed({ if (resumed) speak(result.description) }, 280L)
        }
    }

    private fun handlePartialSpeech(text: String) {
        if (!brainBusy.get()) hud.setTranscript(text)
    }

    private fun handleSpeech(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            voiceLoop.resume()
            return
        }
        if (isLikelyEchoOrNoise(clean)) {
            hud.pushEvent("VOICE -> FILTERED ECHO/NOISE")
            voiceLoop.resumeAfterTts(500L)
            return
        }
        if (!brainBusy.compareAndSet(false, true)) {
            hud.pushEvent("CORTEX -> BUSY; INPUT DROPPED")
            return
        }
        val requestId = requestGeneration.incrementAndGet()
        val timeoutMs = JarvisDirective.timeoutFor(clean)
        armProcessingTimeout(requestId, timeoutMs)
        processInput(clean, requestId)
    }

    private fun processInput(clean: String, requestId: Int) {
        voiceLoop.pauseForProcessing()
        hud.setTranscript(clean)
        hud.setProcessing(true)
        hud.pushEvent("INPUT -> ${clean.take(62)}")
        soundEngine.processing()

        CountdownCommandParser.parse(clean)?.let { timerCommand ->
            handleCountdownCommand(timerCommand)
            return
        }

        WeatherRuntime.answer(clean)?.let { weather ->
            finishLocalCommand(
                spoken = weather.spoken,
                display = weather.display,
                intent = weather.intent,
                mode = BrainMode.ONLINE,
                trace = listOf(
                    "weatherapi_verified_cache",
                    "coordinates=encrypted",
                    "forecast=hourly+current+alerts"
                )
            )
            return
        }

        if (isCloudSetupCommand(clean)) {
            hud.pushEvent("CORTEX MESH CONFIG -> OPEN")
            cancelProcessingTimeout()
            hud.setProcessing(false)
            brainBusy.set(false)
            startActivity(Intent(this, CloudConfigActivity::class.java))
            return
        }

        val deviceResult = deviceCommandRouter.executeDetailed(clean)
        if (deviceResult != null) {
            val mode = when (deviceResult.status) {
                DeviceActionStatus.FAILED -> BrainMode.ALERT
                DeviceActionStatus.EXECUTED_UNVERIFIED -> BrainMode.TACTICAL
                DeviceActionStatus.NEEDS_CLARIFICATION -> BrainMode.THINKING
                else -> BrainMode.EXECUTING
            }
            hud.pushEvent(
                "ACTION -> ${deviceResult.actionId.uppercase(Locale.US)} ${deviceResult.status.name}"
            )
            finishLocalCommand(
                spoken = deviceResult.spoken,
                display = formatDeviceResult(deviceResult),
                intent = "device/${deviceResult.actionId}",
                mode = mode,
                trace = deviceResult.trace,
                speakResult = deviceResult.status != DeviceActionStatus.IN_PROGRESS,
                playSuccess = deviceResult.status != DeviceActionStatus.IN_PROGRESS
            )
            return
        }

        hud.pushEvent("CORTEX MESH -> ROUTING REQUEST")
        val cartesiaStreaming = voiceLoop.beginStreamingSpeech()
        if (cartesiaStreaming) {
            hud.pushEvent("VOICE -> CARTESIA SONIC CONTEXT OPEN")
        }
        Thread {
            val started = System.currentTimeMillis()
            val response = runCatching {
                brain.respond(clean) { token ->
                    if (cartesiaStreaming) voiceLoop.pushStreamingSpeech(token)
                }
            }.getOrElse { error ->
                if (cartesiaStreaming) voiceLoop.cancelStreamingSpeech()
                BrainResponse(
                    spoken = "The cortex mesh request failed: ${error.message ?: error.javaClass.simpleName}.",
                    display = "CORTEX MESH ERROR // ${error.message ?: error.javaClass.simpleName}",
                    intent = "cloud_error",
                    confidence = 0f,
                    mode = BrainMode.ALERT,
                    trace = listOf(
                        "cortex_mesh_request",
                        "exception=${error.javaClass.simpleName}",
                        "streaming_voice_active"
                    ),
                    memory = brain.memorySnapshot(),
                    thoughts = listOf(
                        "The mesh request failed after its eligible cloud nodes were evaluated."
                    ),
                    entities = emptyList(),
                    decision = "cloud_exception",
                    action = BrainAction()
                )
            }
            val elapsed = System.currentTimeMillis() - started

            runOnUiThread {
                if (requestGeneration.get() != requestId || !brainBusy.get()) {
                    hud.pushEvent("CORTEX -> STALE RESPONSE DISCARDED")
                    return@runOnUiThread
                }
                cancelProcessingTimeout()
                hud.submitBrainResponse(response)
                hud.pushEvent("CORTEX MESH -> RESPONSE ${elapsed}ms")
                if (response.action.type != ActionType.NONE) {
                    val executed = brain.execute(response.action)
                    hud.pushEvent(
                        "ACTION -> ${response.action.label.uppercase(Locale.US)} ${if (executed) "OK" else "BLOCKED"}"
                    )
                }
                hud.setProcessing(false)
                brainBusy.set(false)
                if (response.mode != BrainMode.ALERT) soundEngine.success()
                val streamed = cartesiaStreaming && voiceLoop.finishStreamingSpeech {
                    runOnUiThread {
                        lastTtsFinishedAt = SystemClock.elapsedRealtime()
                        cancelTtsWatchdog()
                        hud.pushEvent("VOICE -> CARTESIA COMPLETE")
                        if (resumed && hasMicPermission() && !brainBusy.get()) {
                            voiceLoop.resumeAfterTts(900L)
                        }
                    }
                }
                if (!streamed) speak(response.spoken) else armTtsWatchdog()
            }
        }.start()
    }

    private fun handleDeferredDeviceResult(result: DeviceActionResult) {
        runOnUiThread {
            val mode = when (result.status) {
                DeviceActionStatus.FAILED -> BrainMode.ALERT
                DeviceActionStatus.EXECUTED_UNVERIFIED -> BrainMode.TACTICAL
                DeviceActionStatus.NEEDS_CLARIFICATION -> BrainMode.THINKING
                else -> BrainMode.ONLINE
            }
            hud.pushEvent(
                "SYSTEM CONTROL -> ${result.actionId.uppercase(Locale.US)} ${result.status.name}"
            )
            hud.submitBrainResponse(
                BrainResponse(
                    spoken = result.spoken,
                    display = formatDeviceResult(result),
                    intent = "device/${result.actionId}",
                    confidence = if (
                        result.status == DeviceActionStatus.EXECUTED_UNVERIFIED
                    ) 0.72f else 1f,
                    mode = mode,
                    trace = result.trace,
                    memory = brain.memorySnapshot(),
                    thoughts = listOf(
                        "The command was executed through the allow-listed Android SystemUI control bridge."
                    ),
                    entities = listOf(
                        "target=${result.target}",
                        "status=${result.status.name}"
                    ),
                    decision = "system_control_${result.status.name.lowercase(Locale.US)}",
                    action = BrainAction()
                )
            )
            if (result.status == DeviceActionStatus.FAILED) {
                soundEngine.processing()
            } else {
                soundEngine.success()
            }
            mainHandler.postDelayed({
                if (resumed) speak(result.spoken)
            }, 320L)
        }
    }

    private fun formatDeviceResult(result: DeviceActionResult): String = buildString {
        appendLine(result.spoken)
        appendLine()
        append("ACTION ${result.actionId.uppercase(Locale.US)} // ")
        append(result.status.name)
        append(" // ${result.latencyMs}ms")
    }

    private fun isCloudSetupCommand(input: String): Boolean {
    val normalized = input
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    val exactCommands = setOf(
        "configure api",
        "configure apis",
        "configure api key",
        "configure api keys",
        "configure cortex",
        "cortex setup",
        "mesh setup",
        "api setup",
        "api settings",
        "api setting",
        "cloud setup",
        "configure cloud",
        "open api setting",
        "open api settings",
        "open api setup",
        "connect api",
        "connect apis",
        "connect api key",
        "connect api keys"
    )
    if (normalized in exactCommands) return true

    val mentionsApi = normalized.split(" ").any { it == "api" || it == "apis" }
    val setupIntent = listOf(
        "configure",
        "connect",
        "setup",
        "setting",
        "settings"
    ).any(normalized::contains)
    return mentionsApi && setupIntent
}

    private fun openCloudSetupIfRequired() {
        if (!brain.isCloudConfigured()) {
            hud.pushEvent("LOCAL AGENT -> READY // CLOUD CORTEX OPTIONAL")
        }
    }

    private fun handleCountdownCommand(command: CountdownCommand) {
        when (command) {
            is CountdownCommand.Start -> {
                val snapshot = countdown.start(command.durationMs)
                val spokenDuration = describeDuration(snapshot.remainingSeconds)
                finishLocalCommand(
                    spoken = "Countdown set for $spokenDuration, Sir.",
                    display = "MISSION TIMER ARMED // ${formatCountdown(snapshot.remainingSeconds)}",
                    intent = "countdown_start",
                    mode = BrainMode.EXECUTING,
                    trace = listOf(
                        "voice_timer_parser",
                        "elapsed_realtime_clock",
                        "countdown_active"
                    )
                )
            }

            CountdownCommand.Cancel -> {
                val wasActive = countdown.current().active
                countdown.cancel()
                finishLocalCommand(
                    spoken = if (wasActive) {
                        "Countdown cancelled, Sir."
                    } else {
                        "No countdown is currently running, Sir."
                    },
                    display = if (wasActive) {
                        "MISSION TIMER CANCELLED"
                    } else {
                        "MISSION TIMER // IDLE"
                    },
                    intent = "countdown_cancel",
                    mode = BrainMode.ONLINE,
                    trace = listOf(
                        "countdown_controller",
                        if (wasActive) "cancelled" else "already_idle"
                    )
                )
            }

            CountdownCommand.Status -> {
                val snapshot = countdown.current()
                val spoken = if (snapshot.active) {
                    "${describeDuration(snapshot.remainingSeconds)} remain on the countdown, Sir."
                } else {
                    "No countdown is currently running, Sir."
                }
                finishLocalCommand(
                    spoken = spoken,
                    display = if (snapshot.active) {
                        "TIME REMAINING // ${formatCountdown(snapshot.remainingSeconds)}"
                    } else {
                        "MISSION TIMER // IDLE"
                    },
                    intent = "countdown_status",
                    mode = BrainMode.ONLINE,
                    trace = listOf("countdown_controller", "status_read")
                )
            }

            is CountdownCommand.Invalid -> {
                finishLocalCommand(
                    spoken = command.reason,
                    display = "TIMER INPUT REQUIRED // ${command.reason}",
                    intent = "countdown_invalid",
                    mode = BrainMode.ALERT,
                    trace = listOf("voice_timer_parser", "duration_missing")
                )
            }
        }
    }

    private fun handleCountdownFinished(label: String) {
        hud.pushEvent("$label -> COMPLETE")
        hud.setTranscript("COUNTDOWN COMPLETE")
        hud.submitBrainResponse(
            BrainResponse(
                spoken = "Countdown complete, Sir.",
                display = "$label COMPLETE",
                intent = "countdown_complete",
                confidence = 1f,
                mode = BrainMode.ALERT,
                trace = listOf(
                    "elapsed_realtime_clock",
                    "zero_reached",
                    "completion_signal"
                ),
                memory = brain.memorySnapshot(),
                thoughts = listOf("The active countdown reached zero."),
                entities = listOf("timer=$label"),
                decision = "signal_timer_completion"
            )
        )
        soundEngine.timerComplete()
        if (resumed) speak("Countdown complete, Sir.")
    }

    private fun finishLocalCommand(
        spoken: String,
        display: String,
        intent: String,
        mode: BrainMode,
        trace: List<String>,
        speakResult: Boolean = true,
        playSuccess: Boolean = true
    ) {
        hud.submitBrainResponse(
            BrainResponse(
                spoken = spoken,
                display = display,
                intent = intent,
                confidence = 1f,
                mode = mode,
                trace = trace,
                memory = brain.memorySnapshot(),
                thoughts = listOf("The command was handled locally on Android."),
                entities = emptyList(),
                decision = intent,
                action = BrainAction()
            )
        )
        cancelProcessingTimeout()
        hud.setProcessing(false)
        brainBusy.set(false)
        if (playSuccess && mode != BrainMode.ALERT) soundEngine.success()
        if (speakResult) {
            speak(spoken)
        } else if (resumed && hasMicPermission()) {
            voiceLoop.resumeAfterTts(700L)
        }
    }

    private fun handleVoiceState(state: VoiceLoop.State) {
        if (!brainBusy.get() || state == VoiceLoop.State.PROCESSING) {
            hud.setVoiceState(state)
        }
        if (
            state == VoiceLoop.State.READY &&
            resumed &&
            hasMicPermission() &&
            voiceLoop.isBackendReady() &&
            !announcedOnline
        ) {
            announcedOnline = true
            mainHandler.postDelayed({
                if (resumed && !brainBusy.get()) {
                    when {
                        voiceLoop.isCartesiaConfigured() -> {
                            hud.pushEvent("VOICE -> CARTESIA SONIC-3 // GEMMA EN-GB")
                            speak("Systems online. Cartesia Sonic voice is active, Boss.")
                        }
                        voiceLoop.isPremiumBackendReady() ->
                            speak("Systems online. Premium streaming voice is active, Boss.")
                        else -> {
                            hud.pushEvent("VOICE OUTPUT -> CARTESIA REQUIRED // LOCAL TTS DISABLED")
                        }
                    }
                }
            }, 320L)
        }
    }

    private fun speak(text: String) {
        val clean = speechSafeText(text)
        if (clean.isBlank()) {
            if (resumed && hasMicPermission()) voiceLoop.resumeAfterTts(500L)
            return
        }

        voiceLoop.pauseForTts()
        cancelTtsWatchdog()
        hud.pushEvent(
            when {
                voiceLoop.isCartesiaConfigured() -> "VOICE -> CARTESIA SONIC STREAM"
                voiceLoop.isPremiumBackendReady() -> "VOICE -> STREAM REQUEST"
                else -> "VOICE OUTPUT -> LOCAL TTS DISABLED"
            }
        )

        voiceLoop.speak(clean) {
            runOnUiThread {
                lastTtsFinishedAt = SystemClock.elapsedRealtime()
                cancelTtsWatchdog()
                hud.pushEvent("VOICE -> COMPLETE")
                if (resumed && hasMicPermission() && !brainBusy.get()) {
                    voiceLoop.resumeAfterTts(1_000L)
                }
            }
        }

        if (voiceLoop.isCartesiaConfigured() || voiceLoop.isPremiumBackendReady()) {
            armTtsWatchdog()
        }
    }

    private fun armTtsWatchdog() {
        cancelTtsWatchdog()
        ttsResumeWatchdog = Runnable {
            if (resumed && hasMicPermission() && !brainBusy.get()) {
                hud.pushEvent("VOICE -> STREAM WATCHDOG RELEASE")
                lastTtsFinishedAt = SystemClock.elapsedRealtime()
                voiceLoop.stopSpeaking()
                voiceLoop.resumeAfterTts(700L)
            }
        }.also { mainHandler.postDelayed(it, 60_000L) }
    }

    private fun speechSafeText(text: String): String {
        val plain = JarvisResponseSanitizer.spoken(text)
            .replace(Regex("```[\\s\\S]*?```"), " Code is displayed on screen. ")
            .replace(Regex("[*_#>`]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (plain.length <= 900) {
            plain
        } else {
            plain.take(900).trimEnd() + ". Full response is on screen."
        }
    }

    private fun isLikelyEchoOrNoise(text: String): Boolean {
        val normalized = text
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized in setOf("boss", "yes boss", "okay boss", "ok boss")) return true
        val sinceTts = SystemClock.elapsedRealtime() - lastTtsFinishedAt
        return sinceTts in 0..1_400L && normalized.split(" ").size <= 3
    }

    private fun armProcessingTimeout(
        requestId: Int,
        timeoutMs: Long = 40_000L
    ) {
        cancelProcessingTimeout()
        processingTimeout = Runnable {
            if (brainBusy.get() && requestGeneration.get() == requestId) {
                abortActiveRequest("REQUEST TIMEOUT // VOICE LOOP RECOVERED")
            }
        }.also { mainHandler.postDelayed(it, timeoutMs) }
    }

    private fun cancelProcessingTimeout() {
        processingTimeout?.let { mainHandler.removeCallbacks(it) }
        processingTimeout = null
    }

    private fun cancelTtsWatchdog() {
        ttsResumeWatchdog?.let { mainHandler.removeCallbacks(it) }
        ttsResumeWatchdog = null
    }

    private fun abortActiveRequest(reason: String) {
        requestGeneration.incrementAndGet()
        cancelProcessingTimeout()
        cancelTtsWatchdog()
        brainBusy.set(false)
        voiceLoop.stopSpeaking()
        hud.setProcessing(false)
        hud.setVoiceState(VoiceLoop.State.READY)
        hud.pushEvent(reason)
        if (resumed && hasMicPermission()) voiceLoop.manualRestart()
    }

    private fun describeDuration(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3_600L
        val minutes = (safe % 3_600L) / 60L
        val seconds = safe % 60L
        val parts = mutableListOf<String>()
        if (hours > 0L) {
            parts += "$hours ${if (hours == 1L) "hour" else "hours"}"
        }
        if (minutes > 0L) {
            parts += "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }
        if (seconds > 0L || parts.isEmpty()) {
            parts += "$seconds ${if (seconds == 1L) "second" else "seconds"}"
        }
        return when (parts.size) {
            1 -> parts[0]
            2 -> "${parts[0]} and ${parts[1]}"
            else -> "${parts[0]}, ${parts[1]}, and ${parts[2]}"
        }
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == REQ_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            hud.pushEvent("AUTH -> MICROPHONE GRANTED")
            voiceLoop.resume()
            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)
        } else if (requestCode == REQ_RECORD_AUDIO) {
            hud.pushEvent("AUTH -> MICROPHONE DENIED")
            hud.setVoiceState(VoiceLoop.State.UNAVAILABLE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        WeatherRuntime.removeListener(weatherListener)
        cancelProcessingTimeout()
        cancelTtsWatchdog()
        mainHandler.removeCallbacksAndMessages(null)
        if (::voiceLoop.isInitialized) voiceLoop.destroy()
        if (::countdown.isInitialized) countdown.destroy()
        if (::soundEngine.isInitialized) soundEngine.release()
        if (::hud.isInitialized) hud.release()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    companion object {
        private const val REQ_RECORD_AUDIO = 101
    }
}
