from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent

for script_name in ("integration_provider_mesh.py", "integration_provider_workspace.py"):
    script = ROOT / script_name
    if not script.exists():
        raise FileNotFoundError(f"Required provider integration is missing: {script_name}")
    runpy.run_path(str(script), run_name="__main__")

print("All FRIDAY universal provider integrations applied.")
