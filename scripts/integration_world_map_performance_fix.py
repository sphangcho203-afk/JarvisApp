from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP = ROOT / "helix-ui/src/WorldMapMaster.tsx"

text = MAP.read_text(encoding="utf-8")

old_call = "if (layers.countries) drawCountries(context, dataset.countries, project, projection, width, selectedCountry, hoveredCountry, hitRegions)"
new_call = "if (layers.countries) drawCountries(context, dataset.countries, project, projection, width, projection === 'flat' ? flatCamera.zoom : globeCamera.zoom, selectedCountry, hoveredCountry, hitRegions)"
text = text.replace(old_call, new_call)

old_signature = '''  projection: ProjectionMode,
  width: number,
  selectedCountry: CountryFeature | null,'''
new_signature = '''  projection: ProjectionMode,
  width: number,
  detailZoom: number,
  selectedCountry: CountryFeature | null,'''
text = text.replace(old_signature, new_signature, 1)

old_country_loop = '''  const selectedName = countryName(selectedCountry)
  const hoveredName = countryName(hoveredCountry)
  for (const feature of countries) {
    const path = countryPath(feature, project, projection, width)
    if (!path) continue
    const name = countryName(feature)
    const selected = name === selectedName
    const hovered = name === hoveredName'''
new_country_loop = '''  const selectedName = countryName(selectedCountry)
  const hoveredName = countryName(hoveredCountry)
  for (const feature of countries) {
    const name = countryName(feature)
    const selected = name === selectedName
    const hovered = name === hoveredName
    const path = countryPath(feature, project, projection, width, detailZoom * (selected || hovered ? 1.7 : 1))
    if (!path) continue'''
text = text.replace(old_country_loop, new_country_loop, 1)

old_path_signature = "function countryPath(feature: CountryFeature, project: (coordinate: GeoCoordinate) => ProjectedPoint, projection: ProjectionMode, width: number): Path2D | null {"
new_path_signature = "function countryPath(feature: CountryFeature, project: (coordinate: GeoCoordinate) => ProjectedPoint, projection: ProjectionMode, width: number, detailZoom: number): Path2D | null {"
text = text.replace(old_path_signature, new_path_signature, 1)

old_coordinate_loop = '''    for (const ring of polygon) {
      let previous: ProjectedPoint | null = null
      let drawing = false
      for (const coordinate of ring) {
        const point = project(coordinate)'''
new_coordinate_loop = '''    for (const ring of polygon) {
      let previous: ProjectedPoint | null = null
      let drawing = false
      const pointBudget = detailZoom >= 4 ? 1000 : detailZoom >= 2 ? 480 : detailZoom >= 1.25 ? 260 : 170
      const stride = Math.max(1, Math.floor(ring.length / pointBudget))
      for (let coordinateIndex = 0; coordinateIndex < ring.length; coordinateIndex += stride) {
        const coordinate = ring[coordinateIndex]
        const point = project(coordinate)'''
text = text.replace(old_coordinate_loop, new_coordinate_loop, 1)

old_resolver_guarded = '''function resolveCoordinate(query: string, dataset: WorldMapDataset): GeoCoordinate | null {
  const place = findPlace(dataset.places, query)
  if (place) return place.coordinate
  const normalized = query.toLowerCase()
  const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))
  if (!country) return null
  return countryLabel(country)?.coordinate || null
}'''
old_resolver_unguarded = '''function resolveCoordinate(query: string, dataset: WorldMapDataset): GeoCoordinate | null {
  const place = findPlace(dataset.places, query)
  if (place) return place.coordinate
  const normalized = query.toLowerCase()
  const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))
  return countryLabel(country!)?.coordinate || null
}'''
new_resolver = '''function resolveCoordinate(query: string, dataset: WorldMapDataset): GeoCoordinate | null {
  const normalized = query.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()
  const exactCountry = dataset.countries.find(feature => {
    const name = countryName(feature).toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()
    const iso2 = feature.properties.ISO_A2?.toLowerCase()
    const iso3 = feature.properties.ISO_A3?.toLowerCase()
    return name === normalized || iso2 === normalized || iso3 === normalized
  })
  const exactCountryLabel = exactCountry ? countryLabel(exactCountry) : null
  if (exactCountryLabel) return exactCountryLabel.coordinate

  const place = findPlace(dataset.places, query)
  if (place) return place.coordinate

  const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))
  return country ? countryLabel(country)?.coordinate || null : null
}'''
if old_resolver_guarded in text:
    text = text.replace(old_resolver_guarded, new_resolver, 1)
elif old_resolver_unguarded in text:
    text = text.replace(old_resolver_unguarded, new_resolver, 1)
elif new_resolver not in text:
    raise RuntimeError("FRIDAY route resolver anchor missing")

required = [
    "detailZoom: number",
    "const pointBudget = detailZoom >= 4",
    "const exactCountry = dataset.countries.find",
]
missing = [marker for marker in required if marker not in text]
if missing:
    raise RuntimeError(f"FRIDAY world-map performance integration incomplete: {missing}")

MAP.write_text(text, encoding="utf-8")
print("FRIDAY world map LOD and route correctness applied")
