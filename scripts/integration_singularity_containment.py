from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "helix-ui/src/main.tsx"

main = MAIN.read_text(encoding="utf-8")
containment_import = "import './singularityContainment.css'\n"
if containment_import not in main:
    polish_import = "import './singularityCorePolish.css'\n"
    base_import = "import './singularityCore.css'\n"
    anchor = polish_import if polish_import in main else base_import
    if anchor not in main:
        raise RuntimeError("FRIDAY containment stylesheet anchor missing")
    main = main.replace(anchor, anchor + containment_import, 1)
MAIN.write_text(main, encoding="utf-8")

print("FRIDAY vertical containment chamber integration applied")
