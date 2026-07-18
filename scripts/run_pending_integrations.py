from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# Base sensory, workspace precedence, and Visual Lab continuity are already
# committed directly. X-Camera voice control and its Kotlin escape correction
# remain in the deterministic integration chain until validated.
scripts = [
    ROOT / "integration_xcamera_voice_control.py",
    ROOT / "integration_xcamera_regex_fix.py",
]

for script in scripts:
    if script.exists():
        runpy.run_path(str(script), run_name="__main__")
