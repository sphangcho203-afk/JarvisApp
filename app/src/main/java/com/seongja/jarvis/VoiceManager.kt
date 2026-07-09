package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val engine: JarvisEngine,
    private val router: CommandRouter,
    private val sound: SoundEngine
) : RecognitionListener {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    private val listenIntent: Intent
        get() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis")
        }

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            engine.update(status = "VOICE UNAVAILABLE", response = "Speech recognition is not available on this device.", listening = false, mode = "LOCKED")
            return
        }
        active = true
        ensureRecognizer()
        sound.bootTone()
        engine.update(status = "LISTENING", response = "Voice loop armed. Speak naturally, Sir.", listening = true, energy = 0.55f, mode = "SCAN")
        beginListeningSoon(250)
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        engine.update(status = "STANDBY", response = "Voice loop paused.", listening = false, energy = 0.18f, mode = "STANDBY")
    }

    fun toggle() {
        if (active) stop() else start()
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceManager)
            }
        }
    }

    private fun beginListeningSoon(delayMs: Long) {
        if (!active) return
        handler.postDelayed({
            if (!active) return@postDelayed
            runCatching {
                engine.update(status = "LISTENING", listening = true, energy = 0.62f, mode = "SCAN")
                sound.listenTone()
                recognizer?.startListening(listenIntent)
            }.onFailure {
                engine.update(status = "VOICE ERROR", response = "Speech engine failed to start. Tap to retry.", listening = false, energy = 0.2f, mode = "ERROR")
            }
        }, delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        engine.update(status = "LISTENING", response = "I am listening.", listening = true, energy = 0.72f, mode = "SCAN")
    }

    override fun onBeginningOfSpeech() {
        engine.update(status = "ANALYSING", response = "Speech detected.", listening = true, energy = 0.86f, mode = "ANALYSIS")
    }

    override fun onRmsChanged(rmsdB: Float) {
        val energy = ((rmsdB + 2f) / 12f).coerceIn(0.2f, 1f)
        engine.update(energy = energy, listening = true)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        engine.update(status = "PROCESSING", response = "Processing command.", listening = true, energy = 0.5f, mode = "PROCESS")
    }

    override fun onError(error: Int) {
        if (!active) return
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No command detected. Continuing scan."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Continuing scan."
            SpeechRecognizer.ERROR_AUDIO -> "Audio engine error. Retrying."
            SpeechRecognizer.ERROR_NETWORK -> "Network error. Local interface remains online."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Retrying."
            else -> "Voice error $error. Retrying."
        }
        engine.update(status = "LISTENING", response = message, listening = true, energy = 0.32f, mode = "SCAN")
        beginListeningSoon(900)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val transcript = matches.firstOrNull().orEmpty()
        if (transcript.isBlank()) {
            engine.update(status = "LISTENING", response = "No usable command detected.", listening = true, energy = 0.35f, mode = "SCAN")
            beginListeningSoon(600)
            return
        }

        val result = router.handle(transcript)
        engine.update(
            status = if (result.keepListening) "ONLINE" else "STANDBY",
            transcript = transcript,
            response = result.response,
            listening = result.keepListening,
            energy = 0.45f,
            mode = result.mode,
            commandCount = result.commandCount,
            lastCommand = transcript.take(80),
            signal = result.signal
        )
        sound.speak(result.response)

        if (result.keepListening) beginListeningSoon(1100) else stop()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (partial.isNotBlank()) {
            engine.update(status = "ANALYSING", transcript = partial, listening = true, energy = 0.82f, mode = "ANALYSIS")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
