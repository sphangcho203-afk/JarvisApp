import React, { type CSSProperties } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { HelixCanvas } from './HelixCore'
import { THEMES } from './theme'
import type { AudioMetrics, AudioMetricsRef, CountdownState, HelixState, NativeTelemetry, TerminalLog, ThemeSpec } from './types'

interface Props {
  mode: HelixState
  audioRef: AudioMetricsRef
  metrics: AudioMetrics
  transcript: string
  response: string
  telemetry: NativeTelemetry
  countdown: CountdownState
  bridgeReady: boolean
  logs: TerminalLog[]
  onCoreTap: () => void
  onSetup: () => void
  setupLabel: string
  onClearLogs: () => void
}

export function Hud(props: Props) {
  const theme = THEMES[props.mode]
  const style = { '--accent': theme.hex, '--accent-rgb': theme.rgb, '--accent-glow': theme.glow } as CSSProperties
  return (
    <div className="helix-shell h-screen overflow-hidden text-white" style={style}>
      <div className="hud-grid pointer-events-none fixed inset-0 opacity-60" />
      <div className="scanlines pointer-events-none fixed inset-0 z-40 opacity-20" />
      <div className="noise-layer pointer-events-none fixed inset-0 z-40 opacity-[.025]" />
      <Status mode={props.mode} theme={theme} telemetry={props.telemetry} bridgeReady={props.bridgeReady} />

      <main className="relative z-10 grid h-[calc(100vh-58px)] min-h-0 grid-cols-1 grid-rows-[minmax(0,1fr)_minmax(190px,30vh)] gap-2 p-2 xl:grid-cols-12 xl:grid-rows-[minmax(0,1fr)_230px] xl:gap-3 xl:p-4">
        <aside className="hidden min-h-0 xl:col-span-3 xl:block"><Telemetry theme={theme} metrics={props.metrics} data={props.telemetry} /></aside>
        <CoreStage {...props} theme={theme} />
        <aside className="hidden min-h-0 xl:col-span-3 xl:block"><Diagnostics mode={props.mode} theme={theme} metrics={props.metrics} data={props.telemetry} /></aside>
        <div className="min-h-0 xl:col-span-12"><Terminal theme={theme} logs={props.logs} response={props.response} onClear={props.onClearLogs} /></div>
      </main>
    </div>
  )
}

function Status({ mode, theme, telemetry, bridgeReady }: { mode: HelixState; theme: ThemeSpec; telemetry: NativeTelemetry; bridgeReady: boolean }) {
  return <header className="relative z-20 flex h-[58px] items-center justify-between border-b border-white/8 bg-black/25 px-3 backdrop-blur-xl md:px-6">
    <div className="flex min-w-0 items-center gap-3">
      <div className="grid size-8 place-items-center border font-mono text-[9px]" style={{ borderColor: `rgba(${theme.rgb}/.55)`, color: theme.hex, boxShadow: `inset 0 0 15px rgba(${theme.rgb}/.15)` }}>JH</div>
      <div className="min-w-0"><p className="truncate font-mono text-[11px] tracking-[.2em] text-white/90 md:text-sm">JARVIS // HELIX</p><p className="truncate font-mono text-[7px] tracking-[.22em] text-white/32 md:text-[9px]">ANDROID COGNITIVE INTERFACE // BUILD 0.9.19</p></div>
    </div>
    <div className="flex items-center gap-3 font-mono text-[7px] tracking-[.16em] text-white/45 md:text-[9px]">
      <span className="hidden md:inline">{telemetry.time} // {telemetry.voiceSource.toUpperCase()} // {bridgeReady ? 'ANDROID LINK' : 'SYNC'}</span>
      <div className="flex items-center gap-2"><motion.span animate={{ opacity: mode === 'ERROR' ? [1,.2,1] : [.45,1,.45], scale: [.9,1.2,.9] }} transition={{ duration: mode === 'PROCESSING' ? .75 : 1.5, repeat: Infinity }} className="size-2 rounded-full" style={{ backgroundColor: theme.hex, boxShadow: `0 0 14px ${theme.glow}` }} /><span style={{ color: theme.hex }}>{theme.label}</span></div>
    </div>
  </header>
}

