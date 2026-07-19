from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/seongja/jarvis/XCameraActivity.kt"
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"XCamera voice-control anchor missing: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "    private lateinit var workspaceVoice: FridayWorkspaceVoice\n\n"
    "    private var imageCapture: ImageCapture? = null\n",
    "    private lateinit var workspaceVoice: FridayWorkspaceVoice\n"
    "    private var workspaceSpeech: OnDeviceSpeechInput? = null\n\n"
    "    private var imageCapture: ImageCapture? = null\n",
)

replace_once(
    "    private var captureRunning = false\n\n"
    "    private val cameraPermission = registerForActivityResult(\n",
    "    private var captureRunning = false\n"
    "    private var workspaceSpeaking = false\n"
    "    private var activityResumed = false\n\n"
    "    private val speechRestart = Runnable { startWorkspaceListening() }\n\n"
    "    private val cameraPermission = registerForActivityResult(\n",
)

replace_once(
    '''        workspaceVoice = FridayWorkspaceVoice(
            context = this,
            onDiagnostic = { message ->
                JarvisOperationBus.publish("X-CAMERA VOICE", message.take(180), .9f)
            }
        )
''',
    '''        workspaceVoice = FridayWorkspaceVoice(
            context = this,
            onDiagnostic = { message ->
                JarvisOperationBus.publish("X-CAMERA VOICE", message.take(180), .9f)
            },
            onComplete = {
                runOnUiThread {
                    workspaceSpeaking = false
                    startWorkspaceListening(480L)
                }
            }
        )
''',
)

replace_once(
    "        XCameraRuntime.attach(this)\n"
    "        JarvisOperationBus.publish(\"X-CAMERA\", \"OPTICAL ARRAY INITIALIZING\", .08f)\n",
    "        XCameraRuntime.attach(this)\n"
    "        initializeWorkspaceSpeech()\n"
    "        JarvisOperationBus.publish(\"X-CAMERA\", \"OPTICAL ARRAY INITIALIZING\", .08f)\n",
)

replace_once(
    "    override fun onDestroy() {\n"
    "        XCameraRuntime.detach(this)\n"
    "        if (::workspaceVoice.isInitialized) workspaceVoice.destroy()\n"
    "        cameraExecutor.shutdownNow()\n"
    "        super.onDestroy()\n"
    "    }\n",
    '''    override fun onResume() {
        super.onResume()
        activityResumed = true
        startWorkspaceListening(320L)
    }

    override fun onPause() {
        activityResumed = false
        statusView.removeCallbacks(speechRestart)
        workspaceSpeech?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        activityResumed = false
        statusView.removeCallbacks(speechRestart)
        workspaceSpeech?.destroy()
        workspaceSpeech = null
        XCameraRuntime.detach(this)
        if (::workspaceVoice.isInitialized) workspaceVoice.destroy()
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }
''',
)

replace_once(
    "            if (::workspaceVoice.isInitialized) workspaceVoice.stop()\n"
    "            finish()\n",
    "            workspaceSpeech?.stop()\n"
    "            if (::workspaceVoice.isInitialized) workspaceVoice.stop()\n"
    "            finish()\n",
)

replace_once(
    "        val closeButton = button(\"CLOSE EYES\", RED) {\n"
    "            if (::workspaceVoice.isInitialized) workspaceVoice.stop()\n"
    "            finish()\n"
    "        }\n",
    "        val closeButton = button(\"CLOSE EYES\", RED) {\n"
    "            workspaceSpeech?.stop()\n"
    "            if (::workspaceVoice.isInitialized) workspaceVoice.stop()\n"
    "            finish()\n"
    "        }\n",
)

replace_once(
    "                if (autoScanRequested && !autoScanConsumed) {\n"
    "                    autoScanConsumed = true\n"
    "                    previewView.postDelayed({ captureAndAnalyze() }, 850L)\n"
    "                }\n",
    "                if (autoScanRequested && !autoScanConsumed) {\n"
    "                    autoScanConsumed = true\n"
    "                    previewView.postDelayed({ captureAndAnalyze() }, 850L)\n"
    "                } else {\n"
    "                    startWorkspaceListening(420L)\n"
    "                }\n",
)

replace_once(
    "    private fun switchLens() {\n"
    "        if (captureRunning) return\n",
    "    private fun switchLens() {\n"
    "        if (captureRunning) return\n"
    "        workspaceSpeech?.stop()\n",
)

replace_once(
    "        if (captureRunning) return\n"
    "        captureRunning = true\n",
    "        if (captureRunning) return\n"
    "        statusView.removeCallbacks(speechRestart)\n"
    "        workspaceSpeech?.stop()\n"
    "        captureRunning = true\n",
)

