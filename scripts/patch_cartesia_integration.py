from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"Patch target missing: {label}")
    return text.replace(old, new, 1)


# Hybrid research forwards token deltas into Search Grid synthesis.
path = "app/src/main/java/com/seongja/jarvis/HybridWebResearchClient.kt"
text = read(path)
text = replace_once(
    text,
    "    fun research(userInput: String, memoryContext: String): Result {",
    """    fun research(
        userInput: String,
        memoryContext: String,
        onToken: ((String) -> Unit)? = null
    ): Result {""",
    "HybridWebResearchClient.research signature",
)
text = text.replace(
    "searchGrid.research(userInput, memoryContext)",
    "searchGrid.research(userInput, memoryContext, onToken)",
)
write(path, text)


# Search Grid streams its final Gemini/Groq synthesis.
path = "app/src/main/java/com/seongja/jarvis/SearchGridResearchClient.kt"
text = read(path)
text = replace_once(
    text,
    "    fun research(userInput: String, memoryContext: String): SearchGridResult {",
    """    fun research(
        userInput: String,
        memoryContext: String,
        onToken: ((String) -> Unit)? = null
    ): SearchGridResult {""",
    "SearchGridResearchClient.research signature",
)
text = text.replace(
    "val mesh = cortexMesh.ask(synthesisPrompt, memoryContext)",
    "val mesh = cortexMesh.ask(synthesisPrompt, memoryContext, onToken)",
)
write(path, text)


# Brain passes the optional stream callback only to cloud cortex work.
path = "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt"
text = read(path)
text = replace_once(
    text,
    "    fun respond(rawInput: String): BrainResponse {",
    """    fun respond(
        rawInput: String,
        onCortexToken: ((String) -> Unit)? = null
    ): BrainResponse {""",
    "JarvisBrain.respond signature",
)
text = text.replace(
    "return runCatching { webResearchResponse(input) }\n                .getOrElse { webResearchFallback(input, it) }",
    "return runCatching { webResearchResponse(input, onCortexToken) }\n                .getOrElse { webResearchFallback(input, it, onCortexToken) }",
)
text = text.replace("return cortexResponse(input)", "return cortexResponse(input, onCortexToken)")
text = replace_once(
    text,
    "    private fun cortexResponse(input: String): BrainResponse {\n        val result = cortexMesh.ask(input, memory.promptContext(input))",
    """    private fun cortexResponse(
        input: String,
        onCortexToken: ((String) -> Unit)?
    ): BrainResponse {
        val result = cortexMesh.ask(
            input,
            memory.promptContext(input),
            onCortexToken
        )""",
    "JarvisBrain.cortexResponse",
)
text = replace_once(
    text,
    "    private fun webResearchResponse(input: String): BrainResponse {\n        val hybrid = webResearch.research(input, memory.promptContext(input))",
    """    private fun webResearchResponse(
        input: String,
        onCortexToken: ((String) -> Unit)?
    ): BrainResponse {
        val hybrid = webResearch.research(
            input,
            memory.promptContext(input),
            onCortexToken
        )""",
    "JarvisBrain.webResearchResponse",
)
text = replace_once(
    text,
    "    private fun webResearchFallback(input: String, error: Throwable): BrainResponse {",
    """    private fun webResearchFallback(
        input: String,
        error: Throwable,
        onCortexToken: ((String) -> Unit)?
    ): BrainResponse {""",
    "JarvisBrain.webResearchFallback signature",
)
text = text.replace(
    "val result = cortexMesh.ask(fallbackPrompt, memory.promptContext(input))",
    "val result = cortexMesh.ask(fallbackPrompt, memory.promptContext(input), onCortexToken)",
)
write(path, text)


