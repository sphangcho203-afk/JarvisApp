import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type WheelEvent as ReactWheelEvent } from 'react'
import type { LocationSnapshot } from './types'
import {
  countryLabel,
  findPlace,
  loadWorldMapDataset,
  type CountryFeature,
  type WorldMapDataset,
  type WorldPlace,
} from './worldMapData'
import {
  clamp,
  coordinateToDms,
  estimateFlightDurationHours,
  formatDurationHours,
  greatCircleDistanceKm,
  interpolateGreatCircle,
  projectFlat,
  projectGlobe,
  routePointAt,
  screenTilt,
  type FlatCamera,
  type GeoCoordinate,
  type GlobeCamera,
  type ProjectedPoint,
} from './worldMapMath'
import { worldMapSonic } from './WorldMapSonic'

type ProjectionMode = 'flat' | 'globe'
type LayerKey = 'grid' | 'countries' | 'places' | 'routes' | 'signals' | 'terrain'

type RouteRequest = {
  from: string
  to: string
}

type ResolvedRoute = RouteRequest & {
  fromCoordinate: GeoCoordinate
  toCoordinate: GeoCoordinate
  points: GeoCoordinate[]
  distanceKm: number
}

type HitRegion = {
  path: Path2D
  feature: CountryFeature
}

type PointerDrag = {
  active: boolean
  moved: boolean
  pointerId: number
  startX: number
  startY: number
  previousX: number
  previousY: number
}

export interface WorldMapMasterProps {
  location?: LocationSnapshot
  route?: RouteRequest | null
  briefs?: string[]
  onClose?: () => void
  initialProjection?: ProjectionMode
  embedded?: boolean
}

const DEFAULT_FLAT_CAMERA: FlatCamera = { zoom: 1, panX: 0, panY: 0, centerLongitude: 0 }
const DEFAULT_GLOBE_CAMERA: GlobeCamera = { rotationLongitude: 18, rotationLatitude: 8, zoom: 1 }

const REGION_NAMES = ['North America', 'South America', 'Europe', 'Africa', 'Asia', 'Oceania', 'Antarctica']
const OCEAN_LABELS: Array<{ name: string; coordinate: GeoCoordinate }> = [
  { name: 'NORTH PACIFIC OCEAN', coordinate: [-150, 28] },
  { name: 'SOUTH PACIFIC OCEAN', coordinate: [-125, -30] },
  { name: 'NORTH ATLANTIC OCEAN', coordinate: [-37, 28] },
  { name: 'SOUTH ATLANTIC OCEAN', coordinate: [-24, -31] },
  { name: 'INDIAN OCEAN', coordinate: [78, -28] },
  { name: 'ARCTIC OCEAN', coordinate: [15, 78] },
  { name: 'SOUTHERN OCEAN', coordinate: [0, -66] },
]

