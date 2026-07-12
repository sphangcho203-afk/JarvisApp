package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.WindowManager
import com.jarvis.core.device.DeviceCommandRouter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var hud: AdvancedCivilizationHudView
    private lateinit var brain: JarvisBrain
    private lateinit var voiceLoop: VoiceLoop
    private lateinit var countdown: JarvisCountdownController
    private lateinit var soundEngine: JarvisSoundEngine

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var resumed = false
    private var announcedOnline = false
    private var setupOpenedThisSession = false
    private val brainBusy = AtomicBoolean(false)
    private val deviceCommandRouter by lazy { DeviceCommandRouter(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        hud = AdvancedCivilizationHudView(this)
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

        setContentView(hud)
        hud.pushEvent("PHASE 9 -> TEN-NODE CORTEX MESH")
        hud.pushEvent("LOCAL SERVER BRAIN -> PERMANENTLY REMOVED")
        hud.pushEvent("GEMINI NODES -> 6 // GROQ NODES -> 4")
        hud.pushEvent("VOICE -> DIRECT LISTENING; NO HOLD CONTROL")
        hud.pushEvent("SAY CONFIGURE APIS -> SECURE MESH SETUP")
        hud.pushEvent("TAP -> RECALIBRATE VOICE ARRAY")

        initTts()
        hud.postDelayed({ soundEngine.boot() }, 350L)

        hud.setOnClickListener {
            if (hasMicPermission()) {
                hud.pushEvent("USER -> VOICE ARRAY RECALIBRATION")
                voiceLoop.manualRestart()
            } else {
                hud.pushEvent("AUTH -> REQUESTING MICROPHONE")
                requestMicPermission()
            }
        }

        hud.isLongClickable = false

        if (!hasMicPermission()) requestMicPermission()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                hud.pushEvent("VOICE -> SYNTHESIS FAILED: $status")
                if (resumed && hasMicPermission()) voiceLoop.resume()
                return@TextToSpeech
            }

            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val languageResult = tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.94f)
            tts?.setPitch(0.88f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { hud.pushEvent("VOICE -> SPEAKING") }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        hud.pushEvent("VOICE -> COMPLETE")
                        if (resumed && hasMicPermission() && !brainBusy.get()) {
                            voiceLoop.resumeAfterTts(700L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        hud.pushEvent("VOICE -> OUTPUT ERROR")
                        if (resumed && hasMicPermission() && !brainBusy.get()) {
                            voiceLoop.resumeAfterTts(900L)
                        }
                    }
                }
            })
            hud.pushEvent("VOICE -> SYNTHESIS READY")

            if (resumed && !announcedOnline) {
                announcedOnline = true
                speak("Systems online. Direct listening is active, Sir.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        enterImmersiveMode()
        if (::brain.isInitialized) {
            hud.pushEvent(
                if (brain.isCloudConfigured()) "CORTEX MESH -> READY // ${brain.configuredModel()}"
                else "CORTEX MESH -> CONFIGURATION REQUIRED"
            )
        }
        if (hasMicPermission() && !brainBusy.get()) {
            voiceLoop.resume()
            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)
        }
    }

    override fun onPause() {
        resumed = false
        if (::voiceLoop.isInitialized) voiceLoop.stop()
        super.onPause()
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
        if (!brainBusy.compareAndSet(false, true)) {
            hud.pushEvent("CORTEX -> BUSY; INPUT DROPPED")
            return
        }
        processInput(clean)
    }

    private fun processInput(clean: String) {
        voiceLoop.pauseForProcessing()
        hud.setTranscript(clean)
        hud.setProcessing(true)
        hud.pushEvent("INPUT -> ${clean.take(62)}")
        soundEngine.processing()

        CountdownCommandParser.parse(clean)?.let { timerCommand ->
            handleCountdownCommand(timerCommand)
            return
        }

        if (isCloudSetupCommand(clean)) {
            hud.pushEvent("CORTEX MESH CONFIG -> OPEN")
            hud.setProcessing(false)
            brainBusy.set(false)
            startActivity(Intent(this, CloudConfigActivity::class.java))
            return
        }

        val deviceResponse = deviceCommandRouter.execute(clean)
        if (deviceResponse != null) {
            finishLocalCommand(
                spoken = deviceResponse,
                display = deviceResponse,
                intent = "device_command",
                mode = BrainMode.EXECUTING,
                trace = listOf("android_router", "user_visible_intent")
            )
            return
        }

        hud.pushEvent("CORTEX MESH -> ROUTING REQUEST")
        Thread {
            val started = System.currentTimeMillis()
            val response = runCatching { brain.respond(clean) }.getOrElse { error ->
                BrainResponse(
                    spoken = "The cortex mesh request failed: ${error.message ?: error.javaClass.simpleName}.",
                    display = "CORTEX MESH ERROR // ${error.message ?: error.javaClass.simpleName}",
                    intent = "cloud_error",
                    confidence = 0f,
                    mode = BrainMode.ALERT,
                    trace = listOf("cortex_mesh_request", "exception=${error.javaClass.simpleName}", "localhost_disabled"),
                    memory = brain.memorySnapshot(),
                    thoughts = listOf("The mesh request failed after its eligible cloud nodes were evaluated. No local server fallback was attempted."),
                    entities = emptyList(),
                    decision = "cloud_exception",
                    action = BrainAction()
                )
            }
            val elapsed = System.currentTimeMillis() - started

            runOnUiThread {
                hud.submitBrainResponse(response)
                hud.pushEvent("CORTEX MESH -> RESPONSE ${elapsed}ms")
                if (response.action.type != ActionType.NONE) {
                    val executed = brain.execute(response.action)
                    hud.pushEvent("ACTION -> ${response.action.label.uppercase(Locale.US)} ${if (executed) "OK" else "BLOCKED"}")
                }
                hud.setProcessing(false)
                brainBusy.set(false)
                if (response.mode != BrainMode.ALERT) soundEngine.success()
                speak(response.spoken)
            }
        }.start()
    }

    private fun isCloudSetupCommand(input: String): Boolean {
        val normalized = input.lowercase(Locale.getDefault()).trim()
        return normalized == "configure api" ||
            normalized == "configure apis" ||
            normalized == "configure cortex" ||
            normalized == "cortex setup" ||
            normalized == "mesh setup" ||
            normalized == "api setup" ||
            normalized == "cloud setup" ||
            normalized == "configure cloud" ||
            normalized == "open api settings"
    }

    private fun openCloudSetupIfRequired() {
        if (!brain.isCloudConfigured() && !setupOpenedThisSession && !isFinishing) {
            setupOpenedThisSession = true
            hud.pushEvent("CORTEX MESH -> OPENING SECURE REGISTRY")
            startActivity(Intent(this, CloudConfigActivity::class.java))
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
                    trace = listOf("voice_timer_parser", "elapsed_realtime_clock", "countdown_active")
                )
            }

            CountdownCommand.Cancel -> {
                val wasActive = countdown.current().active
                countdown.cancel()
                finishLocalCommand(
                    spoken = if (wasActive) "Countdown cancelled, Sir." else "No countdown is currently running, Sir.",
                    display = if (wasActive) "MISSION TIMER CANCELLED" else "MISSION TIMER // IDLE",
                    intent = "countdown_cancel",
                    mode = BrainMode.ONLINE,
                    trace = listOf("countdown_controller", if (wasActive) "cancelled" else "already_idle")
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
                    display = if (snapshot.active) "TIME REMAINING // ${formatCountdown(snapshot.remainingSeconds)}" else "MISSION TIMER // IDLE",
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
                trace = listOf("elapsed_realtime_clock", "zero_reached", "completion_signal"),
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
        trace: List<String>
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
        hud.setProcessing(false)
        brainBusy.set(false)
        if (mode != BrainMode.ALERT) soundEngine.success()
        speak(spoken)
    }

    private fun handleVoiceState(state: VoiceLoop.State) {
        if (!brainBusy.get() || state == VoiceLoop.State.PROCESSING) {
            hud.setVoiceState(state)
        }
    }

    private fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            if (resumed && hasMicPermission()) voiceLoop.resumeAfterTts(500L)
            return
        }

        voiceLoop.pauseForTts()
        if (!ttsReady) {
            hud.pushEvent("VOICE -> TTS NOT READY; SHOWING TEXT")
            if (resumed && hasMicPermission() && !brainBusy.get()) voiceLoop.resumeAfterTts(800L)
            return
        }

        val result = tts?.speak(
            clean,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis-${System.currentTimeMillis()}"
        )
        if (result != TextToSpeech.SUCCESS) {
            hud.pushEvent("VOICE -> SPEAK REQUEST FAILED")
            if (resumed && hasMicPermission() && !brainBusy.get()) voiceLoop.resumeAfterTts(900L)
        }
    }

    private fun describeDuration(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3_600L
        val minutes = (safe % 3_600L) / 60L
        val seconds = safe % 60L
        val parts = mutableListOf<String>()
        if (hours > 0L) parts += "$hours ${if (hours == 1L) "hour" else "hours"}"
        if (minutes > 0L) parts += "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
        if (seconds > 0L || parts.isEmpty()) parts += "$seconds ${if (seconds == 1L) "second" else "seconds"}"
        return when (parts.size) {
            1 -> parts[0]
            2 -> "${parts[0]} and ${parts[1]}"
            else -> "${parts[0]}, ${parts[1]}, and ${parts[2]}"
        }
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
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
        if (::voiceLoop.isInitialized) voiceLoop.destroy()
        if (::countdown.isInitialized) countdown.destroy()
        if (::soundEngine.isInitialized) soundEngine.release()
        tts?.shutdown()
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