# VoiceLoop prefers Cartesia and exposes begin/push/finish for token streaming.
path = "app/src/main/java/com/seongja/jarvis/VoiceLoop.kt"
text = read(path)
text = replace_once(
    text,
    """    private lateinit var gateway: JarvisVoiceGateway
    private lateinit var onDeviceInput: OnDeviceSpeechInput
    private lateinit var localVoice: LocalJarvisVoice
""",
    """    private lateinit var gateway: JarvisVoiceGateway
    private lateinit var onDeviceInput: OnDeviceSpeechInput
    private lateinit var localVoice: LocalJarvisVoice
    private lateinit var cartesiaVoice: CartesiaSonicClient
    private val streamingFilter = StreamingResponseFilter()
    private val streamingFallbackText = StringBuilder()
    private var cartesiaStreaming = false
    private var cartesiaAudioStarted = false
    private var cartesiaTextReceived = false
""",
    "VoiceLoop Cartesia fields",
)
if "private val cartesiaListener" not in text:
    marker = "    private val localVoiceListener = object : LocalJarvisVoice.Listener {"
    listener = """    private val cartesiaListener = object : CartesiaSonicClient.Listener {
        override fun onReady(label: String) {
            handler.post { onDiagnostic("VOICE OUTPUT -> $label READY") }
        }

        override fun onAudioStarted(label: String) {
            handler.post {
                cartesiaAudioStarted = true
                onDiagnostic("VOICE OUTPUT -> $label SPEAKING")
                onState(State.READY)
            }
        }

        override fun onCompleted() {
            handler.post {
                cartesiaStreaming = false
                onDiagnostic("VOICE OUTPUT -> CARTESIA COMPLETE")
                val completion = outputCompletion
                outputCompletion = null
                completion?.invoke()
            }
        }

        override fun onDiagnostic(message: String) {
            handler.post { onDiagnostic(message) }
        }

        override fun onError(message: String) {
            handler.post {
                val fallback = JarvisResponseSanitizer.spoken(streamingFallbackText.toString())
                val shouldFallback = !cartesiaAudioStarted && fallback.isNotBlank()
                cartesiaStreaming = false
                onDiagnostic("VOICE OUTPUT -> CARTESIA ERROR // $message")
                if (shouldFallback) {
                    onDiagnostic("VOICE OUTPUT -> ANDROID LOCAL FALLBACK")
                    localVoice.speak(fallback)
                } else {
                    val completion = outputCompletion
                    outputCompletion = null
                    completion?.invoke()
                }
            }
        }
    }

"""
    if marker not in text:
        raise RuntimeError("Patch target missing: VoiceLoop listener insertion")
    text = text.replace(marker, listener + marker, 1)
text = text.replace(
    "        localVoice = LocalJarvisVoice(activity.applicationContext, localVoiceListener)\n        gateway = JarvisVoiceGateway(",
    "        localVoice = LocalJarvisVoice(activity.applicationContext, localVoiceListener)\n        cartesiaVoice = CartesiaSonicClient(activity.applicationContext, cartesiaListener)\n        gateway = JarvisVoiceGateway(",
)
text = text.replace(
    "    fun isVoiceOutputReady(): Boolean = gateway.isReady() || localVoice.isReady()",
    "    fun isVoiceOutputReady(): Boolean =\n        cartesiaVoice.isConfigured() || gateway.isReady() || localVoice.isReady()\n\n    fun isCartesiaConfigured(): Boolean = cartesiaVoice.isConfigured()",
)
if "fun beginStreamingSpeech" not in text:
    marker = "    fun speak(text: String, onComplete: () -> Unit) {"
    methods = """    fun beginStreamingSpeech(): Boolean {
        if (destroyed || !cartesiaVoice.isConfigured()) return false
        pauseForTts()
        localVoice.stop()
        gateway.stopSpeech()
        cartesiaVoice.cancel()
        streamingFilter.reset()
        streamingFallbackText.setLength(0)
        cartesiaAudioStarted = false
        cartesiaTextReceived = false
        outputCompletion = null
        cartesiaStreaming = cartesiaVoice.begin()
        if (cartesiaStreaming) {
            onDiagnostic("VOICE OUTPUT -> CARTESIA CONTEXT OPEN // SONIC-3")
        }
        return cartesiaStreaming
    }

    fun pushStreamingSpeech(token: String) {
        if (!cartesiaStreaming || destroyed || token.isEmpty()) return
        cartesiaTextReceived = true
        streamingFallbackText.append(token)
        val audible = streamingFilter.push(token)
        if (audible.isNotEmpty()) cartesiaVoice.push(audible)
    }

    fun finishStreamingSpeech(onComplete: () -> Unit): Boolean {
        if (!cartesiaStreaming || destroyed || !cartesiaTextReceived) {
            if (cartesiaStreaming) cartesiaVoice.cancel()
            cartesiaStreaming = false
            return false
        }
        outputCompletion = onComplete
        val tail = streamingFilter.finish()
        if (tail.isNotEmpty()) cartesiaVoice.push(tail)
        cartesiaVoice.finish()
        return true
    }

    fun cancelStreamingSpeech() {
        cartesiaStreaming = false
        cartesiaTextReceived = false
        streamingFilter.reset()
        streamingFallbackText.setLength(0)
        cartesiaVoice.cancel()
    }

"""
    if marker not in text:
        raise RuntimeError("Patch target missing: VoiceLoop streaming methods")
    text = text.replace(marker, methods + marker, 1)
