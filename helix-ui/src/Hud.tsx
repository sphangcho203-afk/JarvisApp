import React, { type CSSProperties } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { FridayMark } from './FridayMark'
import { HelixCanvas } from './HelixCore'
import { THEMES } from './theme'
import type { AudioMetrics, AudioMetricsRef, CountdownState, HelixState, NativeTelemetry, OperationState, TerminalLog, ThemeSpec, WeatherTelemetry } from './types'

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

export function Hud(props: Props) {
  const theme = THEMES[props.mode]
  const style = { '--accent': theme.hex, '--accent-rgb': theme.rgb, '--accent-glow': theme.glow } as CSSProperties
  return (
    <div className="helix-shell h-screen overflow-hidden text-white" style={style}>
      <div className="hud-grid pointer-events-none fixed inset-0 opacity-55" />
      <div className="scanlines pointer-events-none fixed inset-0 z-40 opacity-[.16]" />
      <div className="noise-layer pointer-events-none fixed inset-0 z-40 opacity-[.018]" />
      <Status mode={props.mode} theme={theme} telemetry={props.telemetry} bridgeReady={props.bridgeReady} />
      <main className="relative z-10 grid h-[calc(100vh-64px)] min-h-0 grid-cols-1 grid-rows-[minmax(0,1fr)_minmax(210px,31vh)] gap-2 p-2 xl:grid-cols-12 xl:grid-rows-[minmax(0,1fr)_238px] xl:gap-3 xl:p-4">
        <aside className="hidden min-h-0 xl:col-span-3 xl:block"><TelemetryPanel theme={theme} metrics={props.metrics} data={props.telemetry} weather={props.weather} /></aside>
        <CoreStage {...props} theme={theme} />
        <aside className="hidden min-h-0 xl:col-span-3 xl:block"><ServiceGrid theme={theme} data={props.telemetry} /></aside>
        <div className="min-h-0 xl:col-span-12"><Terminal theme={theme} logs={props.logs} response={props.response} operation={props.operation} onClear={props.onClearLogs} /></div>
      </main>
    </div>
  )
}

function Status({ mode, theme, telemetry, bridgeReady }: { mode: HelixState; theme: ThemeSpec; telemetry: NativeTelemetry; bridgeReady: boolean }) {
  const latency = telemetry.latencyMs >= 0 ? `${telemetry.latencyMs} MS` : 'PROBING'
  return (
    <header className="relative z-20 flex h-16 items-center justify-between border-b border-blue-400/15 bg-[#01050c]/86 px-3 backdrop-blur-xl md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <div className="friday-mark size-11 shrink-0"><FridayMark /></div>
        <div className="min-w-0">
          <p className="truncate font-display text-[12px] font-semibold tracking-[.22em] text-white/95 md:text-[15px]">F.R.I.D.A.Y. <span className="text-blue-300/45">//</span> HELIX OPS</p>
          <p className="mt-1 truncate font-tech text-[7px] tracking-[.2em] text-cyan-200/42 md:text-[9px]">SEONGJA PRIVATE INTELLIGENCE // BUILD 0.9.22</p>
        </div>
      </div>
      <div className="flex items-center gap-3 font-tech text-[7px] tracking-[.13em] text-white/42 md:text-[9px]">
        <div className="hidden border-r border-blue-300/15 pr-4 text-right md:block">
          <p>{telemetry.time} // UPTIME {formatUptime(telemetry.uptimeSeconds)}</p>
          <p className="mt-1 text-blue-300/70">NET {telemetry.networkQuality} // {latency}</p>
        </div>
        <div className="flex items-center gap-2">
          <motion.span animate={{ opacity: mode === 'ERROR' ? [1,.18,1] : [.45,1,.45], scale: [.9,1.18,.9] }} transition={{ duration: mode === 'PROCESSING' ? .68 : 1.45, repeat: Infinity }} className="size-2 rounded-full" style={{ backgroundColor: theme.hex, boxShadow: `0 0 14px ${theme.glow}` }} />
          <span style={{ color: theme.hex }}>{bridgeReady ? theme.label : 'BRIDGE SYNC'}</span>
        </div>
      </div>
    </header>
  )
}

