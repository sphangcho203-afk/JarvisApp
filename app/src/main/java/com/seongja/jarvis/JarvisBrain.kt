package com.seongja.jarvis

import android.content.Context
import java.util.Locale

class JarvisBrain(context: Context) {
    private val memory = MemoryVault(context)
    private val contextEngine = ContextEngine()
    private val router = ActionRouter(context)
    private val localEngine = LocalBrainEngine(
        memory = memory,
        contextEngine = contextEngine,
        detector = IntentDetector(),
        extractor = EntityExtractor(),
        decisionEngine = DecisionEngine(context)
    )
    private val llm = OfflineLlmClient()

    fun respond(rawInput: String): BrainResponse {
        val input = rawInput.trim()
        val fallback = localEngine.think(input)

        if (fallback.intent == IntentType.MEMORY_CLEAR.name.lowercase(Locale.US) ||
            input.lowercase(Locale.US).contains("clear memory")
        ) {
            return fallback
        }

        val decision = llm.ask(input, memory.promptContext())
            ?: return fallback.copy(
                trace = fallback.trace + listOf("localhost_llm_unavailable", llm.lastError),
                thoughts = fallback.thoughts + "Local Qwen server unavailable; Phase 5 fallback used.",
                decision = "fallback_local_engine"
            )

        val mode = parseMode(decision.mode)
        val modelAction = actionFromDecision(decision)
        val action = if (modelAction.type != ActionType.NONE) {
            modelAction
        } else if (fallback.action.type != ActionType.NONE && fallback.confidence >= 0.85f) {
            fallback.action
        } else {
            BrainAction()
        }

        when (decision.tool) {
            "set_identity" -> if (decision.argument.isNotBlank()) memory.setIdentity(decision.argument)
            "remember" -> {
                val fact = decision.memoryFact.ifBlank { decision.argument }
                if (fact.isNotBlank()) memory.addFact(fact)
            }
        }

        memory.addHistory("USER: ${input.take(180)}")
        memory.addHistory("JARVIS: ${decision.reply.take(180)}")

        return BrainResponse(
            spoken = decision.reply,
            display = decision.reply,
            intent = "offline_llm/${decision.tool}",
            confidence = decision.confidence,
            mode = mode,
            trace = listOf(
                "voice_or_text_input",
                "localhost:8080",
                "qwen2.5_3b_q4_k_m",
                "schema_constrained_json",
                "safe_tool_allowlist"
            ),
            memory = memory.summary(),
            thoughts = listOf(
                "Inference source: on-device llama-server.",
                "External network: not used.",
                "Selected tool: ${decision.tool}.",
                "Action validation: allowlist enforced."
            ),
            entities = listOf(
                "tool=${decision.tool}",
                "argument=${decision.argument.ifBlank { "none" }}",
                "source=offline_llm"
            ),
            decision = "local_llm_json/${decision.tool}",
            action = action
        )
    }

    fun execute(action: BrainAction): Boolean = router.execute(action)

    fun memorySnapshot(): String = memory.summary()

    fun contextSnapshot(): List<String> = localEngine.contextSnapshot()

    private fun actionFromDecision(decision: OfflineLlmDecision): BrainAction = when (decision.tool) {
        "open_app" -> BrainAction(ActionType.OPEN_APP, decision.argument, "open app")
        "open_settings" -> BrainAction(ActionType.OPEN_SETTINGS, "", "open settings")
        else -> BrainAction()
    }

    private fun parseMode(raw: String): BrainMode = runCatching {
        BrainMode.valueOf(raw.uppercase(Locale.US))
    }.getOrDefault(BrainMode.ONLINE)
}
