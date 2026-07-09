package com.seongja.jarvis

import android.os.BatteryManager
import android.os.Build
import android.content.Context

class DecisionEngine(private val context: Context) {
    private val knowledge = KnowledgeKernel()

    fun decide(
        input: String,
        signal: IntentSignal,
        entities: EntityBundle,
        memory: MemoryVault,
        contextEngine: ContextEngine
    ): BrainResponse {
        val thoughts = mutableListOf(
            "Input normalized and routed through local brain.",
            "Intent ${signal.type.name} selected from pattern matrix.",
            "Confidence ${(signal.confidence * 100).toInt()}%."
        )
        val entityLines = mutableListOf<String>()
        entities.targetApp?.let { entityLines += "target_app=$it" }
        entities.identityName?.let { entityLines += "identity=$it" }
        entities.memoryPayload?.let { entityLines += "memory=${it.take(48)}" }
        entities.searchQuery?.let { entityLines += "search=${it.take(48)}" }
        entities.modeTarget?.let { entityLines += "mode=${it.name}" }
        if (entityLines.isEmpty()) entityLines += "subject=${entities.rawSubject.orEmpty().take(48)}"

        fun response(
            spoken: String,
            display: String,
            mode: BrainMode,
            decision: String,
            action: BrainAction = BrainAction(),
            confidence: Float = signal.confidence
        ): BrainResponse {
            val trace = signal.trace + listOf("entity_extraction", "context_update", "decision_engine", decision)
            memory.addHistory("${signal.type.name}: ${entities.rawSubject ?: input}")
            return BrainResponse(
                spoken = spoken,
                display = display,
                intent = signal.type.name.lowercase(),
                confidence = confidence.coerceIn(0f, 1f),
                mode = mode,
                trace = trace,
                memory = memory.summary(),
                thoughts = thoughts,
                entities = entityLines,
                decision = decision,
                action = action
            )
        }

        return when (signal.type) {
            IntentType.IDENTITY_QUERY -> {
                thoughts += "Identity answered from local memory vault."
                response(
                    spoken = "You are ${memory.callsign()}, my operator and creator.",
                    display = memory.expanded(),
                    mode = BrainMode.THINKING,
                    decision = "identity_from_memory",
                    confidence = 0.99f
                )
            }

            IntentType.MEMORY_WRITE -> {
                val name = entities.identityName
                val payload = entities.memoryPayload
                if (!name.isNullOrBlank()) {
                    memory.setIdentity(name)
                    thoughts += "Identity entity stored locally."
                    response(
                        spoken = "Identity updated. I recognize you as $name.",
                        display = "LOCAL MEMORY WRITE\nIdentity marker: $name\n\n${memory.summary()}",
                        mode = BrainMode.LEARNING,
                        decision = "write_identity_memory",
                        confidence = 0.99f
                    )
                } else if (!payload.isNullOrBlank()) {
                    memory.addFact(payload)
                    thoughts += "Memory payload committed to vault."
                    response(
                        spoken = "Memory stored.",
                        display = "LOCAL MEMORY WRITE\nStored: $payload\n\n${memory.summary()}",
                        mode = BrainMode.LEARNING,
                        decision = "write_fact_memory",
                        confidence = 0.97f
                    )
                } else {
                    response(
                        spoken = "I detected a memory command, but no clear payload.",
                        display = "MEMORY WRITE FAILED\nNo usable memory payload found.",
                        mode = BrainMode.SECURITY,
                        decision = "memory_payload_missing",
                        confidence = 0.42f
                    )
                }
            }

            IntentType.MEMORY_READ -> {
                thoughts += "Memory vault recall requested."
                response(
                    spoken = "Memory vault online.",
                    display = memory.expanded(),
                    mode = BrainMode.THINKING,
                    decision = "read_memory_vault",
                    confidence = 0.97f
                )
            }

            IntentType.MEMORY_CLEAR -> {
                memory.clearUserFactsKeepIdentity()
                thoughts += "Non-core memory cleared; operator identity retained."
                response(
                    spoken = "Memory vault cleared. Core identity retained.",
                    display = "SECURITY ACTION\nMemory cleared. Identity retained: ${memory.callsign()}.",
                    mode = BrainMode.SECURITY,
                    decision = "clear_memory_keep_identity",
                    confidence = 0.98f
                )
            }

            IntentType.APP_OPEN -> {
                val target = entities.targetApp ?: entities.rawSubject?.removePrefix("open")?.trim().orEmpty().ifBlank { "Settings" }
                thoughts += "Action router prepared app launch."
                val action = if (target.equals("Settings", true)) {
                    BrainAction(ActionType.OPEN_SETTINGS, target, "open_settings")
                } else {
                    BrainAction(ActionType.OPEN_APP, target, "open_app")
                }
                response(
                    spoken = "Opening $target.",
                    display = "ACTION ROUTER\nTarget app: $target\nExecutor: Android package manager\nContinuity: ${contextEngine.continuityHint()}",
                    mode = BrainMode.EXECUTING,
                    decision = "execute_app_open",
                    action = action,
                    confidence = 0.88f
                )
            }

            IntentType.WEB_SEARCH -> {
                val q = entities.searchQuery ?: entities.rawSubject ?: input
                thoughts += "Search action routed to browser."
                response(
                    spoken = "Searching for $q.",
                    display = "SEARCH ROUTER\nQuery: $q\nExecutor: Browser intent",
                    mode = BrainMode.EXECUTING,
                    decision = "execute_web_search",
                    action = BrainAction(ActionType.WEB_SEARCH, q, "web_search"),
                    confidence = 0.9f
                )
            }

            IntentType.MODE_CHANGE -> {
                val target = entities.modeTarget ?: BrainMode.ONLINE
                thoughts += "Interface and cognition mode updated."
                response(
                    spoken = "${target.name.lowercase().replaceFirstChar { it.uppercase() }} mode engaged.",
                    display = "MODE SHIFT\nNew mode: ${target.name}\nBrain state: synchronized\nHUD palette: recalibrated",
                    mode = target,
                    decision = "mode_shift_${target.name.lowercase()}",
                    confidence = 0.94f
                )
            }

            IntentType.DIAGNOSTICS -> {
                val battery = batteryPercent()
                val display = buildString {
                    appendLine("DEVICE DIAGNOSTICS")
                    appendLine("Battery: $battery%")
                    appendLine("Model: ${Build.MODEL}")
                    appendLine("SDK: ${Build.VERSION.SDK_INT}")
                    appendLine("Brain cycles: ${contextEngine.cycles}")
                    appendLine("Memory: ${memory.summary()}")
                }.trim()
                thoughts += "Device status collected locally."
                response(
                    spoken = "Diagnostics online. Battery is at $battery percent.",
                    display = display,
                    mode = BrainMode.TACTICAL,
                    decision = "device_diagnostics",
                    confidence = 0.94f
                )
            }

            IntentType.SELF_EXPLAIN -> {
                thoughts += "Brain introspection requested."
                response(
                    spoken = "My local brain is a layered cognition engine, not just command matching.",
                    display = knowledge.explainBrain(),
                    mode = BrainMode.THINKING,
                    decision = "explain_local_brain",
                    confidence = 0.94f
                )
            }

            IntentType.KNOWLEDGE_QUERY -> {
                thoughts += "Offline knowledge kernel generated a limited answer."
                val answer = knowledge.answerOffline(input, memory)
                response(
                    spoken = answer.take(230),
                    display = answer + "\n\nNote: live world knowledge requires Phase 6 cloud cortex.",
                    mode = BrainMode.THINKING,
                    decision = "offline_knowledge_kernel",
                    confidence = 0.72f
                )
            }

            IntentType.CONVERSATION -> {
                thoughts += "General language handled as operator conversation."
                response(
                    spoken = "Understood. Local brain has parsed the meaning and stored the context.",
                    display = "CONVERSATION PARSED\nSubject: ${entities.rawSubject}\n\nI can remember, classify, route actions, answer identity, report diagnostics, and explain my brain locally. Cloud cortex comes next.",
                    mode = BrainMode.THINKING,
                    decision = "conversation_context_store",
                    confidence = 0.66f
                )
            }

            IntentType.UNKNOWN -> {
                response(
                    spoken = "Input unclear. Rephrase the command.",
                    display = "LOW CONFIDENCE\nNo stable intent detected.",
                    mode = BrainMode.SECURITY,
                    decision = "fallback_unclear",
                    confidence = 0.28f
                )
            }
        }
    }

    private fun batteryPercent(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: 0
    }
}
