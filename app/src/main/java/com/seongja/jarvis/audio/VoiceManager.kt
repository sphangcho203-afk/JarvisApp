package com.seongja.jarvis.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition unavailable on this device.")
            return
        }
        if (listening) return
        listening = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onReady()
                override fun onBeginningOfSpeech() = onLevel(0.35f)
                override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onLevel(0.05f)
                override fun onError(error: Int) {
                    listening = false
                    onLevel(0f)
                    onError("Voice channel reset: $error")
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    onLevel(0f)
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onFinal(text) else onError("No command captured.")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onPartial(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(intent())
        }
    }

    fun stop() {
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        onLevel(0f)
    }

    fun isListening(): Boolean = listening

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Jarvis command channel active")
    }
}
