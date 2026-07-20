import { Component, type ErrorInfo, type ReactNode } from 'react'
import { ReferenceCinematicCore } from './ReferenceCinematicCore'
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
  return (
    <HelixErrorBoundary>
      <ReferenceCinematicCore
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
        onOpen={bridge.openWorkspace}
      />
    </HelixErrorBoundary>
  )
}
