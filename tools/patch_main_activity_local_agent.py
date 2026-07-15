from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


root = Path(__file__).resolve().parents[1]
activity = root / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"

replace_once(
    activity,
    """        setContentView(hud)\n        hud.pushEvent(\"PHASE 9.3 -> STREAMING VOICE FABRIC\")\n""",
    """        setContentView(hud)\n        if (intent?.getBooleanExtra(JarvisWakeService.EXTRA_WAKE_DETECTED, false) == true) {\n            hud.pushEvent(\"WAKE PHRASE -> DETECTED // LOCAL SUMMON\")\n        }\n        hud.pushEvent(\"PHASE 10 -> LOCAL DEVICE AGENT\")\n""",
)

replace_once(
    activity,
    """    override fun onResume() {\n        super.onResume()\n        resumed = true\n        enterImmersiveMode()\n""",
    """    override fun onNewIntent(intent: Intent?) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n        if (intent?.getBooleanExtra(JarvisWakeService.EXTRA_WAKE_DETECTED, false) == true) {\n            announcedOnline = false\n            if (::hud.isInitialized) hud.pushEvent(\"WAKE PHRASE -> DETECTED // LOCAL SUMMON\")\n        }\n    }\n\n    override fun onResume() {\n        super.onResume()\n        resumed = true\n        JarvisWakeService.pause(this)\n        enterImmersiveMode()\n""",
)

replace_once(
    activity,
    """                } else {\n                    \"CORTEX MESH -> CONFIGURATION REQUIRED\"\n                }\n""",
    """                } else {\n                    \"LOCAL AGENT -> READY // CLOUD CORTEX OPTIONAL\"\n                }\n""",
)

replace_once(
    activity,
    """    override fun onPause() {\n        resumed = false\n        if (::voiceLoop.isInitialized) voiceLoop.stop()\n        super.onPause()\n    }\n""",
    """    override fun onPause() {\n        resumed = false\n        if (::voiceLoop.isInitialized) voiceLoop.stop()\n        if (JarvisWakeService.isEnabled(this)) JarvisWakeService.resume(this)\n        super.onPause()\n    }\n""",
)

replace_once(
    activity,
    """            mainHandler.postDelayed({\n                if (resumed && !brainBusy.get()) {\n                    speak(\"Systems online. Premium streaming voice is active, Sir.\")\n                }\n            }, 320L)\n""",
    """            mainHandler.postDelayed({\n                if (resumed && !brainBusy.get()) {\n                    if (voiceLoop.isPremiumBackendReady()) {\n                        speak(\"Systems online. Premium streaming voice is active, Sir.\")\n                    } else {\n                        hud.pushEvent(\"VOICE -> ON-DEVICE COMMAND MODE // API KEY FREE\")\n                        hud.setTranscript(\"Local command mode online\")\n                    }\n                }\n            }, 320L)\n""",
)

replace_once(
    activity,
    """        hud.pushEvent(\n            if (voiceLoop.isBackendReady()) {\n                \"VOICE -> STREAM REQUEST\"\n            } else {\n                \"VOICE -> WAITING FOR LOCAL STREAMING RUNTIME\"\n            }\n        )\n""",
    """        hud.pushEvent(\n            if (voiceLoop.isPremiumBackendReady()) {\n                \"VOICE -> STREAM REQUEST\"\n            } else {\n                \"VOICE -> TEXT RESPONSE // LOCAL INPUT MODE\"\n            }\n        )\n""",
)

replace_once(
    activity,
    """        ttsResumeWatchdog = Runnable {\n            if (resumed && hasMicPermission() && !brainBusy.get()) {\n                hud.pushEvent(\"VOICE -> STREAM WATCHDOG RELEASE\")\n                lastTtsFinishedAt = SystemClock.elapsedRealtime()\n                voiceLoop.stopSpeaking()\n                voiceLoop.resumeAfterTts(700L)\n            }\n        }.also { mainHandler.postDelayed(it, 45_000L) }\n""",
    """        if (voiceLoop.isPremiumBackendReady()) {\n            ttsResumeWatchdog = Runnable {\n                if (resumed && hasMicPermission() && !brainBusy.get()) {\n                    hud.pushEvent(\"VOICE -> STREAM WATCHDOG RELEASE\")\n                    lastTtsFinishedAt = SystemClock.elapsedRealtime()\n                    voiceLoop.stopSpeaking()\n                    voiceLoop.resumeAfterTts(700L)\n                }\n            }.also { mainHandler.postDelayed(it, 45_000L) }\n        }\n""",
)

(root / ".github/workflows/apply-main-activity-local-agent.yml").unlink(missing_ok=True)
Path(__file__).unlink(missing_ok=True)
print("MainActivity local-agent handoff applied.")