old_speak = re.compile(
    r"    fun speak\(text: String, onComplete: \(\) -> Unit\) \{.*?\n    \}\n\n    fun stopSpeaking",
    re.S,
)
new_speak = """    fun speak(text: String, onComplete: () -> Unit) {
        if (destroyed) return
        val clean = JarvisResponseSanitizer.spoken(text)
        if (clean.isBlank()) {
            handler.post(onComplete)
            return
        }

        if (cartesiaVoice.isConfigured() && beginStreamingSpeech()) {
            pushStreamingSpeech(clean)
            finishStreamingSpeech(onComplete)
            return
        }

        pauseForTts()
        outputCompletion = onComplete
        if (gateway.isReady()) {
            onDiagnostic("VOICE OUTPUT -> PREMIUM PCM")
            gateway.speak(clean)
            return
        }

        onDiagnostic("VOICE OUTPUT -> LOCAL JARVIS SYNTHESIS")
        if (!localVoice.speak(clean)) {
            val completion = outputCompletion
            outputCompletion = null
            handler.post { completion?.invoke() }
        }
    }

    fun stopSpeaking"""
text, count = old_speak.subn(new_speak, text, count=1)
if count != 1 and "cartesiaVoice.isConfigured() && beginStreamingSpeech()" not in text:
    raise RuntimeError("Patch target missing: VoiceLoop.speak")
text = text.replace(
    """    fun stopSpeaking() {
        outputCompletion = null
        localVoice.stop()
        gateway.stopSpeech()
    }""",
    """    fun stopSpeaking() {
        outputCompletion = null
        cancelStreamingSpeech()
        localVoice.stop()
        gateway.stopSpeech()
    }""",
)
text = text.replace(
    "        onDeviceInput.destroy()\n        localVoice.destroy()\n        gateway.destroy()",
    "        onDeviceInput.destroy()\n        cartesiaVoice.destroy()\n        localVoice.destroy()\n        gateway.destroy()",
)
write(path, text)


# MainActivity opens Cartesia before cloud inference and finalizes the same context.
path = "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
text = read(path)
if "val cartesiaStreaming = voiceLoop.beginStreamingSpeech()" not in text:
    text = text.replace(
        """        hud.pushEvent("CORTEX MESH -> ROUTING REQUEST")
        Thread {
""",
        """        hud.pushEvent("CORTEX MESH -> ROUTING REQUEST")
        val cartesiaStreaming = voiceLoop.beginStreamingSpeech()
        if (cartesiaStreaming) {
            hud.pushEvent("VOICE -> CARTESIA SONIC CONTEXT OPEN")
        }
        Thread {
""",
        1,
    )
