from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Patch anchor missing in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def patch_main_activity() -> None:
    path = "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
    replace_once(
        path,
        "        hud.isLongClickable = false\n",
        "        hud.setWorkspaceListener(::openWorkspace)\n"
        "        hud.isLongClickable = false\n",
    )
    replace_once(
        path,
        "        WeatherRuntime.refresh()\n        if (hasMicPermission() && !brainBusy.get()) {\n",
        "        WeatherRuntime.refresh()\n"
        "        consumeXCameraResult()\n"
        "        if (hasMicPermission() && !brainBusy.get()) {\n",
    )
    replace_once(
        path,
        "    private fun handlePartialSpeech(text: String) {\n",
        '''    private fun openWorkspace(workspace: String) {
        when (workspace.trim().lowercase(Locale.US)) {
            "xcamera", "vision", "eyes" -> XCameraActivity.launch(this, autoScan = false)
            "image", "visual", "studio" -> ImageGenerationActivity.launch(this, "")
            "diary" -> PrivateDiaryActivity.launch(this)
            "memory", "vault" -> MemoryVaultActivity.launch(this)
            "control", "permissions" -> startActivity(Intent(this, PermissionCenterActivity::class.java))
            "apis", "cortex" -> startActivity(Intent(this, CloudConfigActivity::class.java))
            else -> hud.pushEvent("WORKSPACE -> UNKNOWN ${workspace.take(32).uppercase(Locale.US)}")
        }
    }

    private fun consumeXCameraResult() {
        val result = XCameraRuntime.consume() ?: return
        val response = BrainResponse(
            spoken = result.description,
            display = buildString {
                appendLine(if (result.isError) "X-CAMERA // DEGRADED" else "X-CAMERA // VISUAL ANALYSIS VERIFIED")
                appendLine("QUESTION // ${result.question.take(320)}")
                appendLine("MODEL // ${result.model}")
                appendLine("TIME // ${result.elapsedMs}MS")
                append(result.description)
            },
            intent = if (result.isError) "vision/error" else "vision/result",
            confidence = if (result.isError) 0f else .97f,
            mode = if (result.isError) BrainMode.ALERT else BrainMode.ONLINE,
            trace = listOf(
                "workspace=x_camera",
                "model=${result.model}",
                "latency=${result.elapsedMs}ms",
                "capture_persistence=disabled"
            ),
            memory = brain.memorySnapshot(),
            thoughts = listOf("A temporary camera frame was analyzed and discarded."),
            entities = listOf("sensor=xcamera"),
            decision = if (result.isError) "report_xcamera_error" else "return_visual_analysis",
            action = BrainAction()
        )
        hud.submitBrainResponse(response)
        hud.pushEvent(if (result.isError) "X-CAMERA -> DEGRADED" else "X-CAMERA -> RESULT VERIFIED")
        if (!result.isError && !result.spokenInWorkspace) {
            mainHandler.postDelayed({ if (resumed) speak(result.description) }, 280L)
        }
    }

    private fun handlePartialSpeech(text: String) {
''',
    )


def patch_helix_bridge() -> None:
    path = "app/src/main/java/com/seongja/jarvis/HelixHudView.kt"
    replace_once(
        path,
        "    private var coreTapListener: (() -> Unit)? = null\n",
        "    private var coreTapListener: (() -> Unit)? = null\n"
        "    private var workspaceListener: ((String) -> Unit)? = null\n",
    )
    replace_once(
        path,
        "    fun setCoreTapListener(listener: () -> Unit) {\n        coreTapListener = listener\n    }\n",
        "    fun setCoreTapListener(listener: () -> Unit) {\n"
        "        coreTapListener = listener\n"
        "    }\n\n"
        "    fun setWorkspaceListener(listener: (String) -> Unit) {\n"
        "        workspaceListener = listener\n"
        "    }\n",
    )
    replace_once(
        path,
        "        coreTapListener = null\n",
        "        coreTapListener = null\n        workspaceListener = null\n",
    )
    replace_once(
        path,
        '''        @JavascriptInterface
        fun onCoreTap() {
            post {
                if (!released) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    coreTapListener?.invoke()
                }
            }
        }
    }
''',
        '''        @JavascriptInterface
        fun onCoreTap() {
            post {
                if (!released) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    coreTapListener?.invoke()
                }
            }
        }

        @JavascriptInterface
        fun openWorkspace(workspace: String) {
            val clean = workspace.replace(Regex("[^A-Za-z0-9_-]"), "").take(32)
            if (clean.isBlank()) return
            post {
                if (!released) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    dispatchEvent("WORKSPACE -> ${clean.uppercase(Locale.US)}", "SYS")
                    workspaceListener?.invoke(clean)
                }
            }
        }
    }
''',
    )
    text = read(path).replace("FridayHelix/0.9.31", "FridayHelix/0.10.0")
    write(path, text)


