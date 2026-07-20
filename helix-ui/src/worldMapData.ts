import type { GeoCoordinate } from './worldMapMath'

export type CountryProperties = {
  ADMIN?: string
  NAME?: string
  NAME_LONG?: string
  ISO_A2?: string
  ISO_A3?: string
  CONTINENT?: string
  SUBREGION?: string
  POP_EST?: number
  GDP_MD?: number
  LABEL_X?: number
  LABEL_Y?: number
  LABELRANK?: number
  MIN_ZOOM?: number
}

export type GeoPolygon = {
  type: 'Polygon'
  coordinates: GeoCoordinate[][]
}

export type GeoMultiPolygon = {
  type: 'MultiPolygon'
  coordinates: GeoCoordinate[][][]
}

export type CountryFeature = {
  type: 'Feature'
  properties: CountryProperties
  geometry: GeoPolygon | GeoMultiPolygon
  bbox?: [number, number, number, number]
}

export type CountryCollection = {
  type: 'FeatureCollection'
  features: CountryFeature[]
}

export type WorldPlace = {
  id: string
  name: string
  country: string
  coordinate: GeoCoordinate
  rank: number
  population: number
  capital: boolean
}

export type WorldMapDataset = {
  countries: CountryFeature[]
  places: WorldPlace[]
  source: 'network' | 'cache' | 'fallback'
  updatedAt: number
}

const CACHE_NAME = 'friday-world-map-master-v1'
const CACHE_VERSION = 1
const STORE_NAME = 'geospatial'
const COUNTRY_KEY = 'countries-110m'
const PLACE_KEY = 'places-110m'

const COUNTRY_ENDPOINTS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_admin_0_countries.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson',
]

const PLACE_ENDPOINTS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_populated_places_simple.geojson',
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_populated_places_simple.geojson',
]

const FALLBACK_PLACES: WorldPlace[] = [
  place('new-delhi', 'New Delhi', 'India', 77.209, 28.6139, 1, 33800000, true),
  place('tokyo', 'Tokyo', 'Japan', 139.6917, 35.6895, 1, 37400000, true),
  place('london', 'London', 'United Kingdom', -0.1276, 51.5072, 1, 14900000, true),
  place('new-york', 'New York', 'United States', -74.006, 40.7128, 1, 18800000, false),
  place('singapore', 'Singapore', 'Singapore', 103.8198, 1.3521, 1, 5920000, true),
  place('sydney', 'Sydney', 'Australia', 151.2093, -33.8688, 1, 5310000, false),
  place('dubai', 'Dubai', 'United Arab Emirates', 55.2708, 25.2048, 2, 3560000, false),
  place('moscow', 'Moscow', 'Russia', 37.6173, 55.7558, 1, 12600000, true),
  place('beijing', 'Beijing', 'China', 116.4074, 39.9042, 1, 21800000, true),
  place('cairo', 'Cairo', 'Egypt', 31.2357, 30.0444, 1, 22100000, true),
  place('nairobi', 'Nairobi', 'Kenya', 36.8219, -1.2921, 2, 5500000, true),
  place('sao-paulo', 'São Paulo', 'Brazil', -46.6333, -23.5505, 1, 22400000, false),
  place('mexico-city', 'Mexico City', 'Mexico', -99.1332, 19.4326, 1, 22100000, true),
  place('los-angeles', 'Los Angeles', 'United States', -118.2437, 34.0522, 1, 12800000, false),
  place('vancouver', 'Vancouver', 'Canada', -123.1207, 49.2827, 3, 2640000, false),
  place('cape-town', 'Cape Town', 'South Africa', 18.4241, -33.9249, 3, 4890000, false),
]

const FALLBACK_COUNTRIES: CountryFeature[] = [
  fallbackCountry('North America', 'North America', [
    [[-168,72],[-150,70],[-137,58],[-125,51],[-118,32],[-105,24],[-93,17],[-82,22],[-80,31],[-67,45],[-55,52],[-62,60],[-84,70],[-112,75],[-140,74],[-168,72]],
  ], -104, 50),
  fallbackCountry('South America', 'South America', [
    [[-81,12],[-69,10],[-50,4],[-35,-6],[-42,-24],[-54,-36],[-67,-55],[-75,-43],[-79,-20],[-81,12]],
  ], -60, -16),
  fallbackCountry('Europe and Asia', 'Eurasia', [
    [[-10,36],[4,45],[28,60],[55,72],[92,77],[135,65],[165,56],[176,43],[145,34],[128,20],[110,6],[90,8],[72,22],[52,28],[38,40],[20,34],[5,36],[-10,36]],
  ], 70, 48),
  fallbackCountry('Africa', 'Africa', [
    [[-17,36],[12,37],[34,31],[51,12],[43,-13],[31,-35],[11,-35],[-2,-22],[-16,5],[-17,36]],
  ], 19, 4),
  fallbackCountry('Australia', 'Oceania', [
    [[112,-11],[153,-10],[154,-39],[132,-44],[113,-33],[112,-11]],
  ], 134, -25),
  fallbackCountry('Greenland', 'North America', [
    [[-73,59],[-48,58],[-18,69],[-28,83],[-57,84],[-73,72],[-73,59]],
  ], -42, 72),
  fallbackCountry('Antarctica', 'Antarctica', [
    [[-180,-69],[-125,-72],[-65,-70],[0,-74],[70,-69],[130,-72],[180,-69],[180,-90],[-180,-90],[-180,-69]],
  ], 0, -79),
]