export function WorldMapMaster({
  location,
  route,
  briefs = [],
  onClose,
  initialProjection = 'flat',
  embedded = false,
}: WorldMapMasterProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const frameRef = useRef<HTMLDivElement | null>(null)
  const animationRef = useRef<number>(0)
  const hitRegionsRef = useRef<HitRegion[]>([])
  const lastContextRef = useRef<CanvasRenderingContext2D | null>(null)
  const dragRef = useRef<PointerDrag>({ active: false, moved: false, pointerId: -1, startX: 0, startY: 0, previousX: 0, previousY: 0 })
  const flatCameraRef = useRef<FlatCamera>({ ...DEFAULT_FLAT_CAMERA })
  const globeCameraRef = useRef<GlobeCamera>({ ...DEFAULT_GLOBE_CAMERA })
  const fpsSampleRef = useRef({ frames: 0, startedAt: performance.now() })
  const lastCountryRef = useRef<string>('')

  const [dataset, setDataset] = useState<WorldMapDataset>({ countries: [], places: [], source: 'fallback', updatedAt: 0 })
  const [loading, setLoading] = useState(true)
  const [projection, setProjection] = useState<ProjectionMode>(initialProjection)
  const [layers, setLayers] = useState<Record<LayerKey, boolean>>({ grid: true, countries: true, places: true, routes: true, signals: true, terrain: true })
  const [selectedCountry, setSelectedCountry] = useState<CountryFeature | null>(null)
  const [hoveredCountry, setHoveredCountry] = useState<CountryFeature | null>(null)
  const [search, setSearch] = useState('')
  const [zoomDisplay, setZoomDisplay] = useState(1)
  const [fps, setFps] = useState(60)
  const [tilt, setTilt] = useState({ rotateX: 0, rotateY: 0 })
  const [controlsOpen, setControlsOpen] = useState(false)
  const [firstInteraction, setFirstInteraction] = useState(false)

  const resolvedRoute = useMemo(() => resolveRoute(route, dataset), [route, dataset])
  const effectiveLocation = useMemo<GeoCoordinate | null>(() => {
    if (!location?.available) return null
    return [location.longitude, location.latitude]
  }, [location])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    loadWorldMapDataset(controller.signal)
      .then(result => setDataset(result))
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (resolvedRoute && firstInteraction) worldMapSonic.play('route')
  }, [resolvedRoute, firstInteraction])

  useEffect(() => {
    if (effectiveLocation && firstInteraction) worldMapSonic.play('lock')
  }, [effectiveLocation, firstInteraction])

  useEffect(() => {
    if (!effectiveLocation) return
    focusCoordinate(effectiveLocation, projection, flatCameraRef.current, globeCameraRef.current)
    setZoomDisplay(projection === 'flat' ? flatCameraRef.current.zoom : globeCameraRef.current.zoom)
  }, [effectiveLocation, projection])

  const draw = useCallback((timestamp: number) => {
    const canvas = canvasRef.current
    const frame = frameRef.current
    if (!canvas || !frame) return
    const rect = frame.getBoundingClientRect()
    const width = Math.max(1, rect.width)
    const height = Math.max(1, rect.height)
    const dpr = Math.min(2, window.devicePixelRatio || 1)
    const pixelWidth = Math.round(width * dpr)
    const pixelHeight = Math.round(height * dpr)
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth
      canvas.height = pixelHeight
      canvas.style.width = `${width}px`
      canvas.style.height = `${height}px`
    }
    const context = canvas.getContext('2d', { alpha: true })
    if (!context) return
    context.setTransform(dpr, 0, 0, dpr, 0, 0)
    context.clearRect(0, 0, width, height)
    lastContextRef.current = context

    renderMap({
      context,
      width,
      height,
      timestamp,
      projection,
      dataset,
      layers,
      route: resolvedRoute,
      location: effectiveLocation,
      selectedCountry,
      hoveredCountry,
      flatCamera: flatCameraRef.current,
      globeCamera: globeCameraRef.current,
      hitRegions: hitRegionsRef.current,
    })

    const sample = fpsSampleRef.current
    sample.frames += 1
    if (timestamp - sample.startedAt >= 700) {
      setFps(Math.round((sample.frames * 1000) / (timestamp - sample.startedAt)))
      fpsSampleRef.current = { frames: 0, startedAt: timestamp }
    }
    animationRef.current = window.requestAnimationFrame(draw)
  }, [projection, dataset, layers, resolvedRoute, effectiveLocation, selectedCountry, hoveredCountry])

  useEffect(() => {
    animationRef.current = window.requestAnimationFrame(draw)
    return () => window.cancelAnimationFrame(animationRef.current)
  }, [draw])

  const interact = () => {
    if (!firstInteraction) {
      setFirstInteraction(true)
      worldMapSonic.play('boot')
    }
  }

  const setProjectionMode = (mode: ProjectionMode) => {
    interact()
    if (projection === mode) return
    setProjection(mode)
    setZoomDisplay(mode === 'flat' ? flatCameraRef.current.zoom : globeCameraRef.current.zoom)
    worldMapSonic.play('globe')
  }

  const toggleLayer = (layer: LayerKey) => {
    interact()
    setLayers(current => ({ ...current, [layer]: !current[layer] }))
    worldMapSonic.play('layer')
  }

  const onPointerDown = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    interact()
    event.currentTarget.setPointerCapture(event.pointerId)
    dragRef.current = {
      active: true,
      moved: false,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      previousX: event.clientX,
      previousY: event.clientY,
    }
  }

  const onPointerMove = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    setTilt(screenTilt(event.clientX - rect.left, event.clientY - rect.top, rect.width, rect.height))
    const drag = dragRef.current
    if (!drag.active || drag.pointerId !== event.pointerId) {
      updateHover(event)
      return
    }
    const deltaX = event.clientX - drag.previousX
    const deltaY = event.clientY - drag.previousY
    drag.previousX = event.clientX
    drag.previousY = event.clientY
    if (Math.hypot(event.clientX - drag.startX, event.clientY - drag.startY) > 5) drag.moved = true

    if (projection === 'flat') {
      flatCameraRef.current.panX += deltaX
      flatCameraRef.current.panY += deltaY
    } else {
      globeCameraRef.current.rotationLongitude -= deltaX * .28 / globeCameraRef.current.zoom
      globeCameraRef.current.rotationLatitude = clamp(globeCameraRef.current.rotationLatitude + deltaY * .22 / globeCameraRef.current.zoom, -78, 78)
    }
  }

  const onPointerUp = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current
    if (drag.pointerId === event.pointerId && !drag.moved) selectCountryAt(event)
    dragRef.current.active = false
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* pointer already released */ }
  }

  const onPointerLeave = () => {
    dragRef.current.active = false
    setHoveredCountry(null)
    setTilt({ rotateX: 0, rotateY: 0 })
  }

  const onWheel = (event: ReactWheelEvent<HTMLCanvasElement>) => {
    interact()
    event.preventDefault()
    const factor = Math.exp(-event.deltaY * .00125)
    if (projection === 'flat') {
      flatCameraRef.current.zoom = clamp(flatCameraRef.current.zoom * factor, .72, 8)
      setZoomDisplay(flatCameraRef.current.zoom)
    } else {
      globeCameraRef.current.zoom = clamp(globeCameraRef.current.zoom * factor, .72, 2.7)
      setZoomDisplay(globeCameraRef.current.zoom)
    }
  }

  const updateHover = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const country = hitTestCountry(event)
    const name = countryName(country)
    if (name !== lastCountryRef.current) {
      lastCountryRef.current = name
      setHoveredCountry(country)
    }
  }

  const selectCountryAt = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const country = hitTestCountry(event)
    if (!country) return
    setSelectedCountry(country)
    const label = countryLabel(country)
    if (label) {
      focusCoordinate(label.coordinate, projection, flatCameraRef.current, globeCameraRef.current, true)
      setZoomDisplay(projection === 'flat' ? flatCameraRef.current.zoom : globeCameraRef.current.zoom)
    }
    worldMapSonic.play('focus')
    navigator.vibrate?.(13)
  }

  const hitTestCountry = (event: ReactPointerEvent<HTMLCanvasElement>): CountryFeature | null => {
    const context = lastContextRef.current
    if (!context) return null
    const rect = event.currentTarget.getBoundingClientRect()
    const x = event.clientX - rect.left
    const y = event.clientY - rect.top
    for (let index = hitRegionsRef.current.length - 1; index >= 0; index -= 1) {
      const region = hitRegionsRef.current[index]
      if (context.isPointInPath(region.path, x, y)) return region.feature
    }
    return null
  }

  const submitSearch = () => {
    interact()
    const normalized = search.trim().toLowerCase()
    if (!normalized) return
    const place = findPlace(dataset.places, normalized)
    if (place) {
      focusCoordinate(place.coordinate, projection, flatCameraRef.current, globeCameraRef.current, true)
      setZoomDisplay(projection === 'flat' ? flatCameraRef.current.zoom : globeCameraRef.current.zoom)
      worldMapSonic.play('focus')
      return
    }
    const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))
    if (country) {
      setSelectedCountry(country)
      const label = countryLabel(country)
      if (label) focusCoordinate(label.coordinate, projection, flatCameraRef.current, globeCameraRef.current, true)
      setZoomDisplay(projection === 'flat' ? flatCameraRef.current.zoom : globeCameraRef.current.zoom)
      worldMapSonic.play('focus')
      return
    }
    worldMapSonic.play('warning')
  }

  const resetView = () => {
    flatCameraRef.current = { ...DEFAULT_FLAT_CAMERA }
    globeCameraRef.current = { ...DEFAULT_GLOBE_CAMERA }
    setZoomDisplay(1)
    setSelectedCountry(null)
    worldMapSonic.play('layer')
  }

  const style = { '--wm-tilt-x': `${tilt.rotateX}deg`, '--wm-tilt-y': `${tilt.rotateY}deg` } as CSSProperties
  const regionCounts = useMemo(() => countRegions(dataset.countries), [dataset.countries])
  const activeNodes = dataset.places.length + (resolvedRoute ? resolvedRoute.points.length : 0) + (effectiveLocation ? 1 : 0)

  return (
    <section className={`world-map-master ${embedded ? 'embedded' : ''}`} data-projection={projection} style={style}>
      <div className="world-map-frame"><i /><i /><i /><i /></div>
      <header className="world-map-command-bar">
        <div className="world-map-system-clock"><small>SYSTEM TIME</small><b>{new Date().toISOString().slice(11, 19)} UTC</b><span>{new Date().toISOString().slice(0, 10)}</span></div>
        <div className="world-map-brand"><WorldMapLogo /><b>F.R.I.D.A.Y.</b><small>GLOBAL COMMAND NETWORK</small></div>
        <div className="world-map-command-actions">
          <button type="button" onClick={() => setProjectionMode('flat')} className={projection === 'flat' ? 'active' : ''}>2D</button>
          <button type="button" onClick={() => setProjectionMode('globe')} className={projection === 'globe' ? 'active' : ''}>3D</button>
          <button type="button" onClick={() => setControlsOpen(value => !value)}>LAYERS</button>
          {onClose ? <button type="button" onClick={onClose} aria-label="Close world map">×</button> : null}
        </div>
      </header>

      <nav className="world-map-tabs">
        <button className="active" type="button">GLOBAL MAP</button>
        <button type="button">INTELLIGENCE</button>
        <button type="button">ROUTE ANALYSIS</button>
        <button type="button">LIVE LOCATOR</button>
        <button type="button">ASSET TRACKER</button>
        <button type="button">SYSTEM STATUS</button>
      </nav>

      <aside className="world-map-left-rail">
        <MapPanel title="GLOBAL OVERVIEW" className="overview-panel">
          <div className="overview-orb"><span /><span /><span /></div>
          <Metric label="COUNTRIES" value={`${dataset.countries.length}`} />
          <Metric label="PLACES" value={`${dataset.places.length}`} />
          <Metric label="ACTIVE NODES" value={`${activeNodes}`} />
          <Metric label="DATA SOURCE" value={dataset.source.toUpperCase()} />
        </MapPanel>
        <MapPanel title="REGION STATUS" className="region-panel">
          {REGION_NAMES.map(region => <StatusRow key={region} label={region.toUpperCase()} value={`${regionCounts.get(region) || 0}`} state={region === 'Antarctica' ? 'monitoring' : 'stable'} />)}
        </MapPanel>
        <MapPanel title="INTELLIGENCE FEED" className="feed-panel">
          {(briefs.length ? briefs : ['Verified global feed channel ready.', 'Route intelligence waiting for command.', 'Location channel protected by device permission.']).slice(0, 3).map((brief, index) => (
            <div className="map-feed-item" key={`${brief}-${index}`}><i /><span><small>CHANNEL {String(index + 1).padStart(2, '0')}</small>{brief}</span></div>
          ))}
        </MapPanel>
      </aside>

      <main className="world-map-viewport-shell">
        <div className="world-map-coordinate-top">{Array.from({ length: 13 }, (_, index) => <span key={index}>{formatLongitude(-180 + index * 30)}</span>)}</div>
        <div className="world-map-coordinate-left">{[75, 60, 30, 0, -30, -60, -75].map(value => <span key={value}>{formatLatitude(value)}</span>)}</div>
        <div className="world-map-viewport" ref={frameRef}>
          <canvas
            ref={canvasRef}
            aria-label="Interactive FRIDAY global map"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerLeave}
            onPointerLeave={onPointerLeave}
            onWheel={onWheel}
          />
          <div className="world-map-hologram-base"><i /><i /><i /></div>
          {loading ? <div className="world-map-loading"><span /><b>BUILDING GLOBAL GEOMETRY</b><small>COUNTRIES · PLACES · COORDINATES</small></div> : null}
          <div className="world-map-search">
            <input value={search} onChange={event => setSearch(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') submitSearch() }} placeholder="COUNTRY OR CITY" aria-label="Search country or city" />
            <button type="button" onClick={submitSearch}>FOCUS</button>
            <button type="button" onClick={resetView}>RESET</button>
          </div>
          <div className="world-map-projection-readout"><span>{projection === 'flat' ? 'EQUIRECTANGULAR' : 'ORTHOGRAPHIC'}</span><b>{zoomDisplay.toFixed(2)}×</b><small>TIME LAYER ACTIVE</small></div>
          {hoveredCountry || selectedCountry ? <CountryReadout feature={selectedCountry || hoveredCountry!} selected={Boolean(selectedCountry)} /> : null}
        </div>
      </main>

      <aside className="world-map-right-rail">
        <MapPanel title="ROUTE ANALYSIS" className="route-panel">
          {resolvedRoute ? (
            <>
              <RouteStatus icon="✈" label="GEODESIC AIR PATH" value={`${Math.round(resolvedRoute.distanceKm).toLocaleString()} KM`} />
              <RouteStatus icon="◈" label="EST. FLIGHT" value={formatDurationHours(estimateFlightDurationHours(resolvedRoute.distanceKm))} />
              <RouteStatus icon="⌁" label="SEA / RAIL" value="PROVIDER REQUIRED" />
              <div className="route-endpoints"><small>{resolvedRoute.from}</small><i /><small>{resolvedRoute.to}</small></div>
            </>
          ) : <EmptyPanel icon="⌁" text="NO ROUTE SELECTED" />}
        </MapPanel>
        <MapPanel title="ACTIVE OPERATIONS" className="operations-panel">
          <div className="operations-ring"><b>{activeNodes}</b><small>NODES</small></div>
          <div className="operations-list"><span><i />GEOMETRY <b>{dataset.countries.length}</b></span><span><i />PLACES <b>{dataset.places.length}</b></span><span><i />ROUTES <b>{resolvedRoute ? 1 : 0}</b></span></div>
        </MapPanel>
        <MapPanel title="THREAT MATRIX" className="threat-panel">
          <MiniGlobe />
          <div><StatusRow label="CYBER" value="STANDBY" state="stable" /><StatusRow label="NATURAL" value="STANDBY" state="stable" /><StatusRow label="ECONOMIC" value="STANDBY" state="stable" /></div>
        </MapPanel>
        <MapPanel title="SYSTEM HEALTH" className="health-panel">
          <StatusRow label="GEOMETRY CORE" value="100%" state="stable" />
          <StatusRow label="CACHE" value={dataset.source === 'cache' ? 'ACTIVE' : 'READY'} state="stable" />
          <StatusRow label="RENDERER" value={`${fps} FPS`} state={fps < 24 ? 'monitoring' : 'stable'} />
          <StatusRow label="LOCATION" value={effectiveLocation ? 'LOCKED' : 'STANDBY'} state={effectiveLocation ? 'stable' : 'monitoring'} />
        </MapPanel>
      </aside>

      <footer className="world-map-bottom-strip">
        <div className="world-map-live-log"><small>REAL-TIME LOG</small><span>{dataset.source.toUpperCase()} GEOMETRY CHANNEL ONLINE</span><span>{projection.toUpperCase()} PROJECTION SYNCHRONIZED</span><span>{resolvedRoute ? 'ROUTE VECTOR ACTIVE' : 'ROUTE VECTOR STANDBY'}</span></div>
        <div className="world-map-sync"><WaveBars /><b>GLOBAL SYNCHRONIZATION</b><span>{loading ? 'BUILDING' : 'OPERATIONAL'}</span></div>
        <div className="world-map-key-metrics"><Metric label="FPS" value={`${fps}`} /><Metric label="COUNTRIES" value={`${dataset.countries.length}`} /><Metric label="NODES" value={`${activeNodes}`} /><Metric label="ZOOM" value={`${zoomDisplay.toFixed(2)}×`} /></div>
      </footer>

      {controlsOpen ? (
        <div className="world-map-layer-drawer">
          <header><b>MAP LAYERS</b><button type="button" onClick={() => setControlsOpen(false)}>×</button></header>
          {(Object.keys(layers) as LayerKey[]).map(layer => <button type="button" key={layer} onClick={() => toggleLayer(layer)} className={layers[layer] ? 'active' : ''}><i />{layer.toUpperCase()}<span>{layers[layer] ? 'ON' : 'OFF'}</span></button>)}
          <small>DEEP LOCAL DETAIL LOADS THROUGH CONFIGURED VECTOR-TILE OR GEOCODING PROVIDERS. GLOBAL GEOMETRY REMAINS AVAILABLE FROM CACHE.</small>
        </div>
      ) : null}
    </section>
  )
}

