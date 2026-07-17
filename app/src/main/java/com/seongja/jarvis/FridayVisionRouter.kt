package com.seongja.jarvis

import android.content.Context
import java.util.Locale

sealed interface FridayVisionCommand {
    data class Open(
        val question: String,
        val frontCamera: Boolean = false,
        val autoScan: Boolean = true
    ) : FridayVisionCommand

    data object Close : FridayVisionCommand
}

/** Natural owner phrases that must never fall through to generic cloud dialogue. */
object FridayVisionCommandParser {
    private val openEyePhrases = listOf(
        "open your eyes",
        "open your eye",
        "activate x camera",
        "activate x-camera",
        "open x camera",
        "open x-camera",
        "turn on x camera",
        "turn on x-camera",
        "use your camera",
        "use the camera",
        "look through the camera",
        "look at this",
        "look at me",
        "what can you see",
        "what do you see",
        "tell me what you see",
        "scan what you see",
        "scan this",
        "inspect this",
        "inspect my surroundings",
        "look around"
    )

    private val closeEyePhrases = listOf(
        "close your eyes",
        "close your eye",
        "close x camera",
        "close x-camera",
        "turn off x camera",
        "turn off x-camera",
        "stop looking",
        "stop the camera"
    )

    fun parse(raw: String): FridayVisionCommand? {
        val clean = raw.trim()
        val normalized = clean
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (closeEyePhrases.any { normalized.contains(normalize(it)) }) {
            return FridayVisionCommand.Close
        }

        val explicitOpen = openEyePhrases.any { normalized.contains(normalize(it)) }
        val hasVisionNoun = Regex("\\b(x camera|xcamera|camera|eyes?|vision|surroundings|scene)\\b")
            .containsMatchIn(normalized)
        val hasVisionVerb = Regex("\\b(open|activate|use|look|see|scan|inspect|show)\\b")
            .containsMatchIn(normalized)
        if (!explicitOpen && !(hasVisionNoun && hasVisionVerb)) return null

        val frontCamera = listOf(
            "front camera",
            "selfie camera",
            "look at me",
            "see me",
            "my face"
        ).any { normalized.contains(normalize(it)) }

        val question = clean
            .replace(Regex("(?i)^\\s*(hey\\s+)?(friday|jarvis)[, ]*"), "")
            .replace(Regex("(?i)^\\s*(please\\s+)?(open|activate|turn on|use)\\s+(your\\s+)?(eyes?|x[- ]?camera|camera)[, ]*"), "")
            .trim()
            .ifBlank {
                if (frontCamera) {
                    "Tell me what you can see through the front camera."
                } else {
                    "Tell me what you can see, including important objects, visible text, and anything requiring attention."
                }
            }
            .take(1_200)

        return FridayVisionCommand.Open(
            question = question,
            frontCamera = frontCamera,
            autoScan = true
        )
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

class FridayVisionRouter(context: Context) {
    private val appContext = context.applicationContext

    fun intercept(
        input: String,
        memorySummary: String,
        onToken: ((String) -> Unit)? = null
    ): BrainResponse? {
        return when (val command = FridayVisionCommandParser.parse(input)) {
            null -> null
            FridayVisionCommand.Close -> {
                val closed = XCameraRuntime.closeActive()
                val spoken = if (closed) {
                    "Closing my eyes, Sir. The optical feed is offline."
                } else {
                    "My X-Camera is already closed, Sir."
                }
                onToken?.invoke(spoken)
                BrainResponse(
                    spoken = spoken,
                    display = if (closed) {
                        "X-CAMERA // OPTICAL FEED CLOSED\nTEMPORARY FRAME // CLEARED"
                    } else {
                        "X-CAMERA // ALREADY OFFLINE"
                    },
                    intent = "vision/close",
                    confidence = 1f,
                    mode = BrainMode.EXECUTING,
                    trace = listOf(
                        "workspace=x_camera",
                        "camera_visible_to_owner=true",
                        "capture_persistence=disabled"
                    ),
                    memory = memorySummary,
                    thoughts = listOf("The active visual workspace was closed without retaining camera frames."),
                    entities = listOf("sensor=xcamera"),
                    decision = if (closed) "close_xcamera" else "xcamera_already_closed",
                    action = BrainAction()
                )
            }
            is FridayVisionCommand.Open -> {
                JarvisOperationBus.publish("X-CAMERA", "OPENING LIVE OPTICAL WORKSPACE", .08f)
                XCameraActivity.launch(
                    context = appContext,
                    question = command.question,
                    frontCamera = command.frontCamera,
                    autoScan = command.autoScan
                )
                val spoken = "Opening my eyes, Sir. X-Camera is coming online."
                onToken?.invoke(spoken)
                BrainResponse(
                    spoken = spoken,
                    display = buildString {
                        appendLine("X-CAMERA // LIVE OPTICAL WORKSPACE")
                        appendLine("LENS // ${if (command.frontCamera) "FRONT" else "REAR"}")
                        appendLine("ANALYSIS // GEMINI MULTIMODAL VISION")
                        appendLine("FRAME STORAGE // TEMPORARY ONLY")
                        append("QUESTION // ${command.question.take(320)}")
                    },
                    intent = "vision/open",
                    confidence = 1f,
                    mode = BrainMode.EXECUTING,
                    trace = listOf(
                        "workspace=x_camera",
                        "lens=${if (command.frontCamera) "front" else "rear"}",
                        "analysis=gemini_multimodal",
                        "camera_use=visible",
                        "capture_persistence=disabled"
                    ),
                    memory = memorySummary,
                    thoughts = listOf("The camera workspace is explicit, visible, and uses a temporary frame for analysis."),
                    entities = listOf("sensor=xcamera", "privacy=temporary_frame"),
                    decision = "launch_xcamera",
                    action = BrainAction()
                )
            }
        }
    }
}
