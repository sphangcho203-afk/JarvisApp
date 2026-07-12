package com.seongja.jarvis

import android.content.Context
import java.util.Locale

class JarvisBrain(context: Context) {
    private val appContext = context.applicationContext
    private val memory = MemoryVault(appContext)
    private val router = ActionRouter(appContext)
    private val registryStore = SecureCortexRegistry(appContext)
    private val cortexMesh = CortexMeshClient(registryStore)
    private val webResearch = GroqWebResearchClient(registryStore)

    fun isCloudConfigured(): Boolean = registryStore.load().configuredProfiles().isNotEmpty()

    fun configuredModel(): String {
        val registry = registryStore.load()
        val configured = registry.configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        val researchReady = webResearch.isConfigured()
        return "${configured.size} nodes // $online online // web ${if (researchReady) "ready" else "offline"}"
    }

    fun respond(rawInput: String): BrainResponse {
        val input = rawInput.trim()
        localMemoryCommand(input)?.let { return it }
        localMeshCommand(input)?.let { return it }

        if (!isCloudConfigured()) return configurationRequiredResponse()

        if (WebResearchIntent.shouldUseWeb(input)) {
            return runCatching { webResearchResponse(input) }
                .getOrElse { error ->
                    webResearchFallback(input, error)
                }
        }

        val result = cortexMesh.ask(input, memory.promptContext())
        memory.addHistory("USER: ${input.take(180)}")
        memory.addHistory("JARVIS: ${result.reply.take(180)}")

        return BrainResponse(
            spoken = result.reply,
            display = result.reply,
            intent = "cortex_mesh/response",
            confidence = 0.97f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "android_speech_recognizer",
                "task=${result.task.name.lowercase(Locale.US)}",
                "mesh_route=${result.profileLabel}",
                "provider=${result.provider.displayName}",
                "model=${result.model}",
                "attempts=${result.attempts.joinToString(">")}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "The request was classified as ${result.task.name.lowercase(Locale.US)}.",
                "The cortex mesh selected ${result.profileLabel} using weighted task-fit, reliability, latency, freshness, and stability scoring.",
                if (result.attempts.size > 1) "Automatic failover was used." else "The primary selected node succeeded."
            ),
            entities = listOf(
                "source=cortex_mesh",
                "node=${result.profileLabel}",
                "provider=${result.provider.displayName}",
                "model=${result.model}",
                "status=${result.statusCode}"
            ),
            decision = "mesh_response",
            action = BrainAction()
        )
    }

    private fun webResearchResponse(input: String): BrainResponse {
        val result = webResearch.research(input, memory.promptContext())
        memory.addHistory("USER: ${input.take(180)}")
        memory.addHistory("JARVIS WEB: ${result.answer.take(180)}")

        return BrainResponse(
            spoken = result.spokenSummary,
            display = result.displayText(),
            intent = if (WebResearchIntent.isWorldBrief(input)) {
                "web_research/world_brief"
            } else {
                "web_research/grounded_answer"
            },
            confidence = if (result.sources.size >= 2) 0.96f else 0.86f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "android_speech_recognizer",
                "route=live_web_research",
                "provider=Groq Compound web search",
                "node=${result.profileLabel}",
                "model=${result.model}",
                "queries=${result.searchQueries.size}",
                "sources=${result.sources.size}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "This request required current or externally verified information.",
                "Jarvis used Groq Compound with server-side web search.",
                "The detailed answer and returned sources are displayed on screen."
            ),
            entities = buildList {
                add("source=groq_compound_web_search")
                add("node=${result.profileLabel}")
                add("model=${result.model}")
                add("sources=${result.sources.size}")
                result.sources.take(4).forEachIndexed { index, source ->
                    add("source_${index + 1}=${source.title.take(80)}")
                }
            },
            decision = "grounded_web_research",
            action = BrainAction()
        )
    }

    private fun webResearchFallback(input: String, error: Throwable): BrainResponse {
        val reason = error.message ?: error.javaClass.simpleName
        val fallbackPrompt = buildString {
            appendLine(input)
            appendLine()
            appendLine("Live web search was unavailable: $reason")
            appendLine("Answer only from your existing knowledge. Clearly state that the information may not be current and do not pretend that you searched the web.")
        }
        val result = cortexMesh.ask(fallbackPrompt, memory.promptContext())
        memory.addHistory("USER: ${input.take(180)}")
        memory.addHistory("JARVIS FALLBACK: ${result.reply.take(180)}")

        return BrainResponse(
            spoken = "Live web research was unavailable, Sir. ${result.reply}",
            display = "LIVE WEB RESEARCH UNAVAILABLE\n$reason\n\n${result.reply}",
            intent = "web_research/fallback",
            confidence = 0.58f,
            mode = BrainMode.ALERT,
            trace = listOf(
                "route=web_research",
                "grounding=failed",
                "fallback=cortex_mesh",
                "reason=${reason.take(120)}",
                "node=${result.profileLabel}",
                "model=${result.model}"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "The live web route failed.",
                "A normal cortex answer was returned with an explicit freshness warning."
            ),
            entities = listOf(
                "source=cortex_mesh_fallback",
                "web_grounding=unavailable",
                "node=${result.profileLabel}"
            ),
            decision = "research_fallback",
            action = BrainAction()
        )
    }

    fun execute(action: BrainAction): Boolean = router.execute(action)

    fun memorySnapshot(): String = memory.summary()

    fun contextSnapshot(): List<String> = emptyList()

    private fun localMemoryCommand(input: String): BrainResponse? {
        val lower = input.lowercase(Locale.getDefault()).trim()

        if (lower.startsWith("remember that ")) {
            val fact = input.substringAfter("remember that", "").trim()
            if (fact.isNotBlank()) memory.addFact(fact)
            return localResponse(
                spoken = if (fact.isBlank()) "Tell me what to remember, Sir." else "Stored, Sir.",
                display = if (fact.isBlank()) "MEMORY INPUT REQUIRED" else "MEMORY STORED // ${fact.take(120)}",
                intent = "memory_write"
            )
        }

        if (lower.startsWith("call me ")) {
            val name = input.substringAfter("call me", "").trim()
            if (name.isNotBlank()) memory.setIdentity(name)
            return localResponse(
                spoken = if (name.isBlank()) "Tell me the name to use, Sir." else "Understood. I will call you $name.",
                display = if (name.isBlank()) "IDENTITY INPUT REQUIRED" else "OPERATOR // $name",
                intent = "identity_update"
            )
        }

        if (lower == "who am i" || lower == "who am i?" || lower == "identify me") {
            return localResponse(
                spoken = "You are ${memory.callsign()}, my operator.",
                display = memory.profile(),
                intent = "identity_query"
            )
        }

        if (lower.contains("what do you remember") || lower == "memory status") {
            return localResponse(
                spoken = "Here is the memory currently stored, Sir.",
                display = memory.expanded(),
                intent = "memory_read"
            )
        }

        if (lower == "clear memory" || lower == "forget everything") {
            memory.clearUserFactsKeepIdentity()
            return localResponse(
                spoken = "User memory cleared. Core identity retained, Sir.",
                display = "MEMORY CLEARED // IDENTITY RETAINED",
                intent = "memory_clear"
            )
        }

        return null
    }

    private fun localMeshCommand(input: String): BrainResponse? {
        val lower = input.lowercase(Locale.getDefault()).trim()
        if (lower !in setOf(
                "cortex status",
                "api status",
                "cloud status",
                "provider status",
                "mesh status",
                "web status",
                "research status"
            )
        ) return null

        val registry = registryStore.load()
        val configured = registry.configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        val cooling = configured.count { it.isCoolingDown() }
        val researchReady = webResearch.isConfigured()
        val summary = buildString {
            appendLine("CORTEX MESH // CONFIGURED ${configured.size}/10 // ONLINE $online // COOLDOWN $cooling")
            appendLine("WORLD INTELLIGENCE // ${if (researchReady) "READY" else "NEEDS GROQ NODE"}")
            configured.forEach { appendLine("${it.label} // ${it.provider.displayName} // ${it.healthLabel()}") }
        }.trim()

        return localResponse(
            spoken = "The cortex mesh has ${configured.size} configured nodes, with $online currently online. Live web intelligence is ${if (researchReady) "ready" else "not configured"}, Sir.",
            display = summary,
            intent = "cortex_mesh_status"
        )
    }

    private fun configurationRequiredResponse(): BrainResponse = BrainResponse(
        spoken = "The cortex mesh is not configured, Sir. Say configure APIs to add a Groq node.",
        display = "CORTEX MESH CONFIGURATION REQUIRED\nSay: configure APIs",
        intent = "cortex_config_required",
        confidence = 1f,
        mode = BrainMode.ALERT,
        trace = listOf("cloud_only_mode", "mesh_config_missing", "localhost_disabled"),
        memory = memory.summary(),
        thoughts = listOf(
            "No local server was contacted.",
            "At least one Groq node needs a model ID and encrypted key."
        ),
        entities = listOf("mesh=not_configured", "local_server=disabled"),
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
        trace = listOf("android_control_layer", "encrypted_local_state"),
        memory = memory.summary(),
        thoughts = listOf("This deterministic phone control did not require a cloud request."),
        entities = emptyList(),
        decision = intent,
        action = BrainAction()
    )
}