function renderMap(options: {
  context: CanvasRenderingContext2D
  width: number
  height: number
  timestamp: number
  projection: ProjectionMode
  dataset: WorldMapDataset
  layers: Record<LayerKey, boolean>
  route: ResolvedRoute | null
  location: GeoCoordinate | null
  selectedCountry: CountryFeature | null
  hoveredCountry: CountryFeature | null
  flatCamera: FlatCamera
  globeCamera: GlobeCamera
  hitRegions: HitRegion[]
}) {
  const { context, width, height, timestamp, projection, dataset, layers, route, location, selectedCountry, hoveredCountry, flatCamera, globeCamera, hitRegions } = options
  const time = timestamp / 1000
  const project = (coordinate: GeoCoordinate) => projection === 'flat'
    ? projectFlat(coordinate, width, height, flatCamera)
    : projectGlobe(coordinate, width, height, globeCamera)

  drawMapBackground(context, width, height, projection, time, layers.terrain)
  if (layers.grid) drawGraticule(context, width, height, project, projection)
  hitRegions.length = 0
  if (layers.countries) drawCountries(context, dataset.countries, project, projection, width, selectedCountry, hoveredCountry, hitRegions)
  drawOceanLabels(context, OCEAN_LABELS, project, projection)
  if (layers.places) drawPlaces(context, dataset.places, project, projection, projection === 'flat' ? flatCamera.zoom : globeCamera.zoom, time)
  if (layers.routes && route) drawRoute(context, route, project, width, time)
  if (layers.signals) drawSignalNetwork(context, dataset.places, project, time, projection)
  if (location) drawLocationLock(context, location, project, time)
  drawTemporalSweep(context, width, height, time, projection)
}