function CoreStage(props: Props & { theme: ThemeSpec }) {
  const { mode, theme, metrics, telemetry, countdown } = props
  return <section className="core-stage relative min-h-0 overflow-hidden border xl:col-span-6" style={{ borderColor: `rgba(${theme.rgb}/.3)`, boxShadow: `inset 0 0 80px rgba(${theme.rgb}/.055),0 0 40px rgba(${theme.rgb}/.08)` }}>
    <button type="button" aria-label="Reset Jarvis voice input" onClick={props.onCoreTap} className="absolute inset-x-0 top-0 bottom-20 z-20 cursor-crosshair bg-transparent" />
    <div className="absolute inset-0"><HelixCanvas mode={mode} theme={theme} audio={props.audioRef} /></div>
    <div className="pointer-events-none absolute inset-0 z-10">
      <div className="reticle absolute left-1/2 top-[44%] size-[min(76vw,460px)] -translate-x-1/2 -translate-y-1/2 rounded-full" />
      <div className="absolute left-3 top-3 font-mono text-[7px] tracking-[.2em] text-white/35">WEBGL CORE // ORION HELIX</div>
      <div className="absolute right-3 top-3 text-right font-mono text-[7px] tracking-[.16em] text-white/30"><p>RENDER // GPU</p><p className="mt-1">BRIDGE // {props.bridgeReady ? 'LOCKED' : 'SYNCING'}</p></div>
      <div className="absolute left-3 top-12 w-[42%] space-y-1 border-l pl-2 font-mono text-[7px] tracking-[.13em] text-white/45 xl:hidden" style={{ borderColor: theme.hex }}><p>BATTERY // {telemetry.battery}%</p><p>NETWORK // {telemetry.network}</p><p>HEAP // {telemetry.heapMb} MB</p></div>
      <div className="absolute right-3 top-12 w-[42%] space-y-1 border-r pr-2 text-right font-mono text-[7px] tracking-[.13em] text-white/45 xl:hidden" style={{ borderColor: theme.hex }}><p>STATE // {mode}</p><p>VOICE // {Math.round(metrics.rms*100)}%</p><p>PEAK // {Math.round(metrics.peak*100)}%</p></div>
      <AnimatePresence mode="wait"><motion.div key={mode} initial={{ opacity:0,y:8,filter:'blur(8px)' }} animate={{ opacity:1,y:0,filter:'blur(0)' }} exit={{ opacity:0,y:-8,filter:'blur(8px)' }} transition={{ duration:.28 }} className="absolute inset-x-0 bottom-24 text-center"><p className="font-mono text-[10px] tracking-[.36em]" style={{ color: theme.hex, textShadow:`0 0 18px ${theme.glow}` }}>{theme.label}</p><p className="mt-2 px-8 font-mono text-[7px] tracking-[.15em] text-white/35">{theme.note.toUpperCase()}</p></motion.div></AnimatePresence>
    </div>

    <div className="absolute inset-x-3 bottom-3 z-30 border border-white/10 bg-black/65 px-3 py-2 backdrop-blur-md">
      <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
        <div className="min-w-0 font-mono text-[7px] tracking-[.12em] text-white/40">
          <p className="truncate">HEARD // {props.transcript}</p>
          <p className="mt-1 truncate" style={{ color: theme.hex }}>VOICE LINK // {telemetry.voiceSource.toUpperCase()}</p>
        </div>
        <button type="button" onClick={props.onSetup} className="border px-3 py-2 font-mono text-[7px] tracking-[.14em]" style={{ borderColor:`rgba(${theme.rgb}/.45)`,color:theme.hex }}>{props.setupLabel}</button>
      </div>
      {countdown.active ? <div className="mt-2 h-px bg-white/8"><div className="h-px" style={{ width:`${countdown.progress*100}%`,background:theme.hex }} /></div> : null}
    </div>
  </section>
}

function Panel({ title, eyebrow, theme, children }: { title:string; eyebrow:string; theme:ThemeSpec; children:React.ReactNode }) {
  return <section className="hud-panel relative flex h-full min-h-0 flex-col overflow-hidden border" style={{ borderColor:`rgba(${theme.rgb}/.32)`, background:`linear-gradient(145deg,rgba(${theme.rgb}/.075),rgba(2,8,13,.76) 32%,rgba(1,5,9,.9))` }}><header className="border-b border-white/7 px-4 py-3"><p className="font-mono text-[8px] tracking-[.3em] text-white/35">{eyebrow}</p><h2 className="mt-1 font-mono text-xs tracking-[.2em]" style={{ color:theme.hex }}>{title}</h2></header><div className="min-h-0 flex-1 p-4">{children}</div></section>
}

function Telemetry({ theme, metrics, data }: { theme:ThemeSpec; metrics:AudioMetrics; data:NativeTelemetry }) {
  const values = [['DEVICE',data.device],['BATTERY',`${data.battery}%`],['NETWORK',data.network],['HEAP',`${data.heapMb} MB`],['CLOCK',data.time],['CORTEX',data.cloudConfigured?'MESH ONLINE':'LOCAL MODE']]
  return <Panel title="TELEMETRY" eyebrow="ANDROID LIVE SIGNAL" theme={theme}><div className="grid grid-cols-2 gap-2">{values.map(([label,value])=><div key={label} className="border border-white/6 bg-white/[.025] p-3"><p className="font-mono text-[8px] tracking-[.18em] text-white/32">{label}</p><p className="mt-2 truncate font-mono text-[11px] text-white/82">{value}</p></div>)}</div><div className="mt-5 space-y-4"><Meter label="RMS / VOICE" value={metrics.rms} theme={theme}/><Meter label="PEAK ENVELOPE" value={metrics.peak} theme={theme}/><Meter label="CORE ENERGY" value={Math.min(1,metrics.rms*.75+theme.energy*.25)} theme={theme}/></div><p className="mt-5 border-l-2 pl-3 font-mono text-[8px] leading-5 tracking-[.11em] text-white/42" style={{ borderColor:theme.hex }}>VOICE-FIRST INTERFACE. ANDROID PERMISSIONS ARE VERIFIED, NOT ASSUMED.</p></Panel>
}

