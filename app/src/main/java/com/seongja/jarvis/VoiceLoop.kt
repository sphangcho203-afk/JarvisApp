package com.seongja.jarvis

import android.app.Activity
import android.os.Handler
import android.os.Looper

class VoiceLoop(
    private val activity: Activity,
    private val onSpeech: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onRms: (Float) -> Unit,
    private val onState: (State) -> Unit,
    private val onDiagnostic: (String) -> Unit = {}
) {

    enum class State { READY, LISTENING, PROCESSING, ERROR, UNAVAILABLE }

    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var paused = true
    private var listening = false
    private var outputCompletion: (() -> Unit)? = null

    private val gateway = JarvisVoiceGateway(
        context = activity.applicationContext,
        listener = object : JarvisVoiceGateway.Listener {
            override fun onBackendReady() {
                handler.post {
                    onDiagnostic("VOICE BACKEND -> READY // PCM STREAMING")
                    onState(State.READY)
                    if (!paused && !listening) startDelayed(250L)
                }
            }

            override fun onListening() {
                handler.post {
                    if (destroyed || paused) {
                        gateway.stopInput(sendForTranscription = false)
                        return@post
                    }
                    listening = true
                    onPartial("Listening...")
                    onDiagnostic("MIC -> RAW AUDIO ACTIVE // GOOGLE CHIME REMOVED")
                    onState(State.LISTENING)
                }
            }

            override fun onTranscribing() {
                handler.post {
                    listening = false
                    onRms(0f)
                    onDiagnostic("VOICE -> TRANSCRIBING PCM INPUT")
                    onState(State.PROCESSING)
                }
            }

            override fun onTranscript(text: String) {
                handler.post {
                    listening = false
                    onRms(0f)
                    val normalized = SpeechCommandNormalizer.normalize(text)
                    if (normalized.commandText.isBlank()) {
                        onDiagnostic("VOICE TRANSCRIPT -> EMPTY")
                        onState(State.READY)
                        if (!paused) startDelayed(700L)
                        return@post
                    }
                    paused = true
                    onDiagnostic("VOICE TRANSCRIPT -> ${normalized.displayText.take(72)}")
                    onState(State.PROCESSING)
                    JarvisConversationBus.recordUser(normalized.displayText)
                    onSpeech(normalized.displayText)
                }
            }

            override fun onRms(value: Float) {
                handler.post {
                    if (!destroyed && listening) onRms(value)
                }
            }

            override fun onSpeechOutputStarted(provider: String) {
                handler.post {
                    onDiagnostic("VOICE OUTPUT -> ${provider.uppercase()} STREAM")
                    onState(State.READY)
                }
            }

            override fun onSpeechOutputCompleted() {
                handler.post {
                    onDiagnostic("VOICE OUTPUT -> COMPLETE")
                    outputCompletion?.also { outputCompletion = null }?.invoke()
                }
            }

            override fun onDiagnostic(message: String) {
                handler.post { onDiagnostic(message) }
            }

            override fun onError(message: String) {
                handler.post {
                    listening = false
                    onRms(0f)
                    onDiagnostic("VOICE ERROR -> $message")
                    onState(State.ERROR)
                    if (!destroyed && !paused) startDelayed(1_800L)
                }
            }
        }
    )

    init {
        JarvisConversationBus.initialize(activity.applicationContext)
        gateway.connect()
    }

    fun isBackendReady(): Boolean = gateway.isReady()

    fun startDelayed(delayMs: Long) {
        if (destroyed || paused) return
        handler.removeCallbacksAndMessages(START_TOKEN)
        handler.postAtTime(
            { startNow() },
            START_TOKEN,
            android.os.SystemClock.uptimeMillis() + delayMs.coerceAtLeast(200L)
        )
    }

    fun manualRestart() {
        if (destroyed) return
        onDiagnostic("VOICE -> MANUAL STREAM RESET")
        paused = false
        listening = false
        gateway.stopSpeech()
        handler.postDelayed({ gateway.startListening() }, 900L)
    }

    fun pauseForProcessing() {
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(START_TOKEN)
        gateway.stopInput(sendForTranscription = false)
        onRms(0f)
        onState(State.PROCESSING)
    }

    fun pauseForTts() {
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(START_TOKEN)
        gateway.stopInput(sendForTranscription = false)
        onRms(0f)
        onDiagnostic("VOICE INPUT -> PAUSED FOR STREAMING OUTPUT")
        onState(State.READY)
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (destroyed) return
        pauseForTts()
        outputCompletion = onComplete
        gateway.speak(text)
    }

    fun stopSpeaking() {
        outputCompletion = null
        gateway.stopSpeech()
    }

    fun resumeAfterTts(delayMs: Long = 900L) {
        if (destroyed) return
        paused = false
        onDiagnostic("VOICE INPUT -> REARMING")
        startDelayed(delayMs)
    }

    fun stop() {
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(START_TOKEN)
        gateway.stopInput(sendForTranscription = false)
        onRms(0f)
        onState(State.READY)
    }

    fun resume() {
        if (destroyed) return
        paused = false
        gateway.connect()
        startDelayed(650L)
    }

    fun destroy() {
        destroyed = true
        paused = true
        listening = false
        outputCompletion = null
        handler.removeCallbacksAndMessages(null)
        gateway.destroy()
    }

    private fun startNow() {
        if (destroyed || paused || listening) return
        if (!gateway.isReady()) {
            onDiagnostic("VOICE BACKEND -> WAITING FOR LOCAL RUNTIME")
            onState(State.UNAVAILABLE)
            gateway.connect()
            startDelayed(1_500L)
            return
        }
        onState(State.READY)
        gateway.startListening()
    }

    companion object {
        private val START_TOKEN = Any()
    }
}