function drawMapBackground(context: CanvasRenderingContext2D, width: number, height: number, projection: ProjectionMode, time: number, terrain: boolean) {
  const gradient = context.createRadialGradient(width * .5, height * .48, 0, width * .5, height * .48, Math.max(width, height) * .7)
  gradient.addColorStop(0, 'rgba(11, 58, 109, .34)')
  gradient.addColorStop(.46, 'rgba(3, 20, 43, .28)')
  gradient.addColorStop(1, 'rgba(0, 4, 12, 0)')
  context.fillStyle = gradient
  context.fillRect(0, 0, width, height)

  if (projection === 'globe') {
    const radius = Math.min(width, height) * .445
    context.save()
    context.shadowBlur = 48
    context.shadowColor = 'rgba(27, 139, 255, .44)'
    const sphere = context.createRadialGradient(width * .42, height * .37, radius * .04, width / 2, height / 2, radius)
    sphere.addColorStop(0, 'rgba(72, 172, 255, .22)')
    sphere.addColorStop(.45, 'rgba(3, 31, 68, .85)')
    sphere.addColorStop(.84, 'rgba(1, 10, 25, .96)')
    sphere.addColorStop(1, 'rgba(0, 3, 10, .98)')
    context.fillStyle = sphere
    context.beginPath()
    context.arc(width / 2, height / 2, radius, 0, Math.PI * 2)
    context.fill()
    context.strokeStyle = 'rgba(95, 205, 255, .66)'
    context.lineWidth = 1.3
    context.stroke()
    context.restore()
  }

  if (terrain) {
    context.save()
    context.globalAlpha = .17
    context.strokeStyle = 'rgba(39, 131, 221, .23)'
    context.lineWidth = 1
    const offset = (time * 10) % 42
    for (let y = -42 + offset; y < height + 42; y += 42) {
      context.beginPath(); context.moveTo(0, y); context.lineTo(width, y); context.stroke()
    }
    context.restore()
  }
}

