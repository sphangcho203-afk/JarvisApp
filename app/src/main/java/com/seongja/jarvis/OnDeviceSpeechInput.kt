package com.seongja.jarvis

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * API-key-free speech input backed strictly by Android's on-device recognizer.
 * It never falls back to a network recognition service.
 */
class OnDeviceSpeechInput(
    private val activity: Activity,
    private val listener: Listener
) : RecognitionListener {

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onRms(value: Float)
        fun onFinal(text: String)
        fun onError(code: Int, recoverable: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var destroyed = false

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)

    fun isActive(): Boolean = active

    fun start(): Boolean {
        if (destroyed || active || !isAvailable()) return false
        val engine = recognizer ?: createRecognizer() ?: return false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
        }
        active = true
        return runCatching {
            engine.startListening(intent)
            true
        }.getOrElse {
            active = false
            false
        }
    }

    fun stop() {
        if (!active) return
        active = false
        runCatching { recognizer?.cancel() }
        listener.onRms(0f)
    }

    fun destroy() {
        destroyed = true
        active = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (!isAvailable()) return null
        return runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(activity).also {
                it.setRecognitionListener(this)
            }
        }.getOrNull()?.also { recognizer = it }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        listener.onReady()
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        listener.onRms(normalized)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        active = false
        listener.onRms(0f)
        val recoverable = error in setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT
        )
        listener.onError(error, recoverable)
    }

    override fun onResults(results: Bundle?) {
        active = false
        listener.onRms(0f)
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        listener.onFinal(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) listener.onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
