package com.seongja.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class ProviderMeshActivity : Activity() {
    private lateinit var store: SecureProviderMeshRegistry
    private lateinit var client: UniversalProviderMeshClient
    private lateinit var providerSpinner: Spinner
    private lateinit var categoryLabel: TextView
    private lateinit var providerNote: TextView
    private lateinit var baseUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var model: EditText
    private lateinit var enabled: CheckBox
    private lateinit var makeDefaultAi: CheckBox
    private lateinit var status: TextView
    private var presets: List<ProviderPreset> = emptyList()
    private var firstSelection = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = SecureProviderMeshRegistry(this)
        client = UniversalProviderMeshClient(store)
        presets = filteredPresets()
        setContentView(buildUi())
        configureProviderSpinner()
    }

    private fun filteredPresets(): List<ProviderPreset> {
        val category = intent.getStringExtra(EXTRA_CATEGORY)
            ?.let { runCatching { ProviderMeshCategory.valueOf(it) }.getOrNull() }
        return if (category == null) ProviderMeshCatalog.presets else ProviderMeshCatalog.presets.filter { it.category == category }
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(30))
            setBackgroundColor(BG)
        }
        root.addView(label("F.R.I.D.A.Y. // UNIVERSAL PROVIDER MESH", 21f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .09f
        }, matchWidth(bottom = 5))
        root.addView(label("VOICE-TARGETED API + MODEL CONTROL", 10f, SOFT, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .18f
        }, matchWidth(bottom = 14))
        root.addView(label(
            "Credentials are encrypted with Android Keystore AES-GCM. FRIDAY can open any provider by voice, discover models where supported, test the route, and select the default reasoning model.",
            12f,
            MUTED
        ), matchWidth(bottom = 16))

        categoryLabel = label("PROVIDER DIRECTORY", 14f, CYAN, true)
        root.addView(categoryLabel, matchWidth(bottom = 6))
        providerSpinner = Spinner(this).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = panelBackground(PANEL, BLUE)
        }
        root.addView(providerSpinner, matchWidth(bottom = 10))

        providerNote = label("SELECT A PROVIDER", 11f, MUTED)
        root.addView(providerNote, matchWidth(bottom = 10))

        baseUrl = textInput("Secure HTTPS base URL", secret = false)
        apiKey = textInput("Encrypted API key / OAuth access token", secret = true)
        model = textInput("Model ID (discover or enter manually)", secret = false)
        enabled = check("Provider enabled")
        makeDefaultAi = check("Use as default AI reasoning route")
        status = label("STATUS // NOT TESTED", 11f, SOFT, true).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = panelBackground(PANEL, VIOLET)
        }

        root.addView(baseUrl, matchWidth(bottom = 7))
        root.addView(apiKey, matchWidth(bottom = 7))
        root.addView(model, matchWidth(bottom = 5))
        root.addView(enabled, matchWidth(bottom = 2))
        root.addView(makeDefaultAi, matchWidth(bottom = 8))
        root.addView(status, matchWidth(bottom = 10))

        root.addView(actionButton("SAVE PROVIDER") { saveCurrent(false, true) }, matchWidth(bottom = 7))
        root.addView(actionButton("DISCOVER AVAILABLE MODELS") { discoverModels() }, matchWidth(bottom = 7))
        root.addView(actionButton("SAVE + TEST PROVIDER") { testProvider() }, matchWidth(bottom = 7))
        root.addView(actionButton("SAVE + USE AS DEFAULT AI") { saveCurrent(true, true) }, matchWidth(bottom = 7))
        root.addView(actionButton("OPEN LEGACY OPERATIONS CONSOLE") {
            startActivity(Intent(this, CloudConfigActivity::class.java))
        }, matchWidth(bottom = 7))
        root.addView(actionButton("RETURN TO F.R.I.D.A.Y.") { finish() }, matchWidth(bottom = 10))

        root.addView(label(
            "VOICE EXAMPLES\n• Open DeepSeek API setup\n• Open OpenAI setup\n• Open weather API setup\n• Open research APIs setup\n• List OpenRouter models\n• Use Anthropic model claude-model-id\n• Provider mesh status",
            11f,
            MUTED
        ), matchWidth(top = 8))

        return ScrollView(this).apply { addView(root) }
    }

    private fun configureProviderSpinner() {
        val labels = presets.map { "${it.label}  //  ${it.category.label}" }
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                populate(presets[position])
                if (firstSelection) {
                    firstSelection = false
                    if (intent.getBooleanExtra(EXTRA_DISCOVER, false)) providerSpinner.postDelayed({ discoverModels() }, 350L)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val requestedId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        val position = presets.indexOfFirst { it.id == requestedId }.takeIf { it >= 0 } ?: 0
        providerSpinner.setSelection(position)
    }

    private fun selectedPreset(): ProviderPreset = presets.getOrElse(providerSpinner.selectedItemPosition) { presets.first() }

    private fun populate(preset: ProviderPreset) {
        val registry = store.load()
        val profile = registry.profile(preset.id)
        categoryLabel.text = "${preset.category.label} // ${preset.company.uppercase(Locale.US)}"
        providerNote.text = buildString {
            append(preset.note.ifBlank { "PRESET // ${preset.protocol.name.replace('_', ' ')}" })
            append("\nINVOKE // ${profile.resolvedBaseUrl().ifBlank { preset.baseUrl }}${preset.invokePath}")
        }
        baseUrl.setText(profile.baseUrl.ifBlank { preset.baseUrl })
        apiKey.setText(profile.apiKey)
        model.setText(profile.model)
        enabled.isChecked = profile.enabled
        makeDefaultAi.isChecked = registry.defaultAiProviderId == preset.id
        makeDefaultAi.visibility = if (preset.category in setOf(ProviderMeshCategory.AI, ProviderMeshCategory.CUSTOM)) View.VISIBLE else View.GONE
        model.visibility = if (preset.category in setOf(ProviderMeshCategory.AI, ProviderMeshCategory.CUSTOM)) View.VISIBLE else View.GONE
        status.text = buildString {
            append("STATUS // ${profile.healthLabel()}")
            if (profile.lastLatencyMs > 0) append(" // ${profile.lastLatencyMs}MS")
            if (profile.discoveredModels.isNotEmpty()) append(" // ${profile.discoveredModels.size} MODELS")
            if (profile.lastError.isNotBlank()) append("\n${profile.lastError.take(220)}")
        }
    }

    private fun saveCurrent(forceDefault: Boolean, showToast: Boolean): ProviderMeshProfile? = runCatching {
        val preset = selectedPreset()
        val current = store.load().profile(preset.id)
        val nextBase = baseUrl.text.toString().trim().trimEnd('/')
        require(nextBase.startsWith("https://")) { "Only secure HTTPS endpoints are accepted." }
        val changed = nextBase != current.baseUrl || apiKey.text.toString().trim() != current.apiKey || model.text.toString().trim() != current.model
        val profile = current.copy(
            baseUrl = nextBase,
            apiKey = apiKey.text.toString().trim(),
            model = model.text.toString().trim(),
            enabled = enabled.isChecked,
            lastStatusCode = if (changed) 0 else current.lastStatusCode,
            lastLatencyMs = if (changed) 0 else current.lastLatencyMs,
            lastError = if (changed) "" else current.lastError,
            lastVerifiedAtMs = if (changed) 0 else current.lastVerifiedAtMs
        )
        store.updateProfile(profile, forceDefault || makeDefaultAi.isChecked)
        populate(preset)
        if (showToast) Toast.makeText(this, "${preset.label} encrypted and saved.", Toast.LENGTH_SHORT).show()
        profile
    }.getOrElse { error ->
        status.text = "SAVE FAILED // ${error.message ?: error.javaClass.simpleName}"
        if (showToast) Toast.makeText(this, "Provider save failed.", Toast.LENGTH_LONG).show()
        null
    }

    private fun discoverModels() {
        val profile = saveCurrent(false, false) ?: return
        val preset = selectedPreset()
        if (preset.modelsPath.isBlank()) {
            status.text = "MODEL DISCOVERY // NOT EXPOSED BY ${preset.label.uppercase(Locale.US)}"
            return
        }
        status.text = "MODEL DISCOVERY // CONTACTING ${preset.label.uppercase(Locale.US)}..."
        Thread {
            val result = runCatching { client.listModels(profile.id) }
            runOnUiThread {
                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        status.text = "MODEL DISCOVERY // ONLINE // NO CHAT MODELS RETURNED"
                    } else {
                        status.text = "MODEL DISCOVERY // ${models.size} AVAILABLE"
                        AlertDialog.Builder(this)
                            .setTitle("${preset.label} models")
                            .setItems(models.toTypedArray()) { _, which ->
                                model.setText(models[which])
                                saveCurrent(false, false)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }.onFailure { error ->
                    status.text = "MODEL DISCOVERY FAILED // ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }.start()
    }

    private fun testProvider() {
        val profile = saveCurrent(false, false) ?: return
        val preset = selectedPreset()
        status.text = "TESTING // ${preset.label.uppercase(Locale.US)}..."
        Thread {
            val result = runCatching { client.test(profile.id) }
            runOnUiThread {
                result.onSuccess { test ->
                    status.text = "ONLINE // HTTP ${test.statusCode} // ${test.elapsedMs}MS // ${test.detail}${if (test.models.isNotEmpty()) " // ${test.models.size} MODELS" else ""}"
                    populate(preset)
                }.onFailure { error ->
                    status.text = "TEST FAILED // ${error.message ?: error.javaClass.simpleName}"
                    populate(preset)
                }
            }
        }.start()
    }

    private fun textInput(hint: String, secret: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(MUTED)
        setTextColor(Color.WHITE)
        textSize = 13f
        setPadding(dp(11), dp(10), dp(11), dp(10))
        background = panelBackground(PANEL, BLUE)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        typeface = Typeface.MONOSPACE
    }

    private fun check(text: String): CheckBox = CheckBox(this).apply {
        this.text = text
        setTextColor(SOFT)
        textSize = 12f
        typeface = Typeface.MONOSPACE
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(0f, 1.16f)
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 11f
        letterSpacing = .08f
        isAllCaps = false
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        background = panelBackground(PANEL, CYAN)
        setOnClickListener { action() }
    }

    private fun panelBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(3).toFloat()
    }

    private fun matchWidth(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_PROVIDER_ID = "provider_id"
        private const val EXTRA_CATEGORY = "provider_category"
        private const val EXTRA_DISCOVER = "discover_models"
        private val BG = Color.rgb(2, 7, 12)
        private val PANEL = Color.rgb(6, 16, 24)
        private val CYAN = Color.rgb(72, 231, 255)
        private val BLUE = Color.rgb(78, 133, 255)
        private val VIOLET = Color.rgb(169, 116, 255)
        private val SOFT = Color.rgb(196, 220, 235)
        private val MUTED = Color.rgb(115, 144, 161)

        fun launch(
            context: Context,
            providerId: String? = null,
            category: ProviderMeshCategory? = null,
            discoverModels: Boolean = false
        ) {
            context.startActivity(Intent(context, ProviderMeshActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                providerId?.let { putExtra(EXTRA_PROVIDER_ID, it) }
                category?.let { putExtra(EXTRA_CATEGORY, it.name) }
                putExtra(EXTRA_DISCOVER, discoverModels)
            })
        }
    }
}
