import { useEffect, useRef, useState } from 'react'
import type { AudioMetrics, CountdownState, HelixState, NativePayload, NativeTelemetry, TerminalLog } from './types'

declare global {
  interface Window {
    JarvisAndroid?: {
      onHelixReady: () => void
      onCoreTap: () => void
      onTextCommand?: (text: string) => void
      onHelixError?: (message: string) => void
    }
    jarvisHelix?: { receive: (payload: NativePayload) => void }
  }
}

const EMPTY_AUDIO: AudioMetrics = { rms: 0, peak: 0, bass: 0, mid: 0, treble: 0 }
const INITIAL_TELEMETRY: NativeTelemetry = { time: '--:--:--', battery: 0, network: 'SYNCING', heapMb: 0, device: 'ANDROID', voiceSource: 'LOCAL', cloudConfigured: false }
const INITIAL_COUNTDOWN: CountdownState = { active: false, label: 'MISSION TIMER', remainingMs: 0, totalMs: 0, progress: 0 }

export function useNativeBridge() {
  const metricsRef = useRef<AudioMetrics>({ ...EMPTY_AUDIO })
  const peakRef = useRef(0)
  const logId = useRef(4)
  const bootAt = useRef(performance.now())
  const [mode, setMode] = useState<HelixState>('IDLE')
  const [metrics, setMetrics] = useState<AudioMetrics>({ ...EMPTY_AUDIO })
  const [transcript, setTranscript] = useState('Awaiting operator command.')
  const [response, setResponse] = useState('Neural command channel standing by.')
  const [telemetry, setTelemetry] = useState(INITIAL_TELEMETRY)
  const [countdown, setCountdown] = useState(INITIAL_COUNTDOWN)
  const [bridgeReady, setBridgeReady] = useState(false)
  const [logs, setLogs] = useState<TerminalLog[]>([
    { id: 1, time: '00:00:01', channel: 'CORE', text: 'HELIX WebGL lattice initialized.' },
    { id: 2, time: '00:00:02', channel: 'SYS', text: 'Native Android action fabric awaiting bridge.' },
    { id: 3, time: '00:00:03', channel: 'VOICE', text: 'Voice capture proofing armed.' },
  ])

  const pushLog = (channel: TerminalLog['channel'], text: string) => {
    const elapsed = Math.floor((performance.now() - bootAt.current) / 1000)
    const time = new Date(elapsed * 1000).toISOString().slice(11, 19)
    setLogs(current => [...current.slice(-17), { id: logId.current++, time, channel, text }])
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
          case 'transcript': setTranscript(payload.text?.trim() || 'Awaiting operator command.'); break
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
    return () => { delete window.jarvisHelix }
  }, [])

  const submitTextCommand = (text: string) => {
    const clean = text.trim()
    if (!clean) return
    pushLog('CORE', `TEXT INPUT // ${clean.slice(0, 80)}`)
    window.JarvisAndroid?.onTextCommand?.(clean)
  }

  return {
    mode,
    metricsRef,
    metrics,
    transcript,
    response,
    telemetry,
    countdown,
    bridgeReady,
    logs,
    clearLogs: () => setLogs([]),
    tapCore: () => window.JarvisAndroid?.onCoreTap(),
    submitTextCommand,
  }
}

function channelFor(text: string): TerminalLog['channel'] {
  const value = text.toUpperCase()
  if (value.includes('ERROR') || value.includes('FAILED') || value.includes('DENIED')) return 'WARN'
  if (value.includes('VOICE') || value.includes('MIC')) return 'VOICE'
  if (value.includes('ACTION') || value.includes('SYSTEM') || value.includes('DEVICE')) return 'SYS'
  return 'CORE'
}
