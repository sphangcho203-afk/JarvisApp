package com.seongja.jarvis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import android.app.Activity
import com.seongja.jarvis.audio.SoundEngine
import com.seongja.jarvis.audio.VoiceManager
import com.seongja.jarvis.core.JarvisEngine
import com.seongja.jarvis.core.JarvisStateManager
import com.seongja.jarvis.memory.MemoryManager
import com.seongja.jarvis.models.JarvisMode
import com.seongja.jarvis.system.AppLaunchManager
import com.seongja.jarvis.system.DeviceStatusManager
import com.seongja.jarvis.system.DiagnosticsManager
import com.seongja.jarvis.ui.hud.AdvancedJarvisHudView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var hud: AdvancedJarvisHudView
    private lateinit var stateManager: JarvisStateManager
    private lateinit var engine: JarvisEngine
    private lateinit var deviceStatusManager: DeviceStatusManager
    private lateinit var soundEngine: SoundEngine
    private var voiceManager: VoiceManager? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bootProgress = 0f
    private var micGranted = false

    private val telemetryLoop = object : Runnable {
        override fun run() {
            updateTelemetry()
            handler.postDelayed(this, 1000L)
        }
    }

    private val bootLoop = object : Runnable {
        override fun run() {
            bootProgress += 0.035f
            stateManager.setBootProgress(bootProgress)
            hud.render(stateManager.state)
            if (bootProgress < 1f) {
                handler.postDelayed(this, 70L)
            } else {
                stateManager.setMode(JarvisMode.ONLINE, "CORE ONLINE // TOUCH TO SPEAK")
                stateManager.setResponse("Phase 3 interface online. Command channel ready.")
                hud.render(stateManager.state)
                speak("Jarvis phase three interface online.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        stateManager = JarvisStateManager()
        deviceStatusManager = DeviceStatusManager(this)
        engine = JarvisEngine(
            memory = MemoryManager(this),
            appLaunchManager = AppLaunchManager(this),
            deviceStatusManager = deviceStatusManager,
            diagnosticsManager = DiagnosticsManager()
        )
        soundEngine = SoundEngine(getSystemService(VIBRATOR_SERVICE) as? Vibrator)
        hud = AdvancedJarvisHudView(this)
        setContentView(hud)
        tts = TextToSpeech(this, this)

        micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!micGranted) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 703)

        hud.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) toggleVoice()
            true
        }

        handler.post(bootLoop)
        handler.post(telemetryLoop)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(0.94f)
            tts?.setPitch(0.92f)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 703) {
            micGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            updateTelemetry()
            if (micGranted) stateManager.setResponse("Microphone permission granted.") else stateManager.setResponse("Microphone permission denied. Voice channel unavailable.")
            hud.render(stateManager.state)
        }
    }

    private fun toggleVoice() {
        if (!micGranted) {
            stateManager.setMode(JarvisMode.RED_ALERT, "MICROPHONE PERMISSION REQUIRED")
            stateManager.setResponse("Grant microphone permission to use voice commands.")
            soundEngine.pulse(strong = true)
            hud.render(stateManager.state)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 703)
            return
        }
        val manager = voiceManager
        if (manager?.isListening() == true) {
            manager.stop()
            stateManager.setMode(JarvisMode.ONLINE, "COMMAND CHANNEL CLOSED")
            hud.render(stateManager.state)
            return
        }
        stateManager.setMode(JarvisMode.LISTENING, "LISTENING // SPEAK NOW")
        stateManager.setTranscript("Listening...")
        stateManager.setResponse("Command channel open.")
        soundEngine.pulse()
        hud.render(stateManager.state)
        voiceManager = VoiceManager(
            context = this,
            onPartial = { partial ->
                stateManager.setTranscript(partial)
                hud.render(stateManager.state)
            },
            onFinal = { command -> handleCommand(command) },
            onLevel = { level ->
                stateManager.setAudioLevel(level)
                hud.render(stateManager.state)
            },
            onReady = {
                stateManager.setMode(JarvisMode.LISTENING, "VOICE CHANNEL ACTIVE")
                hud.render(stateManager.state)
            },
            onError = { error ->
                stateManager.setMode(JarvisMode.ONLINE, "VOICE CHANNEL STANDBY")
                stateManager.setResponse(error)
                hud.render(stateManager.state)
            }
        )
        voiceManager?.start()
    }

    private fun handleCommand(command: String) {
        stateManager.setTranscript(command)
        stateManager.setMode(JarvisMode.PROCESSING, "PROCESSING COMMAND")
        hud.render(stateManager.state)
        val result = engine.handle(command)
        stateManager.setMode(result.mode ?: if (result.executed) JarvisMode.EXECUTING else JarvisMode.ONLINE)
        stateManager.setResponse(result.reply)
        updateTelemetry()
        hud.render(stateManager.state)
        if (result.visualAlert) soundEngine.pulse(strong = true) else soundEngine.pulse()
        if (result.shouldSpeak) speak(result.reply)
        handler.postDelayed({
            stateManager.setMode(JarvisMode.ONLINE, "CORE ONLINE // TOUCH TO SPEAK")
            hud.render(stateManager.state)
        }, 1800L)
    }

    private fun updateTelemetry() {
        val telemetry = deviceStatusManager.telemetry(
            commandCount = engine.commandCount,
            micReady = micGranted,
            permissionStatus = if (micGranted) "GRANTED" else "PENDING"
        )
        stateManager.setTelemetry(telemetry)
        hud.render(stateManager.state)
    }

    private fun speak(text: String) {
        tts?.speak(text.take(260), TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        voiceManager?.stop()
        soundEngine.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