function drawGraticule(context: CanvasRenderingContext2D, width: number, height: number, project: (coordinate: GeoCoordinate) => ProjectedPoint, projection: ProjectionMode) {
  context.save()
  context.strokeStyle = projection === 'globe' ? 'rgba(91, 190, 255, .14)' : 'rgba(71, 158, 235, .12)'
  context.lineWidth = .7
  for (let longitude = -180; longitude <= 180; longitude += 15) {
    drawProjectedLine(context, Array.from({ length: 73 }, (_, index) => [longitude, -90 + index * 2.5] as GeoCoordinate), project, width)
  }
  for (let latitude = -75; latitude <= 75; latitude += 15) {
    drawProjectedLine(context, Array.from({ length: 145 }, (_, index) => [-180 + index * 2.5, latitude] as GeoCoordinate), project, width)
  }
  context.restore()
}

function drawCountries(
  context: CanvasRenderingContext2D,
  countries: CountryFeature[],
  project: (coordinate: GeoCoordinate) => ProjectedPoint,
  projection: ProjectionMode,
  width: number,
  selectedCountry: CountryFeature | null,
  hoveredCountry: CountryFeature | null,
  hitRegions: HitRegion[],
) {
  const selectedName = countryName(selectedCountry)
  const hoveredName = countryName(hoveredCountry)
  for (const feature of countries) {
    const path = countryPath(feature, project, projection, width)
    if (!path) continue
    const name = countryName(feature)
    const selected = name === selectedName
    const hovered = name === hoveredName
    context.save()
    context.fillStyle = selected
      ? 'rgba(25, 129, 230, .62)'
      : hovered
        ? 'rgba(18, 96, 177, .55)'
        : 'rgba(8, 55, 103, .46)'
    context.strokeStyle = selected
      ? 'rgba(191, 242, 255, .98)'
      : hovered
        ? 'rgba(116, 218, 255, .92)'
        : 'rgba(90, 185, 247, .57)'
    context.lineWidth = selected ? 1.85 : hovered ? 1.45 : .72
    context.shadowBlur = selected ? 20 : hovered ? 12 : 4
    context.shadowColor = selected ? 'rgba(33, 154, 255, .92)' : 'rgba(31, 126, 218, .38)'
    context.fill(path, 'evenodd')
    context.stroke(path)
    context.restore()
    hitRegions.push({ path, feature })
  }

  context.save()
  context.globalCompositeOperation = 'screen'
  context.strokeStyle = 'rgba(136, 225, 255, .08)'
  context.lineWidth = 4
  for (const region of hitRegions) context.stroke(region.path)
  context.restore()
}

