from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, value: str) -> None:
    Path(path).write_text(value, encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    if old not in source:
        raise RuntimeError(f"Patch target missing: {label}")
    return source.replace(old, new, 1)


# Fix deterministic Boss/Sir selection precedence.
path = "app/src/main/java/com/seongja/jarvis/WeatherIntelligence.kt"
text = read(path)
text = text.replace(
    'if (input.hashCode() and 1 == 0) "Boss" else "Sir"',
    'if ((input.hashCode() and 1) == 0) "Boss" else "Sir"',
)
write(path, text)


# Wire weather answers, refreshes, and proactive advisories into the Android voice loop.
path = "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
text = read(path)
text = replace_once(
    text,
    """    private var ttsResumeWatchdog: Runnable? = null
    private var lastTtsFinishedAt = 0L
""",
    """    private var ttsResumeWatchdog: Runnable? = null
    private var lastTtsFinishedAt = 0L
    private val weatherListener: (WeatherSnapshot) -> Unit = { snapshot ->
        runOnUiThread {
            if (::hud.isInitialized) {
                hud.pushEvent("WEATHER -> ${snapshot.compactLabel()}")
            }
            if (resumed && !brainBusy.get() && ::voiceLoop.isInitialized) {
                WeatherRuntime.consumeAdvisory(snapshot)?.let { advisory ->
                    mainHandler.postDelayed({
                        if (resumed && !brainBusy.get()) speak(advisory)
                    }, 550L)
                }
            }
        }
    }
""",
    "MainActivity weather listener",
)
text = replace_once(
    text,
    """        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = ::handlePartialSpeech,
            onRms = hud::setVoiceAmplitude,
            onState = ::handleVoiceState,
            onDiagnostic = { message -> runOnUiThread { hud.pushEvent(message) } }
        )

        setContentView(hud)
""",
    """        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = ::handlePartialSpeech,
            onRms = hud::setVoiceAmplitude,
            onState = ::handleVoiceState,
            onDiagnostic = { message -> runOnUiThread { hud.pushEvent(message) } }
        )
        WeatherRuntime.addListener(weatherListener)
        WeatherRuntime.refresh()

        setContentView(hud)
""",
    "MainActivity weather initialization",
)
text = replace_once(
    text,
    """        if (hasMicPermission() && !brainBusy.get()) {
            voiceLoop.resume()
            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)
        }
""",
    """        WeatherRuntime.refresh()
        if (hasMicPermission() && !brainBusy.get()) {
            voiceLoop.resume()
            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)
        }
""",
    "MainActivity resume weather refresh",
)
text = replace_once(
    text,
    """        CountdownCommandParser.parse(clean)?.let { timerCommand ->
            handleCountdownCommand(timerCommand)
            return
        }

        if (isCloudSetupCommand(clean)) {
""",
    """        CountdownCommandParser.parse(clean)?.let { timerCommand ->
            handleCountdownCommand(timerCommand)
            return
        }

        WeatherRuntime.answer(clean)?.let { weather ->
            finishLocalCommand(
                spoken = weather.spoken,
                display = weather.display,
                intent = weather.intent,
                mode = BrainMode.ONLINE,
                trace = listOf(
                    "weatherapi_verified_cache",
                    "coordinates=encrypted",
                    "forecast=hourly+current+alerts"
                )
            )
            return
        }

        if (isCloudSetupCommand(clean)) {
""",
    "MainActivity local weather answers",
)
text = replace_once(
    text,
    """    override fun onDestroy() {
        cancelProcessingTimeout()
""",
    """    override fun onDestroy() {
        WeatherRuntime.removeListener(weatherListener)
        cancelProcessingTimeout()
""",
    "MainActivity weather cleanup",
)
text = text.replace(
    '        hud.pushEvent("SYSTEM CONTROL -> QUICK SETTINGS EXECUTOR")\n',
    '        hud.pushEvent("SYSTEM CONTROL -> QUICK SETTINGS EXECUTOR")\n        hud.pushEvent("WEATHER CORE -> WEATHERAPI / FORECAST / ALERTS")\n',
    1,
)
write(path, text)


# Keep Weather setup reachable after the one-time flow.
path = "app/src/main/java/com/seongja/jarvis/PermissionCenterActivity.kt"
text = read(path)
if 'text = "OPEN WEATHER SETUP"' not in text:
    marker = '''        root.addView(Button(this).apply {
            text = "REFRESH ANDROID STATUS"
'''
    weather_button = '''        root.addView(Button(this).apply {
            text = "OPEN WEATHER SETUP"
            setOnClickListener {
                startActivity(Intent(this@PermissionCenterActivity, WeatherSetupActivity::class.java))
            }
        }, matchWidth(bottom = 8))

'''
    if marker not in text:
        raise RuntimeError("Patch target missing: PermissionCenter weather button")
    text = text.replace(marker, weather_button + marker, 1)
write(path, text)


# Version alignment.
path = "app/build.gradle.kts"
text = read(path)
text = re.sub(r"versionCode = \d+", "versionCode = 30", text, count=1)
text = re.sub(r'versionName = "[^"]+"', 'versionName = "0.9.20-weather-intelligence"', text, count=1)
write(path, text)

path = "helix-ui/package.json"
text = read(path)
text = re.sub(r'"version": "[^"]+"', '"version": "0.9.20"', text, count=1)
write(path, text)

path = "app/src/main/java/com/seongja/jarvis/HelixHudView.kt"
text = read(path).replace("JarvisHelix/0.9.17", "JarvisHelix/0.9.20")
write(path, text)

required = {
    "app/src/main/java/com/seongja/jarvis/MainActivity.kt": [
        "WeatherRuntime.addListener(weatherListener)",
        "WeatherRuntime.answer(clean)",
        "WeatherRuntime.consumeAdvisory",
    ],
    "app/src/main/java/com/seongja/jarvis/PermissionCenterActivity.kt": [
        "OPEN WEATHER SETUP",
    ],
    "app/build.gradle.kts": [
        "versionCode = 30",
        'versionName = "0.9.20-weather-intelligence"',
    ],
}
for file, markers in required.items():
    source = read(file)
    for marker in markers:
        if marker not in source:
            raise RuntimeError(f"Required marker missing: {file}: {marker}")

print("Weather runtime patch applied successfully.")
