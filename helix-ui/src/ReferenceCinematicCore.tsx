import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { FridayMark } from './FridayMark'
import { inferOperationalScene } from './OperationalCore'
import { sonicDirector } from './SonicDirector'
import type {
  AudioMetrics,
  CountdownState,
  FridayDesignMode,
  HelixState,
  LocationSnapshot,
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
  location: LocationSnapshot
  countdown: CountdownState
  bridgeReady: boolean
  onCoreTap: () => void
  onOpen: (workspace: string) => void
}

type ModuleSpec = {
  route: string
  code: string
  title: string
  detail: string
  glyph: 'eye' | 'spark' | 'memory' | 'lock' | 'mesh' | 'voice' | 'control' | 'shield'
}

const MODULES: ModuleSpec[] = [
  { route: 'xcamera', code: 'VISION', title: 'X-CAMERA', detail: 'OPTICAL INTELLIGENCE', glyph: 'eye' },
  { route: 'image', code: 'CREATE', title: 'VISUAL LAB', detail: 'SYNTHESIS + ANALYSIS', glyph: 'spark' },
  { route: 'memory', code: 'MEMORY', title: 'MEMORY VAULT', detail: 'ENCRYPTED KNOWLEDGE', glyph: 'memory' },
  { route: 'diary', code: 'PRIVATE', title: 'PRIVATE DIARY', detail: 'OWNER-LOCKED JOURNAL', glyph: 'lock' },
  { route: 'providers', code: 'MESH', title: 'API PROVIDERS', detail: 'MODELS + KEYS + ROUTING', glyph: 'mesh' },
  { route: 'voice', code: 'VOICE', title: 'VOICE LAB', detail: 'LOCAL VOICE PROFILE', glyph: 'voice' },
  { route: 'control', code: 'ANDROID', title: 'SYSTEM CONTROL', detail: 'DEVICE AUTOMATION', glyph: 'control' },
  { route: 'permissions', code: 'SECURITY', title: 'PERMISSION CENTER', detail: 'TRUST + ACCESS', glyph: 'shield' },
]

const SCENE_TITLE: Record<OperationalScene, string> = {
  CORE: 'HELIX INTELLIGENCE CORE',
  NEWS: 'GLOBAL BRIEF',
  NAVIGATION: 'ROUTE ANALYSIS',
  LOCATION: 'LIVE LOCATION MODE',
  IMAGE: 'VISUAL SYNTHESIS',
  VISION: 'OPTICAL INTELLIGENCE',
  RESEARCH: 'RESEARCH MATRIX',
  ANALYSIS: 'CALCULATION MATRIX',
  DIARY: 'PRIVATE DIARY',
  MEMORY: 'MEMORY VAULT',
  PROVIDERS: 'PROVIDER MESH',
  SYSTEM: 'SYSTEM CONTROL',
  WEATHER: 'ATMOSPHERIC CORE',
  COMMUNICATION: 'PRIVATE COMMUNICATION',
  TIMER: 'TEMPORAL CONTROL',
  ERROR: 'SYSTEM ALERT',
}

