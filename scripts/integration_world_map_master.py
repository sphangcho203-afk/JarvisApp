from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CINEMATIC = ROOT / "helix-ui/src/FridayCinematicOS.tsx"
MAP = ROOT / "helix-ui/src/WorldMapMaster.tsx"
DATA = ROOT / "helix-ui/src/worldMapData.ts"
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

world_data = DATA.read_text(encoding="utf-8")
asset_import = "import { PACKAGED_COUNTRIES_JSON, PACKAGED_PLACES_JSON } from './generatedWorldMapAsset'\n"
if asset_import not in world_data:
    type_import = "import type { GeoCoordinate } from './worldMapMath'\n"
    if type_import not in world_data:
        raise RuntimeError("World map data import anchor missing")
    world_data = world_data.replace(type_import, type_import + asset_import, 1)
world_data = world_data.replace("source: 'network' | 'cache' | 'fallback'", "source: 'network' | 'cache' | 'package' | 'fallback'")
world_data = world_data.replace("const COUNTRY_KEY = 'countries-110m'", "const COUNTRY_KEY = 'countries-50m-v1'")
world_data = world_data.replace("const COUNTRY_KEY = 'countries-10m-v1'", "const COUNTRY_KEY = 'countries-50m-v1'")
world_data = world_data.replace("const PLACE_KEY = 'places-110m'", "const PLACE_KEY = 'places-50m-v1'")
world_data = world_data.replace("const PLACE_KEY = 'places-10m-v1'", "const PLACE_KEY = 'places-50m-v1'")
old_country_endpoints = '''const COUNTRY_ENDPOINTS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_admin_0_countries.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson',
]'''
old_country_endpoints_10m = '''const COUNTRY_ENDPOINTS = [
  './geodata/countries-10m.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_10m_admin_0_countries.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_countries.geojson',
]'''
new_country_endpoints = '''const COUNTRY_ENDPOINTS = [
  './geodata/countries-50m.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_admin_0_countries.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson',
]'''
world_data = world_data.replace(old_country_endpoints, new_country_endpoints)
world_data = world_data.replace(old_country_endpoints_10m, new_country_endpoints)
old_place_endpoints = '''const PLACE_ENDPOINTS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_populated_places_simple.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_populated_places_simple.geojson',
]'''
old_place_endpoints_10m = '''const PLACE_ENDPOINTS = [
  './geodata/places-10m.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_10m_populated_places_simple.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_populated_places_simple.geojson',
]'''
new_place_endpoints = '''const PLACE_ENDPOINTS = [
  './geodata/places-50m.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_populated_places_simple.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_populated_places_simple.geojson',
]'''
world_data = world_data.replace(old_place_endpoints, new_place_endpoints)
world_data = world_data.replace(old_place_endpoints_10m, new_place_endpoints)
packaged_factory = '''export function createFallbackWorldMapDataset(): WorldMapDataset {
  try {
    if (PACKAGED_COUNTRIES_JSON && PACKAGED_PLACES_JSON) {
      const countries = sanitizeCountries(JSON.parse(PACKAGED_COUNTRIES_JSON))
      const places = sanitizePlaces(JSON.parse(PACKAGED_PLACES_JSON))
      if (countries.length >= 170 && places.length >= 500) {
        return { countries, places, source: 'package', updatedAt: Date.now() }
      }
    }
  } catch (error) {
    console.warn('FRIDAY_PACKAGED_MAP_ASSET_FAILURE', error)
  }
  return {
    countries: FALLBACK_COUNTRIES,
    places: FALLBACK_PLACES,
    source: 'fallback',
    updatedAt: Date.now(),
  }
}

'''
legacy_factory = '''export function createFallbackWorldMapDataset(): WorldMapDataset {
  return {
    countries: FALLBACK_COUNTRIES,
    places: FALLBACK_PLACES,
    source: 'fallback',
    updatedAt: Date.now(),
  }
}

'''
if legacy_factory in world_data:
    world_data = world_data.replace(legacy_factory, packaged_factory, 1)
elif "export function createFallbackWorldMapDataset" not in world_data:
    load_anchor = "export async function loadWorldMapDataset(signal?: AbortSignal): Promise<WorldMapDataset> {"
    if load_anchor not in world_data:
        raise RuntimeError("World map fallback insertion anchor missing")
    world_data = world_data.replace(load_anchor, packaged_factory + load_anchor, 1)
DATA.write_text(world_data, encoding="utf-8")

world_map = MAP.read_text(encoding="utf-8")
world_map = world_map.replace(
    "type CSSProperties, type PointerEvent as ReactPointerEvent, type WheelEvent as ReactWheelEvent } from 'react'",
    "type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode, type WheelEvent as ReactWheelEvent } from 'react'",
)
world_map = world_map.replace("children: React.ReactNode", "children: ReactNode")
world_map = world_map.replace(
    "  countryLabel,\n  findPlace,",
    "  countryLabel,\n  createFallbackWorldMapDataset,\n  findPlace,",
)
world_map = world_map.replace(
    "  const [dataset, setDataset] = useState<WorldMapDataset>({ countries: [], places: [], source: 'fallback', updatedAt: 0 })",
    "  const [dataset, setDataset] = useState<WorldMapDataset>(() => createFallbackWorldMapDataset())",
)
world_map = world_map.replace(
    "          {loading ? <div className=\"world-map-loading\"><span /><b>BUILDING GLOBAL GEOMETRY</b><small>COUNTRIES · PLACES · COORDINATES</small></div> : null}",
    "          {loading && dataset.countries.length === 0 ? <div className=\"world-map-loading\"><span /><b>BUILDING GLOBAL GEOMETRY</b><small>COUNTRIES · PLACES · COORDINATES</small></div> : null}",
)
world_map = world_map.replace(
    "<span>{loading ? 'BUILDING' : 'OPERATIONAL'}</span>",
    "<span>{loading && dataset.source === 'fallback' ? 'UPGRADING' : 'OPERATIONAL'}</span>",
)
world_map = world_map.replace(
    "  const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))\n  return countryLabel(country!)?.coordinate || null",
    "  const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))\n  if (!country) return null\n  return countryLabel(country)?.coordinate || null",
)
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
