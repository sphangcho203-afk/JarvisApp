export type GeoCoordinate = [longitude: number, latitude: number]
export type ProjectedPoint = { x: number; y: number; visible: boolean; depth: number }

export type FlatCamera = {
  zoom: number
  panX: number
  panY: number
  centerLongitude: number
}

export type GlobeCamera = {
  rotationLongitude: number
  rotationLatitude: number
  zoom: number
}

const DEG = Math.PI / 180
const RAD = 180 / Math.PI
const EARTH_RADIUS_KM = 6371.0088

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

export function wrapLongitude(longitude: number): number {
  let value = longitude
  while (value > 180) value -= 360
  while (value < -180) value += 360
  return value
}

export function projectFlat(
  coordinate: GeoCoordinate,
  width: number,
  height: number,
  camera: FlatCamera,
): ProjectedPoint {
  const longitude = wrapLongitude(coordinate[0] - camera.centerLongitude)
  const latitude = clamp(coordinate[1], -89.999, 89.999)
  const scale = camera.zoom
  const x = width / 2 + (longitude / 360) * width * scale + camera.panX
  const y = height / 2 - (latitude / 180) * height * scale + camera.panY
  return { x, y, visible: x > -width && x < width * 2 && y > -height && y < height * 2, depth: 1 }
}

export function projectGlobe(
  coordinate: GeoCoordinate,
  width: number,
  height: number,
  camera: GlobeCamera,
): ProjectedPoint {
  const longitude = (coordinate[0] - camera.rotationLongitude) * DEG
  const latitude = coordinate[1] * DEG
  const rotationLatitude = camera.rotationLatitude * DEG

  const cosLat = Math.cos(latitude)
  const sinLat = Math.sin(latitude)
  const cosLon = Math.cos(longitude)
  const sinLon = Math.sin(longitude)
  const cosRot = Math.cos(rotationLatitude)
  const sinRot = Math.sin(rotationLatitude)

  const sphereX = cosLat * sinLon
  const sphereY = sinLat * cosRot - cosLat * cosLon * sinRot
  const sphereZ = sinLat * sinRot + cosLat * cosLon * cosRot
  const radius = Math.min(width, height) * 0.445 * camera.zoom

  return {
    x: width / 2 + sphereX * radius,
    y: height / 2 - sphereY * radius,
    visible: sphereZ >= -0.012,
    depth: sphereZ,
  }
}

export function greatCircleDistanceKm(a: GeoCoordinate, b: GeoCoordinate): number {
  const lat1 = a[1] * DEG
  const lat2 = b[1] * DEG
  const deltaLat = (b[1] - a[1]) * DEG
  const deltaLon = (b[0] - a[0]) * DEG
  const h = Math.sin(deltaLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)))
}

export function interpolateGreatCircle(a: GeoCoordinate, b: GeoCoordinate, steps = 96): GeoCoordinate[] {
  const start = lonLatToVector(a)
  const end = lonLatToVector(b)
  const dot = clamp(start.x * end.x + start.y * end.y + start.z * end.z, -1, 1)
  const omega = Math.acos(dot)
  const sinOmega = Math.sin(omega)

  if (Math.abs(sinOmega) < 1e-7) return [a, b]

  const result: GeoCoordinate[] = []
  for (let index = 0; index <= steps; index += 1) {
    const t = index / steps
    const scaleA = Math.sin((1 - t) * omega) / sinOmega
    const scaleB = Math.sin(t * omega) / sinOmega
    const vector = {
      x: start.x * scaleA + end.x * scaleB,
      y: start.y * scaleA + end.y * scaleB,
      z: start.z * scaleA + end.z * scaleB,
    }
    result.push(vectorToLonLat(vector))
  }
  return result
}

export function routePointAt(route: GeoCoordinate[], progress: number): GeoCoordinate {
  if (route.length === 0) return [0, 0]
  if (route.length === 1) return route[0]
  const normalized = ((progress % 1) + 1) % 1
  const position = normalized * (route.length - 1)
  const index = Math.min(route.length - 2, Math.floor(position))
  const local = position - index
  const a = route[index]
  const b = route[index + 1]
  return [wrapLongitude(a[0] + (b[0] - a[0]) * local), a[1] + (b[1] - a[1]) * local]
}

export function coordinateToDms(value: number, positive: string, negative: string): string {
  const absolute = Math.abs(value)
  const degrees = Math.floor(absolute)
  const minutesFloat = (absolute - degrees) * 60
  const minutes = Math.floor(minutesFloat)
  const seconds = (minutesFloat - minutes) * 60
  return `${degrees}°${minutes.toString().padStart(2, '0')}′${seconds.toFixed(1).padStart(4, '0')}″ ${value >= 0 ? positive : negative}`
}

export function estimateFlightDurationHours(distanceKm: number, cruiseKmh = 845): number {
  return distanceKm / cruiseKmh
}

export function formatDurationHours(hours: number): string {
  const totalMinutes = Math.max(0, Math.round(hours * 60))
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return `${h}h ${m.toString().padStart(2, '0')}m`
}

export function screenTilt(pointerX: number, pointerY: number, width: number, height: number): { rotateX: number; rotateY: number } {
  const normalizedX = clamp((pointerX / Math.max(1, width)) * 2 - 1, -1, 1)
  const normalizedY = clamp((pointerY / Math.max(1, height)) * 2 - 1, -1, 1)
  return { rotateX: normalizedY * -3.4, rotateY: normalizedX * 4.2 }
}

function lonLatToVector(coordinate: GeoCoordinate): { x: number; y: number; z: number } {
  const longitude = coordinate[0] * DEG
  const latitude = coordinate[1] * DEG
  const cosLatitude = Math.cos(latitude)
  return {
    x: cosLatitude * Math.cos(longitude),
    y: cosLatitude * Math.sin(longitude),
    z: Math.sin(latitude),
  }
}

function vectorToLonLat(vector: { x: number; y: number; z: number }): GeoCoordinate {
  const magnitude = Math.hypot(vector.x, vector.y, vector.z) || 1
  const x = vector.x / magnitude
  const y = vector.y / magnitude
  const z = vector.z / magnitude
  return [Math.atan2(y, x) * RAD, Math.asin(clamp(z, -1, 1)) * RAD]
}