function Meter({ label, value, theme }: { label:string; value:number; theme:ThemeSpec }) { return <div><div className="mb-2 flex justify-between font-mono text-[8px] tracking-[.14em] text-white/38"><span>{label}</span><span>{Math.round(value*100)}%</span></div><div className="h-[2px] bg-white/8"><div className="h-full transition-[width] duration-100" style={{ width:`${value*100}%`,background:theme.hex,boxShadow:`0 0 10px ${theme.glow}` }} /></div></div> }

function Diagnostics({ mode, theme, metrics, data }: { mode:HelixState; theme:ThemeSpec; metrics:AudioMetrics; data:NativeTelemetry }) {
  const raw = [['LANGUAGE',72+metrics.mid*16],['MEMORY',80+metrics.rms*8],['SCREEN',74+metrics.treble*12],['VOICE',58+metrics.peak*38],['SECURITY',94],['ROUTER',data.cloudConfigured?88:72],['SENSORS',82+metrics.bass*10],['ARCHIVE',76]] as const
  return <Panel title="DIAGNOSTICS" eyebrow="DYNAMIC DATA NODES" theme={theme}><div className="space-y-2">{raw.map(([label,n],i)=>{const value=Math.min(100,Math.round(n));const warn=mode==='ERROR'&&i<2;return <div key={label} className="grid grid-cols-[auto_1fr_auto] items-center gap-3 border border-white/6 bg-white/[.02] px-3 py-2.5"><span className="size-1.5 rounded-full" style={{ background:warn?'#ef4444':theme.hex,boxShadow:`0 0 10px ${warn?'rgba(239,68,68,.65)':theme.glow}` }}/><div><div className="flex justify-between"><span className="font-mono text-[9px] tracking-[.15em] text-white/70">{label}</span><span className="font-mono text-[8px] text-white/28">HN-{String(i+1).padStart(2,'0')}</span></div><div className="mt-1.5 h-px bg-white/6"><div className="h-px" style={{ width:`${value}%`,background:warn?'#ef4444':theme.hex }}/></div></div><span className="font-mono text-[9px]" style={{ color:warn?'#ef4444':theme.hex }}>{value}%</span></div>})}</div></Panel>
}

function Terminal({ theme, logs, response, onClear }: { theme:ThemeSpec; logs:TerminalLog[]; response:string; onClear:()=>void }) {
  const scrambled = useScramble(response || 'Standing by, Sir.')
  return <Panel title="RESPONSE TERMINAL" eyebrow="JARVIS COGNITIVE OUTPUT" theme={theme}><button type="button" onClick={onClear} className="absolute right-3 top-3 z-10 border border-white/8 px-2 py-1 font-mono text-[7px] text-white/35">PURGE</button><div className="grid h-full min-h-0 gap-3 md:grid-cols-[1.1fr_1fr]"><div className="hidden min-h-0 overflow-auto pr-2 font-mono text-[9px] leading-5 md:block">{logs.slice(-8).map(log=><div key={log.id} className="grid grid-cols-[54px_44px_1fr] gap-2 border-b border-white/4 py-1"><span className="text-white/22">{log.time}</span><span style={{ color:log.channel==='WARN'?'#fca5a5':log.channel==='SYS'?'#a7f3d0':log.channel==='VOICE'?'#93c5fd':'#c4f5ff' }}>{log.channel}</span><span className="text-white/55">{log.text}</span></div>)}</div><div className="min-h-0 overflow-auto border p-3" style={{ borderColor:`rgba(${theme.rgb}/.22)`,background:`radial-gradient(circle at 20% 20%,rgba(${theme.rgb}/.1),transparent 55%)` }}><p className="font-mono text-[7px] tracking-[.24em] text-white/30">LATEST VERIFIED RESPONSE</p><p className="mt-2 whitespace-pre-wrap font-mono text-[10px] leading-5 md:text-xs" style={{ color:theme.hex,textShadow:`0 0 14px ${theme.glow}` }}>{scrambled}<span className="ml-1 animate-pulse">▌</span></p></div></div></Panel>
}

function useScramble(text:string) {
  const [value,setValue] = React.useState(text)
  React.useEffect(()=>{const chars='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#@%&*+-=<>/[]{}';const started=performance.now();const timer=window.setInterval(()=>{const p=Math.min(1,(performance.now()-started)/300);const locked=Math.floor(text.length*p);setValue(text.split('').map((c,i)=>i<locked||c===' '?c:chars[Math.floor(Math.random()*chars.length)]).join(''));if(p>=1)window.clearInterval(timer)},24);return()=>window.clearInterval(timer)},[text])
  return value
}
