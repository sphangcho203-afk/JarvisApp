package com.seongja.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onTranscript: (String) -> Unit,
    private val onCommand: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var shouldLoop = false

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                speak("Jarvis online.")
            }
        }

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(listener)
        }
    }

    fun toggleListening() {
        if (shouldLoop) stopListening() else startListeningLoop()
    }

    fun startListeningLoop() {
        shouldLoop = true
        startListeningOnce()
    }

    fun stopListening() {
        shouldLoop = false
        recognizer?.stopListening()
        onListeningChanged(false)
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    private fun startListeningOnce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis")
        }

        try {
            onListeningChanged(true)
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            onListeningChanged(false)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onListeningChanged(true)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            onListeningChanged(false)
            if (shouldLoop) startListeningOnce()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val command = matches?.firstOrNull().orEmpty()
            if (command.isNotBlank()) {
                onTranscript(command)
                onCommand(command)
            }
            if (shouldLoop && !command.lowercase(Locale.getDefault()).contains("stop listening")) {
                startListeningOnce()
            } else {
                shouldLoop = false
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank()) onTranscript(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun destroy() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
