from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# Base sensory, workspace precedence, and Visual Lab continuity are already
# committed directly. Only X-Camera's local voice loop remains pending.
scripts = [ROOT / "integration_xcamera_voice_control.py"]

for script in scripts:
    if script.exists():
        runpy.run_path(str(script), run_name="__main__")
