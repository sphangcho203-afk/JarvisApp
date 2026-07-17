import React, { type CSSProperties } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { FridayMark } from './FridayMark'
import { HelixCanvas } from './HelixCore'
import { THEMES } from './theme'
import type {
  AudioMetrics,
  AudioMetricsRef,
  CountdownState,
  HelixState,
  NativeTelemetry,
  OperationState,
  TerminalLog,
  ThemeSpec,
  WeatherTelemetry,
} from './types'

interface Props {
  mode: HelixState
  audioRef: AudioMetricsRef
  metrics: AudioMetrics
  transcript: string
  response: string
  telemetry: NativeTelemetry
  operation: OperationState
  weather: WeatherTelemetry
  countdown: CountdownState
  bridgeReady: boolean
  logs: TerminalLog[]
  onCoreTap: () => void
  onRefreshWeather: () => void
  onClearLogs: () => void
}

type HudIconName =
  | 'battery'
  | 'signal'
  | 'clock'
  | 'wave'
  | 'target'
  | 'chip'
  | 'state'
  | 'voice'
  | 'route'
  | 'keys'
  | 'owner'

export function Hud(props: Props) {
  const theme = THEMES[props.mode]
  const style = {
    '--accent': theme.hex,
    '--accent-rgb': theme.rgb,
    '--accent-glow': theme.glow,
  } as CSSProperties

  return (
    <div className="helix-shell" style={style}>
      <div className="hud-grid" />
      <div className="scanlines" />
      <div className="noise-layer" />
      <Header mode={props.mode} theme={theme} telemetry={props.telemetry} bridgeReady={props.bridgeReady} />
      <main className="ops-main">
        <CoreStage {...props} theme={theme} />
        <Terminal
          theme={theme}
          logs={props.logs}
          response={props.response}
          operation={props.operation}
          onClear={props.onClearLogs}
        />
      </main>
    </div>
  )
}

function Header({
  mode,
  theme,
  telemetry,
  bridgeReady,
}: {
  mode: HelixState
  theme: ThemeSpec
  telemetry: NativeTelemetry
  bridgeReady: boolean
}) {
  const stateLabel = bridgeReady ? theme.label : 'BRIDGE SYNC'
  return (
    <header className="ops-header">
      <div className="ops-identity">
        <div className="friday-mark"><FridayMark /></div>
        <div className="ops-identity-copy">
          <p className="ops-title">F.R.I.D.A.Y. <span>//</span> HELIX OPS</p>
          <p className="ops-subtitle">SEONGJA PRIVATE INTELLIGENCE <span>//</span> BUILD 0.9.23</p>
        </div>
      </div>
      <div className="ops-header-state">
        <div className="ops-header-meta">
          <span>{telemetry.time}</span>
          <span>UPTIME {formatUptime(telemetry.uptimeSeconds)}</span>
        </div>
        <motion.span
          className="ops-state-dot"
          animate={{ opacity: mode === 'ERROR' ? [1, .2, 1] : [.45, 1, .45], scale: [.9, 1.18, .9] }}
          transition={{ duration: mode === 'PROCESSING' ? .7 : 1.5, repeat: Infinity }}
          style={{ background: theme.hex, boxShadow: `0 0 16px ${theme.glow}` }}
        />
        <span className="ops-state-label" style={{ color: theme.hex }}>{stateLabel}</span>
      </div>
    </header>
  )
}

