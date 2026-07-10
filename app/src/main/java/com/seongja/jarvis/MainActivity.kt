package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var hud: AdvancedCivilizationHudView
    private lateinit var brain: JarvisBrain
    private lateinit var voiceLoop: VoiceLoop
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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
        hud.pushEvent("PHASE 6.1 -> VOICE BRIDGE HOTFIX")
        hud.pushEvent("CORTEX -> LOCALHOST:8080")
        hud.pushEvent("TAP -> RESTART LISTENING")
        hud.pushEvent("LONG PRESS -> TEST BRAIN WITHOUT VOICE")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                val defaultResult = tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (defaultResult == TextToSpeech.LANG_MISSING_DATA || defaultResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(0.94f)
                tts?.setPitch(0.88f)
                ttsReady = true
                hud.pushEvent("VOICE -> SYNTHESIS READY")
                speak("Jarvis voice bridge online.")
            } else {
                hud.pushEvent("VOICE -> SYNTHESIS FAILED: $status")
            }
        }

        hud.setOnClickListener {
            if (hasMicPermission()) {
                hud.pushEvent("USER -> MANUAL LISTENING RESTART")
                voiceLoop.restart()
            } else {
                hud.pushEvent("AUTH -> REQUESTING MICROPHONE")
                requestMicPermission()
            }
        }

        hud.setOnLongClickListener {
            hud.pushEvent("DIAGNOSTIC -> BYPASSING SPEECH INPUT")
            handleSpeech("Confirm the offline brain bridge is connected in one short sentence.")
            true
        }

        if (hasMicPermission()) {
            hud.pushEvent("AUTH -> MICROPHONE GRANTED")
            voiceLoop.startDelayed(700)
        } else {
            requestMicPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::voiceLoop.isInitialized && hasMicPermission()) {
            voiceLoop.startDelayed(450)
        }
    }

    override fun onPause() {
        if (::voiceLoop.isInitialized) voiceLoop.stop()
        super.onPause()
    }

    private fun handlePartialSpeech(text: String) {
        hud.setTranscript(text)
    }

    private fun handleSpeech(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        hud.setTranscript(clean)
        hud.setProcessing(true)
        hud.pushEvent("INPUT -> $clean")
        hud.pushEvent("CORTEX -> LOCAL INFERENCE REQUEST")

        Thread {
            val response = brain.respond(clean)
            runOnUiThread {
                hud.submitBrainResponse(response)
                if (response.trace.any { it.contains("unavailable") }) {
                    hud.pushEvent("CORTEX -> SERVER OFFLINE, FALLBACK ACTIVE")
                } else {
                    hud.pushEvent("CORTEX -> RESPONSE RECEIVED")
                }
                if (response.action.type != ActionType.NONE) {
                    val executed = brain.execute(response.action)
                    hud.pushEvent("ACTION -> ${response.action.label.uppercase()} ${if (executed) "OK" else "BLOCKED/FAILED"}")
                }
                speak(response.spoken)
                hud.setProcessing(false)
                voiceLoop.startDelayed(1_300)
            }
        }.start()
    }

    private fun handleVoiceState(state: VoiceLoop.State) {
        hud.setVoiceState(state)
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            hud.pushEvent("VOICE -> TTS NOT READY")
            return
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
        hud.pushEvent("VOICE -> ${if (result == TextToSpeech.SUCCESS) "SPEAKING" else "SPEAK FAILED"}")
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
            voiceLoop.startDelayed(500)
        } else if (requestCode == REQ_RECORD_AUDIO) {
            hud.pushEvent("AUTH -> MICROPHONE DENIED; ENABLE IN APP SETTINGS")
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
