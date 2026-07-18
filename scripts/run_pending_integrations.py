from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# These integrations remain deterministic until the complete Android 16 runtime
# validation succeeds. Each script is idempotent against already-integrated code.
scripts = [
    ROOT / "integration_xcamera_voice_control.py",
    ROOT / "integration_xcamera_regex_fix.py",
    ROOT / "integration_xcamera_voice_parser.py",
    ROOT / "integration_runtime_voice_test_mode.py",
]

for script in scripts:
    if script.exists():
        runpy.run_path(str(script), run_name="__main__")
