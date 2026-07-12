package com.seongja.jarvis

import android.content.Context
import java.util.Locale

class JarvisBrain(context: Context) {
    private val appContext = context.applicationContext
    private val memory = MemoryVault(appContext)
    private val router = ActionRouter(appContext)
    private val registryStore = SecureCortexRegistry(appContext)
    private val cortexMesh = CortexMeshClient(registryStore)

    fun isCloudConfigured(): Boolean = registryStore.load().configuredProfiles().isNotEmpty()

    fun configuredModel(): String {
        val registry = registryStore.load()
        val configured = registry.configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        return "${configured.size} nodes // $online online"
    }

    fun respond(rawInput: String): BrainResponse {
        val input = rawInput.trim()
        localMemoryCommand(input)?.let { return it }
        localMeshCommand(input)?.let { return it }

        if (!isCloudConfigured()) return configurationRequiredResponse()

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
                "mesh status"
            )
        ) return null

        val registry = registryStore.load()
        val configured = registry.configuredProfiles()
        val online = configured.count { it.lastStatusCode in 200..299 && !it.isCoolingDown() }
        val cooling = configured.count { it.isCoolingDown() }
        val summary = buildString {
            appendLine("CORTEX MESH // CONFIGURED ${configured.size}/10 // ONLINE $online // COOLDOWN $cooling")
            configured.forEach { appendLine("${it.label} // ${it.provider.displayName} // ${it.healthLabel()}") }
        }.trim()

        return localResponse(
            spoken = "The cortex mesh has ${configured.size} configured nodes, with $online currently online, Sir.",
            display = summary,
            intent = "cortex_mesh_status"
        )
    }

    private fun configurationRequiredResponse(): BrainResponse = BrainResponse(
        spoken = "The cortex mesh is not configured, Sir. Say configure APIs to open the ten-node setup.",
        display = "CORTEX MESH CONFIGURATION REQUIRED\nSay: configure APIs",
        intent = "cortex_config_required",
        confidence = 1f,
        mode = BrainMode.ALERT,
        trace = listOf("cloud_only_mode", "mesh_config_missing", "localhost_disabled"),
        memory = memory.summary(),
        thoughts = listOf(
            "No local server was contacted.",
            "At least one Gemini or Groq node needs a model ID and encrypted key."
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
