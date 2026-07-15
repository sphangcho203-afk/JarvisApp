from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, value: str) -> None:
    Path(path).write_text(value, encoding="utf-8")


def regex_once(source: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, lambda _: replacement, source, count=1, flags=re.S | re.M)
    if count != 1:
        raise RuntimeError(f"Patch target not found exactly once: {label}")
    return updated


voice_path = "app/src/main/java/com/seongja/jarvis/VoiceLoop.kt"
voice = read(voice_path)

if "private lateinit var localVoice: LocalJarvisVoice" not in voice:
    voice = voice.replace(
        "    private lateinit var onDeviceInput: OnDeviceSpeechInput\n",
        "    private lateinit var onDeviceInput: OnDeviceSpeechInput\n"
        "    private lateinit var localVoice: LocalJarvisVoice\n",
        1,
    )

if "private val localVoiceListener" not in voice:
    listener = '''    private val localVoiceListener = object : LocalJarvisVoice.Listener {
        override fun onReady(label: String) {
            handler.post { onDiagnostic("VOICE OUTPUT -> $label READY") }
        }

        override fun onStarted(label: String) {
            handler.post {
                onDiagnostic("VOICE OUTPUT -> $label SPEAKING")
                onState(State.READY)
            }
        }

        override fun onCompleted() {
            handler.post {
                onDiagnostic("VOICE OUTPUT -> LOCAL COMPLETE")
                val completion = outputCompletion
                outputCompletion = null
                completion?.invoke()
            }
        }

        override fun onError(message: String) {
            handler.post {
                onDiagnostic("VOICE OUTPUT -> LOCAL ERROR // $message")
                val completion = outputCompletion
                outputCompletion = null
                completion?.invoke()
            }
        }
    }

'''
    voice = voice.replace("    init {\n", listener + "    init {\n", 1)

if "localVoice = LocalJarvisVoice" not in voice:
    voice = voice.replace(
        "        onDeviceInput = OnDeviceSpeechInput(activity, onDeviceListener)\n",
        "        onDeviceInput = OnDeviceSpeechInput(activity, onDeviceListener)\n"
        "        localVoice = LocalJarvisVoice(activity.applicationContext, localVoiceListener)\n",
        1,
    )

if "fun isVoiceOutputReady" not in voice:
    voice = voice.replace(
        "    fun isPremiumBackendReady(): Boolean = gateway.isReady()\n",
        "    fun isPremiumBackendReady(): Boolean = gateway.isReady()\n\n"
        "    fun isVoiceOutputReady(): Boolean = gateway.isReady() || localVoice.isReady()\n",
        1,
    )

if "VOICE OUTPUT -> LOCAL JARVIS SYNTHESIS" not in voice:
    voice = regex_once(
        voice,
        r"^    fun speak\(text: String, onComplete: \(\) -> Unit\) \{.*?^    \}\n(?=\n    fun stopSpeaking)",
        '''    fun speak(text: String, onComplete: () -> Unit) {
        if (destroyed) return
        val clean = JarvisResponseSanitizer.spoken(text)
        if (clean.isBlank()) {
            handler.post(onComplete)
            return
        }

        pauseForTts()
        outputCompletion = onComplete
        if (gateway.isReady()) {
            onDiagnostic("VOICE OUTPUT -> PREMIUM PCM")
            gateway.speak(clean)
            return
        }

        onDiagnostic("VOICE OUTPUT -> LOCAL JARVIS SYNTHESIS")
        if (!localVoice.speak(clean)) {
            val completion = outputCompletion
            outputCompletion = null
            handler.post { completion?.invoke() }
        }
    }
''',
        "VoiceLoop.speak",
    )

if "localVoice.stop()" not in voice:
    voice = voice.replace(
        "    fun stopSpeaking() {\n        outputCompletion = null\n        gateway.stopSpeech()\n    }",
        "    fun stopSpeaking() {\n        outputCompletion = null\n        localVoice.stop()\n        gateway.stopSpeech()\n    }",
        1,
    )

if "localVoice.destroy()" not in voice:
    voice = voice.replace(
        "        onDeviceInput.destroy()\n        gateway.destroy()",
        "        onDeviceInput.destroy()\n        localVoice.destroy()\n        gateway.destroy()",
        1,
    )
write(voice_path, voice)


main_path = "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
main = read(main_path)
if "Your local Jarvis voice is active" not in main:
    main = regex_once(
        main,
        r"\s*if \(voiceLoop\.isPremiumBackendReady\(\)\) \{\s*speak\(\"Systems online\. Premium streaming voice is active, Sir\.\"\)\s*\} else \{\s*hud\.pushEvent\(\"VOICE -> ON-DEVICE COMMAND MODE // API KEY FREE\"\)\s*hud\.setTranscript\(\"Local command mode online\"\)\s*\}",
        '''
                    if (voiceLoop.isPremiumBackendReady()) {
                        speak("Systems online. Premium streaming voice is active, Sir.")
                    } else {
                        hud.pushEvent("VOICE -> LOCAL JARVIS OUTPUT // ANDROID TTS")
                        speak("Systems online. Your local Jarvis voice is active, Sir.")
                    }''',
        "MainActivity online voice announcement",
    )
main = main.replace(
    '"VOICE -> TEXT RESPONSE // LOCAL INPUT MODE"',
    '"VOICE -> LOCAL JARVIS SYNTHESIS"',
)
if "val plain = JarvisResponseSanitizer.spoken(text)" not in main:
    main = main.replace(
        "        val plain = text\n",
        "        val plain = JarvisResponseSanitizer.spoken(text)\n",
        1,
    )
