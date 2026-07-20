import { Component, useEffect, useMemo, useState, type ErrorInfo, type ReactNode } from 'react'
import { FridayCinematicOS } from './FridayCinematicOS'
import { WorldMapMaster } from './WorldMapMaster'
import { useNativeBridge } from './nativeBridge'

interface BoundaryProps { children: ReactNode }
interface BoundaryState { error: Error | null }

class HelixErrorBoundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): BoundaryState { return { error } }

  componentDidCatch(error: Error, info: ErrorInfo) {
    const detail = `${error.name}: ${error.message}`.slice(0, 280)
    console.error('HELIX_RENDER_FAILURE', error, info.componentStack)
    window.JarvisAndroid?.onHelixError?.(detail)
  }

  render() {
    if (this.state.error) {
      return (
        <main style={{ alignItems: 'center', background: '#02060d', color: '#eaf7ff', display: 'flex', height: '100%', justifyContent: 'center', padding: '28px', textAlign: 'center', width: '100%' }}>
          <section style={{ border: '1px solid rgba(86,185,255,.32)', maxWidth: '520px', padding: '28px', width: '100%' }}>
            <p style={{ color: '#69c4ff', fontSize: '11px', letterSpacing: '.28em' }}>F.R.I.D.A.Y.</p>
            <h1 style={{ fontSize: '18px', fontWeight: 500, letterSpacing: '.16em' }}>VISUAL CORE RECOVERY</h1>
            <p style={{ color: 'rgba(235,248,255,.62)', fontSize: '12px', lineHeight: 1.7 }}>The native intelligence core remains operational.</p>
            <button type="button" onClick={() => window.location.reload()} style={{ background: '#69c4ff', border: 0, color: '#021018', fontSize: '10px', letterSpacing: '.16em', marginTop: '18px', padding: '12px 16px' }}>RESTART VISUAL CORE</button>
          </section>
        </main>
      )
    }
    return this.props.children
  }
}

export default function App() {
  const bridge = useNativeBridge()
  const previewScene = useMemo(() => new URLSearchParams(window.location.search).get('scene')?.toLowerCase() || '', [])
  const [worldMapOpen, setWorldMapOpen] = useState(previewScene === 'world-map')
  const route = useMemo(() => parseRouteRequest(bridge.transcript), [bridge.transcript])
  const briefs = useMemo(() => extractBriefs(bridge.response), [bridge.response])

  useEffect(() => {
    if (shouldOpenWorldMap(bridge.transcript)) setWorldMapOpen(true)
  }, [bridge.transcript])

  const openWorkspace = (workspace: string) => {
    if (workspace === 'worldmap') {
      setWorldMapOpen(true)
      return
    }
    bridge.openWorkspace(workspace)
  }

  return (
    <HelixErrorBoundary>
      <FridayCinematicOS
        mode={bridge.mode}
        metrics={bridge.metrics}
        transcript={bridge.transcript}
        response={bridge.response}
        responseMeta={bridge.responseMeta}
        designMode={bridge.designMode}
        telemetry={bridge.telemetry}
        operation={bridge.operation}
        weather={bridge.weather}
        location={bridge.location}
        countdown={bridge.countdown}
        bridgeReady={bridge.bridgeReady}
        onCoreTap={bridge.tapCore}
        onOpen={openWorkspace}
      />
      {worldMapOpen ? (
        <WorldMapMaster
          location={bridge.location}
          route={route}
          briefs={briefs}
          initialProjection="flat"
          onClose={previewScene === 'world-map' ? undefined : () => setWorldMapOpen(false)}
        />
      ) : null}
    </HelixErrorBoundary>
  )
}

function shouldOpenWorldMap(transcript: string): boolean {
  const normalized = transcript.toLowerCase().replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim()
  if (!normalized) return false
  return [
    'open world map',
    'show world map',
    'open global map',
    'show global map',
    'world atlas',
    'global command map',
    'show the routes from',
    'show routes from',
    'map the route from',
  ].some(command => normalized.includes(command))
}

function parseRouteRequest(transcript: string): { from: string; to: string } | null {
  const cleaned = transcript.replace(/[?.,!]/g, ' ').replace(/\s+/g, ' ').trim()
  const match = cleaned.match(/(?:routes?|path|travel|navigate)\s+(?:from\s+)?(.{2,48}?)\s+to\s+(.{2,48})$/i)
    || cleaned.match(/from\s+(.{2,48}?)\s+to\s+(.{2,48})$/i)
  if (!match) return null
  return { from: titleCase(match[1]), to: titleCase(match[2]) }
}

function extractBriefs(response: string): string[] {
  const clean = response.replace(/[*#>`_]/g, '').replace(/\s+/g, ' ').trim()
  if (!clean) return []
  return clean.split(/(?<=[.!?])\s+/).map(item => item.trim()).filter(item => item.length > 20).slice(0, 3)
}

function titleCase(value: string): string {
  return value.trim().split(' ').slice(0, 6).map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ')
}
