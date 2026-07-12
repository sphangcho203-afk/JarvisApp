package com.seongja.jarvis

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceLoop(
    private val activity: Activity,
    private val onSpeech: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onRms: (Float) -> Unit,
    private val onState: (State) -> Unit,
    private val onDiagnostic: (String) -> Unit = {}
) : RecognitionListener {

    enum class State { READY, LISTENING, PROCESSING, ERROR, UNAVAILABLE }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false
    private var listening = false
    private var starting = false
    private var paused = false
    private var forceSystemRecognizer = true
    private var busyCount = 0
    private var generation = 0

    private val delayedStart = Runnable { startNow() }

    fun startDelayed(delayMs: Long) {
        if (destroyed || paused) return
        handler.removeCallbacks(delayedStart)
        handler.postDelayed(delayedStart, delayMs.coerceAtLeast(250L))
    }

    fun manualRestart() {
        onDiagnostic("ASR -> MANUAL HARD RESET")
        paused = false
        hardReset()
        startDelayed(1_200)
    }

    fun pauseForProcessing() {
        paused = true
        onRms(0f)
        handler.removeCallbacks(delayedStart)
        listening = false
        starting = false
        runCatching { recognizer?.cancel() }
        onState(State.PROCESSING)
    }

    fun pauseForTts() {
        paused = true
        onRms(0f)
        handler.removeCallbacks(delayedStart)
        listening = false
        starting = false
        runCatching { recognizer?.cancel() }
        onDiagnostic("ASR -> PAUSED FOR SPEECH OUTPUT")
        onState(State.READY)
    }

    fun resumeAfterTts(delayMs: Long = 900L) {
        if (destroyed) return
        paused = false
        onDiagnostic("ASR -> RESUMING AFTER SPEECH OUTPUT")
        startDelayed(delayMs)
    }

    fun stop() {
        paused = true
        onRms(0f)
        handler.removeCallbacks(delayedStart)
        listening = false
        starting = false
        runCatching { recognizer?.cancel() }
        onState(State.READY)
    }

    fun resume() {
        if (destroyed) return
        paused = false
        startDelayed(850)
    }

    fun destroy() {
        destroyed = true
        paused = true
        handler.removeCallbacksAndMessages(null)
        listening = false
        starting = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun startNow() {
        if (destroyed || paused || listening || starting) return
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onDiagnostic("ASR -> NO RECOGNITION SERVICE")
            onState(State.UNAVAILABLE)
            return
        }

        ensureRecognizer()
        val engine = recognizer ?: run {
            onState(State.UNAVAILABLE)
            return
        }

        starting = true
        onState(State.LISTENING)
        onDiagnostic("ASR -> START REQUEST")
        runCatching { engine.startListening(recognitionIntent()) }
            .onFailure {
                starting = false
                listening = false
                onDiagnostic("ASR START ERROR -> ${it.javaClass.simpleName}")
                onState(State.ERROR)
                hardReset()
                startDelayed(2_000)
            }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return

        val useOnDevice = !forceSystemRecognizer &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)

        recognizer = runCatching {
            if (useOnDevice) {
                onDiagnostic("ASR ENGINE -> ANDROID ON-DEVICE")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            } else {
                onDiagnostic("ASR ENGINE -> SYSTEM DEFAULT")
                SpeechRecognizer.createSpeechRecognizer(activity)
            }
        }.onFailure {
            onDiagnostic("ASR CREATE ERROR -> ${it.javaClass.simpleName}")
        }.getOrNull()?.also {
            generation++
            it.setRecognitionListener(this)
        }
    }

    private fun hardReset() {
        handler.removeCallbacks(delayedStart)
        listening = false
        starting = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        generation++
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        starting = false
        listening = true
        busyCount = 0
        onDiagnostic("ASR -> READY FOR SPEECH")
        onState(State.LISTENING)
    }

    override fun onBeginningOfSpeech() {
        starting = false
        listening = true
        onRms(0.72f)
        onDiagnostic("ASR -> SPEECH DETECTED")
        onState(State.LISTENING)
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (paused || destroyed) return
        val normalized = ((rmsdB + 12f) / 24f).coerceIn(0f, 1f)
        onRms(normalized)
    }
    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        listening = false
        starting = false
        onRms(0f)
        onDiagnostic("ASR -> PROCESSING SPEECH")
        onState(State.PROCESSING)
    }

    override fun onError(error: Int) {
        listening = false
        starting = false

        if (paused || destroyed) {
            onDiagnostic("ASR -> INTENTIONAL STOP")
            onRms(0f)
            return
        }

        val label = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "MIC PERMISSION"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE NOT SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE MODEL UNAVAILABLE"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "SERVER DISCONNECTED"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH TIMEOUT"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "TOO MANY REQUESTS"
            else -> "CODE $error"
        }

        onDiagnostic("ASR ERROR -> $label")
        onState(State.ERROR)
        onRms(0f)

        when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                busyCount++
                hardReset()
                if (busyCount >= 2 && !forceSystemRecognizer) {
                    forceSystemRecognizer = true
                    busyCount = 0
                    onDiagnostic("ASR BUSY LOOP -> SWITCHING TO SYSTEM ENGINE")
                    startDelayed(3_000)
                } else {
                    onDiagnostic("ASR BUSY -> HARD RESET ${busyCount}/2")
                    startDelayed(2_500)
                }
            }
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                hardReset()
                startDelayed(2_500)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                paused = true
                onDiagnostic("ASR -> MICROPHONE PERMISSION REQUIRED")
            }
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> startDelayed(900)
            else -> {
                hardReset()
                startDelayed(1_800)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        starting = false
        onRms(0f)
        busyCount = 0
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
            .firstOrNull()
            .orEmpty()
            .trim()

        if (text.isNotBlank()) {
            paused = true
            onDiagnostic("ASR RESULT -> ${text.take(72)}")
            onState(State.PROCESSING)
            onSpeech(text)
        } else {
            onDiagnostic("ASR RESULT -> EMPTY")
            startDelayed(900)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
            .firstOrNull()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) {
            onRms(0.42f)
            onPartial(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