def patch_xcamera_voice() -> None:
    path = "app/src/main/java/com/seongja/jarvis/XCameraActivity.kt"
    replace_once(
        path,
        "    private lateinit var cameraExecutor: ExecutorService\n",
        "    private lateinit var cameraExecutor: ExecutorService\n"
        "    private lateinit var workspaceVoice: FridayWorkspaceVoice\n",
    )
    replace_once(
        path,
        "        cameraExecutor = Executors.newSingleThreadExecutor()\n        setContentView(buildUi())\n",
        "        cameraExecutor = Executors.newSingleThreadExecutor()\n"
        "        workspaceVoice = FridayWorkspaceVoice(\n"
        "            context = this,\n"
        "            onDiagnostic = { message ->\n"
        "                JarvisOperationBus.publish(\"X-CAMERA VOICE\", message.take(180), .9f)\n"
        "            }\n"
        "        )\n"
        "        setContentView(buildUi())\n",
    )
    replace_once(
        path,
        "        XCameraRuntime.detach(this)\n        cameraExecutor.shutdownNow()\n",
        "        XCameraRuntime.detach(this)\n"
        "        if (::workspaceVoice.isInitialized) workspaceVoice.destroy()\n"
        "        cameraExecutor.shutdownNow()\n",
    )
    text = read(path).replace(
        '            XCameraRuntime.storeSystemMessage("X-CAMERA closed by owner command.")\n            finish()\n',
        '            if (::workspaceVoice.isInitialized) workspaceVoice.stop()\n            finish()\n',
    )
    text = text.replace(
        '        val closeButton = button("CLOSE EYES", RED) { finish() }',
        '        val closeButton = button("CLOSE EYES", RED) {\n'
        '            if (::workspaceVoice.isInitialized) workspaceVoice.stop()\n'
        '            finish()\n'
        '        }',
    )
    old_success = '''                resultView.text = analysis.description
                XCameraRuntime.storeResult(question, analysis)
                JarvisConversationBus.recordUser("X-CAMERA: $question")
'''
    new_success = '''                resultView.text = analysis.description
                val spokenInWorkspace = workspaceVoice.speak(analysis.description)
                XCameraRuntime.storeResult(question, analysis, spokenInWorkspace)
                JarvisConversationBus.recordUser("X-CAMERA: $question")
'''
    if new_success not in text:
        if old_success not in text:
            raise RuntimeError("XCamera success anchor missing")
        text = text.replace(old_success, new_success, 1)
    text = text.replace(
        "    val elapsedMs: Long,\n    val isError: Boolean = false\n",
        "    val elapsedMs: Long,\n"
        "    val isError: Boolean = false,\n"
        "    val spokenInWorkspace: Boolean = false\n",
    )
    text = text.replace(
        "    fun storeResult(question: String, result: VisionAnalysis) {\n",
        "    fun storeResult(\n"
        "        question: String,\n"
        "        result: VisionAnalysis,\n"
        "        spokenInWorkspace: Boolean\n"
        "    ) {\n",
    )
    text = text.replace(
        "            model = result.model,\n            elapsedMs = result.elapsedMs\n",
        "            model = result.model,\n"
        "            elapsedMs = result.elapsedMs,\n"
        "            spokenInWorkspace = spokenInWorkspace\n",
        1,
    )
    write(path, text)


def patch_web_workspace_controls() -> None:
    bridge = "helix-ui/src/nativeBridge.ts"
    replace_once(
        bridge,
        "      onCoreTap: () => void\n      onHelixError?: (message: string) => void\n",
        "      onCoreTap: () => void\n"
        "      openWorkspace: (workspace: string) => void\n"
        "      onHelixError?: (message: string) => void\n",
    )
    replace_once(
        bridge,
        "    tapCore: () => window.JarvisAndroid?.onCoreTap(),\n",
        "    tapCore: () => window.JarvisAndroid?.onCoreTap(),\n"
        "    openWorkspace: (workspace: string) => window.JarvisAndroid?.openWorkspace?.(workspace),\n",
    )

    app = "helix-ui/src/App.tsx"
    replace_once(
        app,
        "      <WorkspaceRail />\n",
        "      <WorkspaceRail bridgeReady={bridge.bridgeReady} onOpen={bridge.openWorkspace} />\n",
    )

    rail = "helix-ui/src/WorkspaceRail.tsx"
    text = read(rail)
    text = text.replace(
        "export function WorkspaceRail() {",
        "export function WorkspaceRail({\n"
        "  bridgeReady,\n"
        "  onOpen,\n"
        "}: {\n"
        "  bridgeReady: boolean\n"
        "  onOpen: (workspace: string) => void\n"
        "}) {",
    )
    text = text.replace(
        '''          <article key={capability.id} className="workspace-capability">
            <div className="workspace-capability-head">
              <span>{capability.id}</span>
              <i>{capability.state}</i>
            </div>
            <strong>{capability.title}</strong>
            <small>SAY // {capability.command}</small>
          </article>''',
        '''          <button
            key={capability.id}
            type="button"
            className="workspace-capability"
            disabled={!bridgeReady}
            onClick={() => onOpen(capability.id.toLowerCase())}
          >
            <div className="workspace-capability-head">
              <span>{capability.id}</span>
              <i>{capability.state}</i>
            </div>
            <strong>{capability.title}</strong>
            <small>OPEN // {capability.command}</small>
          </button>''',
    )
    write(rail, text)

    css = "helix-ui/src/workspaceRail.css"
    text = read(css).replace("  pointer-events: none;", "  pointer-events: auto;")
    if ".workspace-capability:disabled" not in text:
        text += "\n.workspace-capability:disabled { opacity: .38; }\n"
        text += ".workspace-capability:not(:disabled) { cursor: pointer; }\n"
        text += ".workspace-capability:not(:disabled):active { transform: scale(.975); border-color: rgba(92, 164, 255, .7); }\n"
    write(css, text)


