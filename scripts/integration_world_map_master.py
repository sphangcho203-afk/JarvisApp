from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CINEMATIC = ROOT / "helix-ui/src/FridayCinematicOS.tsx"
MAP = ROOT / "helix-ui/src/WorldMapMaster.tsx"
MAIN = ROOT / "helix-ui/src/main.tsx"
APP = ROOT / "helix-ui/src/App.tsx"

cinematic = CINEMATIC.read_text(encoding="utf-8")
cinematic = cinematic.replace(
    "type ModuleKind = 'voice' | 'camera' | 'visual' | 'memory' | 'mesh' | 'control' | 'shield' | 'theme'",
    "type ModuleKind = 'map' | 'voice' | 'camera' | 'visual' | 'memory' | 'mesh' | 'control' | 'shield' | 'theme'",
)
map_entry = "  { route: 'worldmap', title: 'WORLD MAP', detail: 'GLOBAL COMMAND NETWORK', kind: 'map' },\n"
anchor = "const MODULES: ModuleSpec[] = [\n"
if map_entry not in cinematic:
    if anchor not in cinematic:
        raise RuntimeError("FRIDAY module matrix anchor missing")
    cinematic = cinematic.replace(anchor, anchor + map_entry, 1)
CINEMATIC.write_text(cinematic, encoding="utf-8")

world_map = MAP.read_text(encoding="utf-8")
world_map = world_map.replace(
    "type CSSProperties, type PointerEvent as ReactPointerEvent, type WheelEvent as ReactWheelEvent } from 'react'",
    "type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode, type WheelEvent as ReactWheelEvent } from 'react'",
)
world_map = world_map.replace("children: React.ReactNode", "children: ReactNode")
MAP.write_text(world_map, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
css_import = "import './worldMapMaster.css'\n"
if css_import not in main:
    reactor_import = "import './fridayCinematicReactor.css'\n"
    if reactor_import not in main:
        raise RuntimeError("FRIDAY stylesheet anchor missing")
    main = main.replace(reactor_import, reactor_import + css_import, 1)
MAIN.write_text(main, encoding="utf-8")

app = APP.read_text(encoding="utf-8")
preview_route_old = "const route = useMemo(() => parseRouteRequest(bridge.transcript), [bridge.transcript])"
preview_route_new = "const route = useMemo(() => parseRouteRequest(bridge.transcript) || (previewScene === 'world-map' ? { from: 'India', to: 'Japan' } : null), [bridge.transcript, previewScene])"
app = app.replace(preview_route_old, preview_route_new)
APP.write_text(app, encoding="utf-8")

print("FRIDAY world map master integration applied")
