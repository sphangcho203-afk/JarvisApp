from pathlib import Path
import runpy
import traceback

ROOT = Path(__file__).resolve().parent
REPORT = ROOT.parent / "helix-build.log"

try:
    for script_name in (
        "integration_provider_parser_fix.py",
        "integration_provider_mesh.py",
        "integration_provider_workspace.py",
        "integration_voice_lab.py",
        "integration_operational_sensory.py",
        "integration_reference_locked_ui.py",
    ):
        script = ROOT / script_name
        if not script.exists():
            raise FileNotFoundError(f"Required FRIDAY integration is missing: {script_name}")
        print(f"Applying {script_name}...")
        runpy.run_path(str(script), run_name="__main__")
except Exception:
    detail = traceback.format_exc()
    REPORT.write_text("FRIDAY SOURCE INTEGRATION FAILURE\n\n" + detail, encoding="utf-8")
    print(detail)
    raise

print("All FRIDAY provider, Voice Lab, operational sensory, and reference UI integrations applied.")