replace_once(
    "                val spokenInWorkspace = workspaceVoice.speak(analysis.description)\n"
    "                XCameraRuntime.storeResult(question, analysis, spokenInWorkspace)\n",
    "                workspaceSpeech?.stop()\n"
    "                workspaceSpeaking = workspaceVoice.speak(analysis.description)\n"
    "                val spokenInWorkspace = workspaceSpeaking\n"
    "                XCameraRuntime.storeResult(question, analysis, spokenInWorkspace)\n"
    "                if (!workspaceSpeaking) startWorkspaceListening(420L)\n",
)

replace_once(
    "        JarvisOperationBus.publish(\"X-CAMERA ERROR\", message.take(220), 1f, false)\n"
    "        JarvisOperationBus.clear(\"OPTICAL ROUTE FAILED\")\n",
    "        JarvisOperationBus.publish(\"X-CAMERA ERROR\", message.take(220), 1f, false)\n"
    "        JarvisOperationBus.clear(\"OPTICAL ROUTE FAILED\")\n"
    "        startWorkspaceListening(650L)\n",
)

methods = '''    private fun initializeWorkspaceSpeech() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            JarvisOperationBus.publish(
                "X-CAMERA VOICE",
                "MICROPHONE PERMISSION NOT AVAILABLE // TOUCH CONTROLS ACTIVE",
                .4f
            )
            return
        }
        workspaceSpeech = OnDeviceSpeechInput(
            activity = this,
            listener = object : OnDeviceSpeechInput.Listener {
                override fun onReady(backend: String) {
                    JarvisOperationBus.publish("X-CAMERA VOICE", "$backend // COMMAND CHANNEL READY", .72f)
                }

                override fun onSpeechDetected() {
                    JarvisOperationBus.publish("X-CAMERA VOICE", "OWNER SPEECH DETECTED", .78f)
                }

                override fun onPartial(text: String) = Unit
                override fun onRms(value: Float) = Unit

                override fun onFinal(text: String) {
                    handleWorkspaceSpeech(text)
                }

                override fun onError(code: Int, recoverable: Boolean) {
                    if (recoverable) startWorkspaceListening(620L)
                }
            }
        )
    }

    private fun startWorkspaceListening(delayMs: Long = 220L) {
        if (!activityResumed || captureRunning || workspaceSpeaking) return
        val speech = workspaceSpeech ?: return
        statusView.removeCallbacks(speechRestart)
        statusView.postDelayed({
            if (!activityResumed || captureRunning || workspaceSpeaking || isFinishing) return@postDelayed
            if (!speech.isActive() && !speech.start()) {
                statusView.postDelayed(speechRestart, 850L)
            }
        }, delayMs.coerceAtLeast(0L))
    }

    private fun handleWorkspaceSpeech(raw: String) {
        val clean = raw.trim()
        val normalized = clean
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) {
            startWorkspaceListening(420L)
            return
        }

        when {
            normalized.contains("close your eyes") ||
                normalized.contains("close x camera") ||
                normalized.contains("stop the camera") ||
                normalized.contains("stop looking") -> {
                closeFromVoice()
            }

            normalized.contains("switch lens") ||
                normalized.contains("switch camera") -> {
                switchLens()
            }

            normalized.contains("front camera") ||
                normalized.contains("selfie camera") ||
                normalized.contains("look at me") -> {
                if (lensFacing != CameraSelector.LENS_FACING_FRONT) {
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    autoScanConsumed = true
                    bindCamera()
                } else {
                    question = clean
                    captureAndAnalyze()
                }
            }

            normalized.contains("rear camera") ||
                normalized.contains("back camera") -> {
                if (lensFacing != CameraSelector.LENS_FACING_BACK) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    autoScanConsumed = true
                    bindCamera()
                } else {
                    question = clean
                    captureAndAnalyze()
                }
            }

            normalized.contains("scan") ||
                normalized.contains("look again") ||
                normalized.contains("what can you see") ||
                normalized.contains("what do you see") ||
                normalized.contains("tell me what you see") ||
                normalized.contains("inspect") -> {
                question = clean
                captureAndAnalyze()
            }

            else -> startWorkspaceListening(420L)
        }
    }

'''
anchor = "    private fun lensLabel(): String =\n"
if methods not in text:
    if anchor not in text:
        raise RuntimeError("XCamera voice-control method anchor missing")
    text = text.replace(anchor, methods + anchor, 1)

text = text.replace(
    "Say ‘open your eyes’ to enter this workspace. FRIDAY captures only when SCAN is active.",
    "VOICE ONLINE // SAY SCAN AGAIN, SWITCH LENS, OR CLOSE YOUR EYES. CAPTURE IS ALWAYS VISIBLE."
)

PATH.write_text(text, encoding="utf-8")
