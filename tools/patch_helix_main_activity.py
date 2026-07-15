from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


root = Path(__file__).resolve().parents[1]
activity = root / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"

replace_once(activity, "private lateinit var hud: AdvancedCivilizationHudView", "private lateinit var hud: HelixHudView")
replace_once(activity, "hud = AdvancedCivilizationHudView(this)", "hud = HelixHudView(this)")
replace_once(activity, "hud.setOnClickListener {", "hud.setCoreTapListener {")

replace_once(
    activity,
    """        setContentView(hud)\n        if (intent?.getBooleanExtra(JarvisWakeService.EXTRA_WAKE_DETECTED, false) == true) {\n""",
    """        setContentView(hud)\n        hud.setCloudConfigured(brain.isCloudConfigured())\n        if (intent?.getBooleanExtra(JarvisWakeService.EXTRA_WAKE_DETECTED, false) == true) {\n""",
)

replace_once(
    activity,
    """        hud.pushEvent(\"PHASE 10 -> LOCAL DEVICE AGENT\")\n        hud.pushEvent(\"ON-DEVICE SPEECH -> API-KEY-FREE COMMAND FALLBACK\")\n""",
    """        hud.pushEvent(\"PHASE 11 -> HELIX WEBGL COGNITIVE INTERFACE\")\n        hud.pushEvent(\"HELIX WEBGL -> REACT THREE FIBER / BLOOM / NATIVE BRIDGE\")\n        hud.pushEvent(\"ON-DEVICE SPEECH -> API-KEY-FREE COMMAND FALLBACK\")\n""",
)

replace_once(
    activity,
    """        if (::brain.isInitialized) {\n            hud.pushEvent(\n""",
    """        if (::brain.isInitialized) {\n            hud.setCloudConfigured(brain.isCloudConfigured())\n            hud.pushEvent(\n""",
)

replace_once(
    activity,
    """        if (::soundEngine.isInitialized) soundEngine.release()\n        super.onDestroy()\n""",
    """        if (::soundEngine.isInitialized) soundEngine.release()\n        if (::hud.isInitialized) hud.release()\n        super.onDestroy()\n""",
)

(root / ".github/workflows/apply-helix-main.yml").unlink(missing_ok=True)
Path(__file__).unlink(missing_ok=True)
print("Helix MainActivity integration applied.")