function drawOceanLabels(context: CanvasRenderingContext2D, labels: Array<{ name: string; coordinate: GeoCoordinate }>, project: (coordinate: GeoCoordinate) => ProjectedPoint, projection: ProjectionMode) {
  context.save()
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.font = '600 9px ui-monospace, SFMono-Regular, Consolas, monospace'
  context.fillStyle = 'rgba(51, 153, 255, .54)'
  context.shadowBlur = 8
  context.shadowColor = 'rgba(0, 65, 170, .7)'
  for (const label of labels) {
    const point = project(label.coordinate)
    if (!point.visible || (projection === 'globe' && point.depth < .2)) continue
    const lines = label.name.split(' ')
    lines.forEach((line, index) => context.fillText(line, point.x, point.y + (index - (lines.length - 1) / 2) * 12))
  }
  context.restore()
}

function drawPlaces(context: CanvasRenderingContext2D, places: WorldPlace[], project: (coordinate: GeoCoordinate) => ProjectedPoint, projection: ProjectionMode, zoom: number, time: number) {
  const rankThreshold = zoom > 4 ? 9 : zoom > 2.4 ? 6 : zoom > 1.35 ? 4 : 2
  context.save()
  for (const place of places) {
    if (place.rank > rankThreshold && !place.capital) continue
    const point = project(place.coordinate)
    if (!point.visible || (projection === 'globe' && point.depth < .06)) continue
    const pulse = 2.1 + (Math.sin(time * 2.3 + place.coordinate[0] * .05) + 1) * .8
    context.beginPath()
    context.fillStyle = place.capital ? 'rgba(215, 250, 255, .98)' : 'rgba(91, 202, 255, .88)'
    context.shadowBlur = 12
    context.shadowColor = 'rgba(45, 165, 255, .96)'
    context.arc(point.x, point.y, place.capital ? pulse + 1 : pulse, 0, Math.PI * 2)
    context.fill()
    if (zoom > 1.55 || place.rank <= 1) {
      context.shadowBlur = 5
      context.font = `${place.capital ? 600 : 500} ${zoom > 3 ? 9 : 7}px ui-monospace, SFMono-Regular, Consolas, monospace`
      context.fillStyle = place.capital ? 'rgba(231, 248, 255, .92)' : 'rgba(178, 216, 240, .64)'
      context.textAlign = 'left'
      context.fillText(place.name.toUpperCase(), point.x + 7, point.y - 5)
    }
  }
  context.restore()
}

function drawRoute(context: CanvasRenderingContext2D, route: ResolvedRoute, project: (coordinate: GeoCoordinate) => ProjectedPoint, width: number, time: number) {
  context.save()
  context.strokeStyle = 'rgba(106, 216, 255, .94)'
  context.lineWidth = 2.1
  context.shadowBlur = 14
  context.shadowColor = 'rgba(36, 151, 255, .95)'
  context.setLineDash([12, 8])
  context.lineDashOffset = -time * 28
  drawProjectedLine(context, route.points, project, width)
  context.setLineDash([])

  const tracer = routePointAt(route.points, time * .075)
  const point = project(tracer)
  if (point.visible) {
    const glow = context.createRadialGradient(point.x, point.y, 0, point.x, point.y, 22)
    glow.addColorStop(0, 'rgba(255,255,255,1)')
    glow.addColorStop(.2, 'rgba(111,224,255,.95)')
    glow.addColorStop(1, 'rgba(24,113,255,0)')
    context.fillStyle = glow
    context.beginPath(); context.arc(point.x, point.y, 22, 0, Math.PI * 2); context.fill()
  }

  drawRouteEndpoint(context, project(route.fromCoordinate), route.from)
  drawRouteEndpoint(context, project(route.toCoordinate), route.to)
  context.restore()
}