function CoreStage(props: Props & { theme: ThemeSpec }) {
  const { mode, theme, metrics, telemetry, countdown, weather, operation } = props
  const leftRows: Array<{ icon: HudIconName; label: string; value: string; meter?: number }> = [
    { icon: 'battery', label: 'BATTERY', value: `${telemetry.battery}%`, meter: telemetry.battery / 100 },
    { icon: 'signal', label: 'NETWORK', value: telemetry.network || 'SYNCING', meter: networkMeter(telemetry.networkQuality) },
    { icon: 'clock', label: 'LATENCY', value: telemetry.latencyMs >= 0 ? `${telemetry.latencyMs} MS` : 'PROBING' },
    { icon: 'wave', label: 'JITTER', value: `${telemetry.jitterMs} MS` },
    { icon: 'target', label: 'LOSS', value: `${telemetry.packetLossPercent}%` },
    { icon: 'chip', label: 'HEAP', value: `${telemetry.heapMb} MB` },
  ]
  const rightRows: Array<{ icon: HudIconName; label: string; value: string; meter?: number }> = [
    { icon: 'state', label: 'STATE', value: operation.active ? operation.stage : mode, meter: operation.active ? operation.progress : undefined },
    { icon: 'voice', label: 'VOICE', value: `${Math.round(metrics.rms * 100)}%`, meter: metrics.rms },
    { icon: 'wave', label: 'PEAK', value: `${Math.round(metrics.peak * 100)}%`, meter: metrics.peak },
    { icon: 'route', label: 'ROUTE', value: telemetry.cartesiaRoute || 'P1' },
    { icon: 'keys', label: 'KEYS', value: `${telemetry.cartesiaKeys} / 4` },
    { icon: 'owner', label: 'OWNER', value: 'PRIVATE' },
  ]

  return (
    <section className="core-stage">
      <button
        type="button"
        aria-label="Recalibrate FRIDAY voice input"
        onClick={props.onCoreTap}
        className="core-touch-target"
      />

      <div className="core-caption core-caption-left">WEBGL CORE <span>//</span> ORION HELIX</div>
      <div className="core-caption core-caption-right">
        <p>RENDER <span>//</span> GPU</p>
        <p>BRIDGE <span>//</span> {props.bridgeReady ? 'SECURED' : 'SYNCING'}</p>
      </div>

      <div className="telemetry-rails">
        <div className="telemetry-rail telemetry-rail-left">
          {leftRows.map(row => <TelemetryRow key={row.label} {...row} theme={theme} />)}
        </div>
        <div className="telemetry-rail telemetry-rail-right">
          {rightRows.map(row => <TelemetryRow key={row.label} {...row} theme={theme} align="right" />)}
        </div>
      </div>

      <div className="core-visual">
        <div className="core-reticle" />
        <HelixCanvas mode={mode} theme={theme} audio={props.audioRef} />
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={`${operation.stage}-${operation.active}-${mode}`}
          initial={{ opacity: 0, y: 7, filter: 'blur(7px)' }}
          animate={{ opacity: 1, y: 0, filter: 'blur(0)' }}
          exit={{ opacity: 0, y: -7, filter: 'blur(7px)' }}
          transition={{ duration: .28 }}
          className="core-state-copy"
        >
          <p className="core-state-title" style={{ color: theme.hex, textShadow: `0 0 18px ${theme.glow}` }}>
            {operation.active ? operation.stage : theme.label}
          </p>
          <p className="core-state-note">{operation.active ? operation.detail : theme.note.toUpperCase()}</p>
          {operation.active ? (
            <div className="core-progress">
              <motion.div
                animate={{ width: `${Math.max(8, operation.progress * 100)}%` }}
                transition={{ duration: .35 }}
                style={{ background: theme.hex, boxShadow: `0 0 12px ${theme.glow}` }}
              />
            </div>
          ) : null}
        </motion.div>
      </AnimatePresence>

      <StatusDock
        telemetry={telemetry}
        weather={weather}
        transcript={props.transcript}
        theme={theme}
        countdown={countdown}
        onRefreshWeather={props.onRefreshWeather}
      />
    </section>
  )
}

