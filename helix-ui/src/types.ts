import type { MutableRefObject } from 'react'

export type HelixState = 'IDLE' | 'LISTENING' | 'PROCESSING' | 'SPEAKING' | 'ERROR'
export type LogChannel = 'CORE' | 'VOICE' | 'SYS' | 'WARN'

export interface AudioMetrics {
  rms: number
  peak: number
  bass: number
  mid: number
  treble: number
}
export type AudioMetricsRef = MutableRefObject<AudioMetrics>

export interface ThemeSpec {
  label: string
  note: string
  hex: string
  rgb: string
  glow: string
  energy: number
  speed: number
}

export interface NativeTelemetry {
  time: string
  battery: number
  network: string
  heapMb: number
  device: string
  voiceSource: string
  cloudConfigured: boolean
}

export interface CountdownState {
  active: boolean
  label: string
  remainingMs: number
  totalMs: number
  progress: number
}

export interface TerminalLog {
  id: number
  time: string
  channel: LogChannel
  text: string
}

export interface NativePayload {
  type: 'ready' | 'state' | 'audio' | 'transcript' | 'response' | 'event' | 'telemetry' | 'countdown'
  mode?: HelixState
  rms?: number
  text?: string
  spoken?: string
  display?: string
  intent?: string
  confidence?: number
  channel?: LogChannel
  telemetry?: Partial<NativeTelemetry>
  countdown?: Partial<CountdownState>
}
