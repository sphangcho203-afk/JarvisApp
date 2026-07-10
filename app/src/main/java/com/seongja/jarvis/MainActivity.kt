package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var hud: AdvancedCivilizationHudView
    private lateinit var brain: JarvisBrain
    private lateinit var voiceLoop: VoiceLoop
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val brainBusy = AtomicBoolean(false)
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        hud = AdvancedCivilizationHudView(this)
        brain = JarvisBrain(this)
        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = ::handlePartialSpeech,
            onState = ::handleVoiceState,
            onDiagnostic = { message -> runOnUiThread { hud.pushEvent(message) } }
        )

        setContentView(hud)
        hud.pushEvent("PHASE 7 -> ANDROID + TERMUX BRIDGE")
        hud.pushEvent("BRIDGE -> LOCALHOST:8765")
        hud.pushEvent("TAP -> HARD RESET LISTENER")
        hud.pushEvent("LONG PRESS -> DIRECT BRIDGE TEST")
        hud.pushEvent("PAIR -> RUN jarvis-v4-pair-code IN TERMUX")
        hud.pushEvent("PAIR -> SAY PAIR CODE + SIX DIGITS")

        initTts()

        hud.setOnClickListener {
            if (hasMicPermission()) {
                hud.pushEvent("USER -> MANUAL ASR HARD RESET")
                voiceLoop.manualRestart()
            } else {
                hud.pushEvent("AUTH -> REQUESTING MICROPHONE")
                requestMicPermission()
            }
        }

        hud.setOnLongClickListener {
            if (brainBusy.compareAndSet(false, true)) {
                hud.pushEvent("DIAGNOSTIC -> DIRECT V4.1 REQUEST")
                processInput("battery status")
            } else {
                hud.pushEvent("CORTEX -> REQUEST ALREADY RUNNING")
            }
            true
        }

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
            val languageResult = tts?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
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
                            voiceLoop.resumeAfterTts(900)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        hud.pushEvent("VOICE -> OUTPUT ERROR")
                        if (resumed && hasMicPermission() && !brainBusy.get()) {
                            voiceLoop.resumeAfterTts(1_100)
                        }
                    }
                }
            })
            hud.pushEvent("VOICE -> SYNTHESIS READY")

            if (resumed) speak("Jarvis voice system online.")
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (hasMicPermission() && !brainBusy.get()) {
            if (ttsReady) speak("Jarvis listening.") else voiceLoop.resume()
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
        hud.pushEvent("INPUT -> ${clean.take(55)}")
        hud.pushEvent("BRIDGE -> REQUEST START")

        Thread {
            val started = System.currentTimeMillis()
            val response = runCatching { brain.respond(clean) }.getOrElse { error ->
                BrainResponse(
                    spoken = "The secure local bridge failed: ${error.javaClass.simpleName}.",
                    display = "Local bridge error: ${error.message ?: error.javaClass.simpleName}",
                    intent = "bridge_error",
                    confidence = 0f,
                    mode = BrainMode.ALERT,
                    trace = listOf("localhost:8765", "exception=${error.javaClass.simpleName}"),
                    memory = brain.memorySnapshot(),
                    thoughts = listOf("The local bridge request threw an exception."),
                    entities = emptyList(),
                    decision = "bridge_exception",
                    action = BrainAction()
                )
            }
            val elapsed = (System.currentTimeMillis() - started) / 1000

            runOnUiThread {
                hud.submitBrainResponse(response)
                hud.pushEvent("BRIDGE -> RESPONSE ${elapsed}s")
                if (response.trace.any { it.contains("unavailable") }) {
                    hud.pushEvent("BRIDGE -> OFFLINE; ANDROID FALLBACK USED")
                }
                if (response.action.type != ActionType.NONE) {
                    val executed = brain.execute(response.action)
                    hud.pushEvent("ACTION -> ${response.action.label.uppercase()} ${if (executed) "OK" else "BLOCKED"}")
                }
                hud.setProcessing(false)
                brainBusy.set(false)
                speak(response.spoken)
            }
        }.start()
    }

    private fun handleVoiceState(state: VoiceLoop.State) {
        if (!brainBusy.get() || state == VoiceLoop.State.PROCESSING) {
            hud.setVoiceState(state)
        }
    }

    private fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            if (resumed && hasMicPermission()) voiceLoop.resumeAfterTts(700)
            return
        }
        voiceLoop.pauseForTts()
        if (!ttsReady) {
            hud.pushEvent("VOICE -> TTS NOT READY; SHOWING TEXT")
            if (resumed && hasMicPermission() && !brainBusy.get()) voiceLoop.resumeAfterTts(1_000)
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
            if (resumed && hasMicPermission() && !brainBusy.get()) voiceLoop.resumeAfterTts(1_100)
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
        } else if (requestCode == REQ_RECORD_AUDIO) {
            hud.pushEvent("AUTH -> MICROPHONE DENIED")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        if (::voiceLoop.isInitialized) voiceLoop.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

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
