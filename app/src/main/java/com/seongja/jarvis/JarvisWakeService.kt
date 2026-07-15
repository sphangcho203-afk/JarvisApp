package com.seongja.jarvis

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-started local wake listener.
 *
 * It uses only Android's on-device speech recognizer. When on-device recognition
 * is unavailable, the service refuses to start rather than silently using a
 * network recognizer. The visible foreground notification remains active while
 * the feature is enabled.
 */
class JarvisWakeService : Service(), RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var destroyed = false
    private var pausedForConversation = false
    private var lastWakeAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        running.set(true)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initializeRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                pauseRecognition()
                return START_STICKY
            }

            ACTION_RESUME -> {
                pausedForConversation = false
                startListeningSoon(250L)
                return START_STICKY
            }

            else -> {
                if (isEnabled(this)) {
                    pausedForConversation = false
                    startListeningSoon(250L)
                }
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        listening = false
        running.set(false)
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        super.onDestroy()
    }

    private fun initializeRecognizer() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            stopSelf()
            return
        }
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
        }
    }

    private fun startListeningSoon(delayMs: Long) {
        if (destroyed || pausedForConversation || !isEnabled(this)) return
        handler.removeCallbacksAndMessages(START_TOKEN)
        handler.postAtTime(
            { startListening() },
            START_TOKEN,
            android.os.SystemClock.uptimeMillis() + delayMs.coerceAtLeast(120L)
        )
    }

    private fun startListening() {
        if (destroyed || pausedForConversation || listening || !isEnabled(this)) return
        val engine = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        listening = true
        runCatching { engine.startListening(intent) }
            .onFailure {
                listening = false
                startListeningSoon(RESTART_DELAY_MS)
            }
    }

    private fun pauseRecognition() {
        pausedForConversation = true
        listening = false
        handler.removeCallbacksAndMessages(START_TOKEN)
        runCatching { recognizer?.cancel() }
    }

    private fun inspectResults(bundle: Bundle?, final: Boolean) {
        val candidates = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        val matched = candidates.firstOrNull { candidate ->
            val normalized = candidate
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            WAKE_PHRASES.any { phrase -> normalized.contains(phrase) }
        }
        if (matched != null) summonJarvis()
        if (final && !pausedForConversation) {
            listening = false
            startListeningSoon(RESTART_DELAY_MS)
        }
    }

    private fun summonJarvis() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWakeAtMs < WAKE_COOLDOWN_MS) return
        lastWakeAtMs = now
        pauseRecognition()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(EXTRA_WAKE_DETECTED, true)
        }
        runCatching { startActivity(intent) }
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, JarvisWakeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.seongja.jarvis.R.drawable.ic_launcher_background)
            .setContentTitle(getString(R.string.wake_service_notification_title))
            .setContentText(getString(R.string.wake_service_notification_text))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(android.app.Notification.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible local microphone service for the Jarvis wake phrase."
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        listening = false
        if (!destroyed && !pausedForConversation) startListeningSoon(RESTART_DELAY_MS)
    }

    override fun onResults(results: Bundle?) = inspectResults(results, final = true)
    override fun onPartialResults(partialResults: Bundle?) =
        inspectResults(partialResults, final = false)

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        const val EXTRA_WAKE_DETECTED = "jarvis_wake_detected"
        private const val ACTION_STOP = "com.seongja.jarvis.action.STOP_WAKE_LISTENER"
        private const val ACTION_PAUSE = "com.seongja.jarvis.action.PAUSE_WAKE_LISTENER"
        private const val ACTION_RESUME = "com.seongja.jarvis.action.RESUME_WAKE_LISTENER"
        private const val CHANNEL_ID = "jarvis_wake_listener"
        private const val NOTIFICATION_ID = 7011
        private const val PREFS = "jarvis_wake_preferences"
        private const val PREF_ENABLED = "wake_enabled"
        private const val RESTART_DELAY_MS = 550L
        private const val WAKE_COOLDOWN_MS = 4_000L
        private val START_TOKEN = Any()
        private val running = AtomicBoolean(false)

        @Volatile
        private var instance: JarvisWakeService? = null

        private val WAKE_PHRASES = setOf(
            "wake up jarvis",
            "hey jarvis",
            "jarvis wake up"
        )

        fun isRunning(): Boolean = running.get()

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false)

        fun canRun(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        fun start(context: Context): Boolean {
            if (!canRun(context)) return false
            setEnabled(context, true)
            return runCatching {
                context.startForegroundService(
                    Intent(context, JarvisWakeService::class.java).apply {
                        action = ACTION_RESUME
                    }
                )
                true
            }.getOrDefault(false)
        }

        fun pause(context: Context): Boolean {
            if (!isRunning()) return true
            instance?.pauseRecognition()
            return runCatching {
                context.startService(
                    Intent(context, JarvisWakeService::class.java).apply {
                        action = ACTION_PAUSE
                    }
                )
                true
            }.getOrDefault(false)
        }

        fun resume(context: Context): Boolean {
            if (!isEnabled(context) || !canRun(context)) return false
            return runCatching {
                context.startForegroundService(
                    Intent(context, JarvisWakeService::class.java).apply {
                        action = ACTION_RESUME
                    }
                )
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context): Boolean {
            setEnabled(context, false)
            return runCatching {
                context.stopService(Intent(context, JarvisWakeService::class.java))
                true
            }.getOrDefault(false)
        }

        private fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ENABLED, enabled)
                .apply()
        }
    }
}
