import { Component, type ErrorInfo, type ReactNode } from 'react'
import { CinematicOs } from './CinematicOs'
import { Hud } from './Hud'
import { WorkspaceRail } from './WorkspaceRail'
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
      const detail = `${this.state.error.name}: ${this.state.error.message}`.slice(0, 240)
      return (
        <main style={{ alignItems:'center', background:'radial-gradient(circle at 50% 35%, rgba(52,113,255,.16), transparent 34%), #02060a', color:'rgba(255,255,255,.9)', display:'flex', fontFamily:'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', height:'100%', justifyContent:'center', padding:'24px', textAlign:'center', width:'100%' }}>
          <section style={{ border:'1px solid rgba(65,132,255,.42)', boxShadow:'inset 0 0 50px rgba(65,132,255,.06), 0 0 35px rgba(65,132,255,.09)', maxWidth:'680px', padding:'28px', width:'100%' }}>
            <p style={{ color:'#5796ff', fontSize:'11px', letterSpacing:'.28em', margin:0 }}>F.R.I.D.A.Y. // HELIX OPS</p>
            <h1 style={{ fontSize:'18px', letterSpacing:'.15em', margin:'18px 0 10px' }}>RENDER FALLBACK ONLINE</h1>
            <p style={{ color:'rgba(255,255,255,.56)', fontSize:'12px', lineHeight:1.8, margin:0 }}>The visual layer stopped, but FRIDAY&apos;s native command core remains operational.</p>
            <pre style={{ background:'rgba(255,255,255,.035)', border:'1px solid rgba(255,255,255,.08)', color:'rgba(255,255,255,.48)', fontSize:'10px', margin:'20px 0', overflowWrap:'anywhere', padding:'12px', whiteSpace:'pre-wrap' }}>{detail}</pre>
            <div style={{ display:'flex', flexWrap:'wrap', gap:'10px', justifyContent:'center' }}>
              <button type="button" onClick={() => window.location.reload()} style={{ background:'#5796ff', border:0, color:'#021018', font:'inherit', fontSize:'10px', letterSpacing:'.16em', padding:'11px 15px' }}>RETRY HELIX</button>
              <button type="button" onClick={() => window.JarvisAndroid?.onCoreTap()} style={{ background:'transparent', border:'1px solid rgba(87,150,255,.45)', color:'#74b5ff', font:'inherit', fontSize:'10px', letterSpacing:'.16em', padding:'10px 14px' }}>RESET VOICE</button>
            </div>
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
      <CinematicOs
        mode={bridge.mode}
        telemetry={bridge.telemetry}
        operation={bridge.operation}
        bridgeReady={bridge.bridgeReady}
        onOpen={bridge.openWorkspace}
        onCoreTap={bridge.tapCore}
      />
      <WorkspaceRail bridgeReady={bridge.bridgeReady} onOpen={bridge.openWorkspace} />
      <Hud
        mode={bridge.mode}
        audioRef={bridge.metricsRef}
        metrics={bridge.metrics}
        transcript={bridge.transcript}
        response={bridge.response}
        telemetry={bridge.telemetry}
        operation={bridge.operation}
        weather={bridge.weather}
        countdown={bridge.countdown}
        bridgeReady={bridge.bridgeReady}
        logs={bridge.logs}
        onCoreTap={bridge.tapCore}
        onRefreshWeather={bridge.refreshWeather}
        onClearLogs={bridge.clearLogs}
      />
    </HelixErrorBoundary>
  )
}
