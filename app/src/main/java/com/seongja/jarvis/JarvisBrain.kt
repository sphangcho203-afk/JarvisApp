package com.seongja.jarvis

import android.app.KeyguardManager
import android.content.Context
import java.util.Locale

class JarvisBrain(context: Context) {
    private val appContext = context.applicationContext
    private val memory = MemoryVault(appContext)
    private val router = ActionRouter(appContext)
    private val registryStore = SecureCortexRegistry(appContext)
    private val integrationStore = SecureIntegrationRegistry(appContext)
    private val cortexMesh = CortexMeshClient(registryStore)
    private val webResearch = HybridWebResearchClient(registryStore)
    private val deepSeek = DeepSeekClient(integrationStore)
    private val youtube = YouTubeDataClient(integrationStore)
    private val capabilityRouter = FridayCapabilityRouter(appContext)
    private val keyguard =
        appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    init {
        JarvisConversationBus.initialize(appContext)
    }

    fun isCloudConfigured(): Boolean =
        registryStore.load().configuredProfiles().isNotEmpty() || deepSeek.isConfigured()

    fun configuredModel(): String {
        val configured = registryStore.load().configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        return buildString {
            append("${configured.size} mesh nodes // $online online")
            append(" // DeepSeek ${if (deepSeek.isConfigured()) "ready" else "offline"}")
            append(" // web ${if (webResearch.isConfigured()) "ready" else "offline"}")
            append(" // YouTube ${if (youtube.isConfigured()) "ready" else "offline"}")
            append(" // ${capabilityRouter.statusLabel()}")
        }
    }

    fun interceptLocalDialogue(rawInput: String): BrainResponse? {
        val lower = normalizedDialogue(rawInput)
        if (lower in WAKE_PHRASES) {
            val greeting = when {
                lower.startsWith("good morning") -> "Good morning, Sir. At your service."
                lower.startsWith("good afternoon") -> "Good afternoon, Sir. At your service."
                lower.startsWith("good evening") -> "Good evening, Sir. At your service."
                lower.contains("are you there") -> "Online and listening, Sir."
                else -> "At your service, Sir."
            }
            return localResponse(
                spoken = greeting,
                display = "VOICE LINK ESTABLISHED\n$greeting\n${OwnerIdentityCore.VERSION}",
                intent = "dialogue/wake"
            )
        }

        if (OwnerIdentityCore.isIdentityQuery(rawInput)) {
            return localResponse(
                spoken = "You are my creator and primary operator, Seongja. My loyalty is expressed through truthful judgment, protected memory, continuity, and reliable execution, not blind agreement.",
                display = buildString {
                    appendLine(OwnerIdentityCore.statusLine(memory.summary()))
                    appendLine("CREATOR // SEONGJA")
                    appendLine("RELATIONSHIP // OWNER-BOUND PRIVATE INTELLIGENCE")
                    appendLine("LOYALTY // TRUTH + PRIVACY + CONTINUITY + COMPETENCE")
                    appendLine("ACTION CLAIMS // REQUIRE ANDROID VERIFICATION")
                }.trim(),
                intent = "identity/owner_core"
            )
        }

        if (isOwnerInquiry(lower)) {
            return localResponse(
                spoken = "This phone belongs to ${memory.callsign()}. Please keep it powered on and return it to the owner or a trusted authority. I will not disclose private contacts or personal history without authentication.",
                display = memory.publicOwnerCard(),
                intent = "security/public_owner_card"
            )
        }

        if (lower in setOf("who are you", "tell me about yourself", "what are you")) {
            return localResponse(
                spoken = "I am FRIDAY, your private personal intelligence, Sir. You built me to know your world, protect your privacy, remember what matters, research what you ask, and act across this phone.",
                display = "F.R.I.D.A.Y. // SEONGJA'S PRIVATE INTELLIGENCE\nVOICE // MEMORY // LIVE RESEARCH // VERIFIED ANDROID ACTIONS\nOPERATIONS CORE // 0.9.24",
                intent = "dialogue/self_identity"
            )
        }

        if (asksAboutCreator(lower)) {
            val operator = memory.callsign()
            return localResponse(
                spoken = "My creator and primary operator is $operator. You designed me as a private Android intelligence system with voice, research, contextual memory, and verified device-control capabilities.",
                display = "CREATOR // $operator\nROLE // PRIMARY OPERATOR AND SYSTEM ARCHITECT\nIDENTITY CORE // ${OwnerIdentityCore.VERSION}",
                intent = "dialogue/creator"
            )
        }

        if (lower in setOf("thank you", "thanks", "thanks friday", "thank you friday", "thanks jarvis", "thank you jarvis")) {
            return localResponse(
                spoken = "Always, Sir.",
                display = "ACKNOWLEDGED // ALWAYS AT YOUR SERVICE",
                intent = "dialogue/thanks"
            )
        }

        if (lower in setOf("how are you", "how are you friday", "system check friday", "how are you jarvis", "system check jarvis")) {
            return localResponse(
                spoken = "Operational and ready, Sir.",
                display = "SYSTEM STATE // OPERATIONAL\nIDENTITY // ${OwnerIdentityCore.VERSION}\nCORTEX // ${configuredModel()}\nMEMORY // ${memory.summary()}",
                intent = "dialogue/status"
            )
        }
        return null
    }

