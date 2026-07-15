import { Hud } from './Hud'
import { useNativeBridge } from './nativeBridge'

export default function App() {
  const bridge = useNativeBridge()
  return <Hud mode={bridge.mode} audioRef={bridge.metricsRef} metrics={bridge.metrics} transcript={bridge.transcript} response={bridge.response} telemetry={bridge.telemetry} countdown={bridge.countdown} bridgeReady={bridge.bridgeReady} logs={bridge.logs} onCoreTap={bridge.tapCore} onClearLogs={bridge.clearLogs} />
}
