package com.seongja.jarvis

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class FridayVoiceLabActivity : Activity() {
    private data class EngineChoice(
        val label: String,
        val packageName: String
    )

    private val initializationGeneration = AtomicInteger(0)
    private var tts: TextToSpeech? = null
    private var activeEnginePackage = ""
    private var engines: List<EngineChoice> = emptyList()
    private var voices: List<Voice> = emptyList()
    private var suppressEngineSelection = false
    private var destroyed = false

    private lateinit var statusView: TextView
    private lateinit var engineSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var rateLabel: TextView
    private lateinit var pitchLabel: TextView
    private lateinit var rateSeek: SeekBar
    private lateinit var pitchSeek: SeekBar
    private lateinit var offlineSwitch: Switch
    private lateinit var sampleInput: EditText
    private lateinit var testButton: Button
    private lateinit var saveButton: Button

    private val savedProfile: FridayVoiceProfile
        get() = FridayVoiceStore.load(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(buildInterface())
        bindControls()
        applyProfileToControls(savedProfile)
        initializeEngine(savedProfile.enginePackage)
    }

    private fun buildInterface(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(4, 10, 16))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(36))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(text("FRIDAY // VOICE LAB", 24f, true, Color.rgb(126, 235, 255)))
        root.addView(text("AUDITION INSTALLED ENGINES • SAVE ONE SYSTEM VOICE", 12f, false, Color.rgb(125, 161, 176)))
        root.addView(space(18))

        statusView = text("VOICE CORE // INITIALIZING", 13f, true, Color.rgb(110, 255, 190))
        root.addView(statusView)
        root.addView(space(18))

        root.addView(section("SPEECH ENGINE"))
        engineSpinner = spinner()
        root.addView(engineSpinner)
        root.addView(space(16))

        root.addView(section("ENGLISH VOICE"))
        voiceSpinner = spinner()
        root.addView(voiceSpinner)
        root.addView(space(12))

        offlineSwitch = Switch(this).apply {
            text = "Prefer voices that work offline"
            setTextColor(Color.rgb(215, 232, 238))
            textSize = 14f
            isChecked = true
        }
        root.addView(offlineSwitch)
        root.addView(space(20))

        rateLabel = section("SPEED // 0.94×")
        root.addView(rateLabel)
        rateSeek = SeekBar(this).apply {
            max = 60
            progress = 24
        }
        root.addView(rateSeek)
        root.addView(space(14))

        pitchLabel = section("PITCH // 1.02×")
        root.addView(pitchLabel)
        pitchSeek = SeekBar(this).apply {
            max = 40
            progress = 22
        }
        root.addView(pitchSeek)
        root.addView(space(20))

        root.addView(section("AUDITION LINE"))
        sampleInput = EditText(this).apply {
            setText(TEST_LINE)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(105, 130, 142))
            setBackgroundColor(Color.rgb(12, 27, 38))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 3
            maxLines = 6
            textSize = 16f
        }
        root.addView(
            sampleInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(space(18))

        testButton = actionButton("▶  TEST SELECTED VOICE")
        saveButton = actionButton("✓  USE AS FRIDAY")
        val resetButton = actionButton("RESET VOICE PROFILE")
        val settingsButton = actionButton("OPEN ANDROID TTS SETTINGS")
        root.addView(testButton)
        root.addView(space(10))
        root.addView(saveButton)
        root.addView(space(10))
        root.addView(resetButton)
        root.addView(space(10))
        root.addView(settingsButton)

        resetButton.setOnClickListener {
            FridayVoiceStore.clear(this)
            applyProfileToControls(FridayVoiceProfile())
            initializeEngine("")
            status("VOICE PROFILE // RESET TO SYSTEM DEFAULT", false)
        }
        settingsButton.setOnClickListener {
            runCatching {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        return scroll
    }

    private fun bindControls() {
        rateSeek.setOnSeekBarChangeListener(simpleSeekListener {
            rateLabel.text = String.format(Locale.US, "SPEED // %.2f×", selectedRate())
        })
        pitchSeek.setOnSeekBarChangeListener(simpleSeekListener {
            pitchLabel.text = String.format(Locale.US, "PITCH // %.2f×", selectedPitch())
        })

        engineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressEngineSelection) return
                val choice = engines.getOrNull(position) ?: return
                if (choice.packageName != activeEnginePackage) initializeEngine(choice.packageName)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        testButton.setOnClickListener { auditionSelectedVoice() }
        saveButton.setOnClickListener { saveSelectedVoice() }
    }

    private fun initializeEngine(requestedPackage: String) {
        val generation = initializationGeneration.incrementAndGet()
        activeEnginePackage = requestedPackage
        setControlsEnabled(false)
        status("VOICE ENGINE // INITIALIZING", false)
        tts?.stop()
        tts?.shutdown()
        tts = null

        val listener = TextToSpeech.OnInitListener { result ->
            if (destroyed || generation != initializationGeneration.get()) return@OnInitListener
            runOnUiThread { onEngineInitialized(result) }
        }
        tts = if (requestedPackage.isBlank()) {
            TextToSpeech(this, listener)
        } else {
            TextToSpeech(this, listener, requestedPackage)
        }
    }

    private fun onEngineInitialized(result: Int) {
        val engine = tts
        if (result != TextToSpeech.SUCCESS || engine == null) {
            voices = emptyList()
            setControlsEnabled(false)
            status("VOICE ENGINE // UNAVAILABLE • INSTALL OR ENABLE A TTS ENGINE", true)
            return
        }

        activeEnginePackage = engine.defaultEngine.orEmpty().ifBlank { activeEnginePackage }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { status("FRIDAY // SPEAKING", false) }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread { status("AUDITION // COMPLETE", false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { status("AUDITION // SPEECH ENGINE ERROR", true) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread { status("AUDITION // ERROR $errorCode", true) }
            }
        })

        populateEngines(engine)
        populateVoices(engine)
        setControlsEnabled(voices.isNotEmpty())
        status(
            if (voices.isEmpty()) {
                "VOICE LAB // NO ENGLISH VOICES FOUND"
            } else {
                "VOICE LAB // ${voices.size} ENGLISH VOICES READY"
            },
            voices.isEmpty()
        )
    }

    private fun populateEngines(engine: TextToSpeech) {
        engines = engine.engines
            .map { EngineChoice(it.label?.toString().orEmpty().ifBlank { it.name }, it.name) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.US) }

        val labels = engines.map { "${it.label}  •  ${it.packageName}" }
        engineSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        val selected = engines.indexOfFirst { it.packageName == activeEnginePackage }
            .takeIf { it >= 0 }
            ?: 0
        suppressEngineSelection = true
        engineSpinner.setSelection(selected, false)
        engineSpinner.post { suppressEngineSelection = false }
    }

    private fun populateVoices(engine: TextToSpeech) {
        voices = runCatching { FridayVoiceSelector.englishVoices(engine.voices.orEmpty()) }
            .getOrDefault(emptyList())
        val labels = voices.map { voice ->
            val route = if (voice.isNetworkConnectionRequired) "NETWORK" else "OFFLINE"
            val locale = voice.locale.toLanguageTag().uppercase(Locale.US)
            "$locale  •  $route  •  Q${voice.quality}  •  ${voice.name}"
        }
        voiceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )

        val profile = savedProfile
        val savedIndex = voices.indexOfFirst { it.name == profile.voiceName }
        val recommendedIndex = voices.indexOfFirst {
            !it.isNetworkConnectionRequired &&
                it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true)
        }
        voiceSpinner.setSelection(
            when {
                profile.enginePackage == activeEnginePackage && savedIndex >= 0 -> savedIndex
                recommendedIndex >= 0 -> recommendedIndex
                else -> 0
            },
            false
        )
    }

    private fun auditionSelectedVoice() {
        val engine = tts ?: return status("AUDITION // ENGINE NOT READY", true)
        val voice = selectedVoice() ?: return status("AUDITION // SELECT A VOICE", true)
        val line = sampleInput.text?.toString().orEmpty().trim().ifBlank { TEST_LINE }

        engine.stop()
        val voiceResult = engine.setVoice(voice)
        engine.setSpeechRate(selectedRate())
        engine.setPitch(selectedPitch())
        if (voiceResult == TextToSpeech.ERROR) {
            status("AUDITION // VOICE COULD NOT BE SELECTED", true)
            return
        }
        val result = engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "friday-voice-lab-test")
        if (result == TextToSpeech.ERROR) status("AUDITION // SYNTHESIS FAILED", true)
    }

    private fun saveSelectedVoice() {
        val voice = selectedVoice() ?: return status("SAVE // SELECT A VOICE FIRST", true)
        val profile = FridayVoiceProfile(
            enginePackage = activeEnginePackage,
            voiceName = voice.name,
            localeTag = voice.locale.toLanguageTag(),
            speechRate = selectedRate(),
            pitch = selectedPitch(),
            preferOffline = offlineSwitch.isChecked
        )
        FridayVoiceStore.save(this, profile)
        status("FRIDAY VOICE // SAVED • ${voice.name}", false)
        auditionSelectedVoice()
    }

    private fun applyProfileToControls(profile: FridayVoiceProfile) {
        rateSeek.progress = ((profile.speechRate - 0.70f) * 100f).roundToInt().coerceIn(0, 60)
        pitchSeek.progress = ((profile.pitch - 0.80f) * 100f).roundToInt().coerceIn(0, 40)
        offlineSwitch.isChecked = profile.preferOffline
        rateLabel.text = String.format(Locale.US, "SPEED // %.2f×", selectedRate())
        pitchLabel.text = String.format(Locale.US, "PITCH // %.2f×", selectedPitch())
    }

    private fun selectedVoice(): Voice? = voices.getOrNull(voiceSpinner.selectedItemPosition)

    private fun selectedRate(): Float = (0.70f + rateSeek.progress / 100f).coerceIn(0.70f, 1.30f)

    private fun selectedPitch(): Float = (0.80f + pitchSeek.progress / 100f).coerceIn(0.80f, 1.20f)

    private fun setControlsEnabled(enabled: Boolean) {
        voiceSpinner.isEnabled = enabled
        rateSeek.isEnabled = enabled
        pitchSeek.isEnabled = enabled
        offlineSwitch.isEnabled = enabled
        sampleInput.isEnabled = enabled
        testButton.isEnabled = enabled
        saveButton.isEnabled = enabled
    }

    private fun status(message: String, error: Boolean) {
        statusView.text = message
        statusView.setTextColor(
            if (error) Color.rgb(255, 120, 135) else Color.rgb(110, 255, 190)
        )
    }

    private fun section(value: String): TextView = text(
        value,
        12f,
        true,
        Color.rgb(145, 205, 220)
    )

    private fun text(value: String, size: Float, bold: Boolean, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            letterSpacing = 0.08f
        }

    private fun spinner(): Spinner = Spinner(this).apply {
        setBackgroundColor(Color.rgb(13, 31, 42))
        minimumHeight = dp(52)
    }

    private fun actionButton(label: String): Button = Button(this).apply {
        text = label
        setTextColor(Color.rgb(222, 248, 255))
        setBackgroundColor(Color.rgb(17, 58, 76))
        gravity = Gravity.CENTER
        minHeight = dp(54)
    }

    private fun space(height: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun simpleSeekListener(onChange: () -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        destroyed = true
        initializationGeneration.incrementAndGet()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val TEST_LINE =
            "Good evening, Sir. FRIDAY here. Your systems are secure, and I am ready for your next command."

        fun launch(context: Context) {
            context.startActivity(Intent(context, FridayVoiceLabActivity::class.java))
        }
    }
}
