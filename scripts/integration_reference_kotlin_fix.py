from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"

text = MAIN.read_text(encoding="utf-8")

# The reference integration is intentionally a generated-source step. Preserve
# Kotlin escape sequences rather than allowing Python to turn them into source
# newlines or unsupported \s escapes.
text = text.replace(r'Regex("\s+")', r'Regex("\\s+")')

broken_display = '''                        display = "YOU'RE HERE, SIR.
$place
${String.format(Locale.US, "%.5f° N  %.5f° E", snapshot.latitude, snapshot.longitude)}",'''
fixed_display = '''                        display = "YOU'RE HERE, SIR.\\n$place\\n" +
                            String.format(
                                Locale.US,
                                "LAT %.5f // LON %.5f",
                                snapshot.latitude,
                                snapshot.longitude
                            ),'''

if broken_display in text:
    text = text.replace(broken_display, fixed_display, 1)
elif 'display = "YOU\'RE HERE, SIR.\\n$place\\n" +' not in text:
    raise RuntimeError("Reference UI location display escape anchor missing")

MAIN.write_text(text, encoding="utf-8")
print("FRIDAY reference UI Kotlin escaping verified")