export async function loadWorldMapDataset(signal?: AbortSignal): Promise<WorldMapDataset> {
  const cached = await readCachedDataset().catch(() => null)
  const networkPromise = loadNetworkDataset(signal)

  if (cached && Date.now() - cached.updatedAt < 1000 * 60 * 60 * 24 * 30) {
    networkPromise.then(dataset => writeCachedDataset(dataset).catch(() => undefined)).catch(() => undefined)
    return { ...cached, source: 'cache' }
  }

  try {
    const network = await networkPromise
    await writeCachedDataset(network).catch(() => undefined)
    return network
  } catch {
    if (cached) return { ...cached, source: 'cache' }
    return { countries: FALLBACK_COUNTRIES, places: FALLBACK_PLACES, source: 'fallback', updatedAt: Date.now() }
  }
}

export function findPlace(places: WorldPlace[], query: string): WorldPlace | null {
  const normalized = normalize(query)
  if (!normalized) return null
  const exact = places.find(item => normalize(item.name) === normalized || normalize(item.country) === normalized)
  if (exact) return exact
  const contains = places.find(item => normalize(item.name).includes(normalized) || normalized.includes(normalize(item.name)))
  return contains || null
}

export function countryLabel(feature: CountryFeature): { name: string; coordinate: GeoCoordinate; rank: number } | null {
  const longitude = feature.properties.LABEL_X
  const latitude = feature.properties.LABEL_Y
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
  return {
    name: feature.properties.NAME_LONG || feature.properties.ADMIN || feature.properties.NAME || 'Unknown',
    coordinate: [longitude as number, latitude as number],
    rank: feature.properties.LABELRANK || 8,
  }
}

async function loadNetworkDataset(signal?: AbortSignal): Promise<WorldMapDataset> {
  const [countryJson, placeJson] = await Promise.all([
    fetchFirstJson(COUNTRY_ENDPOINTS, signal),
    fetchFirstJson(PLACE_ENDPOINTS, signal),
  ])
  const countries = sanitizeCountries(countryJson)
  const places = sanitizePlaces(placeJson)
  if (countries.length < 120) throw new Error('World geometry incomplete')
  return { countries, places: places.length > 40 ? places : FALLBACK_PLACES, source: 'network', updatedAt: Date.now() }
}

