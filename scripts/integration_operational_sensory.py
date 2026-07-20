from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
HUD = ROOT / "app/src/main/java/com/seongja/jarvis/HelixHudView.kt"


def patch(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if label == "design voice command" and "FridayDesignCommandParser.parse(clean)" in text:
        return
    if old not in text:
        raise RuntimeError(f"Operational sensory {label} anchor missing: {old[:240]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


patch(
    MAIN,
    "    private val deviceCommandRouter by lazy {\n"
    "        DeviceCommandRouter(applicationContext, ::handleDeferredDeviceResult)\n"
    "    }\n",
    "    private val deviceCommandRouter by lazy {\n"
    "        DeviceCommandRouter(applicationContext, ::handleDeferredDeviceResult)\n"
    "    }\n"
    "    private val designModeStore by lazy { FridayDesignModeStore(applicationContext) }\n",
    "design store field",
)

patch(
    MAIN,
    "        hud.setCloudConfigured(brain.isCloudConfigured())\n",
    "        hud.setCloudConfigured(brain.isCloudConfigured())\n"
    "        hud.setDesignMode(designModeStore.load().id)\n",
    "initial design dispatch",
)

patch(
    MAIN,
    '        hud.pushEvent("PHASE 11 -> HELIX WEBGL COGNITIVE INTERFACE")\n',
    '        hud.pushEvent("PHASE 14 -> OPERATIONAL SENSORY CORE")\n'
    '        hud.pushEvent("SCENE DIRECTOR -> COMMAND-ADAPTIVE GEOMETRY")\n'
    '        hud.pushEvent("NATIVE SONIC ENGINE -> PROCEDURAL PCM ONLINE")\n',
    "sensory boot diagnostics",
)

patch(
    MAIN,
    '            "voice", "voicelab", "speech", "tts" -> FridayVoiceLabActivity.launch(this)\n'
    '            "cortex", "legacyapis" -> startActivity(Intent(this, CloudConfigActivity::class.java))\n',
    '            "voice", "voicelab", "speech", "tts" -> FridayVoiceLabActivity.launch(this)\n'
    '            "design", "theme", "matrix" -> handleDesignCommand(FridayDesignCommand.Cycle)\n'
    '            "cortex", "legacyapis" -> startActivity(Intent(this, CloudConfigActivity::class.java))\n',
    "design workspace route",
)

patch(
    MAIN,
    '        if (FridayVoiceLabCommand.matches(clean)) {\n'
    '            hud.pushEvent("VOICE LAB -> OPEN")\n'
    '            cancelProcessingTimeout()\n'
    '            hud.setProcessing(false)\n'
    '            brainBusy.set(false)\n'
    '            FridayVoiceLabActivity.launch(this)\n'
    '            return\n'
    '        }\n\n'
    '        CountdownCommandParser.parse(clean)?.let { timerCommand ->\n',
    '        if (FridayVoiceLabCommand.matches(clean)) {\n'
    '            hud.pushEvent("VOICE LAB -> OPEN")\n'
    '            cancelProcessingTimeout()\n'
    '            hud.setProcessing(false)\n'
    '            brainBusy.set(false)\n'
    '            FridayVoiceLabActivity.launch(this)\n'
    '            return\n'
    '        }\n\n'
    '        FridayDesignCommandParser.parse(clean)?.let { designCommand ->\n'
    '            handleDesignCommand(designCommand)\n'
    '            return\n'
    '        }\n\n'
    '        CountdownCommandParser.parse(clean)?.let { timerCommand ->\n',
    "design voice command",
)

patch(
    MAIN,
    "    private fun handleCountdownCommand(command: CountdownCommand) {\n",
    '''    private fun handleDesignCommand(command: FridayDesignCommand) {
        val current = designModeStore.load()
        val next = when (command) {
            FridayDesignCommand.Cycle -> current.next()
            is FridayDesignCommand.Activate -> command.mode
        }
        designModeStore.save(next)
        hud.setDesignMode(next.id)
        hud.pushEvent("DESIGN MATRIX -> ${next.id}")
        soundEngine.themeShift()
        finishLocalCommand(
            spoken = "${next.spokenLabel.replaceFirstChar { it.uppercase() }} activated, Sir.",
            display = "DESIGN MATRIX // ${next.id} INTERFACE ACTIVE",
            intent = "design/${next.id.lowercase(Locale.US)}",
            mode = BrainMode.EXECUTING,
            trace = listOf(
                "voice_design_parser",
                "persistent_local_preference",
                "native_sonic_theme_shift",
                "helix_scene_recomposition"
            ),
            playSuccess = false
        )
    }

    private fun handleCountdownCommand(command: CountdownCommand) {
''',
    "design handler",
)

patch(
    HUD,
    '    private var currentMode = "IDLE"\n'
    '    private var cloudConfigured = false\n',
    '    private var currentMode = "IDLE"\n'
    '    private var designMode = "STANDARD"\n'
    '    private var cloudConfigured = false\n',
    "HUD design state",
)

patch(
    HUD,
    "    fun setCloudConfigured(configured: Boolean) {\n"
    "        cloudConfigured = configured\n"
    "        dispatchTelemetry()\n"
    "    }\n\n"
    "    fun setVoiceState(state: VoiceLoop.State) {\n",
    '''    fun setCloudConfigured(configured: Boolean) {
        cloudConfigured = configured
        dispatchTelemetry()
    }

    fun setDesignMode(mode: String) {
        val normalized = mode
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z]"), "")
            .take(16)
            .ifBlank { "STANDARD" }
        designMode = normalized
        dispatch(JSONObject().put("type", "design").put("design", normalized))
    }

    fun setVoiceState(state: VoiceLoop.State) {
''',
    "HUD design API",
)

patch(
    HUD,
    '                dispatch(JSONObject().put("type", "ready"))\n'
    '                flushPending()\n'
    '                dispatchTelemetry()\n',
    '                dispatch(JSONObject().put("type", "ready"))\n'
    '                dispatch(JSONObject().put("type", "design").put("design", designMode))\n'
    '                flushPending()\n'
    '                dispatchTelemetry()\n',
    "bridge design replay",
)

print("FRIDAY operational sensory runtime integrated into Android")
