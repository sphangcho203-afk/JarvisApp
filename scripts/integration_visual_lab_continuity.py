from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
text = PATH.read_text(encoding="utf-8")

old_resume = "        consumeXCameraResult()\n        if (hasMicPermission() && !brainBusy.get()) {\n"
new_resume = (
    "        consumeXCameraResult()\n"
    "        consumeImageGenerationResult()\n"
    "        if (hasMicPermission() && !brainBusy.get()) {\n"
)
if new_resume not in text:
    if old_resume not in text:
        raise RuntimeError("MainActivity visual-continuity resume anchor missing")
    text = text.replace(old_resume, new_resume, 1)

method = '''    private fun consumeImageGenerationResult() {
        val result = ImageGenerationRuntime.consume() ?: return
        val spoken = if (result.isError) {
            "Visual Lab failed: ${result.message}"
        } else {
            result.message
        }
        val response = BrainResponse(
            spoken = spoken,
            display = buildString {
                appendLine(if (result.isError) "VISUAL LAB // FAILED" else "VISUAL LAB // GENERATION VERIFIED")
                appendLine("PROMPT // ${result.prompt.take(320)}")
                appendLine("MODEL // ${result.model}")
                appendLine("TIME // ${result.elapsedMs}MS")
                appendLine("STORAGE // ${if (result.saved) "PICTURES/FRIDAY" else "PREVIEW ONLY"}")
                append(result.message)
            },
            intent = if (result.isError) "image/error" else "image/result",
            confidence = if (result.isError) 0f else .98f,
            mode = if (result.isError) BrainMode.ALERT else BrainMode.ONLINE,
            trace = listOf(
                "workspace=visual_lab",
                "model=${result.model}",
                "latency=${result.elapsedMs}ms",
                "saved=${result.saved}"
            ),
            memory = brain.memorySnapshot(),
            thoughts = listOf(
                if (result.isError) {
                    "The native image route reported a real provider or decoding failure."
                } else {
                    "The generated visual was decoded successfully before being reported as ready."
                }
            ),
            entities = listOf("workspace=visual_lab"),
            decision = if (result.isError) "report_visual_lab_error" else "return_generated_visual_result",
            action = BrainAction()
        )
        hud.submitBrainResponse(response)
        hud.pushEvent(if (result.isError) "VISUAL LAB -> DEGRADED" else "VISUAL LAB -> RESULT VERIFIED")
        mainHandler.postDelayed({
            if (resumed && !brainBusy.get()) speak(spoken)
        }, 280L)
    }

'''
anchor = "    private fun handlePartialSpeech(text: String) {\n"
if method not in text:
    if anchor not in text:
        raise RuntimeError("MainActivity Visual Lab result-method anchor missing")
    text = text.replace(anchor, method + anchor, 1)

PATH.write_text(text, encoding="utf-8")
