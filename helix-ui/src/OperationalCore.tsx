import { useEffect, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { FridayMark } from './FridayMark'
import { sonicDirector } from './SonicDirector'
import type {
  AudioMetrics,
  CountdownState,
  FridayDesignMode,
  HelixState,
  NativeTelemetry,
  OperationalScene,
  OperationState,
  ResponseMeta,
  WeatherTelemetry,
} from './types'

interface Props {
  mode: HelixState
  metrics: AudioMetrics
  transcript: string
  response: string
  responseMeta: ResponseMeta
  designMode: FridayDesignMode
  telemetry: NativeTelemetry
  operation: OperationState
  weather: WeatherTelemetry
  countdown: CountdownState
  bridgeReady: boolean
  onCoreTap: () => void
  onOpen: (workspace: string) => void
}

const MODULES = [
  ['xcamera', 'VISION', 'X-CAMERA'],
  ['image', 'CREATE', 'VISUAL LAB'],
  ['memory', 'MEMORY', 'MEMORY VAULT'],
  ['diary', 'PRIVATE', 'PRIVATE DIARY'],
  ['providers', 'MESH', 'API PROVIDERS'],
  ['voice', 'VOICE', 'VOICE LAB'],
  ['control', 'ANDROID', 'SYSTEM CONTROL'],
  ['permissions', 'SECURITY', 'PERMISSION CENTER'],
] as const

const SCENE_LABEL: Record<OperationalScene, string> = {
  CORE: 'HELIX CORE',
  NEWS: 'GLOBAL INTELLIGENCE',
  NAVIGATION: 'ROUTE ANALYSIS',
  LOCATION: 'LIVE LOCATION',
  IMAGE: 'VISUAL SYNTHESIS',
  VISION: 'OPTICAL INTELLIGENCE',
  RESEARCH: 'RESEARCH MATRIX',
  ANALYSIS: 'ANALYTICAL ENGINE',
  DIARY: 'PRIVATE DIARY',
  MEMORY: 'MEMORY VAULT',
  PROVIDERS: 'PROVIDER MESH',
  SYSTEM: 'SYSTEM CONTROL',
  WEATHER: 'ATMOSPHERIC CORE',
  COMMUNICATION: 'COMMUNICATION LINK',
  TIMER: 'TEMPORAL CONTROL',
  ERROR: 'SYSTEM ALERT',
}

export function OperationalCore(props: Props) {
  const [launcherOpen, setLauncherOpen] = useState(false)
  const [booting, setBooting] = useState(true)
  const previousMode = useRef<HelixState>('IDLE')
  const previousDesign = useRef<FridayDesignMode>(props.designMode)
  const scene = useMemo(
    () => inferOperationalScene(props.transcript, props.response, props.responseMeta, props.operation, props.mode),
    [props.transcript, props.response, props.responseMeta, props.operation, props.mode],
  )
  const route = useMemo(() => extractRoute(props.transcript), [props.transcript])
  const immersive = props.mode !== 'IDLE' || props.operation.active || scene !== 'CORE'
  const response = polishResponse(props.response)
  const stateLabel = props.operation.active ? props.operation.stage : props.mode === 'IDLE' ? 'ONLINE' : props.mode

  useEffect(() => {
    const timer = window.setTimeout(() => setBooting(false), 1150)
    return () => window.clearTimeout(timer)
  }, [])

  useEffect(() => {
    sonicDirector.transition(previousMode.current, props.mode, scene)
    previousMode.current = props.mode
  }, [props.mode, scene])

  useEffect(() => {
    if (previousDesign.current !== props.designMode) {
      sonicDirector.changeTheme(props.designMode)
      previousDesign.current = props.designMode
    }
  }, [props.designMode])

  const openModule = (routeName: string) => {
    sonicDirector.open()
    navigator.vibrate?.(18)
    setLauncherOpen(false)
    props.onOpen(routeName)
  }

  const activateVoice = () => {
    sonicDirector.wake()
    navigator.vibrate?.([12, 24, 18])
    props.onCoreTap()
  }

  return (
    <div
      className="operational-root"
      data-state={props.mode.toLowerCase()}
      data-scene={scene.toLowerCase()}
      data-design={props.designMode.toLowerCase()}
      data-immersive={immersive ? 'true' : 'false'}
    >
      <div className="operational-depth" />
      <div className="operational-stars" />
      <div className="operational-frame" />

      <AnimatePresence>
        {booting ? (
          <motion.button
            type="button"
            className="operational-boot"
            onClick={() => { sonicDirector.wake(); setBooting(false) }}
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 1.08, filter: 'blur(20px)' }}
            transition={{ duration: .5 }}
          >
            <div className="boot-geometry"><i /><i /><i /><span><FridayMark /></span></div>
            <b>F.R.I.D.A.Y.</b>
            <small>{props.bridgeReady ? 'NATIVE INTELLIGENCE VERIFIED' : 'SYNCHRONIZING CORE'}</small>
          </motion.button>
        ) : null}
      </AnimatePresence>

      <header className="operational-head">
        <button type="button" className="operational-menu" onClick={() => { sonicDirector.open(); setLauncherOpen(true) }} aria-label="Open FRIDAY systems">
          <i /><i /><i />
        </button>
        <div className="operational-identity">
          <span><FridayMark /></span>
          <div><b>F.R.I.D.A.Y.</b><small>{SCENE_LABEL[scene]}</small></div>
        </div>
        <div className="operational-status"><i /><span>{stateLabel}</span></div>
      </header>

      <main className="operational-stage">
        <AnimatePresence mode="wait">
          <motion.section
            key={`${scene}-${props.designMode}`}
            className="scene-shell"
            initial={{ opacity: 0, scale: .92, rotateX: 8, filter: 'blur(18px)' }}
            animate={{ opacity: 1, scale: 1, rotateX: 0, filter: 'blur(0)' }}
            exit={{ opacity: 0, scale: 1.05, rotateX: -6, filter: 'blur(16px)' }}
            transition={{ duration: .55, ease: [0.16, 1, 0.3, 1] }}
          >
            <SceneVisual
              scene={scene}
              mode={props.mode}
              metrics={props.metrics}
              route={route}
              weather={props.weather}
              countdown={props.countdown}
              operation={props.operation}
            />
          </motion.section>
        </AnimatePresence>

        <AnimatePresence mode="wait">
          {props.mode === 'LISTENING' ? (
            <motion.div className="voice-command" key="listening" initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }}>
              <Waveform metrics={props.metrics} />
              <p>{cleanTranscript(props.transcript)}</p>
            </motion.div>
          ) : props.mode === 'PROCESSING' || props.operation.active ? (
            <motion.div className="processing-command" key="processing" initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }}>
              <span>{SCENE_LABEL[scene]}</span>
              <b>{props.operation.active ? props.operation.detail : 'Constructing verified response'}</b>
              <i><em style={{ width: `${Math.max(10, props.operation.progress * 100)}%` }} /></i>
            </motion.div>
          ) : props.mode === 'SPEAKING' || scene !== 'CORE' ? (
            <motion.div className="response-capsule" key="response" initial={{ opacity: 0, y: 24, filter: 'blur(10px)' }} animate={{ opacity: 1, y: 0, filter: 'blur(0)' }} exit={{ opacity: 0, y: 12 }}>
              <small>{SCENE_LABEL[scene]} · VERIFIED</small>
              <p>{response}</p>
            </motion.div>
          ) : (
            <motion.div className="response-capsule home-capsule" key="home" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <small>PRIVATE INTELLIGENCE ONLINE</small>
              <p>Awaiting your command, Sir.</p>
            </motion.div>
          )}
        </AnimatePresence>
      </main>

      <button type="button" className="operational-voice" onClick={activateVoice} aria-label="Speak to FRIDAY">
        <span /><span /><span /><i />
      </button>

      <footer className="operational-footer">
        <span>{props.telemetry.time}</span>
        <span>{props.designMode} MATRIX</span>
        <span>{props.telemetry.battery}%</span>
      </footer>

      <AnimatePresence>
        {launcherOpen ? (
          <motion.div className="module-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onPointerDown={event => { if (event.target === event.currentTarget) setLauncherOpen(false) }}>
            <motion.section className="module-matrix" initial={{ opacity: 0, scale: .92, rotateX: 9 }} animate={{ opacity: 1, scale: 1, rotateX: 0 }} exit={{ opacity: 0, scale: .96 }}>
              <header><div><small>SYSTEM MATRIX</small><h2>FRIDAY MODULES</h2></div><button type="button" onClick={() => setLauncherOpen(false)}>CLOSE</button></header>
              <div className="module-grid">
                {MODULES.map(([routeName, code, title], index) => (
                  <motion.button key={routeName} type="button" onClick={() => openModule(routeName)} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * .035 }}>
                    <i><span /></i><small>{code}</small><b>{title}</b>
                  </motion.button>
                ))}
              </div>
              <footer><span>VOICE COMMAND</span><b>“CHANGE SYSTEM DESIGN”</b><em>{props.designMode}</em></footer>
            </motion.section>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

