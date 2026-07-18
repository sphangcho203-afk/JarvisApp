from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# Base sensory, workspace precedence, and Visual Lab continuity are already
# committed directly. These X-Camera integrations remain deterministic until
# their final Android validation succeeds.
scripts = [
    ROOT / "integration_xcamera_voice_control.py",
    ROOT / "integration_xcamera_regex_fix.py",
    ROOT / "integration_xcamera_voice_parser.py",
]

for script in scripts:
    if script.exists():
        runpy.run_path(str(script), run_name="__main__")