export function ReferenceCinematicCore(props: Props) {
  const [booting, setBooting] = useState(true)
  const [modulesOpen, setModulesOpen] = useState(false)
  const previousMode = useRef<HelixState>('IDLE')
  const previousDesign = useRef<FridayDesignMode>(props.designMode)
  const scene = useMemo(
    () => inferOperationalScene(props.transcript, props.response, props.responseMeta, props.operation, props.mode),
    [props.transcript, props.response, props.responseMeta, props.operation, props.mode],
  )
  const voiceFocus = props.mode === 'LISTENING' || props.mode === 'PROCESSING' || props.mode === 'SPEAKING'
  const route = useMemo(() => parseRoute(props.transcript), [props.transcript])
  const briefs = useMemo(() => extractBriefs(props.response), [props.response])
  const summary = useMemo(() => cleanResponse(props.response), [props.response])

  useEffect(() => {
    const timer = window.setTimeout(() => setBooting(false), 1450)
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
    setModulesOpen(false)
    props.onOpen(routeName)
  }

  const activateVoice = () => {
    sonicDirector.wake()
    navigator.vibrate?.([12, 24, 18])
    props.onCoreTap()
  }

  return (
    <div
      className="reference-root"
      data-state={props.mode.toLowerCase()}
      data-scene={scene.toLowerCase()}
      data-design={props.designMode.toLowerCase()}
      data-voice-focus={voiceFocus ? 'true' : 'false'}
    >
      <div className="reference-void" />
      <div className="reference-stars" />
      <div className="reference-grid-floor" />
      <div className="reference-device-frame" />

      <AnimatePresence>
        {booting ? (
          <motion.button
            type="button"
            className="reference-boot"
            aria-label="Enter FRIDAY"
            onClick={() => { sonicDirector.wake(); setBooting(false) }}
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 1.08, filter: 'blur(26px)' }}
            transition={{ duration: .65, ease: [0.16, 1, 0.3, 1] }}
          >
            <div className="reference-boot-reactor">
              <i /><i /><i /><i />
              <span><FridayMark /></span>
            </div>
            <b>F.R.I.D.A.Y.</b>
            <small>{props.bridgeReady ? 'NATIVE INTELLIGENCE VERIFIED' : 'SYNCHRONIZING NATIVE CORE'}</small>
            <em>TOUCH TO ENTER</em>
          </motion.button>
        ) : null}
      </AnimatePresence>

      <header className="reference-head">
        <button
          type="button"
          className="reference-menu"
          aria-label="Open FRIDAY modules"
          onClick={() => { sonicDirector.open(); setModulesOpen(true) }}
        >
          <i /><i /><i />
        </button>
        <div className="reference-brand">
          <span><FridayMark /></span>
          <div>
            <b>F.R.I.D.A.Y.</b>
            <small>{SCENE_TITLE[scene]}</small>
          </div>
        </div>
        <div className="reference-state">
          <i />
          <span>{stateLabel(props.mode, props.operation)}</span>
        </div>
      </header>

      <div className="reference-voice-line" aria-hidden="true">
        {Array.from({ length: 48 }, (_, index) => {
          const voice = Math.max(.08, props.metrics.rms)
          const height = 4 + (Math.sin(index * .74) ** 2) * (8 + voice * 34)
          return <i key={index} style={{ height }} />
        })}
      </div>

      <main className="reference-stage">
        <AnimatePresence mode="wait">
          <motion.section
            key={`${scene}-${props.designMode}`}
            className="reference-scene"
            initial={{ opacity: 0, scale: .91, rotateX: 8, filter: 'blur(22px)' }}
            animate={{ opacity: 1, scale: 1, rotateX: 0, filter: 'blur(0)' }}
            exit={{ opacity: 0, scale: 1.045, rotateX: -5, filter: 'blur(18px)' }}
            transition={{ duration: .64, ease: [0.16, 1, 0.3, 1] }}
          >
            <ReferenceScene
              scene={scene}
              mode={props.mode}
              metrics={props.metrics}
              operation={props.operation}
              weather={props.weather}
              location={props.location}
              countdown={props.countdown}
              route={route}
              briefs={briefs}
              summary={summary}
            />
          </motion.section>
        </AnimatePresence>

        <CommandSurface
          mode={props.mode}
          scene={scene}
          transcript={props.transcript}
          response={summary}
          metrics={props.metrics}
          operation={props.operation}
        />
      </main>

      <button type="button" className="reference-mic" aria-label="Speak to FRIDAY" onClick={activateVoice}>
        <span /><span /><span /><i />
        <b>{props.mode === 'LISTENING' ? 'LISTENING' : 'VOICE'}</b>
      </button>

      <footer className="reference-footer">
        <span>{props.telemetry.time}</span>
        <span>{props.designMode} MATRIX</span>
        <span>{props.telemetry.networkQuality}</span>
        <span>{props.telemetry.battery}%</span>
      </footer>

      <AnimatePresence>
        {modulesOpen ? (
          <motion.div
            className="reference-module-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onPointerDown={event => { if (event.target === event.currentTarget) setModulesOpen(false) }}
          >
            <motion.section
              className="reference-module-matrix"
              initial={{ opacity: 0, scale: .9, rotateX: 12, filter: 'blur(18px)' }}
              animate={{ opacity: 1, scale: 1, rotateX: 0, filter: 'blur(0)' }}
              exit={{ opacity: 0, scale: .96, filter: 'blur(12px)' }}
              transition={{ duration: .42, ease: [0.16, 1, 0.3, 1] }}
            >
              <header>
                <div><small>SYSTEM MATRIX</small><h2>FRIDAY MODULES</h2></div>
                <button type="button" onClick={() => setModulesOpen(false)}>CLOSE</button>
              </header>
              <div className="reference-module-grid">
                {MODULES.map((module, index) => (
                  <motion.button
                    type="button"
                    key={module.route}
                    onClick={() => openModule(module.route)}
                    initial={{ opacity: 0, y: 18, rotateX: 8 }}
                    animate={{ opacity: 1, y: 0, rotateX: 0 }}
                    transition={{ delay: index * .035 }}
                  >
                    <ModuleGlyph kind={module.glyph} />
                    <small>{module.code}</small>
                    <b>{module.title}</b>
                    <em>{module.detail}</em>
                    <i>OPEN</i>
                  </motion.button>
                ))}
              </div>
              <footer>
                <span>VOICE DESIGN CONTROL</span>
                <b>“CHANGE SYSTEM DESIGN”</b>
                <em>{props.designMode}</em>
              </footer>
            </motion.section>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

function ReferenceScene(props: {
  scene: OperationalScene
  mode: HelixState
  metrics: AudioMetrics
  operation: OperationState
  weather: WeatherTelemetry
  location: LocationSnapshot
  countdown: CountdownState
  route: RouteSpec
  briefs: string[]
  summary: string
}) {
  if (props.scene === 'NEWS') return <GlobalBroadcast briefs={props.briefs} summary={props.summary} />
  if (props.scene === 'NAVIGATION') return <RouteBroadcast route={props.route} summary={props.summary} />
  if (props.scene === 'LOCATION') return <LocationBroadcast location={props.location} />
  if (props.scene === 'IMAGE' || props.scene === 'VISION') return <VisualSynthesis scene={props.scene} />
  if (props.scene === 'RESEARCH' || props.scene === 'ANALYSIS') return <AnalysisMatrix scene={props.scene} operation={props.operation} />
  if (props.scene === 'DIARY' || props.scene === 'MEMORY') return <VaultScene scene={props.scene} />
  if (props.scene === 'PROVIDERS') return <ProviderScene />
  if (props.scene === 'WEATHER') return <WeatherScene weather={props.weather} />
  if (props.scene === 'SYSTEM' || props.scene === 'COMMUNICATION') return <SystemScene scene={props.scene} />
  if (props.scene === 'TIMER') return <TimerScene countdown={props.countdown} />
  return <HelixCore mode={props.mode} metrics={props.metrics} error={props.scene === 'ERROR'} />
}

function HelixCore({ mode, metrics, error }: { mode: HelixState; metrics: AudioMetrics; error: boolean }) {
  const scale = 1 + Math.min(.16, metrics.rms * .2)
  return (
    <div className={`reference-core ${error ? 'reference-core-error' : ''}`} style={{ '--voice-scale': scale } as CSSProperties}>
      <div className="reference-core-plane plane-x" />
      <div className="reference-core-plane plane-y" />
      <div className="reference-core-ring ring-a" />
      <div className="reference-core-ring ring-b" />
      <div className="reference-core-ring ring-c" />
      <div className="reference-core-ring ring-d" />
      <div className="reference-core-poly poly-one" />
      <div className="reference-core-poly poly-two" />
      <div className="reference-core-light"><i /></div>
      <div className="reference-core-nodes">
        {Array.from({ length: 14 }, (_, index) => <i key={index} style={{ '--node': index } as CSSProperties} />)}
      </div>
      <small>{mode === 'LISTENING' ? 'ACQUIRING VOICE' : mode === 'PROCESSING' ? 'COGNITIVE ORBIT' : mode === 'SPEAKING' ? 'RESPONSE CHANNEL' : 'HELIX CORE'}</small>
    </div>
  )
}

function GlobalBroadcast({ briefs, summary }: { briefs: string[]; summary: string }) {
  const cards = briefs.length ? briefs.slice(0, 4) : ['Awaiting verified live-source response.']
  return (
    <div className="global-broadcast">
      <header><div><small>GLOBAL BRIEF</small><b><i /> LIVE</b></div><span>WORLD INTELLIGENCE FEED</span></header>
      <div className="global-stage">
        <div className="global-orbit orbit-one" /><div className="global-orbit orbit-two" /><div className="global-orbit orbit-three" />
        <div className="global-globe">
          <div className="global-longitudes" /><div className="global-latitudes" />
          <div className="global-landmass land-a" /><div className="global-landmass land-b" /><div className="global-landmass land-c" />
          {Array.from({ length: 12 }, (_, index) => <i key={index} style={{ '--point': index } as CSSProperties} />)}
        </div>
        <div className="global-card-stack global-left">
          {cards.slice(0, 2).map((card, index) => <BriefCard key={index} text={card} index={index} />)}
        </div>
        <div className="global-card-stack global-right">
          {cards.slice(2, 4).map((card, index) => <BriefCard key={index + 2} text={card} index={index + 2} />)}
        </div>
      </div>
      <div className="global-summary"><small>VERIFIED SUMMARY</small><p>{summary || 'Live-source summary will appear here after research completes.'}</p></div>
    </div>
  )
}

function BriefCard({ text, index }: { text: string; index: number }) {
  const labels = ['WORLD', 'BUSINESS', 'SCIENCE', 'TECHNOLOGY']
  return <article className="global-brief-card"><header><b><i /> LIVE</b><span>{labels[index] ?? 'BRIEF'}</span></header><p>{text}</p><footer>VERIFIED SOURCE SYNTHESIS</footer></article>
}

function RouteBroadcast({ route, summary }: { route: RouteSpec; summary: string }) {
  const metrics = parseRouteMetrics(summary)
  return (
    <div className="route-broadcast">
      <header><div><small>ROUTE ANALYSIS</small><b><i /> ANALYZING</b></div><span>{route.from} → {route.to}</span></header>
      <div className="route-map">
        <div className="longitude-axis">{['40°E','60°E','80°E','100°E','120°E','140°E','160°E'].map(item => <span key={item}>{item}</span>)}</div>
        <div className="latitude-axis">{['60°N','40°N','20°N','0°','20°S'].map(item => <span key={item}>{item}</span>)}</div>
        <svg viewBox="0 0 840 500" role="img" aria-label={`Routes from ${route.from} to ${route.to}`}>
          <defs>
            <filter id="referenceRouteGlow"><feGaussianBlur stdDeviation="7" result="blur" /><feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
            <linearGradient id="routeArc" x1="0" x2="1"><stop offset="0" stopColor="#d9f8ff" /><stop offset=".48" stopColor="#55adff" /><stop offset="1" stopColor="#d9f8ff" /></linearGradient>
          </defs>
          <path className="asia-outline" d="M34 330 C93 287 115 320 163 290 C218 256 256 284 298 244 C348 196 398 226 442 180 C491 128 537 144 583 109 C638 67 696 86 795 35 M118 360 C164 332 207 348 256 326 C311 300 347 330 404 298 C469 260 516 290 575 257 C639 221 689 234 790 185 M183 214 C221 178 256 181 286 151 C325 111 372 119 411 91 M520 334 C548 304 572 292 604 277 C650 256 688 270 730 239" />
          <path className="route-arc route-primary" d="M248 332 C390 104 601 75 734 181" filter="url(#referenceRouteGlow)" />
          <path className="route-arc route-secondary" d="M248 332 C404 206 583 193 734 181" />
          <path className="route-arc route-tertiary" d="M248 332 C360 305 565 286 734 181" />
          <circle className="route-source" cx="248" cy="332" r="13" />
          <circle className="route-destination" cx="734" cy="181" r="15" />
        </svg>
        <MapLabel x={11} y={42} text="IRAN" />
        <MapLabel x={19} y={31} text="KAZAKHSTAN" />
        <MapLabel x={33} y={48} text="NEW DELHI" />
        <MapLabel x={24} y={68} text="MUMBAI" />
        <MapLabel x={31} y={63} text="INDIA" strong />
        <MapLabel x={48} y={34} text="CHINA" strong />
        <MapLabel x={48} y={21} text="MONGOLIA" />
        <MapLabel x={50} y={10} text="RUSSIA" />
        <MapLabel x={54} y={67} text="THAILAND" />
        <MapLabel x={61} y={78} text="MALAYSIA" />
        <MapLabel x={66} y={88} text="INDONESIA" />
        <MapLabel x={84} y={37} text="TOKYO" />
        <MapLabel x={85} y={43} text="JAPAN" strong />
        <div className="route-origin-label"><small>ORIGIN</small><b>{route.from}</b></div>
        <div className="route-target-label"><small>DESTINATION</small><b>{route.to}</b></div>
      </div>
      <div className="route-metrics">
        {metrics.length ? metrics.map(item => <div key={item.label}><span>{item.label}</span><b>{item.value}</b></div>) : (
          <><div><span>DISTANCE</span><b>CALCULATING</b></div><div><span>DURATION</span><b>CALCULATING</b></div><div><span>ROUTE STATUS</span><b>AWAITING VERIFIED DATA</b></div></>
        )}
      </div>
    </div>
  )
}

function MapLabel({ x, y, text, strong = false }: { x: number; y: number; text: string; strong?: boolean }) {
  return <span className={`route-place ${strong ? 'route-place-strong' : ''}`} style={{ left: `${x}%`, top: `${y}%` }}>{text}</span>
}

function LocationBroadcast({ location }: { location: LocationSnapshot }) {
  const place = location.placeName || (location.acquiring ? 'ACQUIRING DEVICE POSITION' : 'LOCATION NOT VERIFIED')
  return (
    <div className={`location-broadcast ${location.available ? 'location-verified' : ''}`}>
      <header><div><small>LIVE LOCATION MODE</small><b><i /> {location.acquiring ? 'ACQUIRING' : location.available ? 'VERIFIED' : 'STANDBY'}</b></div><span>{location.provider || 'DEVICE POSITIONING'}</span></header>
      <div className="location-sequence">
        {['GLOBAL','ASIA','INDIA','REGION','CITY','LOCATION'].map((step, index) => <div key={step} className={index === 5 && location.available ? 'active' : index < 5 && location.acquiring ? 'scanning' : ''}><i />{step}</div>)}
      </div>
      <div className="location-map-field">
        <div className="location-street-grid" />
        <div className="location-rings"><i /><i /><i /><i /><span /></div>
        <div className="location-pin"><i /></div>
        <div className="location-lock">✓</div>
        <div className="location-coordinates">
          <span>LAT</span><b>{location.available ? formatCoordinate(location.latitude, 'N', 'S') : '—'}</b>
          <span>LON</span><b>{location.available ? formatCoordinate(location.longitude, 'E', 'W') : '—'}</b>
          <span>ACCURACY</span><b>{location.available ? `${Math.round(location.accuracyM)} M` : '—'}</b>
          <span>ALTITUDE</span><b>{location.available && location.altitudeM !== 0 ? `${Math.round(location.altitudeM)} M` : '—'}</b>
        </div>
        <div className="location-place"><small>VERIFIED POSITION</small><b>{place}</b></div>
      </div>
      <div className="location-confirmation">{location.available ? "YOU'RE HERE, SIR." : location.error || 'POSITIONING MATRIX ACTIVE'}</div>
    </div>
  )
}

function VisualSynthesis({ scene }: { scene: 'IMAGE' | 'VISION' }) {
  return <div className="visual-synthesis"><header><small>{scene === 'IMAGE' ? 'VISUAL SYNTHESIS' : 'OPTICAL INTELLIGENCE'}</small><b><i /> LIVE</b></header><div className="visual-volume"><i /><i /><i /><i /><span /></div><div className="visual-scan" /><div className="visual-reticle"><i /><i /><i /><i /></div><small>{scene === 'IMAGE' ? 'GENERATIVE VOLUME' : 'CAMERA ANALYSIS FIELD'}</small></div>
}

function AnalysisMatrix({ scene, operation }: { scene: 'RESEARCH' | 'ANALYSIS'; operation: OperationState }) {
  return <div className="analysis-matrix"><header><small>{scene === 'RESEARCH' ? 'RESEARCH MATRIX' : 'CALCULATION MATRIX'}</small><b><i /> ACTIVE</b></header><div className="analysis-network">{Array.from({ length: 12 }, (_, index) => <i key={index} style={{ '--point': index } as CSSProperties} />)}</div><svg viewBox="0 0 700 350"><path d="M20 284 C92 242 118 257 174 197 C229 140 278 196 336 118 C397 37 442 122 503 77 C568 27 607 80 682 28" /></svg><div className="analysis-equations"><span>∑</span><span>∫</span><span>π</span><span>Δ</span><span>λ</span></div><footer>{operation.active ? operation.detail : 'VERIFIED ANALYSIS READY'}</footer></div>
}

function VaultScene({ scene }: { scene: 'DIARY' | 'MEMORY' }) {
  return <div className="vault-scene"><header><small>{scene === 'DIARY' ? 'PRIVATE DIARY' : 'MEMORY VAULT'}</small><b><i /> OWNER LOCKED</b></header><div className="vault-shell"><i /><i /><i /><i /><span /></div><div className="vault-pages"><i /><i /><i /></div><footer>{scene === 'DIARY' ? 'ENCRYPTED JOURNAL' : 'STRUCTURED PRIVATE KNOWLEDGE'}</footer></div>
}

function ProviderScene() {
  return <div className="provider-scene"><header><small>UNIVERSAL PROVIDER MESH</small><b><i /> ENCRYPTED</b></header><div className="provider-reactor"><span />{Array.from({ length: 10 }, (_, index) => <i key={index} style={{ '--provider': index } as CSSProperties} />)}</div><footer>MODELS · KEYS · ROUTING</footer></div>
}

function WeatherScene({ weather }: { weather: WeatherTelemetry }) {
  return <div className="weather-scene"><header><small>ATMOSPHERIC CORE</small><b><i /> {weather.fresh ? 'LIVE' : 'STANDBY'}</b></header><div className="weather-orb"><i /><i /><i /><span /></div><div className="weather-streams">{Array.from({ length: 9 }, (_, index) => <i key={index} />)}</div><div className="weather-reading"><b>{weather.configured ? `${Math.round(weather.tempC)}°` : '—'}</b><span>{weather.configured ? weather.condition : 'CONFIGURATION PENDING'}</span><small>{weather.location}</small></div></div>
}

function SystemScene({ scene }: { scene: 'SYSTEM' | 'COMMUNICATION' }) {
  return <div className="system-scene"><header><small>{scene === 'SYSTEM' ? 'ANDROID CONTROL MATRIX' : 'PRIVATE COMMUNICATION LINK'}</small><b><i /> SECURED</b></header><div className="system-reactor"><i /><i /><i /><i /><span /></div><div className="system-links">{Array.from({ length: 8 }, (_, index) => <i key={index} style={{ '--link': index } as CSSProperties} />)}</div></div>
}

function TimerScene({ countdown }: { countdown: CountdownState }) {
  const remaining = Math.max(0, Math.ceil(countdown.remainingMs / 1000))
  return <div className="timer-scene"><header><small>TEMPORAL CONTROL</small><b><i /> {countdown.active ? 'ACTIVE' : 'STANDBY'}</b></header><div className="timer-orbit" style={{ '--timer-progress': countdown.progress } as CSSProperties}><i /><i /><span /></div><b>{formatDuration(remaining)}</b><small>{countdown.label}</small></div>
}

function CommandSurface({ mode, scene, transcript, response, metrics, operation }: {
  mode: HelixState
  scene: OperationalScene
  transcript: string
  response: string
  metrics: AudioMetrics
  operation: OperationState
}) {
  if (mode === 'LISTENING') {
    return <motion.div className="reference-command reference-listening" key="listening" initial={{ opacity: 0, y: 22 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }}><div>{Array.from({ length: 36 }, (_, index) => <i key={index} style={{ height: 5 + Math.sin(index * .7) ** 2 * (12 + metrics.rms * 42) }} />)}</div><p>{cleanTranscript(transcript) || 'Listening…'}</p></motion.div>
  }
  if (mode === 'PROCESSING' || operation.active) {
    return <motion.div className="reference-command reference-processing" key="processing" initial={{ opacity: 0, y: 22 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }}><small>{SCENE_TITLE[scene]}</small><b>{operation.active ? operation.detail : 'CONSTRUCTING VERIFIED RESPONSE'}</b><i><em style={{ width: `${Math.max(8, operation.progress * 100)}%` }} /></i></motion.div>
  }
  if (mode === 'SPEAKING' || scene !== 'CORE') {
    return <motion.div className="reference-command reference-response" key="response" initial={{ opacity: 0, y: 22, filter: 'blur(10px)' }} animate={{ opacity: 1, y: 0, filter: 'blur(0)' }} exit={{ opacity: 0, y: 12 }}><small>{SCENE_TITLE[scene]} · VERIFIED</small><p>{response || 'Verified response ready.'}</p></motion.div>
  }
  return <motion.div className="reference-command reference-response reference-home" key="home" initial={{ opacity: 0 }} animate={{ opacity: 1 }}><small>PRIVATE INTELLIGENCE ONLINE</small><p>Awaiting your command, Sir.</p></motion.div>
}

function ModuleGlyph({ kind }: { kind: ModuleSpec['glyph'] }) {
  return <span className={`module-glyph glyph-${kind}`}><i /><i /><i /></span>
}

type RouteSpec = { from: string; to: string }
type RouteMetric = { label: string; value: string }

function parseRoute(text: string): RouteSpec {
  const clean = text.replace(/[?.,!]/g, ' ').replace(/\s+/g, ' ').trim()
  const match = clean.match(/from\s+(.{2,42}?)\s+to\s+(.{2,42})$/i)
  if (!match) return { from: 'ORIGIN', to: 'DESTINATION' }
  return { from: titleCase(match[1]), to: titleCase(match[2]) }
}

function parseRouteMetrics(response: string): RouteMetric[] {
  const values: RouteMetric[] = []
  const distance = response.match(/(?:distance|approximately|about)\s*(?:is|:)?\s*([\d,.]+\s*(?:km|kilometers?|mi|miles?))/i)
  const duration = response.match(/(?:duration|travel time|flight time|takes?)\s*(?:is|:)?\s*([^.;\n]{2,42})/i)
  if (distance?.[1]) values.push({ label: 'DISTANCE', value: distance[1].toUpperCase() })
  if (duration?.[1]) values.push({ label: 'DURATION', value: duration[1].trim().toUpperCase() })
  return values.slice(0, 3)
}

function extractBriefs(response: string): string[] {
  const clean = cleanResponse(response)
  if (!clean) return []
  return clean
    .split(/(?<=[.!?])\s+/)
    .map(item => item.trim())
    .filter(item => item.length > 24)
    .slice(0, 4)
}

function cleanResponse(value: string): string {
  const lines = value
    .replace(/```[\s\S]*?```/g, ' Code prepared on screen. ')
    .split('\n')
    .map(line => line.replace(/^[A-Z0-9 _-]+\s*\/\/\s*/i, '').replace(/[*#>`_]/g, '').trim())
    .filter(Boolean)
  const clean = lines.join(' ').replace(/\s+/g, ' ').trim()
  return clean.length > 620 ? `${clean.slice(0, 617).trimEnd()}…` : clean
}

function cleanTranscript(value: string): string {
  return value.replace(/OWNER CHANNEL ARMED.*$/i, '').replace(/\s+/g, ' ').trim()
}

function titleCase(value: string): string {
  return value.trim().split(' ').slice(0, 5).map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ')
}

function formatCoordinate(value: number, positive: string, negative: string): string {
  return `${Math.abs(value).toFixed(5)}° ${value >= 0 ? positive : negative}`
}

function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = Math.floor(totalSeconds % 60)
  return hours > 0
    ? `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
    : `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

function stateLabel(mode: HelixState, operation: OperationState): string {
  if (operation.active) return operation.stage
  if (mode === 'IDLE') return 'ONLINE'
  return mode
}
