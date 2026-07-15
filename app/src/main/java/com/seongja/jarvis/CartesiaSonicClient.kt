package com.seongja.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Native Cartesia Sonic streaming TTS client.
 *
 * Text deltas are appended to one Cartesia context while PCM16 chunks are
 * decoded and written to Android AudioTrack as soon as they arrive.
 */
class CartesiaSonicClient(
    context: Context,
    private val listener: Listener
) : WebSocketListener() {

    interface Listener {
        fun onReady(label: String)
        fun onAudioStarted(label: String)
        fun onCompleted()
        fun onDiagnostic(message: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val store = SecureVoiceRegistry(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cartesia-pcm-output").apply { isDaemon = true }
    }
    private val destroyed = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val active = AtomicBoolean(false)
    private val audioStarted = AtomicBoolean(false)
    private val completionSent = AtomicBoolean(false)
    private val totalFramesWritten = AtomicLong(0L)
    private val pendingChunks = ConcurrentLinkedQueue<String>()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var contextId: String = ""

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var openedAtMs: Long = 0L

    @Volatile
    private var finishRequested = false

    fun isConfigured(): Boolean = store.load().isConfigured()

    fun isActive(): Boolean = active.get()

    /** Starts a new full-duplex Sonic context. */
    fun begin(): Boolean {
        if (destroyed.get()) return false
        val settings = store.load()
        if (!settings.isConfigured()) return false

        cancel(notify = false)
        active.set(true)
        completionSent.set(false)
        audioStarted.set(false)
        totalFramesWritten.set(0L)
        finishRequested = false
        contextId = UUID.randomUUID().toString()
        openedAtMs = SystemClock.elapsedRealtime()

        val url = buildString {
            append(CartesiaVoiceSettings.WEBSOCKET_ENDPOINT)
            append("?cartesia_version=")
            append(CartesiaVoiceSettings.API_VERSION)
        }
        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", settings.apiKey.trim())
            .header("User-Agent", "Jarvis-Android/0.9.18")
            .build()

        listener.onDiagnostic("CARTESIA -> CONNECTING SONIC-3 // GEMMA EN-GB")
        socket = client.newWebSocket(request, this)
        return true
    }

    /** Pushes one Groq/Gemini text delta without waiting for the full answer. */
    fun push(delta: String) {
        if (!active.get() || destroyed.get()) return
        val clean = delta.replace("\u0000", "").replace("\r", "")
        if (clean.isEmpty()) return
        if (!connected.get()) {
            pendingChunks.add(clean)
            return
        }
        sendGeneration(clean, continuing = true)
    }

    /** Signals the last continuation; Cartesia then emits `done`. */
    fun finish() {
        if (!active.get() || destroyed.get()) return
        finishRequested = true
        if (!connected.get()) return
        flushPending()
        sendGeneration("", continuing = false)
    }

    fun cancel() = cancel(notify = false)

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        cancel(notify = false)
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        audioExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (socket !== webSocket || destroyed.get()) {
            webSocket.close(1000, "stale client")
            return
        }
        connected.set(true)
        val latency = SystemClock.elapsedRealtime() - openedAtMs
        store.recordSuccess(latencyMs = latency, statusCode = response.code)
        mainHandler.post {
            listener.onReady("CARTESIA SONIC-3 // ${CartesiaVoiceSettings.VOICE_LABEL}")
            listener.onDiagnostic("CARTESIA -> SOCKET OPEN ${latency}ms")
        }
        flushPending()
        if (finishRequested) sendGeneration("", continuing = false)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (socket !== webSocket || destroyed.get()) return
        runCatching { JSONObject(text) }
            .onSuccess(::handlePayload)
            .onFailure { fail("Cartesia protocol error: ${it.message}") }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (socket === webSocket) socket = null
        connected.set(false)
        if (active.get() && !completionSent.get()) {
            fail("Cartesia socket closed before completion ($code).")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (socket === webSocket) socket = null
        connected.set(false)
        val status = response?.code ?: 0
        fail("Cartesia unavailable${if (status > 0) " HTTP $status" else ""}: ${t.message ?: t.javaClass.simpleName}", status)
    }

    private fun sendGeneration(transcript: String, continuing: Boolean) {
        val activeSocket = socket ?: return
        val payload = JSONObject().apply {
            put("model_id", CartesiaVoiceSettings.MODEL_ID)
            put("transcript", transcript)
            put(
                "voice",
                JSONObject()
                    .put("mode", "id")
                    .put("id", CartesiaVoiceSettings.VOICE_ID)
            )
            put("language", CartesiaVoiceSettings.LANGUAGE)
            put("context_id", contextId)
            put(
                "output_format",
                JSONObject()
                    .put("container", "raw")
                    .put("encoding", "pcm_s16le")
                    .put("sample_rate", CartesiaVoiceSettings.SAMPLE_RATE_HZ)
            )
            put("continue", continuing)
            put("max_buffer_delay_ms", CartesiaVoiceSettings.MAX_BUFFER_DELAY_MS)
            put(
                "generation_config",
                JSONObject()
                    .put("speed", CartesiaVoiceSettings.SPEED)
                    .put("emotion", "confident")
            )
        }
        if (!activeSocket.send(payload.toString())) {
            fail("Cartesia WebSocket rejected a text continuation.")
        }
    }

    private fun flushPending() {
        while (connected.get()) {
            val chunk = pendingChunks.poll() ?: break
            sendGeneration(chunk, continuing = true)
        }
    }

    private fun handlePayload(payload: JSONObject) {
        if (payload.optString("context_id").let { it.isNotBlank() && it != contextId }) return
        when (payload.optString("type")) {
            "chunk" -> {
                val encoded = payload.optString("data")
                if (encoded.isBlank()) return
                val pcm = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                    .getOrElse {
                        fail("Cartesia returned invalid PCM data.")
                        return
                    }
                enqueueAudio(pcm)
            }

            "done" -> completeAfterDrain()

            "error" -> {
                val status = payload.optInt("status_code", 0)
                val title = payload.optString("title", "Cartesia error")
                val message = payload.optString("message")
                fail("$title${if (message.isNotBlank()) ": $message" else ""}", status)
            }

            "timestamps", "phoneme_timestamps", "flush_done" -> Unit
        }
    }

    private fun enqueueAudio(pcm: ByteArray) {
        if (pcm.isEmpty() || !active.get()) return
        ensureAudioTrack()
        audioExecutor.execute {
            val track = audioTrack ?: return@execute
            if (!audioStarted.getAndSet(true)) {
                mainHandler.post {
                    listener.onAudioStarted("CARTESIA SONIC-3 // GEMMA EN-GB")
                    listener.onDiagnostic("VOICE OUTPUT -> CARTESIA PCM16 ${CartesiaVoiceSettings.SAMPLE_RATE_HZ}HZ")
                }
            }
            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                fail("Cartesia PCM playback failed with code $written.")
            } else {
                totalFramesWritten.addAndGet(written.toLong() / BYTES_PER_FRAME)
            }
        }
    }

    private fun ensureAudioTrack() {
        if (audioTrack != null) return
        synchronized(this) {
            if (audioTrack != null) return
            val minBuffer = AudioTrack.getMinBufferSize(
                CartesiaVoiceSettings.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(CartesiaVoiceSettings.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuffer, CartesiaVoiceSettings.SAMPLE_RATE_HZ))
                .build()
            audioTrack = track
            track.play()
        }
    }

    private fun completeAfterDrain() {
        if (!completionSent.compareAndSet(false, true)) return
        audioExecutor.execute {
            val track = audioTrack
            if (track != null) {
                val deadline = SystemClock.elapsedRealtime() + MAX_DRAIN_WAIT_MS
                while (
                    SystemClock.elapsedRealtime() < deadline &&
                    track.playbackHeadPosition.toLong() < totalFramesWritten.get()
                ) {
                    Thread.sleep(15L)
                }
            }
            releaseAudioTrack()
            active.set(false)
            connected.set(false)
            pendingChunks.clear()
            socket?.close(1000, "context complete")
            socket = null
            mainHandler.post {
                listener.onDiagnostic("VOICE OUTPUT -> CARTESIA COMPLETE")
                listener.onCompleted()
            }
        }
    }

    private fun fail(message: String, statusCode: Int = 0) {
        if (!active.getAndSet(false) && completionSent.get()) return
        store.recordFailure(message, statusCode)
        connected.set(false)
        pendingChunks.clear()
        val previous = socket
        socket = null
        previous?.cancel()
        audioExecutor.execute { releaseAudioTrack() }
        mainHandler.post { listener.onError(message) }
    }

    private fun cancel(notify: Boolean) {
        val wasActive = active.getAndSet(false)
        connected.set(false)
        finishRequested = false
        pendingChunks.clear()
        val id = contextId
        contextId = ""
        socket?.let { activeSocket ->
            if (id.isNotBlank()) {
                activeSocket.send(
                    JSONObject()
                        .put("context_id", id)
                        .put("cancel", true)
                        .toString()
                )
            }
            activeSocket.cancel()
        }
        socket = null
        audioExecutor.execute { releaseAudioTrack() }
        if (notify && wasActive) mainHandler.post(listener::onCompleted)
    }

    private fun releaseAudioTrack() {
        val track = audioTrack
        audioTrack = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        audioStarted.set(false)
        totalFramesWritten.set(0L)
    }

    companion object {
        private const val BYTES_PER_FRAME = 2L
        private const val MAX_DRAIN_WAIT_MS = 8_000L
    }
}