text = text.replace(
    "val response = runCatching { brain.respond(clean) }.getOrElse { error ->",
    """val response = runCatching {
                brain.respond(clean) { token ->
                    if (cartesiaStreaming) voiceLoop.pushStreamingSpeech(token)
                }
            }.getOrElse { error ->
                if (cartesiaStreaming) voiceLoop.cancelStreamingSpeech()""",
    1,
)
old_final = """                hud.setProcessing(false)
                brainBusy.set(false)
                if (response.mode != BrainMode.ALERT) soundEngine.success()
                speak(response.spoken)
"""
new_final = """                hud.setProcessing(false)
                brainBusy.set(false)
                if (response.mode != BrainMode.ALERT) soundEngine.success()
                val streamed = cartesiaStreaming && voiceLoop.finishStreamingSpeech {
                    runOnUiThread {
                        lastTtsFinishedAt = SystemClock.elapsedRealtime()
                        cancelTtsWatchdog()
                        hud.pushEvent("VOICE -> CARTESIA COMPLETE")
                        if (resumed && hasMicPermission() && !brainBusy.get()) {
                            voiceLoop.resumeAfterTts(900L)
                        }
                    }
                }
                if (!streamed) speak(response.spoken) else armTtsWatchdog()
"""
text = replace_once(text, old_final, new_final, "MainActivity streamed completion")
text = text.replace(
    """                    if (voiceLoop.isPremiumBackendReady()) {
                        speak("Systems online. Premium streaming voice is active, Sir.")
                    } else {
                        hud.pushEvent("VOICE -> LOCAL JARVIS OUTPUT // ANDROID TTS")
                        speak("Systems online. Your local Jarvis voice is active, Sir.")
                    }""",
    """                    when {
                        voiceLoop.isCartesiaConfigured() -> {
                            hud.pushEvent("VOICE -> CARTESIA SONIC-3 // GEMMA EN-GB")
                            speak("Systems online. Cartesia Sonic voice is active, Boss.")
                        }
                        voiceLoop.isPremiumBackendReady() ->
                            speak("Systems online. Premium streaming voice is active, Boss.")
                        else -> {
                            hud.pushEvent("VOICE -> LOCAL JARVIS OUTPUT // ANDROID TTS")
                            speak("Systems online. Your local voice is active, Boss.")
                        }
                    }""",
)
text = text.replace(
    """        hud.pushEvent(
            if (voiceLoop.isPremiumBackendReady()) {
                "VOICE -> STREAM REQUEST"
            } else {
                "VOICE -> LOCAL JARVIS SYNTHESIS"
            }
        )""",
    """        hud.pushEvent(
            when {
                voiceLoop.isCartesiaConfigured() -> "VOICE -> CARTESIA SONIC STREAM"
                voiceLoop.isPremiumBackendReady() -> "VOICE -> STREAM REQUEST"
                else -> "VOICE -> LOCAL JARVIS SYNTHESIS"
            }
        )""",
)
if "private fun armTtsWatchdog()" not in text:
    marker = "    private fun speechSafeText(text: String): String {"
    helper = """    private fun armTtsWatchdog() {
        cancelTtsWatchdog()
        ttsResumeWatchdog = Runnable {
            if (resumed && hasMicPermission() && !brainBusy.get()) {
                hud.pushEvent("VOICE -> STREAM WATCHDOG RELEASE")
                lastTtsFinishedAt = SystemClock.elapsedRealtime()
                voiceLoop.stopSpeaking()
                voiceLoop.resumeAfterTts(700L)
            }
        }.also { mainHandler.postDelayed(it, 60_000L) }
    }

"""
    if marker not in text:
        raise RuntimeError("Patch target missing: MainActivity watchdog helper")
    text = text.replace(marker, helper + marker, 1)
text = re.sub(
    r"\n        if \(voiceLoop\.isPremiumBackendReady\(\)\) \{\n            ttsResumeWatchdog = Runnable \{.*?\n        \}\n",
    "\n        if (voiceLoop.isCartesiaConfigured() || voiceLoop.isPremiumBackendReady()) {\n            armTtsWatchdog()\n        }\n",
    text,
    count=1,
    flags=re.S,
)
write(path, text)


