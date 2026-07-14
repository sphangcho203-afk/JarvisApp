package com.seongja.jarvis

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class JarvisVoiceGateway(
    context: Context,
    private val listener: Listener,
    private val endpoint: String = DEFAULT_ENDPOINT
) : WebSocketListener() {

    interface Listener {
        fun onBackendReady()
        fun onListening()
        fun onTranscribing()
        fun onTranscript(text: String)
        fun onRms(value: Float)
        fun onSpeechOutputStarted(provider: String)
        fun onSpeechOutputCompleted()
        fun onDiagnostic(message: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val destroyed = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recordingThread: Thread? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var wantsListening = false

    @Volatile
    private var pendingSpeech: String? = null

    fun isReady(): Boolean = connected.get()

    fun connect() {
        if (destroyed.get() || connected.get() || socket != null) return
        listener.onDiagnostic("VOICE BACKEND -> CONNECTING $endpoint")
        val request = Request.Builder().url(endpoint).build()
        socket = client.newWebSocket(request, this)
    }

    fun startListening() {
        if (destroyed.get()) return
        wantsListening = true
        stopSpeechInternal(notify = false)
        if (!connected.get()) {
            connect()
            return
        }
        sendJson("start_input")
        listener.onDiagnostic("VOICE INPUT -> ACTIVATION REQUESTED")
    }

    fun stopInput(sendForTranscription: Boolean) {
        wantsListening = false
        stopRecorder()
        if (sendForTranscription && connected.get()) {
            sendJson("stop_input")
            mainHandler.post(listener::onTranscribing)
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || destroyed.get()) return
        wantsListening = false
        stopRecorder()
        pendingSpeech = clean
        if (!connected.get()) {
            connect()
            return
        }
        pendingSpeech = null
        socket?.send(
            JSONObject()
                .put("type", "speak")
                .put("text", clean)
                .toString()
        )
    }

    fun stopSpeech() {
        stopSpeechInternal(notify = false)
        pendingSpeech = null
        val previous = socket
        socket = null
        connected.set(false)
        previous?.cancel()
        if (!destroyed.get()) connect()
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        wantsListening = false
        pendingSpeech = null
        stopRecorder()
        stopSpeechInternal(notify = false)
        socket?.close(1000, "activity destroyed")
        socket = null
        connected.set(false)
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        connected.set(true)
        listener.onDiagnostic("VOICE BACKEND -> SOCKET OPEN")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching { JSONObject(text) }
            .onSuccess(::handleJson)
            .onFailure { listener.onError("Voice protocol error: ${it.message}") }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val track = audioTrack ?: return
        if (!speaking.get()) return
        val data = bytes.toByteArray()
        val written = track.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
        if (written < 0) {
            listener.onError("PCM playback failed with code $written")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (socket === webSocket) socket = null
        connected.set(false)
        stopRecorder()
        stopSpeechInternal(notify = false)
        listener.onDiagnostic("VOICE BACKEND -> CLOSED $code")
        scheduleReconnectIfNeeded()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (socket === webSocket) socket = null
        connected.set(false)
        stopRecorder()
        stopSpeechInternal(notify = false)
        listener.onError("Voice backend unavailable: ${t.message ?: t.javaClass.simpleName}")
        scheduleReconnectIfNeeded()
    }

    private fun handleJson(payload: JSONObject) {
        when (payload.optString("type")) {
            "ready" -> {
                mainHandler.post(listener::onBackendReady)
                pendingSpeech?.let { speech ->
                    pendingSpeech = null
                    speak(speech)
                }
                if (wantsListening) sendJson("start_input")
            }

            "state" -> when (payload.optString("value")) {
                "listening" -> startRecorder()
                "transcribing" -> mainHandler.post(listener::onTranscribing)
                "speaking" -> listener.onDiagnostic("VOICE OUTPUT -> SYNTHESIZING")
                "idle" -> Unit
            }

            "transcript" -> {
                val text = payload.optString("text").trim()
                mainHandler.post { listener.onTranscript(text) }
            }

            "audio_start" -> {
                val sampleRate = payload.optInt("sample_rate_hz", OUTPUT_SAMPLE_RATE)
                    .takeIf { it in 8_000..48_000 }
                    ?: OUTPUT_SAMPLE_RATE
                val provider = payload.optString("provider", "streaming")
                startAudioTrack(sampleRate)
                mainHandler.post { listener.onSpeechOutputStarted(provider) }
            }

            "audio_segment" -> {
                val preview = payload.optString("text").take(80)
                if (preview.isNotBlank()) {
                    listener.onDiagnostic("VOICE SEGMENT -> $preview")
                }
            }

            "audio_end" -> stopSpeechInternal(notify = true)

            "error" -> listener.onError(payload.optString("message", "Voice backend error"))

            "pong" -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        if (destroyed.get() || recording.getAndSet(true)) return
        val minBuffer = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(4_096, minBuffer.takeIf { it > 0 } ?: 4_096)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(INPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recording.set(false)
            recorder.release()
            listener.onError("Raw microphone initialization failed.")
            return
        }

        audioRecord = recorder
        recorder.startRecording()
        mainHandler.post(listener::onListening)
        listener.onDiagnostic("VOICE INPUT -> PCM16 16000HZ")

        recordingThread = Thread({
            captureLoop(recorder, bufferSize)
        }, "jarvis-pcm-capture").apply {
            isDaemon = true
            start()
        }
    }

    private fun captureLoop(recorder: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        val startedAt = SystemClock.elapsedRealtime()
        var speechDetected = false
        var lastVoiceAt = startedAt

        while (recording.get() && !destroyed.get()) {
            val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (count <= 0) continue

            socket?.send(buffer.toByteString(0, count))
            val rms = pcmRms(buffer, count)
            mainHandler.post { listener.onRms(rms) }

            val now = SystemClock.elapsedRealtime()
            if (rms >= VOICE_THRESHOLD) {
                speechDetected = true
                lastVoiceAt = now
            }

            val silenceReached = speechDetected && now - lastVoiceAt >= END_SILENCE_MS
            val noSpeechTimeout = !speechDetected && now - startedAt >= NO_SPEECH_TIMEOUT_MS
            val maximumReached = now - startedAt >= MAX_CAPTURE_MS
            if (silenceReached || noSpeechTimeout || maximumReached) {
                recording.set(false)
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                audioRecord = null
                mainHandler.post { listener.onRms(0f) }
                if (connected.get()) {
                    sendJson("stop_input")
                    mainHandler.post(listener::onTranscribing)
                }
                break
            }
        }
    }

    private fun stopRecorder() {
        if (!recording.getAndSet(false)) return
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        recordingThread = null
        mainHandler.post { listener.onRms(0f) }
    }

    private fun startAudioTrack(sampleRate: Int) {
        stopSpeechInternal(notify = false)
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate / 2))
            .build()

        audioTrack = track
        speaking.set(true)
        track.play()
    }

    private fun stopSpeechInternal(notify: Boolean) {
        val wasSpeaking = speaking.getAndSet(false)
        val track = audioTrack
        audioTrack = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        if (notify && wasSpeaking) {
            mainHandler.post(listener::onSpeechOutputCompleted)
        }
    }

    private fun sendJson(type: String) {
        socket?.send(JSONObject().put("type", type).toString())
    }

    private fun scheduleReconnectIfNeeded() {
        if (destroyed.get() || (!wantsListening && pendingSpeech == null)) return
        mainHandler.postDelayed({ connect() }, RECONNECT_DELAY_MS)
    }

    private fun pcmRms(buffer: ByteArray, count: Int): Float {
        if (count < 2) return 0f
        var sum = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < count) {
            val low = buffer[index].toInt() and 0xff
            val high = buffer[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val normalized = sample / 32768.0
            sum += normalized * normalized
            samples++
            index += 2
        }
        if (samples == 0) return 0f
        return sqrt(sum / samples).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_ENDPOINT = "ws://127.0.0.1:8766/voice"
        private const val INPUT_SAMPLE_RATE = 16_000
        private const val OUTPUT_SAMPLE_RATE = 24_000
        private const val VOICE_THRESHOLD = 0.035f
        private const val END_SILENCE_MS = 950L
        private const val NO_SPEECH_TIMEOUT_MS = 8_000L
        private const val MAX_CAPTURE_MS = 20_000L
        private const val RECONNECT_DELAY_MS = 1_500L
    }
}
