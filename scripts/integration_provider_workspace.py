from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
CINEMATIC = ROOT / "helix-ui/src/CinematicOs.tsx"


def patch(path: Path, old: str, new: str, label: str, marker: str = "") -> None:
    text = path.read_text(encoding="utf-8")
    if (marker and marker in text) or new in text:
        return
    if old not in text:
        raise RuntimeError(f"{label} integration anchor missing: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


patch(
    MAIN,
    '            "control", "permissions" -> startActivity(Intent(this, PermissionCenterActivity::class.java))\n'
    '            "apis", "cortex" -> startActivity(Intent(this, CloudConfigActivity::class.java))\n',
    '            "control", "permissions" -> startActivity(Intent(this, PermissionCenterActivity::class.java))\n'
    '            "providers", "provider", "mesh", "apis" -> ProviderMeshActivity.launch(this)\n'
    '            "cortex", "legacyapis" -> startActivity(Intent(this, CloudConfigActivity::class.java))\n',
    "MainActivity provider workspace",
    marker='ProviderMeshActivity.launch(this)',
)

patch(
    CINEMATIC,
    "  { route: 'diary', code: 'PRIVATE', title: 'PRIVATE DIARY', detail: 'OWNER-LOCKED JOURNAL' },\n"
    "  { route: 'control', code: 'ANDROID', title: 'SYSTEM CONTROL', detail: 'DEVICE AUTOMATION' },\n",
    "  { route: 'diary', code: 'PRIVATE', title: 'PRIVATE DIARY', detail: 'OWNER-LOCKED JOURNAL' },\n"
    "  { route: 'providers', code: 'MESH', title: 'API PROVIDERS', detail: 'MODELS + KEYS + ROUTING' },\n"
    "  { route: 'control', code: 'ANDROID', title: 'SYSTEM CONTROL', detail: 'DEVICE AUTOMATION' },\n",
    "Cinematic launcher provider module",
    marker="route: 'providers'",
)

print("Universal provider workspace integrated into MainActivity and CinematicOs")
