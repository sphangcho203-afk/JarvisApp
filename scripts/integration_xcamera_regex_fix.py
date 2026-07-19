from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/seongja/jarvis/XCameraActivity.kt"
text = PATH.read_text(encoding="utf-8")
text = text.replace('Regex("\\s+")', 'Regex("\\\\s+")')
PATH.write_text(text, encoding="utf-8")
