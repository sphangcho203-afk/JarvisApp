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
    private val bridge = LocalBridgeClient(context)

    fun respond(rawInput: String): BrainResponse {
        val input = rawInput.trim()
        val fallback = localEngine.think(input)

        if (fallback.intent == IntentType.MEMORY_CLEAR.name.lowercase(Locale.US) ||
            input.lowercase(Locale.US).contains("clear memory")
        ) {
            return fallback
        }

        extractPairCode(input)?.let { code ->
            val result = bridge.pair(code)
            return pairingResponse(result)
        }

        if (!bridge.isPaired()) {
            return pairingRequiredResponse()
        }

        val decision = bridge.ask(input)
            ?: return if (bridge.lastError == "pairing_required") {
                pairingRequiredResponse()
            } else {
                fallback.copy(
                    trace = fallback.trace + listOf(
                        "localhost_bridge_unavailable",
                        bridge.lastError
                    ),
                    thoughts = fallback.thoughts +
                        "The Termux V4.1 bridge was unavailable; local Android fallback used.",
                    decision = "fallback_local_engine"
                )
            }

        memory.addHistory("USER: ${input.take(180)}")
        memory.addHistory("JARVIS: ${decision.reply.take(180)}")

        val confidence = when {
            decision.intent.equals("UNKNOWN", ignoreCase = true) -> 0.50f
            decision.executionOk -> 0.98f
            else -> 0.82f
        }

        return BrainResponse(
            spoken = decision.reply,
            display = decision.reply,
            intent = "termux_bridge/${decision.intent.lowercase(Locale.US)}",
            confidence = confidence,
            mode = if (decision.executionOk) BrainMode.ONLINE else BrainMode.ALERT,
            trace = listOf(
                "android_speech_recognizer",
                "localhost:8765",
                "token_authenticated",
                "termux_python_brain_v4_1",
                "allowlisted_android_actions"
            ),
            memory = "Termux memory vault active • ${memory.summary()}",
            thoughts = listOf(
                "Inference source: local Python command brain.",
                "External network: not used.",
                "Bridge latency: ${decision.elapsedMs} ms.",
                "Execution result: ${if (decision.executionOk) "success" else "not completed"}."
            ),
            entities = listOf(
                "intent=${decision.intent}",
                "arguments=${decision.arguments.ifBlank { "none" }}",
                "source=termux_v4_1"
            ),
            decision = "local_bridge/${decision.intent.lowercase(Locale.US)}",
            action = BrainAction()
        )
    }

    fun execute(action: BrainAction): Boolean = router.execute(action)

    fun memorySnapshot(): String = memory.summary()

    fun contextSnapshot(): List<String> = localEngine.contextSnapshot()

    private fun pairingRequiredResponse(): BrainResponse = BrainResponse(
        spoken = "Secure pairing is required, Sir. In Termux, run jarvis v4 pair code, then say pair code followed by the six digits.",
        display = "PAIRING REQUIRED\nRun: jarvis-v4-pair-code\nThen say: Pair code 1 2 3 4 5 6",
        intent = "bridge_pairing_required",
        confidence = 1f,
        mode = BrainMode.SECURITY,
        trace = listOf("localhost:8765", "pairing_required"),
        memory = memory.summary(),
        thoughts = listOf("The bridge token has not been paired with this app installation."),
        entities = listOf("pairing=required"),
        decision = "request_pairing",
        action = BrainAction()
    )

    private fun pairingResponse(result: BridgePairResult): BrainResponse = BrainResponse(
        spoken = result.message,
        display = result.message,
        intent = if (result.ok) "bridge_paired" else "bridge_pairing_failed",
        confidence = if (result.ok) 1f else 0f,
        mode = if (result.ok) BrainMode.ONLINE else BrainMode.ALERT,
        trace = listOf("localhost:8765", "one_time_pairing", if (result.ok) "paired" else "rejected"),
        memory = memory.summary(),
        thoughts = listOf(
            if (result.ok) "The random bridge token is now stored in Android private preferences."
            else "The one-time code was rejected or expired."
        ),
        entities = listOf("pairing=${if (result.ok) "complete" else "failed"}"),
        decision = if (result.ok) "pair_bridge" else "pair_bridge_failed",
        action = BrainAction()
    )

    private fun extractPairCode(input: String): String? {
        val lower = input.lowercase(Locale.US)
        val marker = lower.indexOf("pair code")
        if (marker < 0) return null

        val tail = lower.substring(marker + "pair code".length)
        val digitSequence = tail.filter(Char::isDigit)
        if (digitSequence.length >= 6) return digitSequence.take(6)

        val numberWords = mapOf(
            "zero" to "0",
            "oh" to "0",
            "one" to "1",
            "two" to "2",
            "three" to "3",
            "four" to "4",
            "five" to "5",
            "six" to "6",
            "seven" to "7",
            "eight" to "8",
            "nine" to "9"
        )

        val parsed = tail
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                when {
                    token.length == 1 && token[0].isDigit() -> token
                    else -> numberWords[token]
                }
            }
            .joinToString("")

        return parsed.takeIf { it.length >= 6 }?.take(6)
    }
}
