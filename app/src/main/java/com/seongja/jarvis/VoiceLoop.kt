package com.seongja.jarvis

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

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
    private var usingOnDeviceInput = false
    private var consecutiveInputFailures = 0
    private var outputCompletion: (() -> Unit)? = null
    private lateinit var gateway: JarvisVoiceGateway
    private lateinit var onDeviceInput: OnDeviceSpeechInput

    private val gatewayListener = object : JarvisVoiceGateway.Listener {
        override fun onBackendReady() {
            handler.post {
                consecutiveInputFailures = 0
                onDiagnostic("VOICE BACKEND -> READY // PREMIUM PCM STREAMING")
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
                consecutiveInputFailures = 0
                listening = true
                usingOnDeviceInput = false
                onPartial("Listening...")
                onDiagnostic("MIC -> RAW PCM STREAM ACTIVE")
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
            handler.post { deliverTranscript(text, "streaming_backend") }
        }

        override fun onRms(value: Float) {
            handler.post {
                if (!destroyed && listening && !usingOnDeviceInput) onRms(value)
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
                val completion = outputCompletion
                outputCompletion = null
                completion?.invoke()
            }
        }

        override fun onDiagnostic(message: String) {
            handler.post { onDiagnostic(message) }
        }

        override fun onError(message: String) {
            handler.post {
                listening = false
                onRms(0f)
                onDiagnostic("PREMIUM VOICE UNAVAILABLE -> $message")
                if (destroyed) return@post
                if (paused) {
                    onDiagnostic("VOICE ERROR -> IGNORED WHILE INPUT PAUSED")
                    onState(State.READY)
                    return@post
                }

                consecutiveInputFailures++
                if (onDeviceInput.isAvailable()) {
                    onDiagnostic("VOICE INPUT -> SWITCHING TO ANDROID SPEECH")
                    onState(State.READY)
                    startDelayed(recoveryDelayMs())
                } else {
                    onDiagnostic("VOICE INPUT -> RETRYING LOCAL RECOGNIZER DISCOVERY")
                    onState(State.UNAVAILABLE)
                    startDelayed(2_500L)
                }
            }
        }
    }

    private val onDeviceListener = object : OnDeviceSpeechInput.Listener {
        override fun onReady() {
            handler.post {
                if (destroyed || paused) {
                    onDeviceInput.stop()
                    return@post
                }
                consecutiveInputFailures = 0
                listening = true
                usingOnDeviceInput = true
                onPartial("Listening locally...")
                onDiagnostic("MIC -> ${onDeviceInput.backendLabel()} // API KEY FREE")
                onState(State.LISTENING)
            }
        }

        override fun onPartial(text: String) {
            handler.post {
                if (!destroyed && listening && usingOnDeviceInput) onPartial(text)
            }
        }

        override fun onRms(value: Float) {
            handler.post {
                if (!destroyed && listening && usingOnDeviceInput) onRms(value)
            }
        }

        override fun onFinal(text: String) {
            handler.post { deliverTranscript(text, "android_speech") }
        }

        override fun onError(code: Int, recoverable: Boolean) {
            handler.post {
                listening = false
                usingOnDeviceInput = false
                onRms(0f)
                onDiagnostic("ANDROID SPEECH -> ERROR $code")
                if (destroyed) return@post

                // Cancelling recognition during navigation, processing, or TTS
                // can produce a late callback. A paused loop must never turn
                // that expected callback into a permanent red fault state.
                if (paused) {
                    onDiagnostic("ANDROID SPEECH ERROR -> IGNORED WHILE PAUSED")
                    onState(State.READY)
                    return@post
                }

                consecutiveInputFailures++
                if (recoverable || onDeviceInput.isAvailable()) {
                    onDiagnostic("VOICE INPUT -> AUTOMATIC RECOVERY ${consecutiveInputFailures}")
                    onState(State.READY)
                    startDelayed(recoveryDelayMs())
                } else {
                    onDiagnostic("VOICE INPUT -> SERVICE TEMPORARILY UNAVAILABLE")
                    onState(State.UNAVAILABLE)
                    startDelayed(3_000L)
                }
            }
        }
    }

    init {
        JarvisConversationBus.initialize(activity.applicationContext)
        onDeviceInput = OnDeviceSpeechInput(activity, onDeviceListener)
        gateway = JarvisVoiceGateway(
            context = activity.applicationContext,
            listener = gatewayListener
        )
        gateway.connect()
    }

    fun isBackendReady(): Boolean = gateway.isReady() || onDeviceInput.isAvailable()

    fun isPremiumBackendReady(): Boolean = gateway.isReady()

    fun isOnDeviceInputAvailable(): Boolean = onDeviceInput.isAvailable()

    fun startDelayed(delayMs: Long) {
        if (destroyed || paused) return
        handler.removeCallbacksAndMessages(START_TOKEN)
        handler.postAtTime(
            { startNow() },
            START_TOKEN,
            SystemClock.uptimeMillis() + delayMs.coerceAtLeast(200L)
        )
    }

    fun manualRestart() {
        if (destroyed) return
        onDiagnostic("VOICE -> MANUAL INPUT RESET")
        paused = false
        listening = false
        usingOnDeviceInput = false
        consecutiveInputFailures = 0
        handler.removeCallbacksAndMessages(START_TOKEN)
        onDeviceInput.stop()
        gateway.stopSpeech()
        onRms(0f)
        onState(State.READY)
        handler.postDelayed({ startNow() }, 700L)
    }

    fun pauseForProcessing() {
        paused = true
        stopAllInput()
        onState(State.PROCESSING)
    }

    fun pauseForTts() {
        paused = true
        stopAllInput()
        onDiagnostic("VOICE INPUT -> PAUSED FOR OUTPUT")
        onState(State.READY)
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (destroyed) return
        pauseForTts()
        if (!gateway.isReady()) {
            onDiagnostic("VOICE OUTPUT -> PREMIUM BACKEND OFFLINE // TEXT RESPONSE ONLY")
            outputCompletion = null
            handler.post(onComplete)
            return
        }
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
        onState(State.READY)
        startDelayed(delayMs)
    }

    fun stop() {
        paused = true
        stopAllInput()
        onState(State.READY)
    }

    fun resume() {
        if (destroyed) return
        paused = false
        onState(State.READY)
        gateway.connect()
        startDelayed(650L)
    }

    fun destroy() {
        destroyed = true
        paused = true
        listening = false
        usingOnDeviceInput = false
        outputCompletion = null
        handler.removeCallbacksAndMessages(null)
        onDeviceInput.destroy()
        gateway.destroy()
    }

    private fun startNow() {
        if (destroyed || paused || listening) return
        onState(State.READY)
        if (gateway.isReady()) {
            usingOnDeviceInput = false
            gateway.startListening()
            return
        }

        gateway.connect()
        if (onDeviceInput.isAvailable()) {
            usingOnDeviceInput = true
            val started = onDeviceInput.start()
            if (!started) {
                usingOnDeviceInput = false
                consecutiveInputFailures++
                onDiagnostic("ANDROID SPEECH -> START FAILED // RETRYING")
                onState(State.READY)
                startDelayed(recoveryDelayMs())
            }
            return
        }

        consecutiveInputFailures++
        onDiagnostic("VOICE INPUT -> NO ANDROID RECOGNIZER // RETRYING")
        onState(State.UNAVAILABLE)
        startDelayed(3_000L)
    }

    private fun recoveryDelayMs(): Long =
        (450L * consecutiveInputFailures.coerceIn(1, 6)).coerceAtMost(2_700L)

    private fun stopAllInput() {
        listening = false
        usingOnDeviceInput = false
        handler.removeCallbacksAndMessages(START_TOKEN)
        onDeviceInput.stop()
        gateway.stopInput(sendForTranscription = false)
        onRms(0f)
    }

    private fun deliverTranscript(text: String, source: String) {
        listening = false
        usingOnDeviceInput = false
        consecutiveInputFailures = 0
        onRms(0f)
        val normalized = SpeechCommandNormalizer.normalize(text)
        if (normalized.commandText.isBlank()) {
            onDiagnostic("VOICE TRANSCRIPT -> EMPTY // $source")
            onState(State.READY)
            if (!paused) startDelayed(700L)
            return
        }
        paused = true
        onDiagnostic("VOICE TRANSCRIPT -> ${normalized.displayText.take(72)} // $source")
        onState(State.PROCESSING)
        JarvisConversationBus.recordUser(normalized.displayText)
        onSpeech(normalized.displayText)
    }

    companion object {
        private val START_TOKEN = Any()
    }
}