function drawSignalNetwork(context: CanvasRenderingContext2D, places: WorldPlace[], project: (coordinate: GeoCoordinate) => ProjectedPoint, time: number, projection: ProjectionMode) {
  const nodes = places.filter(place => place.rank <= 2).slice(0, 34)
  context.save()
  context.globalAlpha = .16
  context.strokeStyle = 'rgba(66, 167, 255, .5)'
  context.lineWidth = .6
  for (let index = 0; index < nodes.length; index += 2) {
    const a = project(nodes[index].coordinate)
    const b = project(nodes[(index + 7) % nodes.length].coordinate)
    if (!a.visible || !b.visible || (projection === 'globe' && (a.depth < .04 || b.depth < .04))) continue
    context.beginPath()
    context.moveTo(a.x, a.y)
    const controlX = (a.x + b.x) / 2
    const controlY = (a.y + b.y) / 2 - Math.min(70, Math.abs(b.x - a.x) * .18)
    context.quadraticCurveTo(controlX, controlY, b.x, b.y)
    context.stroke()
  }
  context.globalAlpha = .3 + Math.sin(time * 1.2) * .08
  context.restore()
}

function drawLocationLock(context: CanvasRenderingContext2D, coordinate: GeoCoordinate, project: (coordinate: GeoCoordinate) => ProjectedPoint, time: number) {
  const point = project(coordinate)
  if (!point.visible) return
  context.save()
  context.translate(point.x, point.y)
  context.strokeStyle = 'rgba(115, 230, 255, .92)'
  context.fillStyle = 'rgba(220, 252, 255, .98)'
  context.shadowBlur = 20
  context.shadowColor = 'rgba(47, 169, 255, 1)'
  for (let ring = 0; ring < 3; ring += 1) {
    const radius = 13 + ring * 12 + ((time * 18 + ring * 8) % 12)
    context.globalAlpha = 1 - ring * .22
    context.beginPath(); context.arc(0, 0, radius, 0, Math.PI * 2); context.stroke()
  }
  context.globalAlpha = 1
  context.beginPath(); context.arc(0, 0, 5, 0, Math.PI * 2); context.fill()
  context.restore()
}

function drawTemporalSweep(context: CanvasRenderingContext2D, width: number, height: number, time: number, projection: ProjectionMode) {
  context.save()
  context.globalCompositeOperation = 'screen'
  if (projection === 'flat') {
    const x = ((time * 52) % (width + 220)) - 110
    const gradient = context.createLinearGradient(x - 55, 0, x + 55, 0)
    gradient.addColorStop(0, 'rgba(38, 144, 255, 0)')
    gradient.addColorStop(.5, 'rgba(91, 207, 255, .13)')
    gradient.addColorStop(1, 'rgba(38, 144, 255, 0)')
    context.fillStyle = gradient
    context.fillRect(x - 55, 0, 110, height)
  } else {
    const radius = Math.min(width, height) * (.08 + ((time * .08) % .48))
    context.strokeStyle = `rgba(92, 210, 255, ${Math.max(0, .22 - radius / Math.min(width, height) * .25)})`
    context.lineWidth = 1.2
    context.beginPath(); context.arc(width / 2, height / 2, radius, 0, Math.PI * 2); context.stroke()
  }
  context.restore()
}

function drawProjectedLine(context: CanvasRenderingContext2D, coordinates: GeoCoordinate[], project: (coordinate: GeoCoordinate) => ProjectedPoint, width: number) {
  context.beginPath()
  let previous: ProjectedPoint | null = null
  let drawing = false
  for (const coordinate of coordinates) {
    const point = project(coordinate)
    const discontinuity = previous && Math.abs(point.x - previous.x) > width * .55
    if (!point.visible || discontinuity) {
      drawing = false
      previous = point
      continue
    }
    if (!drawing) {
      context.moveTo(point.x, point.y)
      drawing = true
    } else {
      context.lineTo(point.x, point.y)
    }
    previous = point
  }
  context.stroke()
}

function countryPath(feature: CountryFeature, project: (coordinate: GeoCoordinate) => ProjectedPoint, projection: ProjectionMode, width: number): Path2D | null {
  const path = new Path2D()
  const polygons = feature.geometry.type === 'Polygon' ? [feature.geometry.coordinates] : feature.geometry.coordinates
  let segments = 0
  for (const polygon of polygons) {
    for (const ring of polygon) {
      let previous: ProjectedPoint | null = null
      let drawing = false
      for (const coordinate of ring) {
        const point = project(coordinate)
        const discontinuity = previous && Math.abs(point.x - previous.x) > width * .5
        const hidden = !point.visible || (projection === 'globe' && point.depth < -.005)
        if (hidden || discontinuity) {
          drawing = false
          previous = point
          continue
        }
        if (!drawing) {
          path.moveTo(point.x, point.y)
          drawing = true
          segments += 1
        } else {
          path.lineTo(point.x, point.y)
        }
        previous = point
      }
      if (drawing) path.closePath()
    }
  }
  return segments ? path : null
}

function drawRouteEndpoint(context: CanvasRenderingContext2D, point: ProjectedPoint, label: string) {
  if (!point.visible) return
  context.save()
  context.fillStyle = 'rgba(234, 253, 255, .98)'
  context.strokeStyle = 'rgba(38, 151, 255, .98)'
  context.lineWidth = 5
  context.shadowBlur = 15
  context.shadowColor = 'rgba(38, 151, 255, .95)'
  context.beginPath(); context.arc(point.x, point.y, 7, 0, Math.PI * 2); context.fill(); context.stroke()
  context.font = '600 8px ui-monospace, SFMono-Regular, Consolas, monospace'
  context.textAlign = 'left'
  context.fillStyle = 'rgba(238, 250, 255, .94)'
  context.fillText(label.toUpperCase(), point.x + 12, point.y - 8)
  context.restore()
}

