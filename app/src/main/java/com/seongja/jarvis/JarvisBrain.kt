package com.seongja.jarvis

import android.content.Context
import java.util.Locale

class JarvisBrain(context: Context) {
    private val appContext = context.applicationContext
    private val memory = MemoryVault(appContext)
    private val router = ActionRouter(appContext)
    private val configStore = SecureCloudConfigStore(appContext)
    private val cloud = CloudBrainClient(configStore)

    fun isCloudConfigured(): Boolean = configStore.load().isConfigured()

    fun configuredModel(): String = configStore.load().model.ifBlank { "not configured" }

    fun respond(rawInput: String): BrainResponse {
        val input = rawInput.trim()
        localMemoryCommand(input)?.let { return it }

        if (!isCloudConfigured()) {
            return configurationRequiredResponse()
        }

        val result = cloud.ask(input, memory.promptContext())
        memory.addHistory("USER: ${input.take(180)}")
        memory.addHistory("JARVIS: ${result.reply.take(180)}")

        return BrainResponse(
            spoken = result.reply,
            display = result.reply,
            intent = "cloud_cortex/response",
            confidence = 0.96f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "android_speech_recognizer",
                "https_cloud_api",
                "model=${result.model}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "Inference source: configured cloud model.",
                "Localhost inference: disabled.",
                "Termux pairing: removed from the command path."
            ),
            entities = listOf(
                "source=cloud_api",
                "model=${result.model}",
                "status=${result.statusCode}"
            ),
            decision = "cloud_response",
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

    private fun configurationRequiredResponse(): BrainResponse = BrainResponse(
        spoken = "The cloud cortex is not configured, Sir. Say configure API to open secure setup.",
        display = "CLOUD API CONFIGURATION REQUIRED\nSay: configure API",
        intent = "cloud_config_required",
        confidence = 1f,
        mode = BrainMode.ALERT,
        trace = listOf("cloud_only_mode", "api_config_missing", "localhost_disabled"),
        memory = memory.summary(),
        thoughts = listOf(
            "No local server was contacted.",
            "No cloud request can run until an HTTPS endpoint and model are configured."
        ),
        entities = listOf("cloud=not_configured", "local_server=disabled"),
        decision = "request_cloud_configuration",
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
        trace = listOf("android_control_layer", "encrypted_local_memory"),
        memory = memory.summary(),
        thoughts = listOf("This deterministic phone control did not require an AI server."),
        entities = emptyList(),
        decision = intent,
        action = BrainAction()
    )
}