async function fetchFirstJson(endpoints: string[], signal?: AbortSignal): Promise<unknown> {
  let lastError: unknown = null
  for (const endpoint of endpoints) {
    try {
      const controller = new AbortController()
      const timeout = window.setTimeout(() => controller.abort(), 12000)
      const relayAbort = () => controller.abort()
      signal?.addEventListener('abort', relayAbort, { once: true })
      try {
        const response = await fetch(endpoint, { cache: 'force-cache', signal: controller.signal })
        if (!response.ok) throw new Error(`Map source returned ${response.status}`)
        return await response.json()
      } finally {
        window.clearTimeout(timeout)
        signal?.removeEventListener('abort', relayAbort)
      }
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('World map sources unavailable')
}

function sanitizeCountries(value: unknown): CountryFeature[] {
  if (!isRecord(value) || !Array.isArray(value.features)) return []
  return value.features.flatMap(raw => {
    if (!isRecord(raw) || !isRecord(raw.properties) || !isRecord(raw.geometry)) return []
    const type = raw.geometry.type
    if (type !== 'Polygon' && type !== 'MultiPolygon') return []
    const coordinates = raw.geometry.coordinates
    if (!Array.isArray(coordinates)) return []
    const properties = raw.properties
    const feature: CountryFeature = {
      type: 'Feature',
      properties: {
        ADMIN: stringValue(properties.ADMIN),
        NAME: stringValue(properties.NAME),
        NAME_LONG: stringValue(properties.NAME_LONG),
        ISO_A2: stringValue(properties.ISO_A2),
        ISO_A3: stringValue(properties.ISO_A3),
        CONTINENT: stringValue(properties.CONTINENT),
        SUBREGION: stringValue(properties.SUBREGION),
        POP_EST: numberValue(properties.POP_EST),
        GDP_MD: numberValue(properties.GDP_MD),
        LABEL_X: numberValue(properties.LABEL_X),
        LABEL_Y: numberValue(properties.LABEL_Y),
        LABELRANK: numberValue(properties.LABELRANK),
        MIN_ZOOM: numberValue(properties.MIN_ZOOM),
      },
      geometry: { type, coordinates } as GeoPolygon | GeoMultiPolygon,
    }
    return [feature]
  })
}

function sanitizePlaces(value: unknown): WorldPlace[] {
  if (!isRecord(value) || !Array.isArray(value.features)) return []
  return value.features.flatMap((raw, index) => {
    if (!isRecord(raw) || !isRecord(raw.properties) || !isRecord(raw.geometry)) return []
    if (raw.geometry.type !== 'Point' || !Array.isArray(raw.geometry.coordinates)) return []
    const longitude = Number(raw.geometry.coordinates[0])
    const latitude = Number(raw.geometry.coordinates[1])
    const name = stringValue(raw.properties.NAMEPAR) || stringValue(raw.properties.NAME) || stringValue(raw.properties.name)
    if (!name || !Number.isFinite(longitude) || !Number.isFinite(latitude)) return []
    const country = stringValue(raw.properties.ADM0NAME) || stringValue(raw.properties.SOV0NAME) || stringValue(raw.properties.adm0name) || ''
    const rank = numberValue(raw.properties.SCALERANK) || numberValue(raw.properties.scalerank) || 8
    const population = numberValue(raw.properties.POP_MAX) || numberValue(raw.properties.pop_max) || 0
    const capitalText = `${stringValue(raw.properties.CAPIN) || ''} ${stringValue(raw.properties.WORLDCITY) || ''}`.toLowerCase()
    return [{
      id: `${normalize(name)}-${index}`,
      name,
      country,
      coordinate: [longitude, latitude] as GeoCoordinate,
      rank,
      population,
      capital: capitalText.includes('world') || capitalText === '1',
    }]
  })
}

async function readCachedDataset(): Promise<WorldMapDataset | null> {
  const database = await openDatabase()
  const transaction = database.transaction(STORE_NAME, 'readonly')
  const store = transaction.objectStore(STORE_NAME)
  const [countries, places] = await Promise.all([
    requestValue<{ value: CountryFeature[]; updatedAt: number } | undefined>(store.get(COUNTRY_KEY)),
    requestValue<{ value: WorldPlace[]; updatedAt: number } | undefined>(store.get(PLACE_KEY)),
  ])
  database.close()
  if (!countries?.value?.length || !places?.value?.length) return null
  return {
    countries: countries.value,
    places: places.value,
    source: 'cache',
    updatedAt: Math.min(countries.updatedAt, places.updatedAt),
  }
}

async function writeCachedDataset(dataset: WorldMapDataset): Promise<void> {
  const database = await openDatabase()
  const transaction = database.transaction(STORE_NAME, 'readwrite')
  const store = transaction.objectStore(STORE_NAME)
  store.put({ value: dataset.countries, updatedAt: dataset.updatedAt }, COUNTRY_KEY)
  store.put({ value: dataset.places, updatedAt: dataset.updatedAt }, PLACE_KEY)
  await transactionComplete(transaction)
  database.close()
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(CACHE_NAME, CACHE_VERSION)
    request.onerror = () => reject(request.error)
    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(STORE_NAME)) database.createObjectStore(STORE_NAME)
    }
    request.onsuccess = () => resolve(request.result)
  })
}

function requestValue<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
  })
}

function transactionComplete(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
}

function fallbackCountry(name: string, continent: string, rings: GeoCoordinate[][], labelX: number, labelY: number): CountryFeature {
  return {
    type: 'Feature',
    properties: { NAME: name, NAME_LONG: name, ADMIN: name, CONTINENT: continent, LABEL_X: labelX, LABEL_Y: labelY, LABELRANK: 2 },
    geometry: { type: 'Polygon', coordinates: rings },
  }
}

function place(id: string, name: string, country: string, longitude: number, latitude: number, rank: number, population: number, capital: boolean): WorldPlace {
  return { id, name, country, coordinate: [longitude, latitude], rank, population, capital }
}

function normalize(value: string): string {
  return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim()
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function numberValue(value: unknown): number | undefined {
  const result = Number(value)
  return Number.isFinite(result) ? result : undefined
}