function SceneVisual({ scene, mode, metrics, route, weather, countdown, operation }: {
  scene: OperationalScene
  mode: HelixState
  metrics: AudioMetrics
  route: { from: string; to: string }
  weather: WeatherTelemetry
  countdown: CountdownState
  operation: OperationState
}) {
  if (scene === 'NEWS') return <NewsGeometry />
  if (scene === 'NAVIGATION') return <NavigationGeometry from={route.from} to={route.to} />
  if (scene === 'LOCATION') return <LocationGeometry />
  if (scene === 'IMAGE' || scene === 'VISION') return <VisionGeometry scene={scene} />
  if (scene === 'DIARY' || scene === 'MEMORY') return <VaultGeometry scene={scene} />
  if (scene === 'PROVIDERS') return <ProviderGeometry />
  if (scene === 'WEATHER') return <WeatherGeometry weather={weather} />
  if (scene === 'TIMER') return <TimerGeometry countdown={countdown} />
  if (scene === 'ANALYSIS' || scene === 'RESEARCH') return <AnalysisGeometry scene={scene} operation={operation} />
  if (scene === 'SYSTEM' || scene === 'COMMUNICATION') return <SystemGeometry scene={scene} />
  if (scene === 'ERROR') return <CoreGeometry mode="ERROR" metrics={metrics} />
  return <CoreGeometry mode={mode} metrics={metrics} />
}