write(main_path, main)


brain_path = "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt"
brain = read(brain_path)
brain = brain.replace(
    'spoken = "I am Jarvis, Seongja\'s private owner-bound intelligence and Android command system. I reason, research, remember approved information, and operate supported phone controls with verification.",',
    'spoken = "I am Jarvis, your private personal intelligence, Sir. You built me to know your world, protect your privacy, remember what matters, research what you ask, and act across this phone. I am here for you, not the public.",',
)
brain = brain.replace(
    'display = "JARVIS // OWNER-BOUND PERSONAL INTELLIGENCE\\nCORTEX REASONING // LIVE RESEARCH // ENCRYPTED MEMORY // VERIFIED ANDROID ACTIONS",',
    'display = "JARVIS // SEONGJA\'S PRIVATE INTELLIGENCE\\nBUILT BY YOU // FOR YOU\\nVOICE // MEMORY // RESEARCH // VERIFIED ANDROID ACTIONS",',
)
brain = brain.replace(
    'spoken = "User facts, contacts, and conversation memory cleared. The owner identity core remains intact, Sir.",',
    'spoken = "Stored personal facts, contacts, and conversation memory cleared. My identity and connection to you remain intact, Sir.",',
)
if "val cleanReply = OwnerIdentityCore.normalizeOperatorReference(result.reply)" not in brain:
    brain = brain.replace(
        "        val result = cortexMesh.ask(fallbackPrompt, memory.promptContext(input))\n        return BrainResponse(\n",
        "        val result = cortexMesh.ask(fallbackPrompt, memory.promptContext(input))\n"
        "        val cleanReply = OwnerIdentityCore.normalizeOperatorReference(result.reply)\n"
        "        return BrainResponse(\n",
        1,
    )
    brain = brain.replace(
        '${result.reply}",\n            display = "LIVE WEB VERIFICATION UNAVAILABLE\\n${reason.take(360)}\\n\\nKNOWLEDGE-BASED FALLBACK\\n${result.reply}",',
        '$cleanReply",\n            display = "LIVE WEB VERIFICATION UNAVAILABLE\\n${reason.take(360)}\\n\\nKNOWLEDGE-BASED FALLBACK\\n$cleanReply",',
        1,
    )
write(brain_path, brain)


research_path = "app/src/main/java/com/seongja/jarvis/SearchGridResearchClient.kt"
research = read(research_path)
if "val cleanAnswer = JarvisResponseSanitizer.clean" not in research:
    research = research.replace(
        "        val mesh = cortexMesh.ask(synthesisPrompt, memoryContext)\n",
        "        val mesh = cortexMesh.ask(synthesisPrompt, memoryContext)\n"
        "        val cleanAnswer = JarvisResponseSanitizer.clean(mesh.reply, groundedResearch = true)\n",
        1,
    )
    research = research.replace("answer = mesh.reply,", "answer = cleanAnswer,", 1)
    research = research.replace(
        "spokenSummary = createSpokenSummary(mesh.reply),",
        "spokenSummary = createSpokenSummary(cleanAnswer),",
        1,
    )
research = research.replace(
    'appendLine("Operator request: ${plan.originalRequest}")',
    'appendLine("Seongja asked: ${plan.originalRequest}")',
)
if "Never output <think>" not in research:
    research = research.replace(
        '        appendLine("Do not print raw URLs because the Android client attaches them below the answer.")\n',
        '        appendLine("Do not print raw URLs because the Android client attaches them below the answer.")\n'
        '        appendLine("Speak directly to Seongja as you or Sir. Never call him the user or operator.")\n'
        '        appendLine("Return only the final intelligence brief. Never output <think>, <analysis>, scratchpad, or preparation text.")\n'
        '        appendLine("Start with the actual answer. Do not say that you can provide a summary or mention a knowledge cutoff.")\n',
        1,
    )
write(research_path, research)


hud_path = "app/src/main/java/com/seongja/jarvis/HelixHudView.kt"
hud = read(hud_path)
hud = hud.replace("JarvisHelix/0.9.13", "JarvisHelix/0.9.17")
if '"LOCAL JARVIS" in upper' not in hud:
    hud = hud.replace(
        '        when {\n            "ANDROID SPEECH SERVICE" in upper -> voiceSource = "ANDROID SPEECH"',
        '        when {\n            "LOCAL JARVIS" in upper || "ANDROID LOCAL" in upper -> voiceSource = "JARVIS LOCAL"\n'
        '            "ANDROID SPEECH SERVICE" in upper -> voiceSource = "ANDROID SPEECH"',
        1,
    )
write(hud_path, hud)


required = {
    voice_path: ["private lateinit var localVoice", "LOCAL JARVIS SYNTHESIS", "localVoice.destroy()"],
    main_path: ["Your local Jarvis voice is active", "JarvisResponseSanitizer.spoken"],
    brain_path: ["I am Jarvis, your private personal intelligence", "cleanReply"],
    research_path: ["cleanAnswer", "Never output <think>"],
}
for path, markers in required.items():
    source = read(path)
    for marker in markers:
        if marker not in source:
            raise RuntimeError(f"Required marker missing after patch: {path}: {marker}")

print("Private Jarvis patch applied successfully.")
