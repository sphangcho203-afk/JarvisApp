package com.seongja.jarvis

import android.app.KeyguardManager
import android.content.Context
import java.util.Locale

class JarvisBrain(context: Context) {
    private val appContext = context.applicationContext
    private val memory = MemoryVault(appContext)
    private val router = ActionRouter(appContext)
    private val registryStore = SecureCortexRegistry(appContext)
    private val cortexMesh = CortexMeshClient(registryStore)
    private val webResearch = HybridWebResearchClient(registryStore)
    private val keyguard =
        appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    init {
        JarvisConversationBus.initialize(appContext)
    }

    fun isCloudConfigured(): Boolean =
        registryStore.load().configuredProfiles().isNotEmpty()

    fun configuredModel(): String {
        val registry = registryStore.load()
        val configured = registry.configuredProfiles()
        val online = configured.count {
            it.lastStatusCode in 200..299 && !it.isCoolingDown()
        }
        val researchReady = webResearch.isConfigured()
        return "${configured.size} nodes // $online online // web ${if (researchReady) "ready" else "offline"}"
    }

    /**
     * Deterministic conversational phrases must be intercepted before Android's
     * app launcher. Otherwise greetings and owner questions can be mistaken for
     * installed app names through fuzzy matching.
     */
    fun interceptLocalDialogue(rawInput: String): BrainResponse? {
        val lower = normalizedDialogue(rawInput)

        val wakePhrases = setOf(
            "jarvis",
            "hey jarvis",
            "hello jarvis",
            "hi jarvis",
            "wake up jarvis",
            "jarvis wake up",
            "jarvis are you there",
            "are you there jarvis",
            "good morning jarvis",
            "good afternoon jarvis",
            "good evening jarvis"
        )
        if (lower in wakePhrases) {
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
                spoken = "I am Jarvis, Seongja's private owner-bound intelligence and Android command system. I reason, research, remember approved information, and operate supported phone controls with verification.",
                display = "JARVIS // OWNER-BOUND PERSONAL INTELLIGENCE\nCORTEX REASONING // LIVE RESEARCH // ENCRYPTED MEMORY // VERIFIED ANDROID ACTIONS",
                intent = "dialogue/self_identity"
            )
        }

        val asksCreator = lower.contains("creator") && (
            lower.contains("who") ||
                lower.contains("what") ||
                lower.contains("tell") ||
                lower.contains("speak") ||
                lower.contains("know") ||
                lower.contains("describe")
            )
        if (asksCreator) {
            val operator = memory.callsign()
            return localResponse(
                spoken = "My creator and primary operator is $operator. You designed me as a private Android intelligence system with voice, research, contextual memory, and verified device-control capabilities.",
                display = "CREATOR // $operator\nROLE // PRIMARY OPERATOR AND SYSTEM ARCHITECT\nIDENTITY CORE // ${OwnerIdentityCore.VERSION}\nKNOWLEDGE BOUNDARY // RETRIEVED, USER-PROVIDED, AND SECURELY STORED INFORMATION",
                intent = "dialogue/creator"
            )
        }

        if (lower in setOf("thank you", "thanks", "thanks jarvis", "thank you jarvis")) {
            return localResponse(
                spoken = "Always, Sir.",
                display = "ACKNOWLEDGED // ALWAYS AT YOUR SERVICE",
                intent = "dialogue/thanks"
            )
        }

        if (lower in setOf("how are you", "how are you jarvis", "system check jarvis")) {
            return localResponse(
                spoken = "Operational and ready, Sir.",
                display = "SYSTEM STATE // OPERATIONAL\nIDENTITY // ${OwnerIdentityCore.VERSION}\nCORTEX // ${configuredModel()}\nMEMORY // ${memory.summary()}",
                intent = "dialogue/status"
            )
        }

