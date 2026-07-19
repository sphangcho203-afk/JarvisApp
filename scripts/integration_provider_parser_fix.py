from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROVIDERS = ROOT / "app/src/main/java/com/seongja/jarvis/UniversalProviderMesh.kt"
text = PROVIDERS.read_text(encoding="utf-8")


def replace_required(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Provider parser {label} anchor missing: {old!r}")
    text = text.replace(old, new, 1)


replace_required(
    'aliases = listOf("open weather", "openweather")',
    'aliases = listOf("openweather", "openweathermap", "open weather map")',
    "OpenWeather alias",
)

replace_required(
    '        val lower = input.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()\n',
    '        val lower = input.lowercase(Locale.US)\n'
    '            .replace(Regex("[^a-z0-9]+"), " ")\n'
    '            .replace(Regex("\\s+"), " ")\n'
    '            .trim()\n',
    "normalization",
)

PROVIDERS.write_text(text, encoding="utf-8")
print("Provider command parser hardened for punctuation and generic weather setup commands.")
