from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/seongja/jarvis/XCameraActivity.kt"
text = PATH.read_text(encoding="utf-8")

start_marker = "    private fun handleWorkspaceSpeech(raw: String) {\n"
end_marker = "    private fun lensLabel(): String =\n"
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("XCamera workspace-speech method boundaries are missing")

replacement = '''    private fun handleWorkspaceSpeech(raw: String) {
        when (val command = XCameraVoiceCommandParser.parse(raw)) {
            XCameraVoiceCommand.Close -> closeFromVoice()
            XCameraVoiceCommand.SwitchLens -> switchLens()

            is XCameraVoiceCommand.UseFront -> {
                question = command.question
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    if (command.scan) captureAndAnalyze() else startWorkspaceListening(320L)
                } else {
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    autoScanRequested = command.scan
                    autoScanConsumed = !command.scan
                    bindCamera()
                }
            }

            is XCameraVoiceCommand.UseRear -> {
                question = command.question
                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    if (command.scan) captureAndAnalyze() else startWorkspaceListening(320L)
                } else {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    autoScanRequested = command.scan
                    autoScanConsumed = !command.scan
                    bindCamera()
                }
            }

            is XCameraVoiceCommand.Scan -> {
                question = command.question
                captureAndAnalyze()
            }

            XCameraVoiceCommand.Ignore -> startWorkspaceListening(420L)
        }
    }

'''

current = text[start:end]
if current != replacement:
    text = text[:start] + replacement + text[end:]
    PATH.write_text(text, encoding="utf-8")
