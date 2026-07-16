package com.seongja.jarvis

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Secure owner-only configuration console for every cloud and data route. */
class CloudConfigActivity : Activity() {
    private data class ProfileFields(
        val model: EditText,
        val key: EditText,
        val enabled: CheckBox,
        val status: TextView
    )

    private data class SearchFields(
        val key: EditText,
        val enabled: CheckBox,
        val status: TextView
    )

    private lateinit var cortexStore: SecureCortexRegistry
    private lateinit var searchStore: SecureSearchGridRegistry
    private lateinit var voiceStore: SecureVoiceRegistry
    private lateinit var integrationStore: SecureIntegrationRegistry
    private lateinit var systemPrompt: EditText
    private lateinit var globalStatus: TextView
    private val profileFields = linkedMapOf<String, ProfileFields>()
    private val searchFields = linkedMapOf<SearchGridProvider, SearchFields>()
    private val cartesiaKeys = mutableListOf<EditText>()
    private lateinit var cartesiaEnabled: CheckBox
    private lateinit var cartesiaStatus: TextView
    private lateinit var deepSeekKey: EditText
    private lateinit var deepSeekModel: EditText
    private lateinit var deepSeekEnabled: CheckBox
    private lateinit var deepSeekStatus: TextView
    private lateinit var youtubeKey: EditText
    private lateinit var youtubeEnabled: CheckBox
    private lateinit var youtubeStatus: TextView
    private lateinit var gmailKey: EditText
    private lateinit var gmailOAuthClient: EditText
    private lateinit var gmailEnabled: CheckBox
    private lateinit var gmailStatus: TextView
    private var voiceTestClient: CartesiaSonicClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cortexStore = SecureCortexRegistry(this)
        searchStore = SecureSearchGridRegistry(this)
        voiceStore = SecureVoiceRegistry(this)
        integrationStore = SecureIntegrationRegistry(this)
        setContentView(buildUi())
        populate()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(label("F.R.I.D.A.Y. // OPERATIONS CONSOLE", 23f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .12f
        }, matchWidth(bottom = 5))
        root.addView(label("ENCRYPTED PROVIDER MESH // OWNER ACCESS", 11f, SOFT, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .18f
        }, matchWidth(bottom = 14))
        root.addView(label(
            "Keys remain inside Android Keystore encrypted storage. HELIX receives health labels only, never credentials. Gmail mailbox access requires OAuth consent; a project API key alone cannot read messages.",
            12f,
            MUTED
        ), matchWidth(bottom = 16))

        root.addView(sectionTitle("COGNITIVE CORE // GEMINI + GROQ"))
        systemPrompt = textInput("System directive", secret = false, lines = 5)
        root.addView(systemPrompt, matchWidth(bottom = 12))
        CortexDefaults.profiles().forEach { profile ->
            root.addView(cortexPanel(profile), matchWidth(bottom = 10))
        }
        root.addView(actionButton("SAVE + TEST CORTEX MESH") {
            if (saveAll(false)) testCortexMesh()
        }, matchWidth(bottom = 18))

        root.addView(sectionTitle("VOICE CORE // CARTESIA SONIC-3"))
        root.addView(label(
            "P1 is primary. B1, B2, and B3 engage automatically before audio begins when credits, quota, authentication, or network health invalidate the active route.",
            12f,
            MUTED
        ), matchWidth(bottom = 8))
        root.addView(cartesiaPanel(), matchWidth(bottom = 18))

        root.addView(sectionTitle("LIVE RESEARCH // TAVILY + EXA"))
        SearchGridProvider.entries.forEach { provider ->
            root.addView(searchPanel(provider), matchWidth(bottom = 10))
        }
        root.addView(actionButton("SAVE + TEST SEARCH GRID") {
            if (saveAll(false)) testSearchGrid()
        }, matchWidth(bottom = 18))

        root.addView(sectionTitle("SECONDARY CORTEX // DEEPSEEK"))
        root.addView(deepSeekPanel(), matchWidth(bottom = 18))

        root.addView(sectionTitle("YOUTUBE DATA API V3"))
        root.addView(youtubePanel(), matchWidth(bottom = 18))

        root.addView(sectionTitle("GMAIL PROJECT + OAUTH"))
        root.addView(gmailPanel(), matchWidth(bottom = 18))