function resolveRoute(request: RouteRequest | null | undefined, dataset: WorldMapDataset): ResolvedRoute | null {
  if (!request) return null
  const from = resolveCoordinate(request.from, dataset)
  const to = resolveCoordinate(request.to, dataset)
  if (!from || !to) return null
  const points = interpolateGreatCircle(from, to, 128)
  return { ...request, fromCoordinate: from, toCoordinate: to, points, distanceKm: greatCircleDistanceKm(from, to) }
}

function resolveCoordinate(query: string, dataset: WorldMapDataset): GeoCoordinate | null {
  const place = findPlace(dataset.places, query)
  if (place) return place.coordinate
  const normalized = query.toLowerCase()
  const country = dataset.countries.find(feature => countryName(feature).toLowerCase().includes(normalized))
  return countryLabel(country!)?.coordinate || null
}

function focusCoordinate(coordinate: GeoCoordinate, projection: ProjectionMode, flat: FlatCamera, globe: GlobeCamera, zoomIn = false) {
  if (projection === 'flat') {
    flat.centerLongitude = coordinate[0]
    flat.panX = 0
    flat.panY = coordinate[1] * 2.1 * (zoomIn ? 1.2 : 1)
    if (zoomIn) flat.zoom = Math.max(flat.zoom, 2.2)
  } else {
    globe.rotationLongitude = coordinate[0]
    globe.rotationLatitude = coordinate[1]
    if (zoomIn) globe.zoom = Math.max(globe.zoom, 1.35)
  }
}

function countRegions(countries: CountryFeature[]): Map<string, number> {
  const counts = new Map<string, number>()
  for (const country of countries) {
    const region = country.properties.CONTINENT || 'Unknown'
    counts.set(region, (counts.get(region) || 0) + 1)
  }
  return counts
}

function countryName(feature: CountryFeature | null | undefined): string {
  return feature?.properties.NAME_LONG || feature?.properties.ADMIN || feature?.properties.NAME || ''
}

function formatLongitude(value: number): string {
  if (value === 0) return '0°'
  return `${Math.abs(value)}°${value < 0 ? 'W' : 'E'}`
}

function formatLatitude(value: number): string {
  if (value === 0) return '0°'
  return `${Math.abs(value)}°${value < 0 ? 'S' : 'N'}`
}

function MapPanel({ title, className = '', children }: { title: string; className?: string; children: React.ReactNode }) {
  return <section className={`world-map-panel ${className}`}><header><i />{title}</header><div className="world-map-panel-body">{children}</div></section>
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="world-map-metric"><small>{label}</small><b>{value}</b></div>
}

function StatusRow({ label, value, state }: { label: string; value: string; state: 'stable' | 'monitoring' | 'critical' }) {
  return <div className={`world-map-status-row ${state}`}><i /><span>{label}</span><b>{value}</b></div>
}

function RouteStatus({ icon, label, value }: { icon: string; label: string; value: string }) {
  return <div className="world-map-route-status"><i>{icon}</i><span>{label}</span><b>{value}</b></div>
}

function EmptyPanel({ icon, text }: { icon: string; text: string }) {
  return <div className="world-map-empty"><i>{icon}</i><span>{text}</span></div>
}

function CountryReadout({ feature, selected }: { feature: CountryFeature; selected: boolean }) {
  const properties = feature.properties
  const label = countryLabel(feature)
  return (
    <div className={`world-map-country-readout ${selected ? 'selected' : ''}`}>
      <small>{selected ? 'COUNTRY LOCK' : 'COUNTRY FOCUS'}</small>
      <b>{countryName(feature).toUpperCase()}</b>
      <span>{properties.CONTINENT || 'GLOBAL'} · {properties.SUBREGION || 'REGION'}</span>
      {label ? <em>{coordinateToDms(label.coordinate[1], 'N', 'S')} · {coordinateToDms(label.coordinate[0], 'E', 'W')}</em> : null}
    </div>
  )
}

function WorldMapLogo() {
  return (
    <svg viewBox="0 0 100 100" aria-hidden="true">
      <defs><linearGradient id="worldMapLogoEdge" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#e5fbff"/><stop offset=".38" stopColor="#54c8ff"/><stop offset="1" stopColor="#126dff"/></linearGradient></defs>
      <g fill="none" stroke="url(#worldMapLogoEdge)"><path d="M50 4 87 25v50L50 96 13 75V25Z" strokeWidth="2.3"/><path d="M50 12 79 29v42L50 88 21 71V29Z" opacity=".62"/></g>
      <path d="M34 27h34v11H46v10h18v10H46v20H34Z" fill="url(#worldMapLogoEdge)"/>
      <circle cx="50" cy="50" r="3" fill="#e7fcff"/>
    </svg>
  )
}

function MiniGlobe() {
  return <div className="world-map-mini-globe"><i /><i /><i /><span /></div>
}

function WaveBars() {
  return <div className="world-map-wave-bars">{Array.from({ length: 33 }, (_, index) => <i key={index} style={{ height: `${4 + Math.sin(index * .78) ** 2 * 17}px` }} />)}</div>
}
