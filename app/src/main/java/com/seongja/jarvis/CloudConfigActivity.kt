package com.seongja.jarvis

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class CloudConfigActivity : Activity() {
    private data class ProfileFields(
        val model: EditText,
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )

    private data class SearchFields(
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )

    private data class VoiceFields(
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )

    private lateinit var store: SecureCortexRegistry
    private lateinit var searchStore: SecureSearchGridRegistry
    private lateinit var voiceStore: SecureVoiceRegistry
    private lateinit var systemPromptInput: EditText
    private lateinit var globalStatus: TextView
    private val profileFields = linkedMapOf<String, ProfileFields>()
    private val searchFields = linkedMapOf<SearchGridProvider, SearchFields>()
    private lateinit var voiceFields: VoiceFields
    private var voiceTestClient: CartesiaSonicClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureCortexRegistry(this)
        searchStore = SecureSearchGridRegistry(this)
        voiceStore = SecureVoiceRegistry(this)
        setContentView(buildUi())
        populate(store.load(), searchStore.load(), voiceStore.load())
    }

    private fun buildUi(): ScrollView {
        val padding = dp(18)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(7, 12, 20))
        }

        root.addView(TextView(this).apply {
            text = "F.R.I.D.A.Y. // CORTEX + SEARCH GRID"
            textSize = 24f
            setTextColor(Color.rgb(64, 255, 226))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Ten encrypted reasoning nodes plus dedicated Tavily and Exa retrieval engines. FRIDAY searches, deduplicates evidence, cross-checks sources, and sends the evidence packet to the healthiest cortex node for synthesis."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "Keys are encrypted with Android Keystore and never enter GitHub, logs, conversation memory, or source displays. Endpoints are fixed to official HTTPS provider gateways."
            textSize = 13f
            setTextColor(Color.rgb(145, 205, 255))
            setPadding(0, 0, 0, dp(16))
        })

        systemPromptInput = EditText(this).apply {
            hint = "System prompt"
            minLines = 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(
                Color.rgb(16, 28, 40),
                Color.rgb(42, 112, 124)
            )
        }
        root.addView(systemPromptInput, matchWidth(bottom = 16))

        root.addView(sectionTitle("CORTEX MESH // 6 GEMINI + 4 GROQ"))
        CortexDefaults.profiles().forEach { profile ->
            root.addView(buildProfilePanel(profile), matchWidth(bottom = 14))
        }

        root.addView(sectionTitle("VOICE CORE // CARTESIA SONIC-3"))
        root.addView(buildCartesiaPanel(), matchWidth(bottom = 16))

        root.addView(sectionTitle("SEARCH GRID // TAVILY + EXA"))
        root.addView(TextView(this).apply {
            text = "Tavily is optimized for current web discovery and news. Exa provides semantic retrieval and deep-page evidence. Either provider can operate alone; when both are configured, Jarvis merges and deduplicates their results."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(12))
        })
        SearchGridProvider.entries.forEach { provider ->
            root.addView(buildSearchPanel(provider), matchWidth(bottom = 14))
        }

        globalStatus = TextView(this).apply {
            text = "SYSTEM STATUS // NOT TESTED"
            textSize = 14f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(globalStatus)

        root.addView(Button(this).apply {
            text = "SAVE CORTEX + SEARCH + VOICE"
            setOnClickListener { saveAll(showToast = true) }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "SAVE AND TEST ALL CORTEX NODES"
            setOnClickListener {
                if (saveAll(showToast = false)) testAllProfiles()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "SAVE AND TEST SEARCH GRID"
            setOnClickListener {
                if (saveAll(showToast = false)) testAllSearchProviders()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RESET CORTEX MESH"
            setOnClickListener {
                store.clear()
                populate(store.load(), searchStore.load(), voiceStore.load())
                globalStatus.text = "CORTEX MESH // RESET"
                Toast.makeText(
                    this@CloudConfigActivity,
                    "Cortex mesh reset.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RESET SEARCH GRID"
            setOnClickListener {
                searchStore.clear()
                populate(store.load(), searchStore.load(), voiceStore.load())
                globalStatus.text = "SEARCH GRID // RESET"
                Toast.makeText(
                    this@CloudConfigActivity,
                    "Search Grid reset.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RESET VOICE CORE"
            setOnClickListener {
                voiceTestClient?.destroy()
                voiceTestClient = null
                voiceStore.clear()
                populate(store.load(), searchStore.load(), voiceStore.load())
                globalStatus.text = "VOICE CORE // RESET"
                Toast.makeText(this@CloudConfigActivity, "Cartesia voice reset.", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RETURN TO FRIDAY"
            setOnClickListener { finish() }
        }, matchWidth(bottom = 24))

        return ScrollView(this).apply { addView(root) }
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 18f
        setTextColor(Color.rgb(96, 238, 215))
        setPadding(0, dp(8), 0, dp(10))
    }

    private fun buildProfilePanel(profile: CortexProfile): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(
                fill = Color.rgb(12, 23, 35),
                stroke = if (profile.provider == CortexProvider.GEMINI) {
                    Color.rgb(43, 204, 187)
                } else {
                    Color.rgb(166, 73, 202)
                }
            )
        }

        panel.addView(TextView(this).apply {
            text = "${profile.label} // ${profile.provider.displayName.uppercase(Locale.US)}"
            textSize = 17f
            setTextColor(
                if (profile.provider == CortexProvider.GEMINI) {
                    Color.rgb(66, 255, 226)
                } else {
                    Color.rgb(230, 112, 255)
                }
            )
        })

        panel.addView(TextView(this).apply {
            text = profile.provider.endpoint
            textSize = 10f
            setTextColor(Color.GRAY)
            setPadding(0, dp(3), 0, dp(8))
        })

        val model = EditText(this).apply {
            hint = "Model ID from ${profile.provider.displayName} console"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            background = panelBackground(
                Color.rgb(18, 34, 49),
                Color.rgb(40, 78, 94)
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        panel.addView(model, matchWidth(bottom = 8))

        val apiKey = passwordInput("Encrypted API key")
        panel.addView(apiKey, matchWidth(bottom = 6))

        val enabled = CheckBox(this).apply {
            text = "Node enabled"
            setTextColor(Color.LTGRAY)
            isChecked = true
        }
        panel.addView(enabled)

        val health = TextView(this).apply {
            text = "STATUS // NOT CONFIGURED"
            textSize = 12f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(5), 0, dp(5))
        }
        panel.addView(health)

        panel.addView(Button(this).apply {
            text = "SAVE + TEST ${profile.label}"
            setOnClickListener {
                if (saveAll(showToast = false)) testProfile(profile.id)
            }
        }, matchWidth())

        profileFields[profile.id] = ProfileFields(model, apiKey, enabled, health)
        return panel
    }

    private fun buildCartesiaPanel(): LinearLayout {
        val accent = Color.rgb(75, 196, 255)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(Color.rgb(11, 25, 38), accent)
        }
        panel.addView(TextView(this).apply {
            text = "CARTESIA // SONIC-3 // GEMMA EN-GB"
            textSize = 17f
            setTextColor(accent)
        })
        panel.addView(TextView(this).apply {
            text = "${CartesiaVoiceSettings.WEBSOCKET_ENDPOINT} // PCM16 44100HZ // SPEED ${CartesiaVoiceSettings.SPEED}"
            textSize = 10f
            setTextColor(Color.GRAY)
            setPadding(0, dp(3), 0, dp(8))
        })
        val apiKey = passwordInput("Encrypted Cartesia API key")
        panel.addView(apiKey, matchWidth(bottom = 6))
        val enabled = CheckBox(this).apply {
            text = "Cartesia voice enabled"
            setTextColor(Color.LTGRAY)
            isChecked = true
        }
        panel.addView(enabled)
        val health = TextView(this).apply {
            text = "STATUS // NOT CONFIGURED"
            textSize = 12f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(5), 0, dp(5))
        }
        panel.addView(health)
        panel.addView(Button(this).apply {
            text = "SAVE + TEST CARTESIA VOICE"
            setOnClickListener {
                if (saveAll(showToast = false)) testCartesiaVoice()
            }
        }, matchWidth())
        voiceFields = VoiceFields(apiKey, enabled, health)
        return panel
    }

    private fun buildSearchPanel(provider: SearchGridProvider): LinearLayout {
        val accent = when (provider) {
            SearchGridProvider.TAVILY -> Color.rgb(56, 220, 180)
            SearchGridProvider.EXA -> Color.rgb(255, 166, 66)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground(Color.rgb(13, 24, 35), accent)
        }

        panel.addView(TextView(this).apply {
            text = "${provider.displayName.uppercase(Locale.US)} // EVIDENCE ENGINE"
            textSize = 17f
            setTextColor(accent)
        })

        panel.addView(TextView(this).apply {
            text = provider.endpoint
            textSize = 10f
            setTextColor(Color.GRAY)
            setPadding(0, dp(3), 0, dp(8))
        })

        val apiKey = passwordInput("Encrypted ${provider.displayName} API key")
        panel.addView(apiKey, matchWidth(bottom = 6))

        val enabled = CheckBox(this).apply {
            text = "Provider enabled"
            setTextColor(Color.LTGRAY)
            isChecked = true
        }
        panel.addView(enabled)

        val health = TextView(this).apply {
            text = "STATUS // NOT CONFIGURED"
            textSize = 12f
            setTextColor(Color.rgb(147, 210, 255))
            setPadding(0, dp(5), 0, dp(5))
        }
        panel.addView(health)

        panel.addView(Button(this).apply {
            text = "SAVE + TEST ${provider.displayName.uppercase(Locale.US)}"
            setOnClickListener {
                if (saveAll(showToast = false)) testSearchProvider(provider)
            }
        }, matchWidth())

        searchFields[provider] = SearchFields(apiKey, enabled, health)
        return panel
    }

    private fun passwordInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        background = panelBackground(
            Color.rgb(18, 34, 49),
            Color.rgb(40, 78, 94)
        )
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun populate(
        cortexRegistry: CortexRegistry,
        searchRegistry: SearchGridRegistry,
        voiceSettings: CartesiaVoiceSettings
    ) {
        systemPromptInput.setText(cortexRegistry.systemPrompt)
        val byId = cortexRegistry.profiles.associateBy { it.id }
        CortexDefaults.profiles().forEach { default ->
            val profile = byId[default.id] ?: default
            val fields = profileFields[default.id] ?: return@forEach
            fields.model.setText(profile.model)
            fields.apiKey.setText(profile.apiKey)
            fields.enabled.isChecked = profile.enabled
            fields.health.text =
                "STATUS // ${profile.healthLabel()} // SUCCESS ${profile.successes} // FAIL ${profile.failures}"
        }

        val byProvider = searchRegistry.credentials.associateBy { it.provider }
        SearchGridProvider.entries.forEach { provider ->
            val credential = byProvider[provider] ?: SearchGridCredential(provider)
            val fields = searchFields[provider] ?: return@forEach
            fields.apiKey.setText(credential.apiKey)
            fields.enabled.isChecked = credential.enabled
            fields.health.text =
                "STATUS // ${credential.healthLabel()} // SUCCESS ${credential.successes} // FAIL ${credential.failures}"
        }

        voiceFields.apiKey.setText(voiceSettings.apiKey)
        voiceFields.enabled.isChecked = voiceSettings.enabled
        voiceFields.health.text =
            "STATUS // ${voiceSettings.healthLabel()} // SUCCESS ${voiceSettings.successes} // FAIL ${voiceSettings.failures}"

        globalStatus.text = systemSummary(cortexRegistry, searchRegistry, voiceSettings)
    }

    private fun saveAll(showToast: Boolean): Boolean {
        val cortexSaved = saveRegistry(showToast = false)
        val searchSaved = saveSearchGrid(showToast = false)
        val voiceSaved = saveVoice(showToast = false)
        val success = cortexSaved && searchSaved && voiceSaved
        if (showToast) {
            Toast.makeText(
                this,
                if (success) {
                    "Cortex, Search Grid, and Cartesia voice saved securely."
                } else {
                    "Secure configuration could not be fully saved."
                },
                if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
        return success
    }

    private fun saveRegistry(showToast: Boolean): Boolean {
        val current = store.load().profiles.associateBy { it.id }
        val updatedProfiles = CortexDefaults.profiles().map { default ->
            val previous = current[default.id] ?: default
            val fields = profileFields[default.id] ?: return@map previous
            val model = fields.model.text.toString().trim()
            val key = fields.apiKey.text.toString().trim()
            previous.copy(
                model = model,
                apiKey = key,
                enabled = fields.enabled.isChecked,
                lastError = if (previous.model != model || previous.apiKey != key) {
                    ""
                } else {
                    previous.lastError
                },
                cooldownUntilMs = if (previous.apiKey != key) 0L else previous.cooldownUntilMs
            )
        }

        val registry = CortexRegistry(
            profiles = updatedProfiles,
            systemPrompt = systemPromptInput.text.toString().trim()
                .ifBlank { CortexRegistry.DEFAULT_SYSTEM_PROMPT }
        )

        return runCatching {
            store.save(registry)
            globalStatus.text = systemSummary(store.load(), searchStore.load(), voiceStore.load())
            if (showToast) {
                Toast.makeText(this, "Cortex mesh saved securely.", Toast.LENGTH_SHORT).show()
            }
            true
        }.getOrElse {
            globalStatus.text = "CORTEX MESH // SECURE STORAGE ERROR"
            false
        }
    }

    private fun saveSearchGrid(showToast: Boolean): Boolean {
        val current = searchStore.load().credentials.associateBy { it.provider }
        val updated = SearchGridProvider.entries.map { provider ->
            val previous = current[provider] ?: SearchGridCredential(provider)
            val fields = searchFields[provider] ?: return@map previous
            val key = fields.apiKey.text.toString().trim()
            previous.copy(
                apiKey = key,
                enabled = fields.enabled.isChecked,
                lastError = if (previous.apiKey != key) "" else previous.lastError,
                cooldownUntilMs = if (previous.apiKey != key) 0L else previous.cooldownUntilMs
            )
        }

        return runCatching {
            searchStore.save(SearchGridRegistry(updated))
            globalStatus.text = systemSummary(store.load(), searchStore.load(), voiceStore.load())
            if (showToast) {
                Toast.makeText(this, "Search Grid saved securely.", Toast.LENGTH_SHORT).show()
            }
            true
        }.getOrElse {
            globalStatus.text = "SEARCH GRID // SECURE STORAGE ERROR"
            false
        }
    }

    private fun saveVoice(showToast: Boolean): Boolean {
        val previous = voiceStore.load()
        val key = voiceFields.apiKey.text.toString().trim()
        val updated = previous.copy(
            apiKey = key,
            enabled = voiceFields.enabled.isChecked,
            lastError = if (previous.apiKey != key) "" else previous.lastError,
            lastStatusCode = if (previous.apiKey != key) 0 else previous.lastStatusCode
        )
        return runCatching {
            voiceStore.save(updated)
            if (showToast) Toast.makeText(this, "Cartesia voice saved securely.", Toast.LENGTH_SHORT).show()
            true
        }.getOrElse {
            globalStatus.text = "VOICE CORE // SECURE STORAGE ERROR"
            false
        }
    }

    private fun testCartesiaVoice() {
        voiceTestClient?.destroy()
        voiceFields.health.text = "STATUS // CONNECTING CARTESIA..."
        lateinit var testClient: CartesiaSonicClient
        testClient = CartesiaSonicClient(this, object : CartesiaSonicClient.Listener {
            private var sent = false
            override fun onReady(label: String) {
                runOnUiThread {
                    voiceFields.health.text = "STATUS // ONLINE // $label"
                    if (!sent) {
                        sent = true
                        testClient.push("Cartesia Sonic voice core online, Boss.")
                        testClient.finish()
                    }
                }
            }
            override fun onAudioStarted(label: String) {
                runOnUiThread { voiceFields.health.text = "STATUS // SPEAKING // $label" }
            }
            override fun onCompleted() {
                runOnUiThread {
                    voiceFields.health.text = "STATUS // ONLINE // TEST COMPLETE"
                    voiceTestClient?.destroy()
                    voiceTestClient = null
                    populate(store.load(), searchStore.load(), voiceStore.load())
                }
            }
            override fun onDiagnostic(message: String) = Unit
            override fun onError(message: String) {
                runOnUiThread {
                    voiceFields.health.text = "STATUS // FAILED // ${message.take(180)}"
                    voiceTestClient?.destroy()
                    voiceTestClient = null
                    populate(store.load(), searchStore.load(), voiceStore.load())
                }
            }
        })
        voiceTestClient = testClient
        if (!testClient.begin()) {
            voiceFields.health.text = "STATUS // ENTER A CARTESIA API KEY"
            voiceTestClient = null
        }
    }

    private fun testProfile(profileId: String) {
        val fields = profileFields[profileId] ?: return
        fields.health.text = "STATUS // TESTING..."
        Thread {
            val result = runCatching { CortexMeshClient(store).testProfile(profileId) }
            runOnUiThread {
                result.onSuccess {
                    fields.health.text =
                        "STATUS // ONLINE // ${it.provider.displayName.uppercase(Locale.US)} // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                }.onFailure {
                    fields.health.text =
                        "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                globalStatus.text = systemSummary(store.load(), searchStore.load(), voiceStore.load())
            }
        }.start()
    }

    private fun testSearchProvider(provider: SearchGridProvider) {
        val fields = searchFields[provider] ?: return
        fields.health.text = "STATUS // TESTING..."
        Thread {
            val result = runCatching {
                SearchGridResearchClient(searchStore, store).testProvider(provider)
            }
            runOnUiThread {
                result.onSuccess {
                    fields.health.text =
                        "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms // RESULTS ${it.resultCount}"
                }.onFailure {
                    fields.health.text =
                        "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                populate(store.load(), searchStore.load(), voiceStore.load())
            }
        }.start()
    }

    private fun testAllProfiles() {
        val configured = store.load().configuredProfiles()
        if (configured.isEmpty()) {
            globalStatus.text = "CORTEX MESH // ENTER AT LEAST ONE MODEL AND KEY"
            return
        }

        globalStatus.text = "CORTEX MESH // TESTING ${configured.size} NODES..."
        Thread {
            var online = 0
            configured.forEachIndexed { index, profile ->
                runOnUiThread {
                    profileFields[profile.id]?.health?.text =
                        "STATUS // TESTING ${index + 1}/${configured.size}..."
                }
                val result = runCatching { CortexMeshClient(store).testProfile(profile.id) }
                if (result.isSuccess) online++
                runOnUiThread {
                    val fields = profileFields[profile.id]
                    result.onSuccess {
                        fields?.health?.text =
                            "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                    }.onFailure {
                        fields?.health?.text =
                            "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                    }
                    globalStatus.text =
                        "CORTEX MESH // TESTED ${index + 1}/${configured.size} // ONLINE $online"
                }
            }
            runOnUiThread {
                populate(store.load(), searchStore.load(), voiceStore.load())
                globalStatus.text =
                    "CORTEX MESH // TEST COMPLETE // ONLINE $online/${configured.size}"
            }
        }.start()
    }

    private fun testAllSearchProviders() {
        val configured = searchStore.load().configuredCredentials()
        if (configured.isEmpty()) {
            globalStatus.text = "SEARCH GRID // ENTER A TAVILY OR EXA KEY"
            return
        }

        globalStatus.text = "SEARCH GRID // TESTING ${configured.size} PROVIDERS..."
        Thread {
            val client = SearchGridResearchClient(searchStore, store)
            var online = 0
            configured.forEachIndexed { index, credential ->
                runOnUiThread {
                    searchFields[credential.provider]?.health?.text =
                        "STATUS // TESTING ${index + 1}/${configured.size}..."
                }
                val result = runCatching { client.testProvider(credential.provider) }
                if (result.isSuccess) online++
                runOnUiThread {
                    val fields = searchFields[credential.provider]
                    result.onSuccess {
                        fields?.health?.text =
                            "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms // RESULTS ${it.resultCount}"
                    }.onFailure {
                        fields?.health?.text =
                            "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                    }
                    globalStatus.text =
                        "SEARCH GRID // TESTED ${index + 1}/${configured.size} // ONLINE $online"
                }
            }
            runOnUiThread {
                populate(store.load(), searchStore.load(), voiceStore.load())
                globalStatus.text =
                    "SEARCH GRID // TEST COMPLETE // ONLINE $online/${configured.size}"
            }
        }.start()
    }

    private fun systemSummary(
        cortexRegistry: CortexRegistry,
        searchRegistry: SearchGridRegistry,
        voiceSettings: CartesiaVoiceSettings
    ): String {
        val cortexConfigured = cortexRegistry.configuredProfiles()
        val cortexOnline = cortexConfigured.count {
            it.lastStatusCode in 200..299 && !it.isCoolingDown()
        }
        val cortexCooling = cortexConfigured.count { it.isCoolingDown() }
        val searchConfigured = searchRegistry.configuredCredentials()
        val searchOnline = searchConfigured.count {
            it.lastStatusCode in 200..299 && !it.isCoolingDown()
        }
        val searchCooling = searchConfigured.count { it.isCoolingDown() }
        return "CORTEX ${cortexConfigured.size}/10 // ONLINE $cortexOnline // COOLDOWN $cortexCooling\n" +
            "SEARCH GRID ${searchConfigured.size}/2 // ONLINE $searchOnline // COOLDOWN $searchCooling"
    }

    private fun panelBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun matchWidth(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(bottom)) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