function CoreGeometry({ mode, metrics }: { mode: HelixState; metrics: AudioMetrics }) {
  const amplitude = 1 + Math.min(.18, metrics.rms * .22)
  return <div className="core-geometry" style={{ '--voice-scale': amplitude } as React.CSSProperties}>
    <div className="core-axis axis-a" /><div className="core-axis axis-b" />
    <div className="core-ring ring-1" /><div className="core-ring ring-2" /><div className="core-ring ring-3" /><div className="core-ring ring-4" />
    <div className="core-poly poly-a" /><div className="core-poly poly-b" />
    <div className="core-light"><i /></div>
    <div className="core-nodes">{Array.from({ length: 12 }, (_, index) => <i key={index} style={{ '--i': index } as React.CSSProperties} />)}</div>
    <span>{mode === 'LISTENING' ? 'VOICE FIELD' : mode === 'PROCESSING' ? 'COGNITIVE ORBIT' : 'HELIX CORE'}</span>
  </div>
}

function NewsGeometry() {
  return <div className="news-geometry">
    <div className="news-globe"><i /><i /><i />{Array.from({ length: 10 }, (_, index) => <span key={index} style={{ '--i': index } as React.CSSProperties} />)}</div>
    <div className="news-orbit orbit-a" /><div className="news-orbit orbit-b" />
    <div className="news-stream stream-a"><b>WORLD</b><i /></div>
    <div className="news-stream stream-b"><b>BUSINESS</b><i /></div>
    <div className="news-stream stream-c"><b>SCIENCE</b><i /></div>
  </div>
}

