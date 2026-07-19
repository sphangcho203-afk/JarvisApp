from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# The integrations below are committed in source and remain idempotent. CI runs
# them only as a drift guard before validating the exact Android 16 revision.
scripts = [
    ROOT / "integration_xcamera_voice_control.py",
    ROOT / "integration_xcamera_regex_fix.py",
    ROOT / "integration_xcamera_voice_parser.py",
    ROOT / "integration_runtime_voice_test_mode.py",
]

for script in scripts:
    if script.exists():
        runpy.run_path(str(script), run_name="__main__")
