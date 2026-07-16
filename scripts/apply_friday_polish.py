from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path: str, replacements: list[tuple[str, str]]) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    original = text
    for old, new in replacements:
        if old not in text:
            raise RuntimeError(f"Expected pattern missing in {path}: {old[:120]!r}")
        text = text.replace(old, new)
    if text == original:
        raise RuntimeError(f"No changes made to {path}")
    target.write_text(text, encoding="utf-8")


patch(
    "app/src/main/java/com/seongja/jarvis/MainActivity.kt",
    [
        (
            'hud.pushEvent("ANDROID TEXT TO SPEECH -> REMOVED")',
            'hud.pushEvent("LOCAL ANDROID SPEECH OUTPUT -> DISABLED")',
        ),
        (
            '''                        else -> {
                            hud.pushEvent("VOICE -> LOCAL JARVIS OUTPUT // ANDROID TTS")
                            speak("Systems online. Your local voice is active, Boss.")
                        }''',
            '''                        else -> {
                            hud.pushEvent("VOICE OUTPUT -> CARTESIA REQUIRED // LOCAL TTS DISABLED")
                        }''',
        ),
        (
            'else -> "VOICE -> LOCAL JARVIS SYNTHESIS"',
            'else -> "VOICE OUTPUT -> LOCAL TTS DISABLED"',
        ),
        (
            'if (normalized in setOf("sir", "yes sir", "okay sir", "ok sir")) return true',
            'if (normalized in setOf("boss", "yes boss", "okay boss", "ok boss")) return true',
        ),
    ],
)

patch(
    "app/src/main/java/com/seongja/jarvis/HelixHudView.kt",
    [
        (
            'userAgentString = "$userAgentString JarvisHelix/0.9.20"',
            'userAgentString = "$userAgentString FridayHelix/0.9.21"',
        ),
        (
            '''    fun submitBrainResponse(response: BrainResponse) {
        dispatch(''',
            '''    fun submitBrainResponse(response: BrainResponse) {
        val spoken = JarvisResponseSanitizer.spoken(response.spoken)
        val display = JarvisResponseSanitizer.clean(response.display)
        dispatch(''',
        ),
        (
            '.put("spoken", response.spoken.take(MAX_TEXT_CHARS))',
            '.put("spoken", spoken.take(MAX_TEXT_CHARS))',
        ),
        (
            '.put("display", response.display.take(MAX_TEXT_CHARS))',
            '.put("display", display.take(MAX_TEXT_CHARS))',
        ),
        (
            '<div class="eyebrow">JARVIS // HELIX</div>',
            '<div class="eyebrow">F.R.I.D.A.Y. // HELIX</div>',
        ),
        (
            'The visual document could not initialize. The native Android command core remains available.',
            'The visual document could not initialize. FRIDAY\'s native command core remains available.',
        ),
    ],
)

patch(
    "app/src/main/java/com/seongja/jarvis/VoiceLoop.kt",
    [
        (
            'onDiagnostic("VOICE INPUT -> SWITCHING TO ANDROID SPEECH")',
            'onDiagnostic("VOICE INPUT -> SWITCHING TO ON-DEVICE SPEECH")',
        ),
        (
            'onDiagnostic("ANDROID SPEECH -> ${speechErrorName(code)} ($code)")',
            'onDiagnostic("ON-DEVICE SPEECH -> ${speechErrorName(code)} ($code)")',
        ),
        (
            'onDiagnostic("ANDROID SPEECH ERROR -> IGNORED WHILE PAUSED")',
            'onDiagnostic("ON-DEVICE SPEECH ERROR -> IGNORED WHILE PAUSED")',
        ),
        (
            'onDiagnostic("VOICE OUTPUT -> ANDROID LOCAL FALLBACK")',
            'onDiagnostic("VOICE OUTPUT -> CARTESIA FAILED // LOCAL TTS DISABLED")',
        ),
        (
            'onDiagnostic("VOICE OUTPUT -> LOCAL JARVIS SYNTHESIS")',
            'onDiagnostic("VOICE OUTPUT -> LOCAL TTS DISABLED")',
        ),
        (
            'onDiagnostic("ANDROID SPEECH -> START FAILED // RETRYING")',
            'onDiagnostic("ON-DEVICE SPEECH -> START FAILED // RETRYING")',
        ),
        (
            'onDiagnostic("VOICE INPUT -> NO ANDROID RECOGNIZER // RETRYING")',
            'onDiagnostic("VOICE INPUT -> NO LOCAL RECOGNIZER // RETRYING")',
        ),
    ],
)

for path in [
    "app/src/main/java/com/seongja/jarvis/CloudConfigActivity.kt",
    "app/src/main/java/com/seongja/jarvis/WeatherSetupActivity.kt",
    "app/src/main/java/com/seongja/jarvis/PermissionCenterActivity.kt",
    "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt",
]:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    original = text
    text = text.replace("JARVIS //", "F.R.I.D.A.Y. //")
    text = text.replace("RETURN TO JARVIS", "RETURN TO FRIDAY")
    text = text.replace("Jarvis will", "FRIDAY will")
    text = text.replace("Jarvis searches", "FRIDAY searches")
    text = text.replace("while Jarvis is active", "while FRIDAY is active")
    text = text.replace("I am Jarvis", "I am FRIDAY")
    text = text.replace("Jarvis, your", "FRIDAY, your")
    text = text.replace("Jarvis is", "FRIDAY is")
    text = text.replace("Jarvis must", "FRIDAY must")
    text = text.replace("Allows Jarvis", "Allows FRIDAY")
    if text == original:
        raise RuntimeError(f"Expected FRIDAY identity replacements missing in {path}")
    target.write_text(text, encoding="utf-8")

patch(
    "app/src/main/java/com/seongja/jarvis/PermissionCenterActivity.kt",
    [
        (
            "After API setup is complete, the HELIX setup button becomes ANDROID and reopens this screen. Android may revoke permissions or accessibility services at any time, so FRIDAY must continue verifying state before every sensitive action.",
            "Initial onboarding proceeds automatically through cortex, weather, and permissions. Android may revoke permissions or accessibility services at any time, so FRIDAY verifies state before every sensitive action.",
        ),
    ],
)

patch(
    "app/src/main/java/com/seongja/jarvis/WeatherApiClient.kt",
    [
        (
            '.header("User-Agent", "Jarvis-Android/0.9.20")',
            '.header("User-Agent", "Friday-Android/0.9.21")',
        ),
    ],
)

print("FRIDAY identity and audio polish patches applied.")
