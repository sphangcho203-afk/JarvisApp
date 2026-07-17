package com.seongja.jarvis

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

class OwnerVoiceEnrollmentActivity : Activity() {
    private lateinit var store: OwnerVoiceProfileStore
    private var authenticated = false
    private var externalFlow = false
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recorderThread: Thread? = null
    private val samples = mutableListOf<VoiceFeatureVector>()
    private val completedPhrases = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = OwnerVoiceProfileStore(this)
        showLocked()
        authenticateOwner()
    }

    override fun onStop() {
        stopCapture(discard = true)
        if (authenticated && !externalFlow && !isChangingConfigurations) {
            authenticated = false
            samples.clear()
            completedPhrases.clear()
            finish()
        }
        super.onStop()
    }

    @Deprecated("Android credential confirmation callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        externalFlow = false
        if (requestCode == REQUEST_CREDENTIAL && resultCode == RESULT_OK) {
            authenticated = true
            ensureMicrophonePermission()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showEnrollment()
            else showMessage("Microphone permission is required for owner voice enrollment.")
        }
    }

    private fun authenticateOwner() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Open F.R.I.D.A.Y. Owner Voice Lab",
            "Confirm the device owner before enrolling or replacing a voice profile."
        )
        if (intent == null) {
            showMessage("Configure a secure device lock before voice enrollment.")
            return
        }
        externalFlow = true
        startActivityForResult(intent, REQUEST_CREDENTIAL)
    }

    private fun ensureMicrophonePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            showEnrollment()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }
    }

    private fun showLocked() {
        val root = page()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(title("F.R.I.D.A.Y. // OWNER VOICE LAB"), params(bottom = 12))
        root.addView(panel().apply {
            addView(center("OWNER AUTHENTICATION REQUIRED", 14f, GREEN, true), params(bottom = 8))
            addView(center("Enrollment stores derived acoustic measurements only. Raw recordings are discarded after processing.", 11f, SOFT, false), params(bottom = 14))
            addView(button("AUTHENTICATE OWNER", CYAN) { authenticateOwner() }, params(bottom = 7))
            addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showEnrollment() {
        if (!authenticated) return
        val profile = store.load()
        val index = samples.size.coerceAtMost(ENROLLMENT_PHRASES.lastIndex)
        val nextPhrase = ENROLLMENT_PHRASES[index]
        val root = page()
        root.addView(title("F.R.I.D.A.Y. // OWNER VOICE LAB"), params(bottom = 4))
        root.addView(center("ACOUSTIC IDENTITY // LOCAL FEATURE ENROLLMENT", 9f, MUTED, true), params(bottom = 12))
        root.addView(panel().apply {
            addView(label("PROFILE // ${profile.statusLabel()}", 11f, GREEN, true), params(bottom = 4))
            addView(label("NEW SAMPLES // ${samples.size}/${OwnerVoiceMatcher.MIN_ENROLLMENT_SAMPLES}", 11f, CYAN, true), params(bottom = 4))
            addView(label("RAW AUDIO STORAGE // DISABLED", 10f, SOFT, false), params(bottom = 4))
            addView(label("AUTHORIZATION POWER // NONE", 10f, GOLD, true))
        }, params(bottom = 10))

        root.addView(panel().apply {
            addView(label("READ THIS PHRASE NATURALLY", 10f, MUTED, true), params(bottom = 6))
            addView(center(nextPhrase, 16f, Color.WHITE, true), params(bottom = 8))
            addView(label("Use your normal accent, pace, and volume. The capture stops automatically after six seconds.", 10f, SOFT, false))
        }, params(bottom = 9))

        root.addView(button(if (recording.get()) "RECORDING..." else "RECORD NEXT SAMPLE", CYAN) {
            if (!recording.get()) startCapture(nextPhrase)
        }, params(bottom = 6))

        if (samples.size >= OwnerVoiceMatcher.MIN_ENROLLMENT_SAMPLES) {
            root.addView(button("SAVE OWNER VOICE PROFILE", GREEN) { saveProfile() }, params(bottom = 6))
        }
        if (profile.enrolled) {
            root.addView(button("ERASE EXISTING VOICE PROFILE", RED) {
                store.clear()
                samples.clear()
                completedPhrases.clear()
                Toast.makeText(this, "Owner voice profile erased.", Toast.LENGTH_LONG).show()
                showEnrollment()
            }, params(bottom = 6))
        }
        root.addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    @Suppress("MissingPermission")
    private fun startCapture(phrase: String) {
        if (!authenticated || recording.getAndSet(true)) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(4_096, minBuffer.takeIf { it > 0 } ?: 4_096)
        val created = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .build()
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            recording.set(false)
            created.release()
            showMessage("The microphone could not initialize for enrollment.")
            return
        }
        recorder = created
        created.startRecording()
        showEnrollment()
        recorderThread = Thread({
            val accumulator = PcmFeatureAccumulator(SAMPLE_RATE)
            val buffer = ByteArray(bufferSize)
            val started = SystemClock.elapsedRealtime()
            while (recording.get() && SystemClock.elapsedRealtime() - started < CAPTURE_MS) {
                val count = created.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) accumulator.accept(buffer, count)
            }
            recording.set(false)
            runCatching { created.stop() }
            runCatching { created.release() }
            recorder = null
            recorderThread = null
            val sample = accumulator.finish(phrase)
            runOnUiThread {
                if (sample.isUsable()) {
                    samples += sample
                    completedPhrases += phrase
                    Toast.makeText(this, "Voice sample accepted. Raw audio discarded.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Sample was too quiet or too short. Please repeat it.", Toast.LENGTH_LONG).show()
                }
                showEnrollment()
            }
        }, "friday-owner-voice-enrollment").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopCapture(discard: Boolean) {
        if (!recording.getAndSet(false)) return
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recorderThread = null
        if (discard) {
            samples.clear()
            completedPhrases.clear()
        }
    }

    private fun saveProfile() {
        val profile = OwnerVoiceMatcher.build(samples, completedPhrases)
        if (!profile.enrolled) {
            showMessage("At least ${OwnerVoiceMatcher.MIN_ENROLLMENT_SAMPLES} usable samples are required.")
            return
        }
        store.save(profile)
        samples.clear()
        completedPhrases.clear()
        Toast.makeText(this, "Owner voice profile encrypted and enrolled.", Toast.LENGTH_LONG).show()
        showEnrollment()
    }

    private fun showMessage(message: String) {
        val root = page()
        root.addView(title("F.R.I.D.A.Y. // OWNER VOICE LAB"), params(bottom = 12))
        root.addView(panel().apply {
            addView(label(message, 12f, SOFT, false), params(bottom = 12))
            addView(button("RETURN", BLUE) { finish() })
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(22), dp(14), dp(28))
        setBackgroundColor(BG)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 12, 28))
            setStroke(dp(1), BLUE)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun title(text: String) = center(text, 21f, CYAN, true).apply { letterSpacing = .08f }

    private fun center(text: String, size: Float, color: Int, bold: Boolean) =
        label(text, size, color, bold).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    private fun button(text: String, accent: Int, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 10f
        letterSpacing = .06f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(7, 18, 39))
            setStroke(dp(1), accent)
            cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CREDENTIAL = 6301
        private const val REQUEST_MICROPHONE = 6302
        private const val SAMPLE_RATE = 16_000
        private const val CAPTURE_MS = 6_000L
        private val ENROLLMENT_PHRASES = listOf(
            "Wake up Jarvis. At your service, Sir.",
            "Open the newsroom and show me what matters today.",
            "Bring the optical systems online and analyze this object.",
            "Continue the F.R.I.D.A.Y. project from where we stopped.",
            "Secure the vault and return to standby."
        )
        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val RED = Color.rgb(255, 92, 106)
        private val GOLD = Color.rgb(244, 194, 91)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(context: Context) {
            context.startActivity(
                Intent(context, OwnerVoiceEnrollmentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}
