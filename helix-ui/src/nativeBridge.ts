import { useEffect, useRef, useState } from 'react'
import type { AudioMetrics, CountdownState, HelixState, NativePayload, NativeTelemetry, OperationState, TerminalLog, WeatherTelemetry } from './types'

declare global {
  interface Window {
    JarvisAndroid?: {
      onHelixReady: () => void
      onCoreTap: () => void
      onHelixError?: (message: string) => void
    }
    JarvisCommandBridge?: {
      openApiSetup: () => void
      openPermissionCenter: () => void
      openWeatherSetup: () => void
      getWeatherJson: () => string
      refreshWeather: () => void
    }
    jarvisHelix?: { receive: (payload: NativePayload) => void }
  }
}

const EMPTY_AUDIO: AudioMetrics = { rms: 0, peak: 0, bass: 0, mid: 0, treble: 0 }
const INITIAL_TELEMETRY: NativeTelemetry = {
  time: '--:--:--', battery: 0, network: 'SYNCING', heapMb: 0, device: 'ANDROID',
  voiceSource: 'VOICE OFFLINE', cloudConfigured: false, latencyMs: -1, jitterMs: 0,
  packetLossPercent: 0, downlinkMbps: 0, uplinkMbps: 0, networkQuality: 'PROBING',
  uptimeSeconds: 0, cortexConfigured: 0, cortexOnline: 0, searchConfigured: 0,
  searchOnline: 0, cartesiaKeys: 0, cartesiaRoute: '--', cartesiaStatus: 'NOT CONFIGURED',
  deepSeekStatus: 'NOT CONFIGURED', youtubeStatus: 'NOT CONFIGURED', gmailStatus: 'NOT CONFIGURED',
}
const INITIAL_OPERATION: OperationState = { stage: 'SYSTEM READY', detail: 'AWAITING VERIFIED COMMAND', progress: 0, active: false }
const INITIAL_WEATHER: WeatherTelemetry = { configured: false, status: 'NOT CONFIGURED', fresh: false, location: '', tempC: 0, feelsLikeC: 0, condition: '', conditionCode: 0, icon: '◌', isDay: true, windKph: 0, windDirection: '', gustKph: 0, humidity: 0, cloudPercent: 0, precipMm: 0, rainChance: 0, todayMinC: 0, todayMaxC: 0, updatedAtMs: 0, alert: '' }
const INITIAL_COUNTDOWN: CountdownState = { active: false, label: 'MISSION TIMER', remainingMs: 0, totalMs: 0, progress: 0 }