        return null
    }

    fun respond(rawInput: String): BrainResponse {
        val input = rawInput.trim()
        interceptLocalDialogue(input)?.let { return it }
        localMemoryCommand(input)?.let { return it }
        localMeshCommand(input)?.let { return it }

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

        if (!isCloudConfigured()) return configurationRequiredResponse()

        if (WebResearchIntent.shouldUseWeb(input)) {
            return runCatching { webResearchResponse(input) }
                .getOrElse { error -> webResearchFallback(input, error) }
        }

        val result = cortexMesh.ask(input, memory.promptContext(input))
        return BrainResponse(
            spoken = OwnerIdentityCore.normalizeOperatorReference(result.reply),
            display = OwnerIdentityCore.normalizeOperatorReference(result.reply),
            intent = "cortex_mesh/response",
            confidence = 0.97f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "android_speech_recognizer",
                "identity=${OwnerIdentityCore.VERSION}",
                "task=${result.task.name.lowercase(Locale.US)}",
                "mesh_route=${result.profileLabel}",
                "provider=${result.provider.displayName}",
                "model=${result.model}",
                "attempts=${result.attempts.joinToString(">")}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms",
                "memory=contextual_recall"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "The owner-bound identity core was applied before the editable provider prompt.",
                "The request was classified as ${result.task.name.lowercase(Locale.US)}.",
                "Relevant encrypted conversation memories were retrieved locally before the request.",
                "The cortex mesh selected ${result.profileLabel} using task fit, reliability, latency, freshness, and stability.",
                if (result.attempts.size > 1) "Automatic failover was used." else "The primary selected node succeeded."
            ),
            entities = listOf(
                "identity_core=${OwnerIdentityCore.VERSION}",
                "source=cortex_mesh",
                "node=${result.profileLabel}",
                "provider=${result.provider.displayName}",
                "model=${result.model}",
                "status=${result.statusCode}"
            ),
            decision = "owner_bound_mesh_response",
            action = BrainAction()
        )
    }

    private fun webResearchResponse(input: String): BrainResponse {
        val hybrid = webResearch.research(input, memory.promptContext(input))
        val result = hybrid.research
        val spoken = OwnerIdentityCore.normalizeOperatorReference(result.spokenSummary)
        val display = OwnerIdentityCore.normalizeOperatorReference(result.displayText())

        return BrainResponse(
            spoken = spoken,
            display = display,
            intent = if (WebResearchIntent.isWorldBrief(input)) {
                "web_research/world_brief"
            } else {
                "web_research/grounded_answer"
            },
            confidence = when {
                result.sources.size >= 4 -> 0.98f
                result.sources.size >= 2 -> 0.95f
                else -> 0.84f
            },
            mode = BrainMode.ONLINE,
            trace = listOf(
                "android_speech_recognizer",
                "identity=${OwnerIdentityCore.VERSION}",
                "route=hybrid_live_web_research",
                "provider=${hybrid.provider}",
                "attempts=${hybrid.attempts.joinToString(">")}",
                "node=${result.profileLabel}",
                "model=${result.model}",
                "queries=${result.searchQueries.size}",
                "sources=${result.sources.size}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms",
                "memory=contextual_recall"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "The owner-bound identity core remained active during research synthesis.",
                "This request required current or externally verified information.",
                "Jarvis attempted real web-enabled providers with automatic cross-provider failover.",
                "Private contacts were excluded from cloud context.",
                "The detailed answer and available sources are displayed on screen."
            ),
            entities = buildList {
                add("identity_core=${OwnerIdentityCore.VERSION}")
                add("source=hybrid_web_research")
                add("provider=${hybrid.provider}")
                add("node=${result.profileLabel}")
                add("model=${result.model}")
                add("sources=${result.sources.size}")
                result.sources.take(4).forEachIndexed { index, source ->
                    add("source_${index + 1}=${source.title.take(80)}")
                }
            },
            decision = "owner_bound_grounded_web_research",
            action = BrainAction()
        )
    }

    private fun webResearchFallback(input: String, error: Throwable): BrainResponse {
        val reason = error.message ?: error.javaClass.simpleName
        val fallbackPrompt = buildString {
            appendLine(input)
            appendLine()
            appendLine("Live web routes were unavailable: $reason")
            appendLine(JarvisDirective.SUMMARIZATION)
            appendLine("Answer from existing knowledge only. State clearly that freshness cannot be verified. Do not pretend that web research occurred.")
        }
        val result = cortexMesh.ask(fallbackPrompt, memory.promptContext(input))
        val reply = OwnerIdentityCore.normalizeOperatorReference(result.reply)

        return BrainResponse(
            spoken = "Live information could not be verified. Here is a knowledge-based summary, which may not be current. $reply",
            display = "LIVE WEB VERIFICATION UNAVAILABLE\n${reason.take(360)}\n\nKNOWLEDGE-BASED FALLBACK\n$reply",
            intent = "web_research/fallback",
            confidence = 0.55f,
            mode = BrainMode.ALERT,
            trace = listOf(
                "identity=${OwnerIdentityCore.VERSION}",
                "route=hybrid_web_research",
                "live_routes=failed",
                "fallback=cortex_mesh",
                "reason=${reason.take(160)}",
                "node=${result.profileLabel}",
                "model=${result.model}",
                "memory=contextual_recall"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "The owner-bound identity core remained active.",
                "Every live research route failed.",
                "A normal cortex answer was returned with an explicit freshness warning."
            ),
            entities = listOf(
                "identity_core=${OwnerIdentityCore.VERSION}",
                "source=cortex_mesh_fallback",
                "web_grounding=unavailable",
                "node=${result.profileLabel}"
            ),
            decision = "owner_bound_research_fallback",
            action = BrainAction()
        )
    }

    fun execute(action: BrainAction): Boolean = router.execute(action)

    fun memorySnapshot(): String = memory.summary()

    fun contextSnapshot(): List<String> = memory.history().take(12)

    private fun localMemoryCommand(input: String): BrainResponse? {
        val lower = normalizedDialogue(input)

        val ownerUpdate = memory.captureOwnerDetails(input)
        if (ownerUpdate.changed) {
            val storedItems = buildList {
                if (ownerUpdate.emailStored) add("email")
                if (ownerUpdate.phoneCount > 0) {
                    add("${ownerUpdate.phoneCount} phone number${if (ownerUpdate.phoneCount == 1) "" else "s"}")
                }
                if (ownerUpdate.recoverySet) add("recovery contact")
            }
            return localResponse(
                spoken = "Stored privately, Sir. I added ${storedItems.joinToString(", ")} to the encrypted owner profile.",
                display = "OWNER PROFILE UPDATED // ${storedItems.joinToString(" // ").uppercase(Locale.US)}\nSTORAGE // ENCRYPTED LOCAL DATABASE\nCLOUD SHARING // BLOCKED",
                intent = "memory/owner_profile_update"
            )
        }

        val correctionPrefixes = listOf(
            "correct memory to ",
            "replace memory with ",
            "update memory to ",
            "correction "
        )
        val correctionPrefix = correctionPrefixes.firstOrNull(lower::startsWith)
        if (correctionPrefix != null) {
            val rawIndex = input.lowercase(Locale.getDefault()).indexOf(correctionPrefix)
            val value = if (rawIndex >= 0) {
                input.substring(rawIndex + correctionPrefix.length)
                    .trim()
                    .trimEnd('.', '?', '!')
            } else {
                ""
            }
            val stored = memory.addCorrection(value)
            return localResponse(
                spoken = if (stored.isBlank()) {
                    "State the corrected memory after the command, Sir."
                } else {
                    "Correction confirmed. The new memory now overrides older conflicting information, Sir."
                },
                display = if (stored.isBlank()) {
                    "MEMORY CORRECTION // VALUE REQUIRED"
                } else {
                    "MEMORY CORRECTION STORED\n$stored\nPRIORITY // OVERRIDES OLDER CONFLICTS"
                },
                intent = "memory/correction"
            )
        }

        if (lower.startsWith("remember that ")) {
            val fact = input.substringAfter("remember that", "")
                .trim()
                .trimEnd('.', '?', '!')
            val conflict = memory.detectStableConflict(fact)
            if (conflict != null) {
                return localResponse(
                    spoken = "That conflicts with an existing stable memory, Sir. Say: correct memory to ${conflict.proposed}, to replace the older value.",
                    display = "MEMORY CONFLICT\nCURRENT // ${conflict.previous}\nPROPOSED // ${conflict.proposed}\nAWAITING EXPLICIT CORRECTION",
                    intent = "memory/conflict_confirmation"
                )
            }
            if (fact.isNotBlank()) memory.addFact(fact, category = "explicit")
            return localResponse(
                spoken = if (fact.isBlank()) "Tell me what to remember, Sir." else "Stored, Sir.",
                display = if (fact.isBlank()) {
                    "MEMORY INPUT REQUIRED"
                } else {
                    "MEMORY STORED // ${fact.take(160)}"
                },
                intent = "memory/write"
            )
        }

        if (lower.startsWith("call me ")) {
            val name = input.substringAfter("call me", "")
                .trim()
                .trimEnd('.', '?', '!')
            if (name.isNotBlank()) memory.setIdentity(name)
            return localResponse(
                spoken = if (name.isBlank()) {
                    "Tell me the name to use, Sir."
                } else {
                    "Understood. I will call you $name."
                },
                display = if (name.isBlank()) {
                    "IDENTITY INPUT REQUIRED"
                } else {
                    "OPERATOR // $name"
                },
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

        if (
            lower.contains("what do you know about me") ||
            lower.contains("what have you learned about me") ||
            lower.contains("tell me what you remember about me")
        ) {
            return localResponse(
                spoken = "I have assembled your stored profile, corrections, and relevant learned preferences, Sir. Sensitive contacts remain protected.",
                display = memory.expanded(includeSensitive = false),
                intent = "memory/operator_summary"
            )
        }

        if (
            lower in setOf(
                "show my private profile",
                "show my owner profile",
                "show my contact details",
                "what is my email",
                "what are my contact numbers",
                "show private owner details"
            )
        ) {
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
                spoken = "User facts, contacts, and conversation memory cleared. The owner identity core remains intact, Sir.",
                display = "MEMORY CLEARED // OWNER IDENTITY CORE RETAINED",
                intent = "memory/clear"
            )
        }

        return null
    }

    private fun localMeshCommand(input: String): BrainResponse? {
        val lower = normalizedDialogue(input)
        if (lower !in setOf(
                "cortex status",
                "api status",
                "cloud status",
                "provider status",
                "mesh status",
                "web status",
                "research status",
                "identity core status",
                "owner core status"
            )
        ) return null

        val registry = registryStore.load()
        val configured = registry.configuredProfiles()
        val online = configured.count {
            it.lastStatusCode in 200..299 && !it.isCoolingDown()
        }
        val cooling = configured.count { it.isCoolingDown() }
        val researchReady = webResearch.isConfigured()
        val searchProviders = webResearch.configuredSearchProviders()
        val summary = buildString {
            appendLine(OwnerIdentityCore.statusLine(memory.summary()))
            appendLine("CORTEX MESH // CONFIGURED ${configured.size}/10 // ONLINE $online // COOLDOWN $cooling")
            appendLine("WORLD INTELLIGENCE // ${if (researchReady) "HYBRID READY" else "NEEDS A RESEARCH ROUTE"}")
            appendLine("SEARCH GRID // ${searchProviders.joinToString(" + ") { it.displayName }.ifBlank { "NOT CONFIGURED" }}")
            appendLine("PERSISTENT MEMORY // ${memory.summary()}")
            configured.forEach {
                appendLine("${it.label} // ${it.provider.displayName} // ${it.healthLabel()}")
            }
        }.trim()

        return localResponse(
            spoken = "The owner identity core is active. The cortex mesh has ${configured.size} configured nodes, with $online currently online. Research and contextual memory are ${if (researchReady) "ready" else "partially configured"}, Sir.",
            display = summary,
            intent = "system/owner_bound_status"
        )
    }

    private fun configurationRequiredResponse(): BrainResponse = BrainResponse(
        spoken = "The owner identity core and encrypted local memory are active, but the cortex mesh is not configured. Say configure APIs to add a Gemini or Groq node, Sir.",
        display = "${OwnerIdentityCore.VERSION} // ACTIVE\nCORTEX MESH CONFIGURATION REQUIRED\nSAY // CONFIGURE APIS",
        intent = "cortex_config_required",
        confidence = 1f,
        mode = BrainMode.ALERT,
        trace = listOf(
            "identity=${OwnerIdentityCore.VERSION}",
            "mesh_config_missing",
            "local_owner_memory=active"
        ),
        memory = memory.summary(),
        thoughts = listOf(
            "The owner-bound local identity and memory layers remain operational.",
            "At least one Gemini or Groq node needs a model ID and encrypted key for cloud reasoning."
        ),
        entities = listOf(
            "identity_core=active",
            "mesh=not_configured",
            "local_memory=active"
        ),
        decision = "request_cortex_configuration",
        action = BrainAction()
    )

    private fun localResponse(
        spoken: String,
        display: String,
        intent: String
    ): BrainResponse = BrainResponse(
        spoken = spoken,
        display = display,
        intent = intent,
        confidence = 1f,
        mode = BrainMode.ONLINE,
        trace = listOf(
            "identity=${OwnerIdentityCore.VERSION}",
            "android_dialogue_layer",
            "encrypted_local_state"
        ),
        memory = memory.summary(),
        thoughts = listOf(
            "The owner-bound deterministic layer produced this response locally."
        ),
        entities = listOf("identity_core=${OwnerIdentityCore.VERSION}"),
        decision = intent,
        action = BrainAction()
    )

    private fun normalizedDialogue(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isOwnerInquiry(lower: String): Boolean =
        lower.contains("who owns this phone") ||
            lower.contains("whose phone is this") ||
            lower.contains("who does this phone belong to") ||
            lower.contains("who actually owns this phone") ||
            (lower.contains("lost my phone") && lower.contains("owner")) ||
            (lower.contains("found this phone") && lower.contains("owner"))
}