    fun respond(
        rawInput: String,
        onCortexToken: ((String) -> Unit)? = null
    ): BrainResponse {
        val input = rawInput.trim()
        interceptLocalDialogue(input)?.let { return it }
        localMemoryCommand(input)?.let { return it }
        localMeshCommand(input)?.let { return it }

        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }
        YouTubeDataClient.commandFor(input)?.let { command ->
            return youtubeResponse(command, onCortexToken)
        }

        memory.detectStableConflict(input)?.let { conflict ->
            return localResponse(
                spoken = "Sir, that conflicts with a stable memory. I currently remember: ${conflict.previous}. To replace it, say: correct memory to ${conflict.proposed}.",
                display = buildString {
                    appendLine("MEMORY CONFLICT DETECTED")
                    appendLine("CATEGORY // ${conflict.category.uppercase(Locale.US)}")
                    appendLine("CURRENT // ${conflict.previous}")
                    appendLine("PROPOSED // ${conflict.proposed}")
                    appendLine("COMMAND // CORRECT MEMORY TO ${conflict.proposed.uppercase(Locale.US)}")
                }.trim(),
                intent = "memory/conflict_confirmation"
            )
        }

        if (WebResearchIntent.shouldUseWeb(input)) {
            return liveResearchResponse(input, onCortexToken)
        }
        if (!isCloudConfigured()) return configurationRequiredResponse()
        return resilientCortexResponse(input, onCortexToken)
    }

    private fun youtubeResponse(
        command: YouTubeCommand,
        onToken: ((String) -> Unit)?
    ): BrainResponse {
        if (!youtube.isConfigured()) {
            return integrationRequiredResponse(
                spoken = "YouTube Data API v3 is not configured, Sir. Say configure APIs and add the project key.",
                display = "YOUTUBE DATA // CONFIGURATION REQUIRED\nSAY // CONFIGURE APIS",
                intent = "youtube/config_required"
            )
        }
        JarvisOperationBus.publish("YOUTUBE DATA SEARCH", "QUERYING VERIFIED PUBLIC VIDEO DATA", .16f)
        onToken?.invoke("Searching YouTube data, Sir. ")
        return runCatching {
            JarvisOperationBus.publish("YOUTUBE DATA SEARCH", "FILTERING PUBLIC RESULTS // SAFE SEARCH ACTIVE", .54f)
            val result = youtube.execute(command)
            JarvisOperationBus.publish("YOUTUBE DATA VERIFIED", "${result.videos.size} RESULTS // ${result.elapsedMs}MS", .92f)
            onToken?.invoke(result.spoken())
            BrainResponse(
                spoken = result.spoken(),
                display = result.display(),
                intent = "youtube_data/verified_results",
                confidence = if (result.videos.isNotEmpty()) .98f else .72f,
                mode = BrainMode.ONLINE,
                trace = listOf(
                    "route=youtube_data_v3",
                    "results=${result.videos.size}",
                    "http=${result.statusCode}",
                    "latency=${result.elapsedMs}ms",
                    "safe_search=strict",
                    "region=IN"
                ),
                memory = memory.summary(),
                thoughts = listOf("Only public YouTube metadata was requested."),
                entities = result.videos.take(5).map { "video=${it.videoId}" },
                decision = "return_verified_youtube_metadata",
                action = BrainAction()
            )
        }.getOrElse { error ->
            val message = "YouTube data search failed: ${error.message ?: error.javaClass.simpleName}."
            JarvisOperationBus.publish("YOUTUBE DATA ERROR", message, 1f, false)
            onToken?.invoke(message)
            BrainResponse(
                spoken = message,
                display = "YOUTUBE DATA ERROR\n${message.take(360)}",
                intent = "youtube_data/error",
                confidence = 0f,
                mode = BrainMode.ALERT,
                trace = listOf("route=youtube_data_v3", "status=failed"),
                memory = memory.summary(),
                decision = "report_youtube_failure",
                action = BrainAction()
            )
        }.also { JarvisOperationBus.clear("YOUTUBE DATA CYCLE COMPLETE") }
    }

    private fun liveResearchResponse(
        input: String,
        onToken: ((String) -> Unit)?
    ): BrainResponse {
        if (!webResearch.isConfigured()) {
            return knowledgeFallback(
                input = input,
                reason = "No live research provider is configured.",
                onToken = onToken
            )
        }
        JarvisOperationBus.publish("LIVE WEB SEARCH", "DISCOVERING CURRENT SOURCES", .08f)
        onToken?.invoke("Searching the web, Sir. ")
        return runCatching {
            JarvisOperationBus.publish("LIVE WEB SEARCH", "QUERYING TAVILY // EXA // GROUNDED FALLBACKS", .28f)
            val hybrid = webResearch.research(input, memory.promptContext(input), onToken)
            val result = hybrid.research
            JarvisOperationBus.publish("EVIDENCE SYNTHESIS", "CROSS-CHECKING ${result.sources.size} SOURCES", .78f)
            BrainResponse(
                spoken = result.spokenSummary,
                display = result.displayText(),
                intent = if (WebResearchIntent.isWorldBrief(input)) "web_research/world_brief" else "web_research/grounded_answer",
                confidence = when {
                    result.sources.size >= 4 -> .98f
                    result.sources.size >= 2 -> .95f
                    else -> .84f
                },
                mode = BrainMode.ONLINE,
                trace = listOf(
                    "route=hybrid_live_web_research",
                    "provider=${hybrid.provider}",
                    "attempts=${hybrid.attempts.joinToString(">")}",
                    "node=${result.profileLabel}",
                    "model=${result.model}",
                    "queries=${result.searchQueries.size}",
                    "sources=${result.sources.size}",
                    "http=${result.statusCode}",
                    "latency=${result.elapsedMs}ms"
                ),
                memory = memory.summary(),
                thoughts = listOf(
                    "Private contacts were excluded from cloud context.",
                    "The detailed answer and sources are displayed on screen."
                ),
                entities = buildList {
                    add("source=hybrid_web_research")
                    add("provider=${hybrid.provider}")
                    result.sources.take(4).forEachIndexed { index, source ->
                        add("source_${index + 1}=${source.title.take(80)}")
                    }
                },
                decision = "owner_bound_grounded_web_research",
                action = BrainAction()
            )
        }.getOrElse { error ->
            knowledgeFallback(input, error.message ?: error.javaClass.simpleName, onToken)
        }.also { JarvisOperationBus.clear("RESEARCH CYCLE COMPLETE") }
    }

    private fun resilientCortexResponse(
        input: String,
        onToken: ((String) -> Unit)?
    ): BrainResponse {
        JarvisOperationBus.publish("COGNITIVE ROUTING", "SCORING AVAILABLE REASONING NODES", .18f)
        val meshConfigured = registryStore.load().configuredProfiles().isNotEmpty()
        if (meshConfigured) {
            val mesh = runCatching { cortexResponse(input, onToken) }
            mesh.onSuccess {
                JarvisOperationBus.clear("CORTEX RESPONSE VERIFIED")
                return it
            }
            val error = mesh.exceptionOrNull()
            if (error?.message?.contains("after speech began", ignoreCase = true) == true) throw error
            JarvisOperationBus.publish("CORTEX FAILOVER", "PRIMARY MESH DEGRADED // EVALUATING DEEPSEEK", .52f)
        }
        if (deepSeek.isConfigured()) {
            return deepSeekResponse(input, onToken).also { JarvisOperationBus.clear("DEEPSEEK RESPONSE VERIFIED") }
        }
        JarvisOperationBus.clear("NO COGNITIVE ROUTE COMPLETED")
        return configurationRequiredResponse()
    }

    private fun cortexResponse(input: String, onToken: ((String) -> Unit)?): BrainResponse {
        val result = cortexMesh.ask(input, memory.promptContext(input), onToken)
        val reply = OwnerIdentityCore.normalizeOperatorReference(result.reply)
        return BrainResponse(
            spoken = reply,
            display = reply,
            intent = "cortex_mesh/response",
            confidence = .97f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "task=${result.task.name.lowercase(Locale.US)}",
                "mesh_route=${result.profileLabel}",
                "provider=${result.provider.displayName}",
                "model=${result.model}",
                "attempts=${result.attempts.joinToString(">")}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms"
            ),
            memory = memory.summary(),
            thoughts = listOf("Relevant encrypted memories were retrieved locally."),
            entities = listOf("source=cortex_mesh", "node=${result.profileLabel}", "model=${result.model}"),
            decision = "owner_bound_mesh_response",
            action = BrainAction()
        )
    }

    private fun deepSeekResponse(input: String, onToken: ((String) -> Unit)?): BrainResponse {
        JarvisOperationBus.publish("DEEPSEEK CORTEX", "REQUESTING RESILIENT REASONING ROUTE", .68f)
        val result = deepSeek.ask(input, memory.promptContext(input), onToken)
        val reply = OwnerIdentityCore.normalizeOperatorReference(result.reply)
        return BrainResponse(
            spoken = reply,
            display = reply,
            intent = "deepseek/response",
            confidence = .94f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "route=deepseek_fallback",
                "model=${result.model}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms"
            ),
            memory = memory.summary(),
            thoughts = listOf("DeepSeek was selected as the resilient secondary cortex."),
            entities = listOf("source=deepseek", "model=${result.model}"),
            decision = "owner_bound_deepseek_response",
            action = BrainAction()
        )
    }

    private fun knowledgeFallback(
        input: String,
        reason: String,
        onToken: ((String) -> Unit)?
    ): BrainResponse {
        JarvisOperationBus.publish("WEB ROUTE DEGRADED", "LIVE VERIFICATION FAILED // KNOWLEDGE FALLBACK", .66f)
        val prompt = buildString {
            appendLine(input)
            appendLine()
            appendLine("Live web routes were unavailable: $reason")
            appendLine(JarvisDirective.SUMMARIZATION)
            appendLine("Answer from existing knowledge only. State clearly that freshness cannot be verified.")
        }
        val fallback = runCatching {
            if (registryStore.load().configuredProfiles().isNotEmpty()) {
                val result = cortexMesh.ask(prompt, memory.promptContext(input), onToken)
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "cortex_mesh"
            } else {
                val result = deepSeek.ask(prompt, memory.promptContext(input), onToken)
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "deepseek"
            }
        }
        return fallback.fold(
            onSuccess = { (reply, route) ->
                BrainResponse(
                    spoken = "Live information could not be verified. This summary may not be current. $reply",
                    display = "LIVE WEB VERIFICATION UNAVAILABLE\n${reason.take(360)}\n\nKNOWLEDGE-BASED FALLBACK\n$reply",
                    intent = "web_research/fallback",
                    confidence = .55f,
                    mode = BrainMode.ALERT,
                    trace = listOf("live_routes=failed", "fallback=$route"),
                    memory = memory.summary(),
                    decision = "owner_bound_research_fallback",
                    action = BrainAction()
                )
            },
            onFailure = { fallbackError ->
                val spoken = "Live web verification and fallback reasoning are unavailable, Sir. ${fallbackError.message ?: fallbackError.javaClass.simpleName}."
                onToken?.invoke(spoken)
                BrainResponse(
                    spoken = spoken,
                    display = "RESEARCH GRID UNAVAILABLE\nLIVE ROUTE // FAILED\nFALLBACK // FAILED\n${reason.take(240)}",
                    intent = "web_research/unavailable",
                    confidence = 0f,
                    mode = BrainMode.ALERT,
                    trace = listOf("live_routes=failed", "fallback=failed"),
                    memory = memory.summary(),
                    decision = "report_research_unavailable",
                    action = BrainAction()
                )
            }
        )
    }

    fun execute(action: BrainAction): Boolean = router.execute(action)
    fun memorySnapshot(): String = memory.summary()
    fun contextSnapshot(): List<String> = memory.history().take(12)

    private fun localMemoryCommand(input: String): BrainResponse? {
        val lower = normalizedDialogue(input)
        val ownerUpdate = memory.captureOwnerDetails(input)
        if (ownerUpdate.changed) {
            val items = buildList {
                if (ownerUpdate.emailStored) add("email")
                if (ownerUpdate.phoneCount > 0) add("${ownerUpdate.phoneCount} phone number${if (ownerUpdate.phoneCount == 1) "" else "s"}")
                if (ownerUpdate.recoverySet) add("recovery contact")
            }
            return localResponse(
                spoken = "Stored privately, Sir. I added ${items.joinToString(", ")} to the encrypted owner profile.",
                display = "OWNER PROFILE UPDATED // ${items.joinToString(" // ").uppercase(Locale.US)}\nSTORAGE // ENCRYPTED LOCAL DATABASE\nCLOUD SHARING // BLOCKED",
                intent = "memory/owner_profile_update"
            )
        }

        CORRECTION_PREFIXES.firstOrNull(lower::startsWith)?.let { prefix ->
            val value = payloadAfterPrefix(input, prefix)
            val stored = memory.addCorrection(value)
            return localResponse(
                spoken = if (stored.isBlank()) "State the corrected memory after the command, Sir." else "Correction confirmed. The new memory now overrides older conflicting information, Sir.",
                display = if (stored.isBlank()) "MEMORY CORRECTION // VALUE REQUIRED" else "MEMORY CORRECTION STORED\n$stored\nPRIORITY // OVERRIDES OLDER CONFLICTS",
                intent = "memory/correction"
            )
        }

        if (lower.startsWith("remember that ")) {
            val fact = payloadAfterPrefix(input, "remember that ")
            memory.detectStableConflict(fact)?.let { conflict ->
                return localResponse(
                    spoken = "That conflicts with an existing stable memory, Sir. Say: correct memory to ${conflict.proposed}, to replace it.",
                    display = "MEMORY CONFLICT\nCURRENT // ${conflict.previous}\nPROPOSED // ${conflict.proposed}\nAWAITING EXPLICIT CORRECTION",
                    intent = "memory/conflict_confirmation"
                )
            }
            if (fact.isNotBlank()) memory.addFact(fact, category = "explicit")
            return localResponse(
                spoken = if (fact.isBlank()) "Tell me what to remember, Sir." else "Stored, Sir.",
                display = if (fact.isBlank()) "MEMORY INPUT REQUIRED" else "MEMORY STORED // ${fact.take(160)}",
                intent = "memory/write"
            )
        }

        if (lower.startsWith("call me ")) {
            val name = payloadAfterPrefix(input, "call me ")
            if (name.isNotBlank()) memory.setIdentity(name)
            return localResponse(
                spoken = if (name.isBlank()) "Tell me the name to use, Sir." else "Understood. I will call you $name.",
                display = if (name.isBlank()) "IDENTITY INPUT REQUIRED" else "OPERATOR // $name",
                intent = "memory/identity_update"
            )
        }

        if (lower == "who am i" || lower == "identify me") {
            return localResponse(
                spoken = "You are ${memory.callsign()}, my creator and primary operator.",
                display = memory.profile(),
                intent = "memory/identity_query"
            )
        }

        if (lower.contains("what do you know about me") || lower.contains("what have you learned about me") || lower.contains("tell me what you remember about me")) {
            return localResponse(
                spoken = "I have assembled your stored profile, corrections, and relevant learned preferences, Sir. Sensitive contacts remain protected.",
                display = memory.expanded(includeSensitive = false),
                intent = "memory/operator_summary"
            )
        }

        if (lower in PRIVATE_PROFILE_COMMANDS) {
            if (keyguard.isDeviceLocked) {
                return localResponse(
                    spoken = "Private owner information is locked. Authenticate on the device first, Sir.",
                    display = "PRIVATE MEMORY // DEVICE AUTHENTICATION REQUIRED",
                    intent = "security/private_profile_blocked"
                )
            }
            return localResponse(
                spoken = "Your private owner profile is displayed on screen, Sir.",
                display = memory.privateOwnerCard(),
                intent = "memory/private_owner_profile"
            )
        }

        if (lower.contains("what do you remember") || lower == "memory status") {
            return localResponse(
                spoken = "Persistent owner memory is online, Sir. The current memory status is displayed.",
                display = memory.expanded(includeSensitive = false),
                intent = "memory/read"
            )
        }

        if (lower == "clear memory" || lower == "forget everything") {
            memory.clearUserFactsKeepIdentity()
            return localResponse(
                spoken = "Stored personal facts, contacts, and conversation memory cleared. My identity and connection to you remain intact, Sir.",
                display = "MEMORY CLEARED // OWNER IDENTITY CORE RETAINED",
                intent = "memory/clear"
            )
        }
        return null
    }

    private fun localMeshCommand(input: String): BrainResponse? {
        val lower = normalizedDialogue(input)
        if (lower !in STATUS_COMMANDS) return null
        val configured = registryStore.load().configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        val cooling = configured.count { it.isCoolingDown() }
        val searchProviders = webResearch.configuredSearchProviders()
        val integrations = integrationStore.load()
        val voice = SecureVoiceRegistry(appContext).load()
        val summary = buildString {
            appendLine(OwnerIdentityCore.statusLine(memory.summary()))
            appendLine("CORTEX MESH // CONFIGURED ${configured.size}/10 // ONLINE $online // COOLDOWN $cooling")
            appendLine("DEEPSEEK // ${integrations.deepSeekHealthLabel()}")
            appendLine("WORLD INTELLIGENCE // ${if (webResearch.isConfigured()) "HYBRID READY" else "NEEDS A RESEARCH ROUTE"}")
            appendLine("SEARCH GRID // ${searchProviders.joinToString(" + ") { it.displayName }.ifBlank { "NOT CONFIGURED" }}")
            appendLine("YOUTUBE DATA // ${integrations.youtubeHealthLabel()}")
            appendLine("GMAIL // ${integrations.gmailHealthLabel()}")
            appendLine("VOICE MESH // ${voice.healthLabel()}")
            appendLine("PERSISTENT MEMORY // ${memory.summary()}")
            configured.forEach { appendLine("${it.label} // ${it.provider.displayName} // ${it.healthLabel()}") }
        }.trim()
        return localResponse(
            spoken = "The operations core is active. The cortex mesh has ${configured.size} configured nodes, with $online currently online. Voice has ${voice.configuredKeys().size} encrypted routes, Sir.",
            display = summary,
            intent = "system/owner_bound_status"
        )
    }

    private fun configurationRequiredResponse(): BrainResponse = integrationRequiredResponse(
        spoken = "The owner identity core and encrypted local memory are active, but no reasoning route is configured. Say configure APIs to add Gemini, Groq, or DeepSeek, Sir.",
        display = "${OwnerIdentityCore.VERSION} // ACTIVE\nCOGNITIVE ROUTE CONFIGURATION REQUIRED\nSAY // CONFIGURE APIS",
        intent = "cortex_config_required"
    )

    private fun integrationRequiredResponse(spoken: String, display: String, intent: String): BrainResponse =
        BrainResponse(
            spoken = spoken,
            display = display,
            intent = intent,
            confidence = 1f,
            mode = BrainMode.ALERT,
            trace = listOf("identity=${OwnerIdentityCore.VERSION}", "configuration_missing"),
            memory = memory.summary(),
            thoughts = listOf("The owner-bound local identity and memory layers remain operational."),
            entities = listOf("identity_core=active"),
            decision = "request_secure_configuration",
            action = BrainAction()
        )

    private fun localResponse(spoken: String, display: String, intent: String): BrainResponse =
        BrainResponse(
            spoken = spoken,
            display = display,
            intent = intent,
            confidence = 1f,
            mode = BrainMode.ONLINE,
            trace = listOf("identity=${OwnerIdentityCore.VERSION}", "android_dialogue_layer", "encrypted_local_state"),
            memory = memory.summary(),
            thoughts = listOf("The owner-bound deterministic layer produced this response locally."),
            entities = listOf("identity_core=${OwnerIdentityCore.VERSION}"),
            decision = intent,
            action = BrainAction()
        )

    private fun payloadAfterPrefix(input: String, lowerPrefix: String): String {
        val index = input.lowercase(Locale.getDefault()).indexOf(lowerPrefix)
        if (index < 0) return ""
        return input.substring(index + lowerPrefix.length).trim().trimEnd('.', '?', '!')
    }

    private fun normalizedDialogue(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun asksAboutCreator(lower: String): Boolean =
        lower.contains("creator") && listOf("who", "what", "tell", "speak", "know", "describe").any(lower::contains)

    private fun isOwnerInquiry(lower: String): Boolean =
        lower.contains("who owns this phone") ||
            lower.contains("whose phone is this") ||
            lower.contains("who does this phone belong to") ||
            lower.contains("who actually owns this phone") ||
            (lower.contains("lost my phone") && lower.contains("owner")) ||
            (lower.contains("found this phone") && lower.contains("owner"))

    companion object {
        private val WAKE_PHRASES = setOf(
            "friday", "hey friday", "hello friday", "hi friday", "wake up friday", "friday wake up", "friday are you there", "are you there friday",
            "jarvis", "hey jarvis", "hello jarvis", "hi jarvis", "wake up jarvis", "jarvis wake up", "jarvis are you there", "are you there jarvis",
            "good morning friday", "good afternoon friday", "good evening friday", "good morning jarvis", "good afternoon jarvis", "good evening jarvis"
        )
        private val CORRECTION_PREFIXES = listOf("correct memory to ", "replace memory with ", "update memory to ", "correction ")
        private val PRIVATE_PROFILE_COMMANDS = setOf(
            "show my private profile", "show my owner profile", "show my contact details", "what is my email", "what are my contact numbers", "show private owner details"
        )
        private val STATUS_COMMANDS = setOf(
            "cortex status", "api status", "cloud status", "provider status", "mesh status", "web status", "research status", "identity core status", "owner core status", "operations status", "system status"
        )
    }
}
