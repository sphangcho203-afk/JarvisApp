import { useEffect, useRef, useState } from 'react'
import type { AudioMetrics, CountdownState, HelixState, NativePayload, NativeTelemetry, TerminalLog, WeatherTelemetry } from './types'

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
const INITIAL_TELEMETRY: NativeTelemetry = { time: '--:--:--', battery: 0, network: 'SYNCING', heapMb: 0, device: 'ANDROID', voiceSource: 'LOCAL', cloudConfigured: false }
const INITIAL_WEATHER: WeatherTelemetry = { configured: false, status: 'NOT CONFIGURED', fresh: false, location: '', tempC: 0, feelsLikeC: 0, condition: '', conditionCode: 0, icon: '◌', isDay: true, windKph: 0, windDirection: '', gustKph: 0, humidity: 0, cloudPercent: 0, precipMm: 0, rainChance: 0, todayMinC: 0, todayMaxC: 0, updatedAtMs: 0, alert: '' }
const INITIAL_COUNTDOWN: CountdownState = { active: false, label: 'MISSION TIMER', remainingMs: 0, totalMs: 0, progress: 0 }

export function useNativeBridge() {
  const metricsRef = useRef<AudioMetrics>({ ...EMPTY_AUDIO })
  const peakRef = useRef(0)
  const logId = useRef(4)
  const bootAt = useRef(performance.now())
  const weatherSignature = useRef('')
  const [mode, setMode] = useState<HelixState>('IDLE')
  const [metrics, setMetrics] = useState<AudioMetrics>({ ...EMPTY_AUDIO })
  const [transcript, setTranscript] = useState('Listening for you, Sir.')
  const [response, setResponse] = useState('Jarvis is standing by, Sir.')
  const [telemetry, setTelemetry] = useState(INITIAL_TELEMETRY)
  const [weather, setWeather] = useState(INITIAL_WEATHER)
  const [countdown, setCountdown] = useState(INITIAL_COUNTDOWN)
  const [bridgeReady, setBridgeReady] = useState(false)
  const [logs, setLogs] = useState<TerminalLog[]>([
    { id: 1, time: '00:00:01', channel: 'CORE', text: 'HELIX lattice initialized.' },
    { id: 2, time: '00:00:02', channel: 'SYS', text: 'Android command fabric awaiting verified permissions.' },
    { id: 3, time: '00:00:03', channel: 'VOICE', text: 'Jarvis voice array armed.' },
  ])

  const pushLog = (channel: TerminalLog['channel'], text: string) => {
    const elapsed = Math.floor((performance.now() - bootAt.current) / 1000)
    const time = new Date(elapsed * 1000).toISOString().slice(11, 19)
    setLogs(current => [...current.slice(-17), { id: logId.current++, time, channel, text }])
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
        pushLog(next.alert ? 'WARN' : 'SYS', next.alert ? `WEATHER ALERT // ${next.alert}` : `WEATHER // ${next.icon} ${Math.round(next.tempC)}°C ${next.condition.toUpperCase()}`)
      }
    } catch (error) {
      console.warn('WEATHER_BRIDGE_PARSE_FAILED', error)
    }
  }

  useEffect(() => {
    window.jarvisHelix = {
      receive(payload) {
        switch (payload.type) {
          case 'ready': setBridgeReady(true); pushLog('SYS', 'Native Android bridge synchronized.'); break
          case 'state': if (payload.mode) setMode(payload.mode); break
          case 'audio': {
            const rms = Math.max(0, Math.min(1, payload.rms ?? 0))
            peakRef.current = Math.max(rms, peakRef.current * .91)
            const next = { rms, peak: peakRef.current, bass: Math.min(1, rms * 1.18), mid: Math.min(1, rms * .92 + peakRef.current * .08), treble: Math.min(1, rms * .72 + peakRef.current * .2) }
            metricsRef.current = next
            setMetrics(next)
            break
          }
          case 'transcript': setTranscript(payload.text?.trim() || 'Listening for you, Sir.'); break
          case 'response':
            setResponse(payload.display?.trim() || payload.spoken?.trim() || 'Response received.')
            if (payload.intent) pushLog('CORE', `${payload.intent.toUpperCase()} // ${Math.round((payload.confidence ?? 0) * 100)}%`)
            break
          case 'event': if (payload.text) pushLog(payload.channel ?? channelFor(payload.text), payload.text); break
          case 'telemetry': setTelemetry(current => ({ ...current, ...payload.telemetry })); break
          case 'countdown': setCountdown(current => ({ ...current, ...payload.countdown })); break
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

  const openSetup = () => {
    if (!telemetry.cloudConfigured) {
      window.JarvisCommandBridge?.openApiSetup()
    } else if (!weather.configured) {
      window.JarvisCommandBridge?.openWeatherSetup()
    } else {
      window.JarvisCommandBridge?.openPermissionCenter()
    }
  }

  return {
    mode,
    metricsRef,
    metrics,
    transcript,
    response,
    telemetry,
    weather,
    countdown,
    bridgeReady,
    logs,
    clearLogs: () => setLogs([]),
    setupLabel: !telemetry.cloudConfigured ? 'API SETUP' : !weather.configured ? 'WEATHER' : 'ANDROID',
    openSetup,
    refreshWeather: () => {
      window.JarvisCommandBridge?.refreshWeather()
      window.setTimeout(readWeather, 900)
    },
    tapCore: () => {
      if (!telemetry.cloudConfigured && window.JarvisCommandBridge) {
        window.JarvisCommandBridge.openApiSetup()
      } else {
        window.JarvisAndroid?.onCoreTap()
      }
    },
  }
}

function channelFor(text: string): TerminalLog['channel'] {
  const value = text.toUpperCase()
  if (value.includes('ERROR') || value.includes('FAILED') || value.includes('DENIED') || value.includes('ALERT')) return 'WARN'
  if (value.includes('VOICE') || value.includes('MIC')) return 'VOICE'
  if (value.includes('ACTION') || value.includes('SYSTEM') || value.includes('DEVICE') || value.includes('PERMISSION') || value.includes('WEATHER')) return 'SYS'
  return 'CORE'
}
