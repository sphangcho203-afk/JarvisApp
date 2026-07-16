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

/**
 * API-key-free speech input with real capture proof and silent cue windows.
 *
 * Dedicated on-device recognition is preferred, but some vendor builds report
 * readiness without delivering microphone activity. This class falls back to
 * Android's normal recognition service instead of leaving FRIDAY in a fake
 * listening state. Short OEM start/end beeps are suppressed without muting the
 * device throughout the conversation.
 */
class OnDeviceSpeechInput(
    private val activity: Activity,
    private val listener: Listener
) : RecognitionListener {

    interface Listener {
        fun onReady(backend: String)
        fun onSpeechDetected()
        fun onPartial(text: String)
        fun onRms(value: Float)
        fun onFinal(text: String)
        fun onError(code: Int, recoverable: Boolean)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val cueSilencer = SpeechCueSilencer(activity.applicationContext)
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var destroyed = false
    private var dedicatedOnDeviceDisabled = false
    private var usingDedicatedOnDevice = false
    private var expectedClientErrors = 0
    private var sessionId = 0
    private var speechActivitySeen = false
    private var speechDetectedReported = false

    fun isAvailable(): Boolean =
        hasDedicatedOnDeviceRecognizer() || SpeechRecognizer.isRecognitionAvailable(activity)

    fun isActive(): Boolean = active

    fun backendLabel(): String =
        if (usingDedicatedOnDevice) "ON-DEVICE SPEECH" else "SYSTEM SPEECH SERVICE"

    fun start(): Boolean {
        if (destroyed || active || !isAvailable()) return false
        val engine = recognizer ?: createRecognizer() ?: return false
        val currentSession = ++sessionId
        speechActivitySeen = false
        speechDetectedReported = false

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 450L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            if (usingDedicatedOnDevice) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        active = true
        cueSilencer.suppress(START_CUE_SUPPRESSION_MS)
        return runCatching {
            engine.startListening(intent)
            armStartupWatchdog(currentSession)
            true
        }.getOrElse {
            active = false
            cueSilencer.restore()
            resetRecognizer()
            false
        }
    }

    fun stop() {
        if (!active && recognizer == null) return
        active = false
        sessionId++
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        cueSilencer.suppress(END_CUE_SUPPRESSION_MS)
        expectClientCancellation()
        runCatching { recognizer?.cancel() }
        listener.onRms(0f)
    }

    fun destroy() {
        destroyed = true
        active = false
        sessionId++
        handler.removeCallbacksAndMessages(null)
        cueSilencer.suppress(END_CUE_SUPPRESSION_MS)
        expectClientCancellation()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        cueSilencer.release()
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

    private fun armStartupWatchdog(currentSession: Int) {
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        val delay = if (usingDedicatedOnDevice) DEDICATED_STARTUP_TIMEOUT_MS else SYSTEM_STARTUP_TIMEOUT_MS
        handler.postAtTime(
            {
                if (
                    !destroyed &&
                    active &&
                    sessionId == currentSession &&
                    !speechActivitySeen
                ) {
                    if (usingDedicatedOnDevice) dedicatedOnDeviceDisabled = true
                    abandonCurrentRecognizer(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                }
            },
            WATCHDOG_TOKEN,
            android.os.SystemClock.uptimeMillis() + delay
        )
    }

    private fun armResultWatchdog(currentSession: Int) {
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        handler.postAtTime(
            {
                if (!destroyed && active && sessionId == currentSession) {
                    abandonCurrentRecognizer(SpeechRecognizer.ERROR_NO_MATCH)
                }
            },
            WATCHDOG_TOKEN,
            android.os.SystemClock.uptimeMillis() + RESULT_TIMEOUT_MS
        )
    }

    private fun markSpeechActivity() {
        if (!active) return
        speechActivitySeen = true
        if (!speechDetectedReported) {
            speechDetectedReported = true
            listener.onSpeechDetected()
        }
    }

    private fun abandonCurrentRecognizer(code: Int) {
        if (destroyed) return
        active = false
        sessionId++
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        val old = recognizer
        recognizer = null
        usingDedicatedOnDevice = false
        cueSilencer.suppress(END_CUE_SUPPRESSION_MS)
        expectClientCancellation()
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
        listener.onRms(0f)
        listener.onError(code, recoverable = true)
    }

    private fun resetRecognizer() {
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        runCatching { recognizer?.destroy() }
        recognizer = null
        usingDedicatedOnDevice = false
    }

    private fun expectClientCancellation() {
        expectedClientErrors++
        handler.postDelayed({
            if (expectedClientErrors > 0) expectedClientErrors--
        }, EXPECTED_ERROR_TTL_MS)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        if (!active || destroyed) return
        listener.onReady(backendLabel())
    }

    override fun onBeginningOfSpeech() {
        markSpeechActivity()
        listener.onRms(0.24f)
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (!active || destroyed) return
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        if (normalized >= SPEECH_ACTIVITY_THRESHOLD) markSpeechActivity()
        listener.onRms(normalized)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        if (!active || destroyed) return
        markSpeechActivity()
        cueSilencer.suppress(END_CUE_SUPPRESSION_MS)
        armResultWatchdog(sessionId)
    }

    override fun onError(error: Int) {
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        active = false
        listener.onRms(0f)
        if (destroyed) return

        if (error == SpeechRecognizer.ERROR_CLIENT && expectedClientErrors > 0) {
            expectedClientErrors--
            return
        }

        val shouldFallBackToSystemRecognizer = usingDedicatedOnDevice && error in setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH
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
        handler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
        cueSilencer.suppress(END_CUE_SUPPRESSION_MS)
        active = false
        listener.onRms(0f)
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) markSpeechActivity()
        listener.onFinal(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (!active || destroyed) return
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) {
            markSpeechActivity()
            listener.onRms(0.38f)
            listener.onPartial(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        private val WATCHDOG_TOKEN = Any()
        private const val DEDICATED_STARTUP_TIMEOUT_MS = 4_500L
        private const val SYSTEM_STARTUP_TIMEOUT_MS = 8_000L
        private const val RESULT_TIMEOUT_MS = 5_000L
        private const val EXPECTED_ERROR_TTL_MS = 1_800L
        private const val SPEECH_ACTIVITY_THRESHOLD = 0.035f
        private const val START_CUE_SUPPRESSION_MS = 460L
        private const val END_CUE_SUPPRESSION_MS = 520L
    }
}
