from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


root = Path(__file__).resolve().parents[1]
wake = root / "app/src/main/java/com/seongja/jarvis/JarvisWakeService.kt"
agent = root / "app/src/main/java/com/jarvis/core/device/LocalAppAgent.kt"
activity = root / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"

replace_once(
    wake,
    "import android.speech.SpeechRecognizer\n",
    "import android.speech.SpeechRecognizer\nimport com.jarvis.core.device.JarvisAppAutomationService\nimport com.jarvis.core.device.ScreenContextBridge\n",
)
replace_once(
    wake,
    """        lastWakeAtMs = now\n        pauseRecognition()\n\n        val intent = Intent(this, MainActivity::class.java).apply {\n""",
    """        lastWakeAtMs = now\n        pauseRecognition()\n        ScreenContextBridge.capture(JarvisAppAutomationService.snapshot())\n\n        val intent = Intent(this, MainActivity::class.java).apply {\n""",
)

replace_once(
    agent,
    """        val snapshot = JarvisAppAutomationService.snapshot()\n            ?: return result(\n                actionId = \"screen_context\",\n                target = \"active window\",\n                status = DeviceActionStatus.FAILED,\n                spoken = \"Android did not expose a readable active window.\",\n                startedAtMs = startedAtMs,\n                trace = listOf(\"local_app_agent\", \"active_window_unavailable\")\n            )\n\n        return result(\n""",
    """        val liveSnapshot = JarvisAppAutomationService.snapshot()\n        val snapshot = when {\n            liveSnapshot != null && liveSnapshot.packageName != appContext.packageName -> liveSnapshot\n            else -> ScreenContextBridge.recent()\n        } ?: return result(\n            actionId = \"screen_context\",\n            target = \"active window\",\n            status = DeviceActionStatus.FAILED,\n            spoken = \"Android did not expose a readable app screen. Summon Jarvis while the target app is visible, then ask again.\",\n            startedAtMs = startedAtMs,\n            trace = listOf(\n                \"local_app_agent\",\n                \"active_window_unavailable\",\n                \"pre_summon_snapshot_unavailable\"\n            )\n        )\n\n        return result(\n""",
)
replace_once(
    agent,
    """                \"snapshot_not_persisted\"\n""",
    """                if (liveSnapshot?.packageName == snapshot.packageName) {\n                    \"live_snapshot\"\n                } else {\n                    \"pre_summon_snapshot\"\n                },\n                \"snapshot_not_persisted\"\n""",
)

replace_once(
    activity,
    """        hud.pushEvent(\"GOOGLE SPEECH RECOGNIZER -> REMOVED\")\n        hud.pushEvent(\"ANDROID TEXT TO SPEECH -> REMOVED\")\n        hud.pushEvent(\"MICROPHONE -> RAW PCM16 // NO GOOGLE CHIME\")\n        hud.pushEvent(\"VOICE OUTPUT -> OPENAI ONYX / ELEVENLABS PCM\")\n        hud.pushEvent(\"GEMINI NODES -> 6 // GROQ NODES -> 4\")\n        hud.pushEvent(\"SAY CONFIGURE APIS -> SECURE MESH SETUP\")\n""",
    """        hud.pushEvent(\"ON-DEVICE SPEECH -> API-KEY-FREE COMMAND FALLBACK\")\n        hud.pushEvent(\"ANDROID TEXT TO SPEECH -> REMOVED\")\n        hud.pushEvent(\"MICROPHONE -> RAW PCM / ON-DEVICE HANDOFF\")\n        hud.pushEvent(\"PREMIUM VOICE OUTPUT -> OPTIONAL LOCAL RUNTIME\")\n        hud.pushEvent(\"CLOUD CORTEX -> OPTIONAL\")\n        hud.pushEvent(\"APP AUTOMATION -> GMAIL / WHATSAPP / SCREEN CONTEXT\")\n""",
)

(root / ".github/workflows/apply-screen-context-handoff.yml").unlink(missing_ok=True)
Path(__file__).unlink(missing_ok=True)
print("Pre-summon screen context handoff applied.")
