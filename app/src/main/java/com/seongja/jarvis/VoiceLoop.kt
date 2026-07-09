package com.seongja.jarvis

import android.app.Activity
import android.content.Intent
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
    private val onState: (State) -> Unit
) : RecognitionListener {

    enum class State { READY, LISTENING, PROCESSING, ERROR, UNAVAILABLE }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onState(State.UNAVAILABLE)
            return
        }
        if (active) return
        active = true
        onState(State.LISTENING)
        ensureRecognizer()
        runCatching { recognizer?.startListening(intent()) }
            .onFailure {
                active = false
                onState(State.ERROR)
                startDelayed(900)
            }
    }

    fun startDelayed(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ start() }, delayMs)
    }

    fun restart() {
        stop()
        startDelayed(180)
    }

    fun stop() {
        active = false
        runCatching { recognizer?.cancel() }
        onState(State.READY)
    }

    fun destroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity).also { it.setRecognitionListener(this) }
        }
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1050L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
    }

    override fun onReadyForSpeech(params: Bundle?) { onState(State.LISTENING) }
    override fun onBeginningOfSpeech() { onState(State.LISTENING) }
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        active = false
        onState(State.PROCESSING)
    }

    override fun onError(error: Int) {
        active = false
        onState(State.ERROR)
        startDelayed(if (error == SpeechRecognizer.ERROR_NO_MATCH) 450 else 1000)
    }

    override fun onResults(results: Bundle?) {
        active = false
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().firstOrNull().orEmpty()
        if (text.isNotBlank()) {
            onState(State.PROCESSING)
            onSpeech(text)
        } else {
            startDelayed(450)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().firstOrNull().orEmpty()
        if (text.isNotBlank()) onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
