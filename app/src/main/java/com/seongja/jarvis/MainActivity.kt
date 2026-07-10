package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var hud: AdvancedCivilizationHudView
    private lateinit var brain: JarvisBrain
    private lateinit var voiceLoop: VoiceLoop
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        hud = AdvancedCivilizationHudView(this)
        brain = JarvisBrain(this)
        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = ::handlePartialSpeech,
            onState = ::handleVoiceState
        )

        setContentView(hud)
        hud.pushEvent("PHASE 6 -> OFFLINE LLM BRIDGE")
        hud.pushEvent("CORTEX -> QWEN2.5 3B @ LOCALHOST:8080")
        hud.pushEvent("MEMORY -> ${brain.memorySnapshot()}")
        hud.pushEvent("TOOLS -> SAFE ANDROID ALLOWLIST")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.94f)
                tts?.setPitch(0.84f)
                speak("Jarvis phase six online. Offline language model bridge initialized.")
            } else {
                hud.pushEvent("VOICE -> SYNTHESIS FAILED")
            }
        }

        hud.setOnClickListener {
            hud.pushEvent("USER -> MANUAL CORTEX WAKE")
            voiceLoop.restart()
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceLoop.startDelayed(650)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
        }
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
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
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
        } else {
            hud.pushEvent("AUTH -> MICROPHONE DENIED")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        voiceLoop.destroy()
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
