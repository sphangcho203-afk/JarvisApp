from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
VOICE_LOOP = ROOT / "app/src/main/java/com/seongja/jarvis/VoiceLoop.kt"


def patch(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Voice Lab {label} integration anchor missing: {old[:220]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


patch(
    MAIN,
    '        hud.pushEvent("LOCAL ANDROID SPEECH OUTPUT -> DISABLED")\n',
    '        hud.pushEvent("VOICE LAB -> INSTALLED ENGINES / AUDITION / SAVED PROFILE")\n',
    "boot diagnostic",
)

patch(
    MAIN,
    '        enterImmersiveMode()\n'
    '        if (::brain.isInitialized) {\n',
    '        enterImmersiveMode()\n'
    '        if (::voiceLoop.isInitialized) voiceLoop.refreshVoiceProfile()\n'
    '        if (::brain.isInitialized) {\n',
    "resume profile refresh",
)

patch(
    MAIN,
    '            "providers", "provider", "mesh", "apis" -> ProviderMeshActivity.launch(this)\n'
    '            "cortex", "legacyapis" -> startActivity(Intent(this, CloudConfigActivity::class.java))\n',
    '            "providers", "provider", "mesh", "apis" -> ProviderMeshActivity.launch(this)\n'
    '            "voice", "voicelab", "speech", "tts" -> FridayVoiceLabActivity.launch(this)\n'
    '            "cortex", "legacyapis" -> startActivity(Intent(this, CloudConfigActivity::class.java))\n',
    "workspace route",
)

patch(
    MAIN,
    '        soundEngine.processing()\n\n'
    '        CountdownCommandParser.parse(clean)?.let { timerCommand ->\n',
    '        soundEngine.processing()\n\n'
    '        if (FridayVoiceLabCommand.matches(clean)) {\n'
    '            hud.pushEvent("VOICE LAB -> OPEN")\n'
    '            cancelProcessingTimeout()\n'
    '            hud.setProcessing(false)\n'
    '            brainBusy.set(false)\n'
    '            FridayVoiceLabActivity.launch(this)\n'
    '            return\n'
    '        }\n\n'
    '        CountdownCommandParser.parse(clean)?.let { timerCommand ->\n',
    "voice command",
)

patch(
    MAIN,
    '''                    when {
                        voiceLoop.isCartesiaConfigured() -> {
                            hud.pushEvent("VOICE -> CARTESIA SONIC-3 // GEMMA EN-GB")
                            speak("Systems online. Cartesia Sonic voice is active, Boss.")
                        }
                        voiceLoop.isPremiumBackendReady() ->
                            speak("Systems online. Premium streaming voice is active, Boss.")
                        else -> {
                            hud.pushEvent("VOICE OUTPUT -> CARTESIA REQUIRED // LOCAL TTS DISABLED")
                        }
                    }
''',
    '''                    when {
                        voiceLoop.isCartesiaConfigured() -> {
                            hud.pushEvent("VOICE -> CARTESIA SONIC-3 // GEMMA EN-GB")
                            speak("Systems online. Cartesia Sonic voice is active, Boss.")
                        }
                        voiceLoop.isPremiumBackendReady() ->
                            speak("Systems online. Premium streaming voice is active, Boss.")
                        voiceLoop.isLocalVoiceReady() -> {
                            hud.pushEvent("VOICE -> ${voiceLoop.localVoiceLabel()}")
                            speak("Systems online. FRIDAY Voice Lab profile is active, Boss.")
                        }
                        else -> {
                            hud.pushEvent("VOICE OUTPUT -> NO SPEECH ENGINE READY")
                        }
                    }
''',
    "online announcement",
)

patch(
    MAIN,
    '''            when {
                voiceLoop.isCartesiaConfigured() -> "VOICE -> CARTESIA SONIC STREAM"
                voiceLoop.isPremiumBackendReady() -> "VOICE -> STREAM REQUEST"
                else -> "VOICE OUTPUT -> LOCAL TTS DISABLED"
            }
''',
    '''            when {
                voiceLoop.isCartesiaConfigured() -> "VOICE -> CARTESIA SONIC STREAM"
                voiceLoop.isPremiumBackendReady() -> "VOICE -> STREAM REQUEST"
                voiceLoop.isLocalVoiceReady() -> "VOICE -> ${voiceLoop.localVoiceLabel()}"
                else -> "VOICE OUTPUT -> NO SPEECH ENGINE READY"
            }
''',
    "speech diagnostic",
)

patch(
    VOICE_LOOP,
    '    fun isCartesiaConfigured(): Boolean = cartesiaVoice.isConfigured()\n\n'
    '    fun isOnDeviceInputAvailable(): Boolean = onDeviceInput.isAvailable()\n',
    '    fun isCartesiaConfigured(): Boolean = cartesiaVoice.isConfigured()\n\n'
    '    fun isLocalVoiceReady(): Boolean = localVoice.isReady()\n\n'
    '    fun localVoiceLabel(): String = localVoice.label()\n\n'
    '    fun refreshVoiceProfile() = localVoice.reloadProfile()\n\n'
    '    fun isOnDeviceInputAvailable(): Boolean = onDeviceInput.isAvailable()\n',
    "local voice status API",
)

patch(
    VOICE_LOOP,
    '''                if (shouldFallback) {
                    onDiagnostic("VOICE OUTPUT -> CARTESIA FAILED // LOCAL TTS DISABLED")
                    localVoice.speak(fallback)
                } else {
''',
    '''                if (shouldFallback) {
                    onDiagnostic("VOICE OUTPUT -> CARTESIA FAILED // LOCAL VOICE LAB FALLBACK")
                    if (!localVoice.speak(fallback)) {
                        val completion = outputCompletion
                        outputCompletion = null
                        completion?.invoke()
                    }
                } else {
''',
    "Cartesia local fallback",
)

patch(
    VOICE_LOOP,
    '        onDiagnostic("VOICE OUTPUT -> LOCAL TTS DISABLED")\n'
    '        if (!localVoice.speak(clean)) {\n',
    '        onDiagnostic("VOICE OUTPUT -> LOCAL VOICE LAB")\n'
    '        if (!localVoice.speak(clean)) {\n',
    "local speech path",
)

print("FRIDAY Voice Lab integrated into MainActivity and VoiceLoop")
