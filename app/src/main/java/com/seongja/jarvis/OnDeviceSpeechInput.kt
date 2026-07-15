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
 * API-key-free Android speech input.
 *
 * A dedicated on-device recognizer is preferred when the phone provides one.
 * If its language pack or service is unavailable, the class falls back to the
 * phone's normal SpeechRecognizer service instead of leaving Jarvis unusable.
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
    private var cancellationInFlight = false
    private var dedicatedOnDeviceDisabled = false
    private var usingDedicatedOnDevice = false

    fun isAvailable(): Boolean =
        hasDedicatedOnDeviceRecognizer() || SpeechRecognizer.isRecognitionAvailable(activity)

    fun isActive(): Boolean = active

    fun backendLabel(): String =
        if (usingDedicatedOnDevice) "ANDROID ON-DEVICE" else "ANDROID SPEECH SERVICE"

    fun start(): Boolean {
        if (destroyed || active || !isAvailable()) return false
        val engine = recognizer ?: createRecognizer() ?: return false
        cancellationInFlight = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            if (usingDedicatedOnDevice) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        active = true
        return runCatching {
            engine.startListening(intent)
            true
        }.getOrElse {
            active = false
            resetRecognizer()
            false
        }
    }

    fun stop() {
        if (!active) return
        active = false
        cancellationInFlight = true
        runCatching { recognizer?.cancel() }
        listener.onRms(0f)
    }

    fun destroy() {
        destroyed = true
        active = false
        cancellationInFlight = true
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun hasDedicatedOnDeviceRecognizer(): Boolean =
        !dedicatedOnDeviceDisabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)

    private fun createRecognizer(): SpeechRecognizer? {
        if (!isAvailable()) return null
        val useDedicated = hasDedicatedOnDeviceRecognizer()
        val created = runCatching {
            if (useDedicated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            } else {
                SpeechRecognizer.createSpeechRecognizer(activity)
            }
        }.getOrNull() ?: return null

        usingDedicatedOnDevice = useDedicated
        created.setRecognitionListener(this)
        recognizer = created
        return created
    }

    private fun resetRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        usingDedicatedOnDevice = false
    }

    override fun onReadyForSpeech(params: Bundle?) {
        cancellationInFlight = false
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
        if (destroyed) return

        // SpeechRecognizer.cancel() commonly reports ERROR_CLIENT. That is an
        // expected acknowledgement of our own pause, not a microphone failure.
        if (cancellationInFlight) {
            cancellationInFlight = false
            return
        }

        val shouldFallBackToSystemRecognizer = usingDedicatedOnDevice && error in setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        )
        if (shouldFallBackToSystemRecognizer) {
            dedicatedOnDeviceDisabled = true
            resetRecognizer()
            listener.onError(error, recoverable = true)
            return
        }

        if (error in setOf(
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                SpeechRecognizer.ERROR_SERVER
            )
        ) {
            resetRecognizer()
        }

        val recoverable = error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        listener.onError(error, recoverable)
    }

    override fun onResults(results: Bundle?) {
        active = false
        cancellationInFlight = false
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