function NavigationGeometry({ from, to }: { from: string; to: string }) {
  return <div className="navigation-geometry">
    <div className="geo-grid">{['60°N','30°N','0°','30°S'].map(item => <span key={item}>{item}</span>)}</div>
    <svg viewBox="0 0 600 380" role="img" aria-label={`Route from ${from} to ${to}`}>
      <defs><filter id="routeGlow"><feGaussianBlur stdDeviation="5" result="blur" /><feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge></filter></defs>
      <path className="map-contour" d="M36 236 C110 190 137 250 201 205 C268 160 315 185 367 138 C430 82 494 105 565 58" />
      <path className="route-path" d="M105 265 C245 70 405 80 525 124" filter="url(#routeGlow)" />
      <circle className="route-origin" cx="105" cy="265" r="10" /><circle className="route-target" cx="525" cy="124" r="12" />
    </svg>
    <div className="route-label route-from"><small>ORIGIN</small><b>{from}</b></div>
    <div className="route-label route-to"><small>DESTINATION</small><b>{to}</b></div>
  </div>
}

function LocationGeometry() {
  return <div className="location-geometry">
    <div className="location-grid" /><div className="location-radar"><i /><i /><i /><span /></div>
    <div className="location-pin"><i /></div><div className="location-check">✓</div>
    <small>ACQUIRING VERIFIED DEVICE POSITION</small>
  </div>
}

function VisionGeometry({ scene }: { scene: 'IMAGE' | 'VISION' }) {
  return <div className="vision-geometry">
    <div className="wire-cube"><i /><i /><i /><i /><i /><i /></div>
    <div className="vision-scan" /><div className="vision-reticle"><i /><i /><i /><i /></div>
    <small>{scene === 'IMAGE' ? 'SYNTHESIS VOLUME' : 'OPTICAL FIELD'}</small>
  </div>
}

function VaultGeometry({ scene }: { scene: 'DIARY' | 'MEMORY' }) {
  return <div className="vault-geometry">
    <div className="vault-shell"><i /><i /><i /><span /></div>
    <div className="vault-pages"><i /><i /><i /></div>
    <small>{scene === 'DIARY' ? 'OWNER-LOCKED JOURNAL' : 'ENCRYPTED KNOWLEDGE'}</small>
  </div>
}

function ProviderGeometry() {
  return <div className="provider-geometry">
    <div className="provider-core" />
    {Array.from({ length: 8 }, (_, index) => <div className="provider-node" key={index} style={{ '--i': index } as React.CSSProperties}><i /></div>)}
    <small>ENCRYPTED MODEL ROUTES</small>
  </div>
}

function WeatherGeometry({ weather }: { weather: WeatherTelemetry }) {
  return <div className="weather-geometry">
    <div className="weather-orb"><i /><i /><i /></div>
    <div className="weather-wind">{Array.from({ length: 7 }, (_, index) => <i key={index} />)}</div>
    <b>{weather.configured ? `${Math.round(weather.tempC)}°` : '—'}</b><small>{weather.configured ? weather.condition : 'ATMOSPHERIC LINK PENDING'}</small>
  </div>
}

function TimerGeometry({ countdown }: { countdown: CountdownState }) {
  const seconds = Math.ceil(countdown.remainingMs / 1000)
  return <div className="timer-geometry"><div className="timer-ring" style={{ '--timer': countdown.progress } as React.CSSProperties}><i /></div><b>{countdown.active ? formatTime(seconds) : '00:00'}</b><small>{countdown.label}</small></div>
}

function AnalysisGeometry({ scene, operation }: { scene: 'ANALYSIS' | 'RESEARCH'; operation: OperationState }) {
  return <div className="analysis-geometry">
    <div className="analysis-lattice">{Array.from({ length: 9 }, (_, index) => <i key={index} style={{ '--i': index } as React.CSSProperties} />)}</div>
    <svg viewBox="0 0 520 260"><path d="M15 215 C82 190 92 120 156 148 C224 178 258 35 323 84 C392 137 410 68 505 29" /></svg>
    <b>{scene === 'RESEARCH' ? 'SOURCE SYNTHESIS' : 'CALCULATION MATRIX'}</b><small>{operation.active ? operation.stage : 'VERIFIED ANALYSIS READY'}</small>
  </div>
}