function TelemetryRow({
  icon,
  label,
  value,
  meter,
  theme,
  align = 'left',
}: {
  icon: HudIconName
  label: string
  value: string
  meter?: number
  theme: ThemeSpec
  align?: 'left' | 'right'
}) {
  return (
    <div className={`telemetry-row ${align === 'right' ? 'telemetry-row-right' : ''}`}>
      <span className="telemetry-icon" style={{ color: theme.hex, borderColor: `rgba(${theme.rgb}/.38)` }}>
        <HudIcon name={icon} />
      </span>
      <div className="telemetry-copy">
        <span className="telemetry-label">{label}</span>
        <span className="telemetry-value">{value}</span>
      </div>
      {typeof meter === 'number' ? <MicroMeter value={meter} theme={theme} /> : <SignalTrace theme={theme} />}
    </div>
  )
}

function StatusDock({
  telemetry,
  weather,
  transcript,
  theme,
  countdown,
  onRefreshWeather,
}: {
  telemetry: NativeTelemetry
  weather: WeatherTelemetry
  transcript: string
  theme: ThemeSpec
  countdown: CountdownState
  onRefreshWeather: () => void
}) {
  const weatherText = weather.configured
    ? `${Math.round(weather.tempC)}°C // ${weather.condition.toUpperCase()} // ${weather.location.toUpperCase().slice(0, 18)}`
    : 'CONFIGURATION PENDING'
  const services = [
    ['CORTEX', `${telemetry.cortexOnline}/${telemetry.cortexConfigured}`],
    ['SEARCH', telemetry.searchConfigured > 0 ? `${telemetry.searchOnline}/${telemetry.searchConfigured}` : 'OFFLINE'],
    ['DEEPSEEK', compactHealth(telemetry.deepSeekStatus)],
    ['ROUTE', telemetry.cartesiaRoute || 'P1'],
    ['KEYS', `${telemetry.cartesiaKeys}/4`],
  ]

  return (
    <div className="status-dock">
      <button type="button" onClick={onRefreshWeather} className="status-line status-weather">
        <span className="status-symbol">{weather.icon || weatherGlyph(weather.condition)}</span>
        <span className="status-line-main"><b>WEATHER CORE</b> <i>//</i> {weatherText}</span>
        <SignalTrace theme={theme} active={weather.fresh} />
      </button>
      <div className="status-line status-voice">
        <span className="voice-bars" aria-hidden="true"><i /><i /><i /><i /><i /></span>
        <span className="status-line-main"><b>VOICE LINK</b> <i>//</i> PRIVATE CHANNEL <i>//</i> {truncateOperational(transcript)}</span>
        <span className="status-network">NET <i>//</i> {telemetry.networkQuality}<small>↓{formatSpeed(telemetry.downlinkMbps)} ↑{formatSpeed(telemetry.uplinkMbps)}</small></span>
      </div>
      <div className="service-strip">
        {services.map(([label, value]) => (
          <div key={label} className="service-cell">
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
      {countdown.active ? <div className="countdown-line"><div style={{ width: `${countdown.progress * 100}%`, background: theme.hex }} /></div> : null}
    </div>
  )
}

function Terminal({
  theme,
  logs,
  response,
  operation,
  onClear,
}: {
  theme: ThemeSpec
  logs: TerminalLog[]
  response: string
  operation: OperationState
  onClear: () => void
}) {
  const scrambled = useScramble(response)
  const visibleLogs = compactLogs(logs).slice(-4)
  return (
    <section className="terminal-panel">
      <div className="terminal-kicker">F.R.I.D.A.Y. COGNITIVE OUTPUT <span>//</span> VERIFIED CHANNEL</div>
      <div className="terminal-heading-row">
        <h2>RESPONSE TERMINAL</h2>
        <button type="button" onClick={onClear}>PURGE</button>
      </div>
      <div className="terminal-log" aria-label="Recent operational events">
        {visibleLogs.map(log => (
          <div key={log.id} className="terminal-log-row">
            <span>{log.time}</span>
            <b className={`channel-${log.channel.toLowerCase()}`}>{displayChannel(log.channel)}</b>
            <p>{polishLog(log.text)}</p>
          </div>
        ))}
      </div>
      <div className="response-box" style={{ borderColor: `rgba(${theme.rgb}/.28)` }}>
        <div className="response-box-label">
          <span>{operation.active ? operation.stage : 'LATEST VERIFIED RESPONSE'}</span>
          <b>{operation.active ? `${Math.round(operation.progress * 100)}%` : 'LOCKED'}</b>
        </div>
        <p style={{ color: theme.hex, textShadow: `0 0 14px ${theme.glow}` }}>
          {operation.active ? operation.detail : scrambled}<span className="terminal-cursor">▌</span>
        </p>
      </div>
    </section>
  )
}

function MicroMeter({ value, theme }: { value: number; theme: ThemeSpec }) {
  const safe = Math.max(0, Math.min(1, value))
  return (
    <span className="micro-meter" aria-hidden="true">
      {Array.from({ length: 8 }, (_, index) => (
        <i
          key={index}
          style={{
            height: `${4 + index * 1.25}px`,
            opacity: index / 8 <= safe ? 1 : .16,
            background: theme.hex,
          }}
        />
      ))}
    </span>
  )
}

function SignalTrace({ theme, active = true }: { theme: ThemeSpec; active?: boolean }) {
  return (
    <span className="signal-trace" aria-hidden="true" style={{ color: theme.hex, opacity: active ? 1 : .34 }}>
      <i /><i /><i /><i /><i /><i /><i />
    </span>
  )
}

function HudIcon({ name }: { name: HudIconName }) {
  const common = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
  const paths: Record<HudIconName, React.ReactNode> = {
    battery: <><rect x="5" y="4" width="10" height="16" rx="1.5" {...common} /><path d="M8 2h4" {...common} /><path d="M8 8h4v8H8z" {...common} /></>,
    signal: <><path d="M3 9c4.7-4.5 9.3-4.5 14 0" {...common} /><path d="M6 12c2.8-2.6 5.2-2.6 8 0" {...common} /><path d="M9 15c.8-.7 1.2-.7 2 0" {...common} /><circle cx="10" cy="18" r="1" fill="currentColor" /></>,
    clock: <><circle cx="10" cy="11" r="7" {...common} /><path d="M10 7v4l3 2" {...common} /></>,
    wave: <path d="M2 11h3l2-6 3 12 3-9 2 3h3" {...common} />,
    target: <><circle cx="10" cy="11" r="7" {...common} /><circle cx="10" cy="11" r="2.5" {...common} /><path d="M10 2v3M10 17v3M1 11h3M16 11h3" {...common} /></>,
    chip: <><rect x="5" y="6" width="10" height="10" rx="1" {...common} /><path d="M8 9h4v4H8zM7 3v3M10 3v3M13 3v3M7 16v3M10 16v3M13 16v3M2 8h3M2 11h3M2 14h3M15 8h3M15 11h3M15 14h3" {...common} /></>,
    state: <><circle cx="10" cy="11" r="7" {...common} /><path d="M6 9h8M7 12h6" {...common} /></>,
    voice: <><path d="M7 8a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z" {...common} /><path d="M4 12a6 6 0 0 0 12 0M10 18v2" {...common} /></>,
    route: <><circle cx="4" cy="15" r="1.5" {...common} /><circle cx="16" cy="6" r="1.5" {...common} /><path d="M5.5 14c4-1 4-6 9-7" {...common} /></>,
    keys: <><rect x="3" y="4" width="14" height="14" rx="2" {...common} /><path d="M7 9h6M7 13h6M10 6v10" {...common} /></>,
    owner: <><path d="M10 2l7 3v5c0 5-2.6 8-7 10-4.4-2-7-5-7-10V5z" {...common} /><path d="M8 10a2 2 0 1 1 4 0v3H8z" {...common} /></>,
  }
  return <svg viewBox="0 0 20 22" role="presentation">{paths[name]}</svg>
}

function compactLogs(logs: TerminalLog[]): TerminalLog[] {
  const result: TerminalLog[] = []
  for (const log of logs) {
    const polished = polishLog(log.text)
    const previous = result[result.length - 1]
    if (previous && polishLog(previous.text) === polished) continue
    result.push(log)
  }
  return result
}

function polishLog(value: string): string {
  const upper = value.toUpperCase().trim()
  const replacements: Array<[RegExp, string]> = [
    [/VOICE INPUT\s*->\s*PAUSED FOR OUTPUT.*/, 'INPUT CHANNEL HELD FOR RESPONSE'],
    [/VOICE\s*->\s*CARTESIA SONIC STREAM.*/, 'CARTESIA VOICE ROUTE ENGAGED'],
    [/VOICE OUTPUT\s*->\s*CARTESIA CONTEXT OPEN.*/, 'SONIC-3 CONTEXT ESTABLISHED'],
    [/VOICE OUTPUT\s*->\s*CARTESIA COMPLETE.*/, 'VOICE RESPONSE COMPLETED'],
    [/VOICE INPUT\s*->\s*REARMING.*/, 'OWNER CHANNEL REARMED'],
    [/CORTEX MESH\s*->\s*ROUTING REQUEST.*/, 'COGNITIVE ROUTE SELECTED'],
    [/CORTEX MESH\s*->\s*RESPONSE.*/, 'CORTEX RESPONSE VERIFIED'],
  ]
  for (const [pattern, replacement] of replacements) {
    if (pattern.test(upper)) return replacement
  }
  return upper.replace(/\s+/g, ' ').slice(0, 76)
}

function displayChannel(channel: TerminalLog['channel']): string {
  if (channel === 'CORE') return 'PROCESS'
  if (channel === 'SYS') return 'SYSTEM'
  return channel
}

function networkMeter(quality: string): number {
  const value = quality.toUpperCase()
  if (value.includes('OPTIMAL')) return 1
  if (value.includes('STABLE')) return .82
  if (value.includes('DEGRADED')) return .48
  if (value.includes('UNSTABLE')) return .22
  return .35
}

function formatSpeed(value: number): string {
  return `${value.toFixed(value >= 100 ? 0 : 1)} MBPS`
}

function truncateOperational(value: string): string {
  const clean = value.toUpperCase().replace(/\s+/g, ' ').trim()
  if (!clean) return 'OWNER CHANNEL ARMED'
  return clean.length > 34 ? `${clean.slice(0, 31)}…` : clean
}

function weatherGlyph(condition: string): string {
  const value = condition.toLowerCase()
  if (value.includes('thunder') || value.includes('storm')) return '⛈'
  if (value.includes('rain') || value.includes('drizzle')) return '☂'
  if (value.includes('snow') || value.includes('sleet')) return '❄'
  if (value.includes('fog') || value.includes('mist')) return '◌'
  if (value.includes('cloud') || value.includes('overcast')) return '☁'
  return '☼'
}

function formatUptime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  return [h, m, s].map(number => String(number).padStart(2, '0')).join(':')
}

function compactHealth(value: string): string {
  return value
    .replace(/\/\/.*$/, '')
    .trim()
    .replace('NOT CONFIGURED', 'OFFLINE')
    .replace('PROJECT KEY STORED', 'OAUTH')
    .slice(0, 15)
}

function useScramble(text: string) {
  const [value, setValue] = React.useState(text)
  React.useEffect(() => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#@%&*+-=<>/[]{}'
    const started = performance.now()
    const timer = window.setInterval(() => {
      const progress = Math.min(1, (performance.now() - started) / 260)
      const locked = Math.floor(text.length * progress)
      setValue(text.split('').map((character, index) => index < locked || character === ' ' ? character : chars[Math.floor(Math.random() * chars.length)]).join(''))
      if (progress >= 1) window.clearInterval(timer)
    }, 24)
    return () => window.clearInterval(timer)
  }, [text])
  return value
}