function CoreStage(props: Props & { theme: ThemeSpec }) {
  const { mode, theme, metrics, telemetry, countdown, weather, operation } = props
  return (
    <section className="core-stage relative min-h-0 overflow-hidden border xl:col-span-6" style={{ borderColor: `rgba(${theme.rgb}/.32)`, boxShadow: `inset 0 0 90px rgba(${theme.rgb}/.055),0 0 45px rgba(${theme.rgb}/.08)` }}>
      <button type="button" aria-label="Recalibrate FRIDAY voice input" onClick={props.onCoreTap} className="absolute inset-x-0 top-0 bottom-24 z-20 cursor-crosshair bg-transparent" />
      <div className="absolute inset-0"><HelixCanvas mode={mode} theme={theme} audio={props.audioRef} /></div>
      <div className="pointer-events-none absolute inset-0 z-10">
        <div className="reticle absolute left-1/2 top-[45%] size-[min(76vw,460px)] -translate-x-1/2 -translate-y-1/2 rounded-full" />
        <div className="absolute left-3 top-3 font-tech text-[7px] tracking-[.2em] text-white/38">ORION HELIX // GPU RENDER</div>
        <div className="absolute right-3 top-3 text-right font-tech text-[7px] tracking-[.14em] text-white/34"><p>NATIVE BRIDGE // {props.bridgeReady ? 'SECURED' : 'SYNCING'}</p><p className="mt-1">OWNER CHANNEL // PRIVATE</p></div>

        <div className="absolute left-3 top-12 w-[44%] space-y-1 border-l pl-2 font-tech text-[7px] tracking-[.11em] text-white/52 xl:hidden" style={{ borderColor: theme.hex }}>
          <DataLine label="BATTERY" value={`${telemetry.battery}%`} />
          <DataLine label="LATENCY" value={telemetry.latencyMs >= 0 ? `${telemetry.latencyMs} MS` : 'PROBING'} />
          <DataLine label="JITTER" value={`${telemetry.jitterMs} MS`} />
          <DataLine label="LOSS" value={`${telemetry.packetLossPercent}%`} />
        </div>
        <div className="absolute right-3 top-12 w-[44%] space-y-1 border-r pr-2 text-right font-tech text-[7px] tracking-[.11em] text-white/52 xl:hidden" style={{ borderColor: theme.hex }}>
          <DataLine label="STATE" value={mode} />
          <DataLine label="VOICE" value={`${Math.round(metrics.rms*100)}%`} />
          <DataLine label="ROUTE" value={telemetry.cartesiaRoute || '--'} />
          <DataLine label="KEYS" value={`${telemetry.cartesiaKeys}/4`} />
        </div>

        <AnimatePresence mode="wait">
          <motion.div key={`${operation.stage}-${operation.active}`} initial={{ opacity:0,y:8,filter:'blur(8px)' }} animate={{ opacity:1,y:0,filter:'blur(0)' }} exit={{ opacity:0,y:-8,filter:'blur(8px)' }} transition={{ duration:.26 }} className="absolute inset-x-5 bottom-32 text-center">
            <p className="font-display text-[10px] font-semibold tracking-[.3em] md:text-xs" style={{ color: operation.active ? theme.hex : 'rgba(210,230,255,.72)', textShadow: operation.active ? `0 0 18px ${theme.glow}` : 'none' }}>{operation.active ? operation.stage : theme.label}</p>
            <p className="mx-auto mt-2 max-w-[520px] truncate font-tech text-[7px] tracking-[.13em] text-white/38">{operation.active ? operation.detail : theme.note.toUpperCase()}</p>
            {operation.active ? <div className="mx-auto mt-3 h-px w-[min(70%,360px)] overflow-hidden bg-white/8"><motion.div className="h-full" animate={{ width:`${Math.max(8,operation.progress*100)}%` }} transition={{ duration:.35 }} style={{ background:theme.hex,boxShadow:`0 0 12px ${theme.glow}` }} /></div> : null}
          </motion.div>
        </AnimatePresence>
      </div>

      <div className="absolute inset-x-3 bottom-3 z-30 border border-blue-300/12 bg-[#01050b]/78 px-3 py-2 backdrop-blur-md">
        <WeatherChip weather={weather} theme={theme} onRefresh={props.onRefreshWeather} />
        <div className="mt-2 grid grid-cols-[minmax(0,1fr)_auto] gap-3 font-tech text-[7px] tracking-[.1em] text-white/42">
          <div className="min-w-0"><p className="truncate">VOICE CAPTURE // {props.transcript}</p><p className="mt-1 truncate" style={{ color:theme.hex }}>OUTPUT ROUTE // {telemetry.voiceSource.toUpperCase()} // FAILOVER ARMED</p></div>
          <div className="text-right"><p>NET // {telemetry.networkQuality}</p><p className="mt-1 text-blue-300/75">↓{telemetry.downlinkMbps.toFixed(1)} ↑{telemetry.uplinkMbps.toFixed(1)} MBPS</p></div>
        </div>
        <div className="mt-2 grid grid-cols-3 gap-1 font-tech text-[6px] tracking-[.08em] text-white/38 xl:hidden">
          <MiniStatus label="CORTEX" value={`${telemetry.cortexOnline}/${telemetry.cortexConfigured}`} />
          <MiniStatus label="SEARCH" value={`${telemetry.searchOnline}/${telemetry.searchConfigured}`} />
          <MiniStatus label="DEEPSEEK" value={compactHealth(telemetry.deepSeekStatus)} />
        </div>
        {countdown.active ? <div className="mt-2 h-px bg-white/8"><div className="h-px" style={{ width:`${countdown.progress*100}%`,background:theme.hex }} /></div> : null}
      </div>
    </section>
  )
}