function SystemGeometry({ scene }: { scene: 'SYSTEM' | 'COMMUNICATION' }) {
  return <div className="system-geometry"><div className="system-rings"><i /><i /><i /><i /></div><div className="system-links">{Array.from({ length: 6 }, (_, index) => <i key={index} style={{ '--i': index } as React.CSSProperties} />)}</div><small>{scene === 'COMMUNICATION' ? 'PRIVATE COMMUNICATION LINK' : 'ANDROID CONTROL MATRIX'}</small></div>
}

function Waveform({ metrics }: { metrics: AudioMetrics }) {
  return <div className="operational-waveform">{Array.from({ length: 44 }, (_, index) => {
    const factor = .22 + Math.abs(Math.sin(index * .58)) * .78
    const height = 6 + factor * (16 + metrics.rms * 64)
    return <i key={index} style={{ height }} />
  })}</div>
}

export function inferOperationalScene(transcript: string, response: string, meta: ResponseMeta, operation: OperationState, mode: HelixState): OperationalScene {
  if (mode === 'ERROR') return 'ERROR'
  const value = `${transcript} ${response} ${meta.intent} ${meta.decision} ${meta.trace.join(' ')} ${meta.entities.join(' ')} ${operation.stage} ${operation.detail}`.toLowerCase()
  if (/where am i|my location|current location|locate me|you.re here/.test(value)) return 'LOCATION'
  if (/route|directions|navigation|navigate|map|distance|travel from|road to|flight to/.test(value)) return 'NAVIGATION'
  if (/world news|headlines|breaking news|happening around|global brief|news brief/.test(value)) return 'NEWS'
  if (/image|visualize|visual lab|generate picture|synthesis/.test(value)) return 'IMAGE'
  if (/camera|vision|x-camera|optical|what can you see/.test(value)) return 'VISION'
  if (/private diary|journal|diary/.test(value)) return 'DIARY'
  if (/memory vault|remember|memory\//.test(value)) return 'MEMORY'
  if (/provider|api mesh|openai|anthropic|deepseek|gemini|model route/.test(value)) return 'PROVIDERS'
  if (/weather|forecast|temperature|rain|storm|humidity/.test(value)) return 'WEATHER'
  if (/timer|countdown|alarm|temporal/.test(value)) return 'TIMER'
  if (/whatsapp|gmail|message|email|call|communication/.test(value)) return 'COMMUNICATION'
  if (/system control|device\/|permission|brightness|volume|bluetooth|wifi/.test(value)) return 'SYSTEM'
  if (/calculate|calculation|equation|compare|analytics|analysis/.test(value)) return 'ANALYSIS'
  if (/research|sources|search grid|investigate|report/.test(value)) return 'RESEARCH'
  return 'CORE'
}

function extractRoute(text: string): { from: string; to: string } {
  const clean = text.replace(/[?.,!]/g, ' ').replace(/\s+/g, ' ').trim()
  const match = clean.match(/from\s+(.{2,32}?)\s+to\s+(.{2,32})$/i)
  if (match) return { from: titleCase(match[1]), to: titleCase(match[2]) }
  return { from: 'ORIGIN', to: 'DESTINATION' }
}

function titleCase(value: string) {
  return value.trim().split(' ').slice(0, 4).map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase()).join(' ')
}

function cleanTranscript(value: string) {
  const clean = value.replace(/OWNER CHANNEL ARMED.*$/i, '').replace(/\s+/g, ' ').trim()
  return clean || 'Listening…'
}

function polishResponse(value: string) {
  const lines = value
    .replace(/```[\s\S]*?```/g, ' Code prepared on screen. ')
    .split('\n')
    .map(line => line.replace(/^[A-Z0-9 _-]+\s*\/\/\s*/i, '').replace(/[*#>`_]/g, '').trim())
    .filter(Boolean)
  const clean = lines.join(' ').replace(/\s+/g, ' ').trim()
  return clean.length > 360 ? `${clean.slice(0, 357).trimEnd()}…` : clean || 'Verified response ready.'
}

function formatTime(totalSeconds: number) {
  const safe = Math.max(0, totalSeconds)
  const minutes = Math.floor(safe / 60).toString().padStart(2, '0')
  const seconds = Math.floor(safe % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
}