def patch_versions_and_tests() -> None:
    hud = "helix-ui/src/Hud.tsx"
    write(hud, read(hud).replace("BUILD 0.9.31", "BUILD 0.10.0"))

    brain = "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt"
    write(
        brain,
        read(brain).replace(
            "OPERATIONS CORE // 0.9.24",
            "OPERATIONS CORE // 0.10.0 // SENSORY CORE",
        ),
    )

    cartesia = "app/src/main/java/com/seongja/jarvis/CartesiaSonicClient.kt"
    write(cartesia, read(cartesia).replace("Friday-Android/0.9.22", "Friday-Android/0.10.0"))

    package_path = ROOT / "helix-ui/package.json"
    package = json.loads(package_path.read_text(encoding="utf-8"))
    package["version"] = "0.10.0"
    package_path.write_text(json.dumps(package, indent=2) + "\n", encoding="utf-8")

    lock_path = ROOT / "helix-ui/package-lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    lock["version"] = "0.10.0"
    lock.setdefault("packages", {}).setdefault("", {})["version"] = "0.10.0"
    lock_path.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")

    smoke = "app/src/androidTest/java/com/seongja/jarvis/RuntimeSmokeTest.kt"
    replace_once(
        smoke,
        "            OwnerAccessGateActivity::class.java\n",
        "            OwnerAccessGateActivity::class.java,\n"
        "            XCameraActivity::class.java,\n"
        "            ImageGenerationActivity::class.java,\n"
        "            PrivateDiaryActivity::class.java,\n"
        "            MemoryVaultActivity::class.java,\n"
        "            OwnerVoiceEnrollmentActivity::class.java\n",
    )


def expand_blueprint() -> None:
    path = "docs/FRIDAY_JARVIS_MASTER_BLUEPRINT.md"
    text = read(path)
    marker = "# 15. Owner-selectable operating modes"
    if marker in text:
        return
    text += '''

---

# 15. Owner-selectable operating modes

## Hybrid intelligence

- Android-native deterministic actions run first.
- Local/offline reasoning is preferred for privacy, availability and low latency when configured.
- Encrypted cloud cortex nodes are optional for stronger current models, research and multimodal work.
- FRIDAY must show which route handled the request and fail over honestly.

## Fully offline private mode

The earlier Termux architecture remains an approved project track:

- microphone capture to `whisper.cpp`
- local wake phrase
- localhost-only `llama.cpp` or Ollama inference
- mobile RAG and encrypted local memory
- allow-listed Android action planner
- no public network dependency
- localhost services bound to `127.0.0.1`
- atomic memory writes and bounded storage

Offline mode must be selectable rather than silently removed when cloud features are added.

# 16. Screen awareness and contextual follow-ups

- “See my screen” uses an explicit on-demand Accessibility snapshot of readable text, controls and fields.
- Screen context is temporary and must not be stored by default.
- Pixel-level screen vision requires explicit Android MediaProjection consent and a visible capture state.
- The assistant must never imply it can see the screen when Android has not supplied context.
- A local action-state ledger should resolve follow-ups such as “turn it off back” to the most recent compatible device action, while asking when ambiguity remains.

# 17. First-launch, multilingual and floating experience

- First-launch permission/API onboarding should appear once, then reopen only by owner command or reset.
- FRIDAY should understand Seongja's multilingual speech patterns, including English, Assamese, Hindi, Bengali and Nepali where the selected recognition backend supports them.
- A compact floating HELIX may provide visible state and quick return to the full chamber, subject to Android overlay permission and battery restrictions.
- Sounds, boot sequences, voice-state animations and haptics are functional feedback, not decoration.

# 18. Completion reporting

When a release truly passes signing, automated verification and physical-phone acceptance, FRIDAY may send a completion report to Seongja's own authorized Gmail account. No completion message should be sent earlier.
'''
    write(path, text)


def cleanup() -> None:
    helper = ROOT / ".github/workflows/feature-recovery-finalizer.yml"
    helper.unlink(missing_ok=True)


def main() -> None:
    patch_main_activity()
    patch_helix_bridge()
    patch_xcamera_voice()
    patch_web_workspace_controls()
    patch_versions_and_tests()
    expand_blueprint()
    cleanup()


if __name__ == "__main__":
    main()
