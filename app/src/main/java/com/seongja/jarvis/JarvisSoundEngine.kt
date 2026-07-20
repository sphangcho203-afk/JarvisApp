package com.seongja.jarvis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Native procedural sensory engine.
 *
 * Every cue is synthesized in memory from layered tones, sweeps and envelopes.
 * No generic Android notification tone and no copyrighted audio asset is used.
 */
internal class JarvisSoundEngine {
    private enum class Cue {
        BOOT,
        PROCESSING,
        RESEARCH,
        SPEAKING,
        SUCCESS,
        WARNING,
        ERROR,
        STANDBY,
        TIMER_COMPLETE,
        THEME_SHIFT
    }

    private data class CueSpec(
        val durationMs: Int,
        val notes: DoubleArray,
        val sweepStartHz: Double = 0.0,
        val sweepEndHz: Double = 0.0,
        val pulseHz: Double = 0.0,
        val gain: Double = 0.16
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "friday-sensory-audio").apply { isDaemon = true }
    }
    private val released = AtomicBoolean(false)
    private val lastCueAt = AtomicLong(0L)
    private val lock = Any()

    @Volatile
    private var activeTrack: AudioTrack? = null

    fun boot() = play(Cue.BOOT)
    fun processing() = play(Cue.PROCESSING)
    fun research() = play(Cue.RESEARCH)
    fun speaking() = play(Cue.SPEAKING)
    fun success() = play(Cue.SUCCESS)
    fun warning() = play(Cue.WARNING)
    fun error() = play(Cue.ERROR)
    fun standby() = play(Cue.STANDBY)
    fun timerComplete() = play(Cue.TIMER_COMPLETE)
    fun themeShift() = play(Cue.THEME_SHIFT)

    private fun play(cue: Cue) {
        if (released.get()) return
        val now = SystemClock.elapsedRealtime()
        val minimumGap = if (cue == Cue.PROCESSING) 260L else 90L
        if (now - lastCueAt.get() < minimumGap) return
        lastCueAt.set(now)
        executor.execute {
            if (released.get()) return@execute
            val spec = specFor(cue)
            val pcm = synthesize(spec, cue)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
                    .build()
            }.getOrNull() ?: return@execute

            synchronized(lock) {
                runCatching { activeTrack?.stop() }
                runCatching { activeTrack?.release() }
                activeTrack = track
            }
            runCatching {
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                track.setVolume(0.72f)
                track.play()
                Thread.sleep(spec.durationMs.toLong() + 70L)
            }
            synchronized(lock) {
                if (activeTrack === track) activeTrack = null
            }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun synthesize(spec: CueSpec, cue: Cue): ShortArray {
        val frames = max(1, SAMPLE_RATE * spec.durationMs / 1_000)
        val result = ShortArray(frames * 2)
        val attackFrames = max(1, (frames * 0.08).toInt())
        val releaseFrames = max(1, (frames * 0.32).toInt())

        for (frame in 0 until frames) {
            val time = frame.toDouble() / SAMPLE_RATE.toDouble()
            val attack = min(1.0, frame.toDouble() / attackFrames.toDouble())
            val releaseStart = frames - releaseFrames
            val release = if (frame < releaseStart) 1.0 else {
                max(0.0, (frames - frame).toDouble() / releaseFrames.toDouble())
            }
            val envelope = attack * release * release
            val pulse = if (spec.pulseHz > 0.0) {
                0.54 + 0.46 * (0.5 + 0.5 * sin(2.0 * PI * spec.pulseHz * time))
            } else 1.0

            var left = 0.0
            var right = 0.0
            spec.notes.forEachIndexed { index, frequency ->
                val entrance = (index * 0.035 * SAMPLE_RATE).toInt()
                if (frame >= entrance) {
                    val localTime = (frame - entrance).toDouble() / SAMPLE_RATE.toDouble()
                    val harmonic = sin(2.0 * PI * frequency * localTime) +
                        0.17 * sin(2.0 * PI * frequency * 2.0 * localTime)
                    val pan = if (spec.notes.size <= 1) 0.0 else {
                        -0.58 + (1.16 * index.toDouble() / (spec.notes.size - 1).toDouble())
                    }
                    val leftGain = cos((pan + 1.0) * PI / 4.0)
                    val rightGain = sin((pan + 1.0) * PI / 4.0)
                    left += harmonic * leftGain
                    right += harmonic * rightGain
                }
            }

            if (spec.sweepStartHz > 0.0 && spec.sweepEndHz > 0.0) {
                val progress = frame.toDouble() / frames.toDouble()
                val frequency = spec.sweepStartHz + (spec.sweepEndHz - spec.sweepStartHz) * progress
                val sweep = sin(2.0 * PI * frequency * time) * 0.46
                left += sweep
                right += sweep
            }

            val shimmer = when (cue) {
                Cue.BOOT, Cue.THEME_SHIFT, Cue.RESEARCH ->
                    sin(2.0 * PI * (1_420.0 + 110.0 * sin(time * 5.0)) * time) * 0.055
                Cue.ERROR, Cue.WARNING -> sin(2.0 * PI * 92.0 * time) * 0.14
                else -> 0.0
            }
            left += shimmer
            right += shimmer

            val scale = spec.gain * envelope * pulse / max(1.0, spec.notes.size * 0.72)
            result[frame * 2] = toPcm(left * scale)
            result[frame * 2 + 1] = toPcm(right * scale)
        }
        return result
    }

    private fun toPcm(value: Double): Short =
        (value.coerceIn(-0.96, 0.96) * Short.MAX_VALUE).toInt().toShort()

    private fun specFor(cue: Cue): CueSpec = when (cue) {
        Cue.BOOT -> CueSpec(1_850, doubleArrayOf(145.0, 290.0, 580.0), 44.0, 82.0, 0.0, 0.20)
        Cue.PROCESSING -> CueSpec(760, doubleArrayOf(180.0, 270.0, 405.0), 0.0, 0.0, 2.2, 0.12)
        Cue.RESEARCH -> CueSpec(1_450, doubleArrayOf(293.66, 369.99, 440.0, 554.37), 160.0, 680.0, 1.6, 0.11)
        Cue.SPEAKING -> CueSpec(520, doubleArrayOf(392.0, 493.88, 659.25), 0.0, 0.0, 0.0, 0.10)
        Cue.SUCCESS -> CueSpec(620, doubleArrayOf(440.0, 659.25, 987.77), 320.0, 1_120.0, 0.0, 0.14)
        Cue.WARNING -> CueSpec(820, doubleArrayOf(164.81, 246.94), 240.0, 120.0, 2.0, 0.13)
        Cue.ERROR -> CueSpec(980, doubleArrayOf(110.0, 155.56), 210.0, 82.0, 1.45, 0.16)
        Cue.STANDBY -> CueSpec(420, doubleArrayOf(220.0, 330.0), 0.0, 0.0, 0.0, 0.075)
        Cue.TIMER_COMPLETE -> CueSpec(1_250, doubleArrayOf(523.25, 659.25, 783.99, 1_046.5), 260.0, 1_360.0, 0.0, 0.17)
        Cue.THEME_SHIFT -> CueSpec(1_300, doubleArrayOf(360.0, 720.0, 960.0, 1_240.0), 180.0, 1_600.0, 0.0, 0.15)
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        synchronized(lock) {
            runCatching { activeTrack?.stop() }
            runCatching { activeTrack?.release() }
            activeTrack = null
        }
        executor.shutdownNow()
    }

    companion object {
        private const val SAMPLE_RATE = 48_000
    }
}
