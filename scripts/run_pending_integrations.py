from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent

scripts = [ROOT / "finalize_workspace_precedence.py"]
scripts.extend(sorted(ROOT.glob("integration_*.py")))

for script in scripts:
    if script.exists() and script.name != Path(__file__).name:
        runpy.run_path(str(script), run_name="__main__")
