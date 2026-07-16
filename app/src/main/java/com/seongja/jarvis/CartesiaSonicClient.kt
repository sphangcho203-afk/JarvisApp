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
 * Native Cartesia Sonic streaming TTS with encrypted P1/B1/B2/B3 failover.
 * A backup is selected only before audio begins, preventing two voices from being
 * spliced into one sentence. Buffered text is replayed safely on the new route.
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

    private val store = SecureVoiceRegistry(context.applicationContext)
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
    private val transcriptHistory = StringBuilder()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var contextId: String = ""
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var openedAtMs: Long = 0L
    @Volatile private var finishRequested = false
    @Volatile private var activeKeyIndex = 0
    @Volatile private var activeApiKey = ""
    @Volatile private var failoverCount = 0

    fun isConfigured(): Boolean = store.load().isConfigured()
    fun isActive(): Boolean = active.get()
    fun configuredKeyCount(): Int = store.load().configuredKeys().size
    fun activeRouteLabel(): String = store.load().routeLabel(activeKeyIndex)

    /** Starts a new full-duplex Sonic context. */
    fun begin(): Boolean {
        if (destroyed.get()) return false
        val selected = store.selectActiveKey() ?: return false
        cancel(notify = false)
        active.set(true)
        completionSent.set(false)
        audioStarted.set(false)
        totalFramesWritten.set(0L)
        finishRequested = false
        failoverCount = 0
        transcriptHistory.setLength(0)
        pendingChunks.clear()
        activeKeyIndex = selected.first
        activeApiKey = selected.second
        openSocket()
        return true
    }

    /** Pushes one Groq/Gemini/DeepSeek text delta without waiting for the full answer. */
    fun push(delta: String) {
        if (!active.get() || destroyed.get()) return
        val clean = delta.replace("\u0000", "").replace("\r", "")
        if (clean.isEmpty()) return
        synchronized(transcriptHistory) { transcriptHistory.append(clean) }
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

    private fun openSocket() {
        if (!active.get() || destroyed.get()) return
        contextId = UUID.randomUUID().toString()
        openedAtMs = SystemClock.elapsedRealtime()
        connected.set(false)
        val url = "${CartesiaVoiceSettings.WEBSOCKET_ENDPOINT}?cartesia_version=${CartesiaVoiceSettings.API_VERSION}"
        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", activeApiKey)
            .header("User-Agent", "Friday-Android/0.9.22")
            .build()
        val route = store.load().routeLabel(activeKeyIndex)
        mainHandler.post {
            listener.onDiagnostic(
                "CARTESIA $route -> CONNECTING SONIC-3 // FAILOVER ${failoverCount}/${CartesiaVoiceSettings.MAX_KEYS - 1}"
            )
        }
        socket = client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (socket !== webSocket || destroyed.get() || !active.get()) {
            webSocket.close(1000, "stale client")
            return
        }
        connected.set(true)
        val latency = SystemClock.elapsedRealtime() - openedAtMs
        store.recordSuccess(activeKeyIndex, latency, response.code)
        val route = store.load().routeLabel(activeKeyIndex)
        mainHandler.post {
            listener.onReady("CARTESIA $route // SONIC-3 // ${CartesiaVoiceSettings.VOICE_LABEL}")
            listener.onDiagnostic("CARTESIA $route -> SOCKET OPEN ${latency}ms")
        }
        flushPending()
        if (finishRequested) sendGeneration("", continuing = false)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (socket !== webSocket || destroyed.get()) return
        runCatching { JSONObject(text) }
            .onSuccess(::handlePayload)
            .onFailure { handleFailure("Cartesia protocol error: ${it.message}", 0) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (socket !== webSocket) return
        socket = null
        connected.set(false)
        if (active.get() && !completionSent.get()) {
            handleFailure("Cartesia socket closed before completion ($code).", code)
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (socket !== webSocket) return
        socket = null
        connected.set(false)
        val status = response?.code ?: 0
        handleFailure(
            "Cartesia unavailable${if (status > 0) " HTTP $status" else ""}: ${t.message ?: t.javaClass.simpleName}",
            status
        )
    }

    private fun sendGeneration(transcript: String, continuing: Boolean) {
        val activeSocket = socket ?: return
        val payload = JSONObject().apply {
            put("model_id", CartesiaVoiceSettings.MODEL_ID)
            put("transcript", transcript)
            put(
                "voice",
                JSONObject().put("mode", "id").put("id", CartesiaVoiceSettings.VOICE_ID)
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
                JSONObject().put("speed", CartesiaVoiceSettings.SPEED).put("emotion", "confident")
            )
        }
        if (!activeSocket.send(payload.toString())) {
            handleFailure("Cartesia WebSocket rejected a text continuation.", 0)
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
                        handleFailure("Cartesia returned invalid PCM data.", 0)
                        return
                    }
                enqueueAudio(pcm)
            }
            "done" -> completeAfterDrain()
            "error" -> {
                val status = payload.optInt("status_code", 0)
                val title = payload.optString("title", "Cartesia error")
                val message = payload.optString("message")
                handleFailure("$title${if (message.isNotBlank()) ": $message" else ""}", status)
            }
            "timestamps", "phoneme_timestamps", "flush_done" -> Unit
        }
    }

    private fun handleFailure(message: String, statusCode: Int) {
        if (!active.get() || destroyed.get()) return
        if (!audioStarted.get() && failoverCount < CartesiaVoiceSettings.MAX_KEYS - 1) {
            val next = store.recordFailure(
                keyIndex = activeKeyIndex,
                message = message,
                statusCode = statusCode,
                rotate = true
            )
            if (next != null && next.first != activeKeyIndex) {
                failoverCount++
                val previousRoute = store.load().routeLabel(activeKeyIndex)
                val nextRoute = store.load().routeLabel(next.first)
                val previousSocket = socket
                socket = null
                connected.set(false)
                previousSocket?.cancel()
                activeKeyIndex = next.first
                activeApiKey = next.second
                pendingChunks.clear()
                val replay = synchronized(transcriptHistory) { transcriptHistory.toString() }
                if (replay.isNotBlank()) pendingChunks.add(replay)
                mainHandler.post {
                    listener.onDiagnostic(
                        "CARTESIA $previousRoute -> DEGRADED // FAILOVER TO $nextRoute"
                    )
                }
                openSocket()
                return
            }
        }
        store.recordFailure(activeKeyIndex, message, statusCode, rotate = false)
        failFinal(message)
    }

    private fun enqueueAudio(pcm: ByteArray) {
        if (pcm.isEmpty() || !active.get()) return
        ensureAudioTrack()
        audioExecutor.execute {
            val track = audioTrack ?: return@execute
            if (!audioStarted.getAndSet(true)) {
                val route = store.load().routeLabel(activeKeyIndex)
                mainHandler.post {
                    listener.onAudioStarted("CARTESIA $route // SONIC-3 // GEMMA EN-GB")
                    listener.onDiagnostic(
                        "VOICE OUTPUT -> CARTESIA $route PCM16 ${CartesiaVoiceSettings.SAMPLE_RATE_HZ}HZ"
                    )
                }
            }
            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                failFinal("Cartesia PCM playback failed with code $written.")
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
            transcriptHistory.setLength(0)
            socket?.close(1000, "context complete")
            socket = null
            mainHandler.post {
                listener.onDiagnostic("VOICE OUTPUT -> CARTESIA COMPLETE")
                listener.onCompleted()
            }
        }
    }

    private fun failFinal(message: String) {
        if (!active.getAndSet(false) && completionSent.get()) return
        connected.set(false)
        pendingChunks.clear()
        transcriptHistory.setLength(0)
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
        transcriptHistory.setLength(0)
        val id = contextId
        contextId = ""
        socket?.let { activeSocket ->
            if (id.isNotBlank()) {
                activeSocket.send(JSONObject().put("context_id", id).put("cancel", true).toString())
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
        private const val MAX_DRAIN_WAIT_MS = 12_000L
    }
}