        globalStatus = label("SYSTEM GRID // INITIALIZING", 12f, SOFT, true).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelBackground(PANEL, BLUE)
        }
        root.addView(globalStatus, matchWidth(bottom = 10))
        root.addView(actionButton("SAVE COMPLETE OPERATIONS GRID") { saveAll(true) }, matchWidth(bottom = 8))
        root.addView(actionButton("REFRESH VERIFIED HEALTH") { populate() }, matchWidth(bottom = 8))
        root.addView(actionButton("RETURN TO F.R.I.D.A.Y.") { finish() }, matchWidth(bottom = 10))
        return ScrollView(this).apply { addView(root) }
    }

    private fun cortexPanel(profile: CortexProfile): View {
        val accent = if (profile.provider == CortexProvider.GEMINI) CYAN else VIOLET
        val body = panel(accent)
        body.addView(label(
            "${profile.label} // ${profile.provider.displayName.uppercase(Locale.US)}",
            15f,
            accent,
            true
        ))
        body.addView(label(profile.provider.routeLabel, 9f, MUTED), matchWidth(bottom = 6))
        val model = textInput("Model ID", secret = false)
        val key = textInput("Encrypted API key", secret = true)
        val enabled = check("Node enabled")
        val status = statusLabel()
        body.addView(model, matchWidth(bottom = 6))
        body.addView(key, matchWidth(bottom = 4))
        body.addView(enabled)
        body.addView(status, matchWidth(bottom = 4))
        body.addView(actionButton("TEST ${profile.label}") {
            if (saveAll(false)) testProfile(profile.id)
        }, matchWidth())
        profileFields[profile.id] = ProfileFields(model, key, enabled, status)
        return body
    }

    private fun cartesiaPanel(): View {
        val body = panel(BLUE)
        body.addView(label("CARTESIA // SONIC-3 // GEMMA EN-GB", 15f, BLUE, true))
        listOf("P1 // PRIMARY KEY", "B1 // BACKUP KEY", "B2 // BACKUP KEY", "B3 // BACKUP KEY")
            .forEachIndexed { index, title ->
                body.addView(label(title, 9f, if (index == 0) CYAN else SOFT, true))
                val field = textInput("Encrypted Cartesia ${if (index == 0) "primary" else "backup $index"} key", true)
                cartesiaKeys += field
                body.addView(field, matchWidth(bottom = 6))
            }
        cartesiaEnabled = check("Cartesia voice mesh enabled")
        cartesiaStatus = statusLabel()
        body.addView(cartesiaEnabled)
        body.addView(cartesiaStatus, matchWidth(bottom = 4))
        body.addView(actionButton("SAVE + TEST VOICE FAILOVER MESH") {
            if (saveAll(false)) testCartesia()
        }, matchWidth())
        return body
    }

    private fun searchPanel(provider: SearchGridProvider): View {
        val accent = if (provider == SearchGridProvider.TAVILY) GREEN else ORANGE
        val body = panel(accent)
        body.addView(label("${provider.displayName.uppercase()} // EVIDENCE ENGINE", 15f, accent, true))
        body.addView(label(provider.endpoint, 9f, MUTED), matchWidth(bottom = 6))
        val key = textInput("Encrypted ${provider.displayName} API key", true)
        val enabled = check("Provider enabled")
        val status = statusLabel()
        body.addView(key, matchWidth(bottom = 4))
        body.addView(enabled)
        body.addView(status, matchWidth(bottom = 4))
        body.addView(actionButton("TEST ${provider.displayName.uppercase()}") {
            if (saveAll(false)) testSearchProvider(provider)
        }, matchWidth())
        searchFields[provider] = SearchFields(key, enabled, status)
        return body
    }

    private fun deepSeekPanel(): View {
        val body = panel(VIOLET)
        deepSeekModel = textInput("Model ID", false)
        deepSeekKey = textInput("Encrypted DeepSeek API key", true)
        deepSeekEnabled = check("DeepSeek fallback enabled")
        deepSeekStatus = statusLabel()
        body.addView(label("DEEPSEEK // RESILIENT FALLBACK CORTEX", 15f, VIOLET, true))
        body.addView(label(DeepSeekClient.ENDPOINT, 9f, MUTED), matchWidth(bottom = 6))
        body.addView(deepSeekModel, matchWidth(bottom = 6))
        body.addView(deepSeekKey, matchWidth(bottom = 4))
        body.addView(deepSeekEnabled)
        body.addView(deepSeekStatus, matchWidth(bottom = 4))
        body.addView(actionButton("SAVE + TEST DEEPSEEK") {
            if (saveAll(false)) testDeepSeek()
        }, matchWidth())
        return body
    }

    private fun youtubePanel(): View {
        val body = panel(RED)
        youtubeKey = textInput("Encrypted YouTube API key", true)
        youtubeEnabled = check("YouTube public-data intelligence enabled")
        youtubeStatus = statusLabel()
        body.addView(label("YOUTUBE // VERIFIED PUBLIC SEARCH", 15f, RED, true))
        body.addView(label(
            "Search, channel, title, video ID, publication data, and India trending. Private account data is not accessed.",
            11f,
            MUTED
        ), matchWidth(bottom = 6))
        body.addView(youtubeKey, matchWidth(bottom = 4))
        body.addView(youtubeEnabled)
        body.addView(youtubeStatus, matchWidth(bottom = 4))
        body.addView(actionButton("SAVE + TEST YOUTUBE DATA") {
            if (saveAll(false)) testYouTube()
        }, matchWidth())
        return body
    }

    private fun gmailPanel(): View {
        val body = panel(GREEN)
        gmailKey = textInput("Google Cloud project API key", true)
        gmailOAuthClient = textInput("Android OAuth client ID", false)
        gmailEnabled = check("Gmail integration enabled")
        gmailStatus = statusLabel()
        body.addView(label("GMAIL // OAUTH-GATED PRIVATE DATA", 15f, GREEN, true))
        body.addView(label(
            "The project key identifies the Google Cloud project. Reading, searching, sending, or changing Gmail requires an OAuth client and explicit Google account consent.",
            11f,
            MUTED
        ), matchWidth(bottom = 6))
        body.addView(gmailKey, matchWidth(bottom = 6))
        body.addView(gmailOAuthClient, matchWidth(bottom = 4))
        body.addView(gmailEnabled)
        body.addView(gmailStatus)
        return body
    }

    private fun populate() {
        val cortex = cortexStore.load()
        val byId = cortex.profiles.associateBy { it.id }
        systemPrompt.setText(cortex.systemPrompt)
        CortexDefaults.profiles().forEach { default ->
            val profile = byId[default.id] ?: default
            profileFields[default.id]?.let { fields ->
                fields.model.setText(profile.model)
                fields.key.setText(profile.apiKey)
                fields.enabled.isChecked = profile.enabled
                fields.status.text = "STATUS // ${profile.healthLabel()} // OK ${profile.successes} // FAIL ${profile.failures}"
            }
        }

        val search = searchStore.load()
        val searchByProvider = search.credentials.associateBy { it.provider }
        SearchGridProvider.entries.forEach { provider ->
            val credential = searchByProvider[provider] ?: SearchGridCredential(provider)
            searchFields[provider]?.let { fields ->
                fields.key.setText(credential.apiKey)
                fields.enabled.isChecked = credential.enabled
                fields.status.text = "STATUS // ${credential.healthLabel()} // OK ${credential.successes} // FAIL ${credential.failures}"
            }
        }

        val voice = voiceStore.load()
        val allKeys = listOf(voice.apiKey) + voice.backupApiKeys
        cartesiaKeys.forEachIndexed { index, field -> field.setText(allKeys.getOrElse(index) { "" }) }
        cartesiaEnabled.isChecked = voice.enabled
        cartesiaStatus.text = "STATUS // ${voice.healthLabel()} // OK ${voice.successes} // FAIL ${voice.failures}"

        val integrations = integrationStore.load()
        deepSeekKey.setText(integrations.deepSeekApiKey)
        deepSeekModel.setText(integrations.deepSeekModel)
        deepSeekEnabled.isChecked = integrations.deepSeekEnabled
        deepSeekStatus.text = "STATUS // ${integrations.deepSeekHealthLabel()} // OK ${integrations.deepSeekSuccesses} // FAIL ${integrations.deepSeekFailures}"
        youtubeKey.setText(integrations.youtubeApiKey)
        youtubeEnabled.isChecked = integrations.youtubeEnabled
        youtubeStatus.text = "STATUS // ${integrations.youtubeHealthLabel()} // OK ${integrations.youtubeSuccesses} // FAIL ${integrations.youtubeFailures}"
        gmailKey.setText(integrations.gmailApiKey)
        gmailOAuthClient.setText(integrations.gmailOAuthClientId)
        gmailEnabled.isChecked = integrations.gmailEnabled
        gmailStatus.text = "STATUS // ${integrations.gmailHealthLabel()}"
        globalStatus.text = systemSummary(cortex, search, voice, integrations)
    }

    private fun saveAll(showToast: Boolean): Boolean = runCatching {
        val previousCortex = cortexStore.load()
        val previousById = previousCortex.profiles.associateBy { it.id }
        val profiles = CortexDefaults.profiles().map { default ->
            val previous = previousById[default.id] ?: default
            val fields = profileFields[default.id] ?: return@map previous
            val newModel = fields.model.text.toString().trim()
            val newKey = fields.key.text.toString().trim()
            previous.copy(
                model = newModel,
                apiKey = newKey,
                enabled = fields.enabled.isChecked,
                cooldownUntilMs = if (newKey != previous.apiKey || newModel != previous.model) 0L else previous.cooldownUntilMs,
                lastError = if (newKey != previous.apiKey || newModel != previous.model) "" else previous.lastError
            )
        }
        cortexStore.save(
            CortexRegistry(
                profiles = profiles,
                systemPrompt = systemPrompt.text.toString().trim().ifBlank { CortexRegistry.DEFAULT_SYSTEM_PROMPT }
            )
        )

        val previousSearch = searchStore.load().credentials.associateBy { it.provider }
        searchStore.save(
            SearchGridRegistry(
                SearchGridProvider.entries.map { provider ->
                    val previous = previousSearch[provider] ?: SearchGridCredential(provider)
                    val fields = searchFields[provider] ?: return@map previous
                    val newKey = fields.key.text.toString().trim()
                    previous.copy(
                        apiKey = newKey,
                        enabled = fields.enabled.isChecked,
                        cooldownUntilMs = if (newKey != previous.apiKey) 0L else previous.cooldownUntilMs,
                        lastError = if (newKey != previous.apiKey) "" else previous.lastError
                    )
                }
            )
        )

        val previousVoice = voiceStore.load()
        val primary = cartesiaKeys.getOrNull(0)?.text?.toString()?.trim().orEmpty()
        val backups = cartesiaKeys.drop(1).map { it.text.toString().trim() }.filter(String::isNotBlank)
        val voiceChanged = primary != previousVoice.apiKey || backups != previousVoice.backupApiKeys
        voiceStore.save(
            previousVoice.copy(
                apiKey = primary,
                backupApiKeys = backups,
                enabled = cartesiaEnabled.isChecked,
                activeKeyIndex = if (voiceChanged) 0 else previousVoice.activeKeyIndex,
                keyCooldownUntilMs = if (voiceChanged) emptyList() else previousVoice.keyCooldownUntilMs,
                lastStatusCode = if (voiceChanged) 0 else previousVoice.lastStatusCode,
                lastError = if (voiceChanged) "" else previousVoice.lastError
            )
        )

        val previousIntegration = integrationStore.load()
        val nextDeepSeekKey = deepSeekKey.text.toString().trim()
        val nextDeepSeekModel = deepSeekModel.text.toString().trim().ifBlank { "deepseek-chat" }
        val nextYouTubeKey = youtubeKey.text.toString().trim()
        integrationStore.save(
            previousIntegration.copy(
                deepSeekApiKey = nextDeepSeekKey,
                deepSeekModel = nextDeepSeekModel,
                deepSeekEnabled = deepSeekEnabled.isChecked,
                deepSeekLastStatusCode = if (nextDeepSeekKey != previousIntegration.deepSeekApiKey || nextDeepSeekModel != previousIntegration.deepSeekModel) 0 else previousIntegration.deepSeekLastStatusCode,
                deepSeekLastError = if (nextDeepSeekKey != previousIntegration.deepSeekApiKey || nextDeepSeekModel != previousIntegration.deepSeekModel) "" else previousIntegration.deepSeekLastError,
                youtubeApiKey = nextYouTubeKey,
                youtubeEnabled = youtubeEnabled.isChecked,
                youtubeLastStatusCode = if (nextYouTubeKey != previousIntegration.youtubeApiKey) 0 else previousIntegration.youtubeLastStatusCode,
                youtubeLastError = if (nextYouTubeKey != previousIntegration.youtubeApiKey) "" else previousIntegration.youtubeLastError,
                gmailApiKey = gmailKey.text.toString().trim(),
                gmailOAuthClientId = gmailOAuthClient.text.toString().trim(),
                gmailEnabled = gmailEnabled.isChecked
            )
        )
        populate()
        if (showToast) Toast.makeText(this, "Operations grid encrypted and saved.", Toast.LENGTH_SHORT).show()
        true
    }.getOrElse { error ->
        globalStatus.text = "SECURE SAVE FAILURE // ${error.message ?: error.javaClass.simpleName}"
        if (showToast) Toast.makeText(this, "Secure save failed.", Toast.LENGTH_LONG).show()
        false
    }

    private fun testProfile(profileId: String) {
        val fields = profileFields[profileId] ?: return
        fields.status.text = "STATUS // TESTING VERIFIED ROUTE..."
        Thread {
            val result = runCatching { CortexMeshClient(cortexStore).testProfile(profileId) }
            runOnUiThread {
                result.onSuccess {
                    fields.status.text = "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                }.onFailure {
                    fields.status.text = "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                refreshSummary()
            }
        }.start()
    }

    private fun testCortexMesh() {
        val configured = cortexStore.load().configuredProfiles()
        if (configured.isEmpty()) {
            globalStatus.text = "CORTEX MESH // NO CONFIGURED NODES"
            return
        }
        Thread {
            var online = 0
            configured.forEachIndexed { index, profile ->
                runOnUiThread { globalStatus.text = "CORTEX TEST // ${index + 1}/${configured.size} // ONLINE $online" }
                if (runCatching { CortexMeshClient(cortexStore).testProfile(profile.id) }.isSuccess) online++
            }
            runOnUiThread { populate(); globalStatus.text = "CORTEX TEST COMPLETE // ONLINE $online/${configured.size}" }
        }.start()
    }

    private fun testSearchProvider(provider: SearchGridProvider) {
        val fields = searchFields[provider] ?: return
        fields.status.text = "STATUS // TESTING LIVE EVIDENCE..."
        Thread {
            val result = runCatching { SearchGridResearchClient(searchStore, cortexStore).testProvider(provider) }
            runOnUiThread {
                result.onSuccess {
                    fields.status.text = "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms // RESULTS ${it.resultCount}"
                }.onFailure {
                    fields.status.text = "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                refreshSummary()
            }
        }.start()
    }

    private fun testSearchGrid() {
        val configured = searchStore.load().configuredCredentials()
        if (configured.isEmpty()) {
            globalStatus.text = "SEARCH GRID // NO CONFIGURED EVIDENCE PROVIDER"
            return
        }
        Thread {
            var online = 0
            val client = SearchGridResearchClient(searchStore, cortexStore)
            configured.forEachIndexed { index, credential ->
                runOnUiThread { globalStatus.text = "SEARCH TEST // ${index + 1}/${configured.size} // ONLINE $online" }
                if (runCatching { client.testProvider(credential.provider) }.isSuccess) online++
            }
            runOnUiThread { populate(); globalStatus.text = "SEARCH TEST COMPLETE // ONLINE $online/${configured.size}" }
        }.start()
    }

    private fun testCartesia() {
        voiceTestClient?.destroy()
        cartesiaStatus.text = "STATUS // ACQUIRING VOICE ROUTE..."
        lateinit var testClient: CartesiaSonicClient
        testClient = CartesiaSonicClient(this, object : CartesiaSonicClient.Listener {
            private var sent = false
            override fun onReady(label: String) = runOnUiThread {
                cartesiaStatus.text = "STATUS // $label // ONLINE"
                if (!sent) {
                    sent = true
                    testClient.push("F.R.I.D.A.Y. voice failover mesh operational, Boss.")
                    testClient.finish()
                }
            }
            override fun onAudioStarted(label: String) = runOnUiThread {
                cartesiaStatus.text = "STATUS // SPEAKING // $label"
            }
            override fun onCompleted() = runOnUiThread {
                voiceTestClient?.destroy(); voiceTestClient = null; populate()
            }
            override fun onDiagnostic(message: String) = runOnUiThread { cartesiaStatus.text = message }
            override fun onError(message: String) = runOnUiThread {
                cartesiaStatus.text = "STATUS // FAILED // ${message.take(180)}"
                voiceTestClient?.destroy(); voiceTestClient = null; refreshSummary()
            }
        })
        voiceTestClient = testClient
        if (!testClient.begin()) {
            cartesiaStatus.text = "STATUS // NO AVAILABLE CARTESIA KEY"
            voiceTestClient = null
        }
    }

    private fun testDeepSeek() {
        deepSeekStatus.text = "STATUS // TESTING DEEPSEEK..."
        Thread {
            val result = runCatching { DeepSeekClient(integrationStore).test() }
            runOnUiThread {
                result.onSuccess {
                    deepSeekStatus.text = "STATUS // ONLINE // ${it.model} // HTTP ${it.statusCode} // ${it.elapsedMs}ms"
                }.onFailure {
                    deepSeekStatus.text = "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                refreshSummary()
            }
        }.start()
    }

    private fun testYouTube() {
        youtubeStatus.text = "STATUS // TESTING YOUTUBE DATA..."
        Thread {
            val result = runCatching { YouTubeDataClient(integrationStore).test() }
            runOnUiThread {
                result.onSuccess {
                    youtubeStatus.text = "STATUS // ONLINE // HTTP ${it.statusCode} // ${it.elapsedMs}ms // RESULTS ${it.videos.size}"
                }.onFailure {
                    youtubeStatus.text = "STATUS // FAILED // ${it.message ?: it.javaClass.simpleName}"
                }
                refreshSummary()
            }
        }.start()
    }

    private fun refreshSummary() {
        globalStatus.text = systemSummary(
            cortexStore.load(),
            searchStore.load(),
            voiceStore.load(),
            integrationStore.load()
        )
    }

    private fun systemSummary(
        cortex: CortexRegistry,
        search: SearchGridRegistry,
        voice: CartesiaVoiceSettings,
        integrations: IntegrationSettings
    ): String {
        val cortexConfigured = cortex.configuredProfiles()
        val cortexOnline = cortexConfigured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        val searchConfigured = search.configuredCredentials()
        val searchOnline = searchConfigured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        return buildString {
            appendLine("CORTEX // ${cortexConfigured.size}/10 CONFIGURED // $cortexOnline VERIFIED ONLINE")
            appendLine("RESEARCH // ${searchConfigured.size}/2 CONFIGURED // $searchOnline VERIFIED ONLINE")
            appendLine("VOICE // ${voice.configuredKeys().size}/4 KEYS // ${voice.healthLabel()}")
            appendLine("DEEPSEEK // ${integrations.deepSeekHealthLabel()}")
            appendLine("YOUTUBE // ${integrations.youtubeHealthLabel()}")
            append("GMAIL // ${integrations.gmailHealthLabel()}")
        }
    }

    private fun panel(accent: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = panelBackground(PANEL, accent)
    }

    private fun sectionTitle(text: String): TextView = label(text, 16f, CYAN, true).apply {
        letterSpacing = .08f
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(0f, 1.15f)
        }

    private fun statusLabel(): TextView = label("STATUS // NOT CONFIGURED", 10f, SOFT, true).apply {
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun check(text: String): CheckBox = CheckBox(this).apply {
        this.text = text
        setTextColor(SOFT)
        typeface = Typeface.MONOSPACE
        isChecked = true
    }

    private fun textInput(hint: String, secret: Boolean, lines: Int = 1): EditText = EditText(this).apply {
        this.hint = hint
        minLines = lines
        gravity = if (lines > 1) Gravity.TOP else Gravity.CENTER_VERTICAL
        inputType = InputType.TYPE_CLASS_TEXT or when {
            secret -> InputType.TYPE_TEXT_VARIATION_PASSWORD
            lines > 1 -> InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> 0
        }
        setTextColor(Color.WHITE)
        setHintTextColor(MUTED)
        typeface = Typeface.MONOSPACE
        textSize = 12f
        setPadding(dp(10), dp(9), dp(10), dp(9))
        background = panelBackground(INPUT, BORDER)
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 10f
        letterSpacing = .08f
        setTextColor(Color.WHITE)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        background = panelBackground(Color.rgb(12, 34, 54), BLUE)
        setOnClickListener { action() }
    }

    private fun panelBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(2).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun matchWidth(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        voiceTestClient?.destroy()
        voiceTestClient = null
        super.onDestroy()
    }

    companion object {
        private val BG = Color.rgb(2, 7, 13)
        private val PANEL = Color.rgb(6, 17, 28)
        private val INPUT = Color.rgb(8, 24, 38)
        private val BORDER = Color.rgb(35, 75, 104)
        private val CYAN = Color.rgb(65, 220, 255)
        private val BLUE = Color.rgb(40, 126, 255)
        private val GREEN = Color.rgb(67, 229, 170)
        private val ORANGE = Color.rgb(255, 166, 74)
        private val VIOLET = Color.rgb(192, 108, 255)
        private val RED = Color.rgb(255, 92, 112)
        private val SOFT = Color.rgb(180, 216, 236)
        private val MUTED = Color.rgb(99, 132, 154)
    }
}
