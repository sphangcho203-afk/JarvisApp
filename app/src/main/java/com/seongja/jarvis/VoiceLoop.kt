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
    private val onState: (State) -> Unit,
    private val onDiagnostic: (String) -> Unit = {}
) : RecognitionListener {

    enum class State { READY, LISTENING, PROCESSING, ERROR, UNAVAILABLE }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var onDeviceRecognizer = false
    private var destroyed = false

    fun start() {
        if (destroyed || active) return
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

        active = true
        onState(State.LISTENING)
        onDiagnostic(if (onDeviceRecognizer) "ASR -> ON-DEVICE LISTENING" else "ASR -> SYSTEM LISTENING")

        runCatching { engine.startListening(intent()) }
            .onFailure {
                active = false
                onDiagnostic("ASR START ERROR -> ${it.javaClass.simpleName}")
                onState(State.ERROR)
                startDelayed(1_000)
            }
    }

    fun startDelayed(delayMs: Long) {
        if (destroyed) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ start() }, delayMs)
    }

    fun restart() {
        active = false
        runCatching { recognizer?.cancel() }
        startDelayed(250)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        active = false
        runCatching { recognizer?.cancel() }
        onState(State.READY)
    }

    fun destroy() {
        destroyed = true
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return

        recognizer = runCatching {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
            ) {
                onDeviceRecognizer = true
                onDiagnostic("ASR ENGINE -> ANDROID ON-DEVICE")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            } else {
                onDeviceRecognizer = false
                onDiagnostic("ASR ENGINE -> SYSTEM DEFAULT; OFFLINE NOT GUARANTEED")
                SpeechRecognizer.createSpeechRecognizer(activity)
            }
        }.onFailure {
            onDiagnostic("ASR CREATE ERROR -> ${it.javaClass.simpleName}")
        }.getOrNull()?.also { it.setRecognitionListener(this) }
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onDiagnostic("ASR -> READY FOR SPEECH")
        onState(State.LISTENING)
    }

    override fun onBeginningOfSpeech() {
        onDiagnostic("ASR -> SPEECH DETECTED")
        onState(State.LISTENING)
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        active = false
        onDiagnostic("ASR -> PROCESSING SPEECH")
        onState(State.PROCESSING)
    }

    override fun onError(error: Int) {
        active = false
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

        val delay = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5_000L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_500L
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 550L
            else -> 1_200L
        }
        startDelayed(delay)
    }

    override fun onResults(results: Bundle?) {
        active = false
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
            .firstOrNull()
            .orEmpty()
            .trim()

        if (text.isNotBlank()) {
            onDiagnostic("ASR RESULT -> ${text.take(80)}")
            onState(State.PROCESSING)
            onSpeech(text)
        } else {
            onDiagnostic("ASR RESULT -> EMPTY")
            startDelayed(500)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
            .firstOrNull()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
