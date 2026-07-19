from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# Only genuinely pending, idempotent integrations belong here. The Android 16
# runtime-test mode is committed directly and must not be regenerated in CI.
scripts = [
    ROOT / "integration_xcamera_voice_control.py",
    ROOT / "integration_xcamera_regex_fix.py",
    ROOT / "integration_xcamera_voice_parser.py",
]

for script in scripts:
    if script.exists():
        runpy.run_path(str(script), run_name="__main__")