function DataLine({ label, value }: { label:string; value:string }) { return <p><span className="text-white/28">{label} // </span>{value}</p> }
function MiniStatus({ label, value }: { label:string; value:string }) { return <div className="border border-white/7 bg-white/[.02] px-2 py-1"><span className="text-white/25">{label}</span><span className="float-right text-blue-300/75">{value}</span></div> }

function WeatherChip({ weather, theme, onRefresh }: { weather: WeatherTelemetry; theme: ThemeSpec; onRefresh: () => void }) {
  if (!weather.configured) return <div className="w-full border border-white/8 px-2 py-1.5 font-tech text-[7px] tracking-[.12em] text-white/34">WEATHER CORE // CONFIGURATION PENDING</div>
  return <button type="button" aria-label="Refresh FRIDAY weather intelligence" onClick={onRefresh} className="grid w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2 border border-white/8 px-2 py-1.5 text-left font-tech text-[7px] tracking-[.09em]" style={{ color: weather.alert ? '#fca5a5' : theme.hex }}><span className="text-sm">{weather.icon || weatherGlyph(weather.condition)}</span><span className="min-w-0 truncate">WEATHER CORE // {Math.round(weather.tempC)}°C // {weather.condition.toUpperCase()} // WIND {Math.round(weather.windKph)} KPH // RAIN {weather.rainChance}%</span><span className="text-white/30">{weather.fresh ? weather.location.toUpperCase().slice(0,16) : 'STALE'}</span></button>
}

function weatherGlyph(condition: string): string {
  const value = condition.toLowerCase()
  if (value.includes('thunder') || value.includes('storm')) return '⛈️'
  if (value.includes('rain') || value.includes('drizzle')) return '🌧️'
  if (value.includes('snow') || value.includes('sleet')) return '🌨️'
  if (value.includes('fog') || value.includes('mist')) return '🌫️'
  if (value.includes('cloud') || value.includes('overcast')) return '☁️'
  return '☀️'
}

function Panel({ title, eyebrow, theme, children }: { title:string; eyebrow:string; theme:ThemeSpec; children:React.ReactNode }) {
  return <section className="hud-panel relative flex h-full min-h-0 flex-col overflow-hidden border" style={{ borderColor:`rgba(${theme.rgb}/.32)`,background:`linear-gradient(145deg,rgba(${theme.rgb}/.075),rgba(2,8,16,.82) 34%,rgba(1,5,11,.94))` }}><header className="border-b border-white/7 px-4 py-3"><p className="font-tech text-[7px] tracking-[.27em] text-white/32">{eyebrow}</p><h2 className="mt-1 font-display text-xs font-semibold tracking-[.18em]" style={{ color:theme.hex }}>{title}</h2></header><div className="min-h-0 flex-1 p-4">{children}</div></section>
}

function TelemetryPanel({ theme, metrics, data, weather }: { theme:ThemeSpec; metrics:AudioMetrics; data:NativeTelemetry; weather:WeatherTelemetry }) {
  const values = [
    ['DEVICE',data.device],['BATTERY',`${data.battery}%`],['NETWORK',data.networkQuality],['LATENCY',data.latencyMs>=0?`${data.latencyMs} MS`:'PROBING'],
    ['JITTER',`${data.jitterMs} MS`],['PACKET LOSS',`${data.packetLossPercent}%`],['DOWNLINK',`${data.downlinkMbps.toFixed(1)} MBPS`],['UPLINK',`${data.uplinkMbps.toFixed(1)} MBPS`],
    ['WEATHER',weather.configured?`${Math.round(weather.tempC)}°C ${weather.condition}`:'PENDING'],['UPTIME',formatUptime(data.uptimeSeconds)],
  ]
  return <Panel title="LIVE TELEMETRY" eyebrow="VERIFIED NATIVE + NETWORK SIGNAL" theme={theme}><div className="grid grid-cols-2 gap-2">{values.map(([label,value])=><div key={label} className="border border-white/6 bg-white/[.025] p-2.5"><p className="font-tech text-[7px] tracking-[.15em] text-white/28">{label}</p><p className="mt-1.5 truncate font-tech text-[10px] text-white/82">{value}</p></div>)}</div><div className="mt-4 space-y-3"><Meter label="VOICE ENERGY" value={metrics.rms} theme={theme}/><Meter label="PEAK ENVELOPE" value={metrics.peak} theme={theme}/><Meter label="CORE LOAD" value={Math.min(1,metrics.rms*.7+theme.energy*.3)} theme={theme}/></div></Panel>
}

function ServiceGrid({ theme, data }: { theme:ThemeSpec; data:NativeTelemetry }) {
  const rows = [
    ['CORTEX MESH',`${data.cortexOnline}/${data.cortexConfigured} ONLINE`],
    ['SEARCH GRID',`${data.searchOnline}/${data.searchConfigured} ONLINE`],
    ['CARTESIA',`${data.cartesiaRoute} // ${data.cartesiaKeys}/4 KEYS`],
    ['DEEPSEEK',compactHealth(data.deepSeekStatus)],
    ['YOUTUBE DATA',compactHealth(data.youtubeStatus)],
    ['GMAIL',compactHealth(data.gmailStatus)],
    ['NATIVE BRIDGE',data.cloudConfigured?'SECURED':'LOCAL CORE'],
    ['MEMORY HEAP',`${data.heapMb} MB`],
  ]
  return <Panel title="SERVICE GRID" eyebrow="ROUTES // FAILOVER // AUTH STATE" theme={theme}><div className="space-y-2">{rows.map(([label,value],i)=><div key={label} className="grid grid-cols-[8px_minmax(0,1fr)] items-center gap-3 border border-white/6 bg-white/[.02] px-3 py-2.5"><motion.span animate={{ opacity:[.35,1,.35] }} transition={{ duration:1.4+i*.08,repeat:Infinity }} className="size-1.5 rounded-full" style={{ background:value.includes('ERROR')||value.includes('REQUIRED')?'#f59e0b':theme.hex,boxShadow:`0 0 10px ${theme.glow}` }}/><div className="min-w-0"><p className="font-tech text-[8px] tracking-[.13em] text-white/35">{label}</p><p className="mt-1 truncate font-tech text-[9px] text-white/75">{value}</p></div></div>)}</div></Panel>
}

function Meter({ label, value, theme }: { label:string; value:number; theme:ThemeSpec }) { return <div><div className="mb-2 flex justify-between font-tech text-[7px] tracking-[.13em] text-white/35"><span>{label}</span><span>{Math.round(value*100)}%</span></div><div className="h-[2px] bg-white/8"><div className="h-full transition-[width] duration-100" style={{ width:`${value*100}%`,background:theme.hex,boxShadow:`0 0 10px ${theme.glow}` }} /></div></div> }

function Terminal({ theme, logs, response, operation, onClear }: { theme:ThemeSpec; logs:TerminalLog[]; response:string; operation:OperationState; onClear:()=>void }) {
  const scrambled = useScramble(response)
  return <Panel title="RESPONSE TERMINAL" eyebrow="F.R.I.D.A.Y. COGNITIVE OUTPUT // VERIFIED CHANNEL" theme={theme}>
    <button type="button" onClick={onClear} className="absolute right-3 top-3 z-10 border border-white/8 px-2 py-1 font-tech text-[7px] tracking-[.1em] text-white/35">PURGE</button>
    <div className="grid h-full min-h-0 gap-2 md:grid-cols-[1.05fr_1.15fr]">
      <div className="min-h-0 overflow-auto border border-white/6 bg-black/20 px-2 font-tech text-[7px] leading-4 md:text-[9px] md:leading-5">{logs.slice(-8).map(log=><div key={log.id} className="grid grid-cols-[44px_38px_1fr] gap-1.5 border-b border-white/4 py-1 md:grid-cols-[54px_44px_1fr] md:gap-2"><span className="text-white/20">{log.time}</span><span style={{ color:log.channel==='WARN'?'#fca5a5':log.channel==='SYS'?'#a7f3d0':log.channel==='VOICE'?'#93c5fd':'#c4f5ff' }}>{log.channel}</span><span className="truncate text-white/52">{log.text}</span></div>)}</div>
      <div className="min-h-0 overflow-auto border p-3" style={{ borderColor:`rgba(${theme.rgb}/.24)`,background:`radial-gradient(circle at 20% 20%,rgba(${theme.rgb}/.1),transparent 58%)` }}>
        <div className="flex items-center justify-between gap-3"><p className="font-tech text-[7px] tracking-[.2em] text-white/28">{operation.active ? operation.stage : 'LATEST VERIFIED RESPONSE'}</p><span className="font-tech text-[6px] tracking-[.12em] text-blue-300/55">{operation.active ? `${Math.round(operation.progress*100)}%` : 'LOCKED'}</span></div>
        {operation.active ? <p className="mt-2 font-tech text-[8px] tracking-[.1em] text-white/48">{operation.detail}<span className="ml-1 animate-pulse" style={{ color:theme.hex }}>▌</span></p> : <p className="mt-2 whitespace-pre-wrap font-tech text-[9px] leading-4 md:text-xs md:leading-5" style={{ color:theme.hex,textShadow:`0 0 14px ${theme.glow}` }}>{scrambled}<span className="ml-1 animate-pulse">▌</span></p>}
      </div>
    </div>
  </Panel>
}

function formatUptime(seconds:number):string { const h=Math.floor(seconds/3600);const m=Math.floor((seconds%3600)/60);const s=Math.floor(seconds%60);return [h,m,s].map(n=>String(n).padStart(2,'0')).join(':') }
function compactHealth(value:string):string { return value.replace(/\/\/.*$/,'').trim().replace('NOT CONFIGURED','OFFLINE').slice(0,24) }

function useScramble(text:string) {
  const [value,setValue] = React.useState(text)
  React.useEffect(()=>{const chars='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#@%&*+-=<>/[]{}';const started=performance.now();const timer=window.setInterval(()=>{const p=Math.min(1,(performance.now()-started)/300);const locked=Math.floor(text.length*p);setValue(text.split('').map((c,i)=>i<locked||c===' '?c:chars[Math.floor(Math.random()*chars.length)]).join(''));if(p>=1)window.clearInterval(timer)},24);return()=>window.clearInterval(timer)},[text])
  return value
}