# Cloud configuration receives encrypted Cartesia setup and a live audio test.
path = "app/src/main/java/com/seongja/jarvis/CloudConfigActivity.kt"
text = read(path)
if "private data class VoiceFields" not in text:
    text = text.replace(
        """    private data class SearchFields(
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )
""",
        """    private data class SearchFields(
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )

    private data class VoiceFields(
        val apiKey: EditText,
        val enabled: CheckBox,
        val health: TextView
    )
""",
        1,
    )
text = text.replace(
    "    private lateinit var searchStore: SecureSearchGridRegistry\n",
    "    private lateinit var searchStore: SecureSearchGridRegistry\n    private lateinit var voiceStore: SecureVoiceRegistry\n",
    1,
)
text = text.replace(
    "    private val searchFields = linkedMapOf<SearchGridProvider, SearchFields>()\n",
    "    private val searchFields = linkedMapOf<SearchGridProvider, SearchFields>()\n    private lateinit var voiceFields: VoiceFields\n    private var voiceTestClient: CartesiaSonicClient? = null\n",
    1,
)
text = text.replace(
    """        store = SecureCortexRegistry(this)
        searchStore = SecureSearchGridRegistry(this)
        setContentView(buildUi())
        populate(store.load(), searchStore.load())
""",
    """        store = SecureCortexRegistry(this)
        searchStore = SecureSearchGridRegistry(this)
        voiceStore = SecureVoiceRegistry(this)
        setContentView(buildUi())
        populate(store.load(), searchStore.load(), voiceStore.load())
""",
)
text = text.replace(
    """        root.addView(sectionTitle("SEARCH GRID // TAVILY + EXA"))
""",
    """        root.addView(sectionTitle("VOICE CORE // CARTESIA SONIC-3"))
        root.addView(buildCartesiaPanel(), matchWidth(bottom = 16))

        root.addView(sectionTitle("SEARCH GRID // TAVILY + EXA"))
""",
    1,
)
text = text.replace("SAVE CORTEX + SEARCH GRID", "SAVE CORTEX + SEARCH + VOICE")
if "RESET VOICE CORE" not in text:
    marker = """        root.addView(Button(this).apply {
            text = "RETURN TO JARVIS"
"""
    reset = """        root.addView(Button(this).apply {
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

"""
    if marker not in text:
        raise RuntimeError("Patch target missing: CloudConfig reset insertion")
    text = text.replace(marker, reset + marker, 1)
if "private fun buildCartesiaPanel" not in text:
    marker = "    private fun buildSearchPanel(provider: SearchGridProvider): LinearLayout {"
    method = """    private fun buildCartesiaPanel(): LinearLayout {
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

"""
    if marker not in text:
        raise RuntimeError("Patch target missing: CloudConfig voice panel insertion")
    text = text.replace(marker, method + marker, 1)
text = text.replace(
    """    private fun populate(
        cortexRegistry: CortexRegistry,
        searchRegistry: SearchGridRegistry
    ) {""",
    """    private fun populate(
        cortexRegistry: CortexRegistry,
        searchRegistry: SearchGridRegistry,
        voiceSettings: CartesiaVoiceSettings
    ) {""",
)
text = text.replace(
    """        globalStatus.text = systemSummary(cortexRegistry, searchRegistry)
""",
    """        voiceFields.apiKey.setText(voiceSettings.apiKey)
        voiceFields.enabled.isChecked = voiceSettings.enabled
        voiceFields.health.text =
            "STATUS // ${voiceSettings.healthLabel()} // SUCCESS ${voiceSettings.successes} // FAIL ${voiceSettings.failures}"

        globalStatus.text = systemSummary(cortexRegistry, searchRegistry, voiceSettings)
""",
    1,
)
text = text.replace(
    """        val cortexSaved = saveRegistry(showToast = false)
        val searchSaved = saveSearchGrid(showToast = false)
        val success = cortexSaved && searchSaved
""",
    """        val cortexSaved = saveRegistry(showToast = false)
        val searchSaved = saveSearchGrid(showToast = false)
        val voiceSaved = saveVoice(showToast = false)
        val success = cortexSaved && searchSaved && voiceSaved
""",
)
text = text.replace("Cortex and Search Grid saved securely.", "Cortex, Search Grid, and Cartesia voice saved securely.")
text = text.replace(
    "globalStatus.text = systemSummary(store.load(), searchStore.load())",
    "globalStatus.text = systemSummary(store.load(), searchStore.load(), voiceStore.load())",
)
text = text.replace(
    "populate(store.load(), searchStore.load())",
    "populate(store.load(), searchStore.load(), voiceStore.load())",
)
if "private fun saveVoice" not in text:
    marker = "    private fun testProfile(profileId: String) {"
    methods = """    private fun saveVoice(showToast: Boolean): Boolean {
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

"""
    if marker not in text:
        raise RuntimeError("Patch target missing: CloudConfig save/test voice")
    text = text.replace(marker, methods + marker, 1)