export function useNativeBridge() {
  const metricsRef = useRef<AudioMetrics>({ ...EMPTY_AUDIO })
  const peakRef = useRef(0)
  const logId = useRef(5)
  const bootAt = useRef(performance.now())
  const weatherSignature = useRef('')
  const [mode, setMode] = useState<HelixState>('IDLE')
  const [metrics, setMetrics] = useState<AudioMetrics>({ ...EMPTY_AUDIO })
  const [transcript, setTranscript] = useState('OWNER CHANNEL ARMED // AWAITING SPEECH')
  const [response, setResponse] = useState('F.R.I.D.A.Y. operational. Awaiting command input.')
  const [telemetry, setTelemetry] = useState(INITIAL_TELEMETRY)
  const [operation, setOperation] = useState(INITIAL_OPERATION)
  const [weather, setWeather] = useState(INITIAL_WEATHER)
  const [countdown, setCountdown] = useState(INITIAL_COUNTDOWN)
  const [bridgeReady, setBridgeReady] = useState(false)
  const [logs, setLogs] = useState<TerminalLog[]>([
    { id: 1, time: '00:00:01', channel: 'CORE', text: 'HELIX OPERATIONS LATTICE // INITIALIZED' },
    { id: 2, time: '00:00:02', channel: 'SYS', text: 'NATIVE TELEMETRY FABRIC // SYNCHRONIZING' },
    { id: 3, time: '00:00:03', channel: 'VOICE', text: 'CARTESIA FAILOVER MATRIX // ARMED' },
    { id: 4, time: '00:00:04', channel: 'CORE', text: 'LIVE RESEARCH PHASE SIGNALING // READY' },
  ])

  const pushLog = (channel: TerminalLog['channel'], text: string) => {
    const elapsed = Math.floor((performance.now() - bootAt.current) / 1000)
    const time = new Date(elapsed * 1000).toISOString().slice(11, 19)
    setLogs(current => [...current.slice(-23), { id: logId.current++, time, channel, text }])
  }

  const readWeather = () => {
    const raw = window.JarvisCommandBridge?.getWeatherJson?.()
    if (!raw) return
    try {
      const next = { ...INITIAL_WEATHER, ...(JSON.parse(raw) as Partial<WeatherTelemetry>) }
      setWeather(next)
      const signature = `${next.updatedAtMs}|${next.conditionCode}|${next.alert}`
      if (next.configured && signature !== weatherSignature.current) {
        weatherSignature.current = signature
        pushLog(next.alert ? 'WARN' : 'SYS', next.alert ? `WEATHER ALERT // ${next.alert}` : `WEATHER CORE // ${next.icon} ${Math.round(next.tempC)}°C ${next.condition.toUpperCase()}`)
      }
    } catch (error) {
      console.warn('WEATHER_BRIDGE_PARSE_FAILED', error)
    }
  }

  useEffect(() => {
    window.jarvisHelix = {
      receive(payload) {
        switch (payload.type) {
          case 'ready': setBridgeReady(true); pushLog('SYS', 'NATIVE OPERATIONS BRIDGE // SECURED'); break
          case 'state': if (payload.mode) setMode(payload.mode); break
          case 'audio': {
            const rms = Math.max(0, Math.min(1, payload.rms ?? 0))
            peakRef.current = Math.max(rms, peakRef.current * .91)
            const next = { rms, peak: peakRef.current, bass: Math.min(1, rms * 1.18), mid: Math.min(1, rms * .92 + peakRef.current * .08), treble: Math.min(1, rms * .72 + peakRef.current * .2) }
            metricsRef.current = next
            setMetrics(next)
            break
          }
          case 'transcript': setTranscript(payload.text?.trim() || 'OWNER CHANNEL ARMED // AWAITING SPEECH'); break
          case 'response':
            setResponse(payload.display?.trim() || payload.spoken?.trim() || 'VERIFIED RESPONSE RECEIVED')
            if (payload.intent) pushLog('CORE', `${payload.intent.toUpperCase()} // CONFIDENCE ${Math.round((payload.confidence ?? 0) * 100)}%`)
            break
          case 'event': if (payload.text) pushLog(payload.channel ?? channelFor(payload.text), payload.text); break
          case 'telemetry': setTelemetry(current => ({ ...current, ...payload.telemetry })); break
          case 'countdown': setCountdown(current => ({ ...current, ...payload.countdown })); break
          case 'operation': {
            const next: OperationState = {
              stage: payload.stage?.trim() || 'SYSTEM READY',
              detail: payload.detail?.trim() || 'AWAITING VERIFIED COMMAND',
              progress: Math.max(0, Math.min(1, payload.progress ?? 0)),
              active: payload.active ?? false,
            }
            setOperation(next)
            break
          }
        }
      },
    }
    window.JarvisAndroid?.onHelixReady()
    const firstRead = window.setTimeout(readWeather, 900)
    const poll = window.setInterval(readWeather, 60_000)
    return () => {
      window.clearTimeout(firstRead)
      window.clearInterval(poll)
      delete window.jarvisHelix
    }
  }, [])

  return {
    mode, metricsRef, metrics, transcript, response, telemetry, operation, weather,
    countdown, bridgeReady, logs,
    clearLogs: () => setLogs([]),
    refreshWeather: () => {
      window.JarvisCommandBridge?.refreshWeather()
      window.setTimeout(readWeather, 900)
    },
    tapCore: () => window.JarvisAndroid?.onCoreTap(),
  }
}

function channelFor(text: string): TerminalLog['channel'] {
  const value = text.toUpperCase()
  if (value.includes('ERROR') || value.includes('FAILED') || value.includes('DENIED') || value.includes('ALERT') || value.includes('DEGRADED')) return 'WARN'
  if (value.includes('VOICE') || value.includes('MIC') || value.includes('CARTESIA')) return 'VOICE'
  if (value.includes('ACTION') || value.includes('SYSTEM') || value.includes('DEVICE') || value.includes('PERMISSION') || value.includes('WEATHER')) return 'SYS'
  return 'CORE'
}
