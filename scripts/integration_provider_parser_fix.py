from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROVIDERS = ROOT / "app/src/main/java/com/seongja/jarvis/UniversalProviderMesh.kt"
text = PROVIDERS.read_text(encoding="utf-8")

old_alias = 'aliases = listOf("open weather", "openweather")'
new_alias = 'aliases = listOf("openweather", "openweathermap", "open weather map")'
if new_alias not in text:
    if old_alias not in text:
        raise RuntimeError("Provider parser OpenWeather alias anchor missing")
    text = text.replace(old_alias, new_alias, 1)

normalized_marker = '            .replace(Regex("[^a-z0-9]+"), " ")'
if normalized_marker not in text:
    lines = text.splitlines(keepends=True)
    index = next(
        (
            i
            for i, line in enumerate(lines)
            if line.strip().startswith("val lower = input.lowercase(Locale.US).replace(Regex(")
        ),
        -1,
    )
    if index < 0:
        raise RuntimeError("Provider parser normalization statement not found")
    lines[index:index + 1] = [
        "        val lower = input.lowercase(Locale.US)\n",
        "            .replace(Regex(\"[^a-z0-9]+\"), \" \")\n",
        "            .replace(Regex(\"\\\\s+\"), \" \")\n",
        "            .trim()\n",
    ]
    text = "".join(lines)

generic_marker = '        val genericWeatherSetup = lower in setOf('
if generic_marker not in text:
    anchor = '        val preset = ProviderMeshCatalog.match(lower)\n'
    if anchor not in text:
        raise RuntimeError("Provider parser preset matching anchor missing")
    generic_block = '''        val genericWeatherSetup = lower in setOf(
            "open weather setup",
            "open weather api setup",
            "open weather apis setup",
            "weather api setup",
            "weather apis setup",
            "configure weather",
            "configure weather api",
            "configure weather apis"
        )
        if (genericWeatherSetup) {
            return ProviderMeshCommand.Open(category = ProviderMeshCategory.WEATHER)
        }
        val genericResearchSetup = lower in setOf(
            "open research setup",
            "open research api setup",
            "open research apis setup",
            "research api setup",
            "research apis setup",
            "configure research api",
            "configure research apis"
        )
        if (genericResearchSetup) {
            return ProviderMeshCommand.Open(category = ProviderMeshCategory.RESEARCH)
        }
'''
    text = text.replace(anchor, generic_block + anchor, 1)

PROVIDERS.write_text(text, encoding="utf-8")
print("Provider command parser hardened for punctuation and generic category setup commands.")