text = text.replace(
    """    private fun systemSummary(
        cortexRegistry: CortexRegistry,
        searchRegistry: SearchGridRegistry
    ): String {""",
    """    private fun systemSummary(
        cortexRegistry: CortexRegistry,
        searchRegistry: SearchGridRegistry,
        voiceSettings: CartesiaVoiceSettings
    ): String {""",
)
text = text.replace(
    """        return "CORTEX ${cortexConfigured.size}/10 // ONLINE $cortexOnline // COOLDOWN $cortexCooling\n" +
            "SEARCH GRID ${searchConfigured.size}/2 // ONLINE $searchOnline // COOLDOWN $searchCooling"
""",
    """        val voice = if (voiceSettings.isConfigured()) voiceSettings.healthLabel() else "NOT CONFIGURED"
        return "CORTEX ${cortexConfigured.size}/10 // ONLINE $cortexOnline // COOLDOWN $cortexCooling\n" +
            "SEARCH GRID ${searchConfigured.size}/2 // ONLINE $searchOnline // COOLDOWN $searchCooling\n" +
            "VOICE CORE // $voice"
""",
)
write(path, text)


# Version identity.
path = "app/build.gradle.kts"
text = read(path)
text = re.sub(r"versionCode = \d+", "versionCode = 28", text, count=1)
text = re.sub(r'versionName = "[^"]+"', 'versionName = "0.9.18-cartesia-sonic"', text, count=1)
write(path, text)

path = "helix-ui/package.json"
text = read(path).replace('"version": "0.9.17"', '"version": "0.9.18"')
write(path, text)

path = "helix-ui/src/Hud.tsx"
text = read(path).replace("BUILD 0.9.17", "BUILD 0.9.18")
text = text.replace("JARVIS LOCAL", "CARTESIA / LOCAL")
write(path, text)


required = {
    "app/src/main/java/com/seongja/jarvis/VoiceLoop.kt": [
        "beginStreamingSpeech",
        "CartesiaSonicClient",
        "CARTESIA CONTEXT OPEN",
    ],
    "app/src/main/java/com/seongja/jarvis/MainActivity.kt": [
        "brain.respond(clean) { token",
        "finishStreamingSpeech",
        "CARTESIA SONIC STREAM",
    ],
    "app/src/main/java/com/seongja/jarvis/CloudConfigActivity.kt": [
        "buildCartesiaPanel",
        "SAVE + TEST CARTESIA VOICE",
        "SecureVoiceRegistry",
    ],
    "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt": [
        "onCortexToken",
        "cortexMesh.ask(\n            input",
    ],
    "app/src/main/java/com/seongja/jarvis/SearchGridResearchClient.kt": [
        "onToken: ((String) -> Unit)? = null",
    ],
}
for file, markers in required.items():
    source = read(file)
    for value in markers:
        if value not in source:
            raise RuntimeError(f"Required marker missing after patch: {file}: {value}")

print("Cartesia Sonic integration patch applied successfully.")
