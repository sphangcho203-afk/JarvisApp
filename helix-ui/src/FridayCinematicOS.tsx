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

type ModuleKind = 'voice' | 'camera' | 'visual' | 'memory' | 'mesh' | 'control' | 'shield' | 'theme'

type ModuleSpec = {
  route: string
  title: string
  detail: string
  kind: ModuleKind
}

const MODULES: ModuleSpec[] = [
  { route: 'voice', title: 'VOICE LAB', detail: 'LOCAL VOICE PROFILE', kind: 'voice' },
  { route: 'xcamera', title: 'X-CAMERA', detail: 'OPTICAL INTELLIGENCE', kind: 'camera' },
  { route: 'image', title: 'VISUAL LAB', detail: 'SYNTHESIS + ANALYSIS', kind: 'visual' },
  { route: 'memory', title: 'MEMORY VAULT', detail: 'ENCRYPTED KNOWLEDGE', kind: 'memory' },
  { route: 'providers', title: 'API MESH', detail: 'MODELS + KEYS + ROUTING', kind: 'mesh' },
  { route: 'control', title: 'SYSTEM CONTROL', detail: 'DEVICE AUTOMATION', kind: 'control' },
  { route: 'permissions', title: 'PERMISSION CENTER', detail: 'TRUST + ACCESS', kind: 'shield' },
  { route: 'design', title: 'THEME MATRIX', detail: 'CINEMATIC DESIGN MODES', kind: 'theme' },
]

const SCENE_LABEL: Record<OperationalScene, string> = {
  CORE: 'HELIX CORE',
  NEWS: 'GLOBAL BRIEF',
  NAVIGATION: 'ROUTE ANALYSIS',
  LOCATION: 'LIVE LOCATION MODE',
  IMAGE: 'VISUAL SYNTHESIS',
  VISION: 'OPTICAL INTELLIGENCE',
  RESEARCH: 'RESEARCH MATRIX',
  ANALYSIS: 'CALCULATION MATRIX',
  DIARY: 'PRIVATE DIARY',
  MEMORY: 'MEMORY VAULT',
  PROVIDERS: 'API MESH',
  SYSTEM: 'SYSTEM CONTROL',
  WEATHER: 'ATMOSPHERIC CORE',
  COMMUNICATION: 'PRIVATE COMMUNICATION',
  TIMER: 'TEMPORAL CONTROL',
  ERROR: 'SYSTEM ALERT',
}

export function FridayCinematicOS(props: Props) {
  const [booting, setBooting] = useState(true)
  const [modulesOpen, setModulesOpen] = useState(false)
  const previousMode = useRef<HelixState>('IDLE')
  const previousDesign = useRef<FridayDesignMode>(props.designMode)

  const scene = useMemo(
    () => inferOperationalScene(props.transcript, props.response, props.responseMeta, props.operation, props.mode),
    [props.transcript, props.response, props.responseMeta, props.operation, props.mode],
  )
  const route = useMemo(() => parseRoute(props.transcript), [props.transcript])
  const briefs = useMemo(() => extractBriefs(props.response), [props.response])
  const summary = useMemo(() => cleanResponse(props.response), [props.response])
  const command = cleanTranscript(props.transcript)
  const voiceFocus = props.mode === 'LISTENING' || props.mode === 'PROCESSING' || props.mode === 'SPEAKING'

  useEffect(() => {
    const timer = window.setTimeout(() => setBooting(false), 1050)
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

  const activateVoice = () => {
    sonicDirector.wake()
    navigator.vibrate?.([12, 22, 16])
    props.onCoreTap()
  }

  const openModule = (routeName: string) => {
    sonicDirector.open()
    navigator.vibrate?.(16)
    setModulesOpen(false)
    props.onOpen(routeName)
  }

  return (
    <div
      className="fos-root"
      data-state={props.mode.toLowerCase()}
      data-scene={scene.toLowerCase()}
      data-design={props.designMode.toLowerCase()}
      data-voice-focus={voiceFocus ? 'true' : 'false'}
    >
      <div className="fos-space" />
      <div className="fos-frame"><i /><i /><i /><i /></div>

      <AnimatePresence>
        {booting ? (
          <motion.button
            className="fos-boot"
            type="button"
            aria-label="Enter FRIDAY"
            onClick={() => setBooting(false)}
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 1.05, filter: 'blur(20px)' }}
            transition={{ duration: .55, ease: [0.16, 1, 0.3, 1] }}
          >
            <QuantumCore compact />
            <b>F.R.I.D.A.Y.</b>
            <small>{props.bridgeReady ? 'NATIVE INTELLIGENCE VERIFIED' : 'SYNCHRONIZING HELIX CORE'}</small>
          </motion.button>
        ) : null}
      </AnimatePresence>

      <header className="fos-header">
        <button className="fos-menu" type="button" aria-label="Open FRIDAY modules" onClick={() => setModulesOpen(true)}>
          <i /><i /><i />
        </button>
        <div className="fos-brand">
          <span><FridayMark /></span>
          <b>F.R.I.D.A.Y.</b>
          <small>{scene === 'CORE' ? 'HELIX CORE' : SCENE_LABEL[scene]}</small>
        </div>
        <div className="fos-status"><i /><span>{stateLabel(props.mode, props.operation)}</span></div>
      </header>

      <Waveform metrics={props.metrics} active={voiceFocus} />

      <main className="fos-stage">
        <AnimatePresence mode="wait">
          <motion.section
            className="fos-scene"
            key={`${scene}-${props.designMode}`}
            initial={{ opacity: 0, scale: .95, filter: 'blur(16px)' }}
            animate={{ opacity: 1, scale: 1, filter: 'blur(0)' }}
            exit={{ opacity: 0, scale: 1.025, filter: 'blur(12px)' }}
            transition={{ duration: .52, ease: [0.16, 1, 0.3, 1] }}
          >
            {scene === 'CORE' ? <HomeScene mode={props.mode} metrics={props.metrics} /> : null}
            {scene === 'NEWS' ? <NewsScene briefs={briefs} summary={summary} /> : null}
            {scene === 'NAVIGATION' ? <RouteScene route={route} summary={summary} /> : null}
            {scene === 'LOCATION' ? <LocationScene location={props.location} /> : null}
            {!['CORE', 'NEWS', 'NAVIGATION', 'LOCATION'].includes(scene) ? (
              <OperationalModuleScene
                scene={scene}
                operation={props.operation}
                weather={props.weather}
                countdown={props.countdown}
              />
            ) : null}
          </motion.section>
        </AnimatePresence>

        {scene !== 'CORE' && command ? <CommandChip text={command} /> : null}
        <ResponseCapsule
          scene={scene}
          mode={props.mode}
          response={summary}
          operation={props.operation}
        />
      </main>

      <button className="fos-mic" type="button" aria-label="Speak to FRIDAY" onClick={activateVoice}>
        <i /><i /><i /><span />
      </button>

      <footer className="fos-footer">
        <span>{props.telemetry.time}</span>
        <span>{props.designMode} MATRIX</span>
        <span>{props.telemetry.networkQuality}</span>
        <span>{props.telemetry.battery}%</span>
      </footer>

      <AnimatePresence>
        {modulesOpen ? (
          <motion.div className="fos-modules-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <motion.section
              className="fos-modules"
              initial={{ opacity: 0, scale: .94, rotateX: 8 }}
              animate={{ opacity: 1, scale: 1, rotateX: 0 }}
              exit={{ opacity: 0, scale: .97 }}
              transition={{ duration: .42, ease: [0.16, 1, 0.3, 1] }}
            >
              <header>
                <div className="fos-module-brand"><FridayMark /><b>F.R.I.D.A.Y.</b><small>HELIX OPS</small></div>
                <button type="button" aria-label="Close modules" onClick={() => setModulesOpen(false)}>×</button>
              </header>
              <QuantumCore compact />
              <div className="fos-module-grid">
                {MODULES.map(module => (
                  <button type="button" key={module.route} onClick={() => openModule(module.route)}>
                    <ModuleGlyph kind={module.kind} />
                    <span><b>{module.title}</b><small>{module.detail}</small></span>
                  </button>
                ))}
              </div>
              <div className="fos-mode-strip">
                <small>OPERATING MODE</small>
                <div>
                  {['STANDARD', 'FOCUS', 'STEALTH', 'SECURE', 'LAB'].map(mode => (
                    <span key={mode} className={props.designMode === mode ? 'active' : ''}><i />{mode}</span>
                  ))}
                </div>
              </div>
            </motion.section>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

function Waveform({ metrics, active }: { metrics: AudioMetrics; active: boolean }) {
  const energy = Math.max(active ? .18 : .06, metrics.rms)
  return (
    <div className="fos-wave" aria-hidden="true">
      {Array.from({ length: 62 }, (_, index) => {
        const envelope = Math.sin((index / 61) * Math.PI) ** 1.35
        const variation = .35 + Math.sin(index * .78) ** 2
        const height = 2 + envelope * variation * (10 + energy * 50)
        return <i key={index} style={{ height }} />
      })}
      <small>{active ? 'LISTENING' : 'ONLINE'}</small>
    </div>
  )
}

function HomeScene({ mode, metrics }: { mode: HelixState; metrics: AudioMetrics }) {
  return (
    <div className="fos-home">
      <QuantumCore energy={Math.max(metrics.rms, mode === 'PROCESSING' ? .28 : .08)} />
    </div>
  )
}

function QuantumCore({ compact = false, energy = .08 }: { compact?: boolean; energy?: number }) {
  const scale = 1 + Math.min(.13, energy * .17)
  return (
    <div className={`fos-quantum-core ${compact ? 'compact' : ''}`} style={{ '--energy-scale': scale } as CSSProperties}>
      <svg viewBox="0 0 600 600" aria-hidden="true">
        <defs>
          <radialGradient id="fosCoreLight" cx="50%" cy="50%" r="50%">
            <stop offset="0" stopColor="#ffffff" />
            <stop offset=".08" stopColor="#d8fbff" />
            <stop offset=".18" stopColor="#54c8ff" />
            <stop offset=".36" stopColor="#087cff" stopOpacity=".95" />
            <stop offset=".68" stopColor="#062b67" stopOpacity=".42" />
            <stop offset="1" stopColor="#020817" stopOpacity="0" />
          </radialGradient>
          <filter id="fosGlow"><feGaussianBlur stdDeviation="7" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
        </defs>
        <g className="fos-orbit orbit-a"><ellipse cx="300" cy="300" rx="248" ry="86" /></g>
        <g className="fos-orbit orbit-b"><ellipse cx="300" cy="300" rx="248" ry="86" transform="rotate(60 300 300)" /></g>
        <g className="fos-orbit orbit-c"><ellipse cx="300" cy="300" rx="248" ry="86" transform="rotate(120 300 300)" /></g>
        <g className="fos-rings">
          {[68, 105, 142, 184, 230].map(radius => <circle key={radius} cx="300" cy="300" r={radius} />)}
        </g>
        <g className="fos-geometry">
          <polygon points="300,95 478,198 478,402 300,505 122,402 122,198" />
          <polygon points="300,135 443,218 443,382 300,465 157,382 157,218" transform="rotate(30 300 300)" />
          <polygon points="300,175 407,237 407,363 300,425 193,363 193,237" />
          <line x1="70" y1="300" x2="530" y2="300" />
          <line x1="300" y1="70" x2="300" y2="530" />
          <line x1="138" y1="138" x2="462" y2="462" />
          <line x1="462" y1="138" x2="138" y2="462" />
        </g>
        <circle className="fos-core-halo" cx="300" cy="300" r="128" fill="url(#fosCoreLight)" filter="url(#fosGlow)" />
        <circle className="fos-core-light" cx="300" cy="300" r="29" fill="url(#fosCoreLight)" filter="url(#fosGlow)" />
        <g className="fos-core-nodes">
          {Array.from({ length: 18 }, (_, index) => {
            const angle = (index / 18) * Math.PI * 2
            const radius = index % 2 === 0 ? 184 : 230
            return <circle key={index} cx={300 + Math.cos(angle) * radius} cy={300 + Math.sin(angle) * radius} r={index % 3 === 0 ? 5 : 3} />
          })}
        </g>
      </svg>
    </div>
  )
}

function CommandChip({ text }: { text: string }) {
  return <motion.div className="fos-command-chip" initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}><WaveIcon /><span>“{text}”</span></motion.div>
}

function ResponseCapsule({ scene, mode, response, operation }: {
  scene: OperationalScene
  mode: HelixState
  response: string
  operation: OperationState
}) {
  const home = scene === 'CORE'
  const text = mode === 'LISTENING'
    ? 'Listening…'
    : operation.active
      ? operation.detail
      : home
        ? 'F.R.I.D.A.Y. operational. Awaiting command.'
        : response || 'Verified intelligence ready.'
  return (
    <motion.div className={`fos-response ${home ? 'home' : ''}`} layout>
      <div className="fos-response-wave"><WaveIcon /></div>
      <small>{home ? 'PRIVATE INTELLIGENCE ONLINE' : `${SCENE_LABEL[scene]} · VERIFIED`}</small>
      <p>{text}</p>
    </motion.div>
  )
}

function NewsScene({ briefs, summary }: { briefs: string[]; summary: string }) {
  const cards = [...briefs]
  while (cards.length < 4) cards.push('Awaiting a verified live-source brief.')
  const labels = ['WORLD', 'BUSINESS', 'SCIENCE', 'TECHNOLOGY']
  return (
    <section className="fos-panel fos-news">
      <header><div><b>GLOBAL BRIEF</b><span><i /> LIVE</span></div><small>WORLD INTELLIGENCE FEED</small></header>
      <div className="fos-news-stage">
        <div className="fos-news-column left">
          {cards.slice(0, 2).map((text, index) => <NewsCard key={index} index={index} label={labels[index]} text={text} />)}
        </div>
        <WorldGlobe />
        <div className="fos-news-column right">
          {cards.slice(2, 4).map((text, index) => <NewsCard key={index + 2} index={index + 2} label={labels[index + 2]} text={text} />)}
        </div>
      </div>
      <div className="fos-summary"><small>SUMMARY</small><p>{summary || 'Live verified research will be summarized here.'}</p></div>
    </section>
  )
}

function NewsCard({ index, label, text }: { index: number; label: string; text: string }) {
  return (
    <article className={`fos-news-card card-${index}`}>
      <div className="fos-news-thumb"><span>LIVE</span></div>
      <small>{label}</small>
      <p>{text}</p>
    </article>
  )
}

function WorldGlobe() {
  return (
    <div className="fos-globe-wrap">
      <div className="fos-globe-orbit orbit-1" /><div className="fos-globe-orbit orbit-2" /><div className="fos-globe-orbit orbit-3" />
      <svg className="fos-globe" viewBox="0 0 500 500" aria-label="Global intelligence map">
        <defs>
          <radialGradient id="fosEarth" cx="35%" cy="25%" r="72%">
            <stop offset="0" stopColor="#d9f7ff" stopOpacity=".26" />
            <stop offset=".22" stopColor="#0b4d8c" stopOpacity=".7" />
            <stop offset=".62" stopColor="#03162e" />
            <stop offset="1" stopColor="#010611" />
          </radialGradient>
          <filter id="fosEarthGlow"><feGaussianBlur stdDeviation="4" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
        </defs>
        <circle cx="250" cy="250" r="205" fill="url(#fosEarth)" stroke="#5fcaff" strokeOpacity=".65" filter="url(#fosEarthGlow)" />
        <g className="fos-globe-grid">
          {[90, 135, 175].map(rx => <ellipse key={`x-${rx}`} cx="250" cy="250" rx={rx} ry="205" />)}
          {[75, 125, 165].map(ry => <ellipse key={`y-${ry}`} cx="250" cy="250" rx="205" ry={ry} />)}
        </g>
        <g className="fos-continents">
          <path d="M95 147l34-34 62-7 44 28 15 40-21 39-51 20-23 44-43-14-25-44-17-33z" />
          <path d="M194 246l31 6 25 35-7 65-29 77-23-22-11-61-20-43z" />
          <path d="M269 110l48-25 88 20 45 39-14 37-60 15-20 29-52-9-22-37-35-22z" />
          <path d="M315 215l40-19 60 19 39 50-29 45-56-8-32-34-35-18z" />
          <path d="M398 347l31-5 28 22-6 31-39 11-26-21z" />
        </g>
        <g className="fos-globe-points">
          {[[118,168],[176,216],[218,322],[284,154],[344,210],[385,268],[408,359],[250,92],[95,250],[270,410]].map(([x,y], index) => <circle key={index} cx={x} cy={y} r={index % 3 === 0 ? 6 : 4} />)}
        </g>
      </svg>
    </div>
  )
}

function RouteScene({ route, summary }: { route: RouteSpec; summary: string }) {
  const metrics = parseRouteMetrics(summary)
  return (
    <section className="fos-panel fos-route">
      <header><div><b>ROUTE ANALYSIS</b><span><i /> ANALYZE</span></div><small>{route.from} → {route.to}</small></header>
      <div className="fos-route-map">
        <div className="fos-longitudes">{['40°E','60°E','80°E','100°E','120°E','140°E','160°E'].map(item => <span key={item}>{item}</span>)}</div>
        <div className="fos-latitudes">{['60°N','40°N','20°N','0°','20°S'].map(item => <span key={item}>{item}</span>)}</div>
        <svg viewBox="0 0 900 540" aria-label={`Routes from ${route.from} to ${route.to}`}>
          <defs>
            <filter id="fosRouteGlow"><feGaussianBlur stdDeviation="6" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
            <linearGradient id="fosRouteGradient"><stop offset="0" stopColor="#eaffff" /><stop offset=".42" stopColor="#36a8ff" /><stop offset="1" stopColor="#eaffff" /></linearGradient>
          </defs>
          <g className="fos-map-grid">
            {Array.from({ length: 11 }, (_, index) => <line key={`v${index}`} x1={70 + index * 72} x2={70 + index * 72} y1="46" y2="492" />)}
            {Array.from({ length: 7 }, (_, index) => <line key={`h${index}`} x1="70" x2="830" y1={50 + index * 72} y2={50 + index * 72} />)}
          </g>
          <path className="fos-asia" d="M82 316l70-65 72 12 48-47 73 16 55-60 62 18 48-70 82 21 58-57 94 22 78-39 92 44 28 58-65 38-74-5-56 55-83-7-63 51-86-16-66 52-85-32-71 25-63-35zM236 216l-23 91 59 104 71-37 37-88-35-55zM536 184l51 49 91-1 57-63M652 302l25 72 91 30 38-52" />
          <path className="fos-route-line primary" d="M265 333 C415 89 646 90 754 190" filter="url(#fosRouteGlow)" />
          <path className="fos-route-line secondary" d="M265 333 C436 204 620 201 754 190" />
          <path className="fos-route-line tertiary" d="M265 333 C445 337 618 310 754 190" />
          <circle className="fos-route-node" cx="265" cy="333" r="13" />
          <circle className="fos-route-node target" cx="754" cy="190" r="15" />
        </svg>
        <MapName x={12} y={43} text="IRAN" />
        <MapName x={21} y={30} text="KAZAKHSTAN" />
        <MapName x={33} y={52} text="NEW DELHI" />
        <MapName x={25} y={68} text="MUMBAI" />
        <MapName x={34} y={60} text="INDIA" strong />
        <MapName x={50} y={36} text="CHINA" strong />
        <MapName x={49} y={23} text="MONGOLIA" />
        <MapName x={51} y={11} text="RUSSIA" />
        <MapName x={56} y={69} text="THAILAND" />
        <MapName x={63} y={80} text="MALAYSIA" />
        <MapName x={68} y={89} text="INDONESIA" />
        <MapName x={84} y={36} text="TOKYO" />
        <MapName x={85} y={43} text="JAPAN" strong />
      </div>
      <div className="fos-route-table">
        <header><span>{route.from} → {route.to}</span><small>DISTANCE</small><small>DURATION</small></header>
        <RouteRow icon="✈" label="AIR ROUTE" distance={metricValue(metrics, 'DISTANCE')} duration={metricValue(metrics, 'DURATION')} />
        <RouteRow icon="⌁" label="SEA ROUTE" distance="CALCULATING" duration="CALCULATING" />
        <RouteRow icon="▣" label="RAIL ROUTE" distance="CALCULATING" duration="CALCULATING" />
      </div>
    </section>
  )
}

function RouteRow({ icon, label, distance, duration }: { icon: string; label: string; distance: string; duration: string }) {
  return <div><i>{icon}</i><b>{label}</b><span>{distance}</span><span>{duration}</span></div>
}

function MapName({ x, y, text, strong = false }: { x: number; y: number; text: string; strong?: boolean }) {
  return <span className={`fos-map-name ${strong ? 'strong' : ''}`} style={{ left: `${x}%`, top: `${y}%` }}>{text}</span>
}

function LocationScene({ location }: { location: LocationSnapshot }) {
  const verified = location.available
  const place = location.placeName || (location.acquiring ? 'ACQUIRING DEVICE POSITION' : 'LOCATION NOT VERIFIED')
  return (
    <section className={`fos-panel fos-location ${verified ? 'verified' : ''}`}>
      <header><div><b>LIVE LOCATION MODE</b><span><i /> {location.acquiring ? 'ACQUIRING' : verified ? 'VERIFIED' : 'STANDBY'}</span></div><small>{location.provider || 'DEVICE POSITIONING'}</small></header>
      <div className="fos-location-body">
        <aside>
          <small>ZOOM SEQUENCE</small>
          {['GLOBAL','ASIA','INDIA','REGION','CITY','LOCATION'].map((step, index) => <span key={step} className={verified && index === 5 ? 'active' : location.acquiring && index < 5 ? 'scan' : ''}><i />{step}</span>)}
        </aside>
        <div className="fos-radar">
          <div className="fos-radar-grid" />
          <div className="fos-radar-rings"><i /><i /><i /><i /></div>
          <div className="fos-radar-sweep" />
          <div className="fos-location-pin"><i /></div>
          <div className="fos-location-check">✓</div>
          <div className="fos-coordinates">
            <span>{verified ? formatCoordinate(location.latitude, 'N', 'S') : '—'}</span>
            <span>{verified ? formatCoordinate(location.longitude, 'E', 'W') : '—'}</span>
            <small>ACCURACY: {verified ? `${Math.round(location.accuracyM)} m` : '—'}</small>
            <small>ALT: {verified && location.altitudeM ? `${Math.round(location.altitudeM)} m` : '—'}</small>
          </div>
          <b>{place}</b>
        </div>
      </div>
      <div className="fos-location-confirm">{verified ? "YOU'RE HERE, SIR." : location.error || 'POSITIONING MATRIX ACTIVE'}</div>
    </section>
  )
}

function OperationalModuleScene({ scene, operation, weather, countdown }: {
  scene: OperationalScene
  operation: OperationState
  weather: WeatherTelemetry
  countdown: CountdownState
}) {
  const reading = scene === 'WEATHER'
    ? weather.configured ? `${Math.round(weather.tempC)}° · ${weather.condition}` : 'WEATHER PROVIDER REQUIRED'
    : scene === 'TIMER'
      ? countdown.active ? formatDuration(Math.max(0, Math.ceil(countdown.remainingMs / 1000))) : 'TEMPORAL CORE READY'
      : operation.active ? operation.detail : 'SYSTEM READY'
  return (
    <section className="fos-panel fos-module-scene">
      <header><div><b>{SCENE_LABEL[scene]}</b><span><i /> ACTIVE</span></div><small>SECURED CHANNEL</small></header>
      <div className="fos-module-visual">
        <QuantumCore />
        <div className="fos-module-scan" />
      </div>
      <div className="fos-module-reading"><small>{SCENE_LABEL[scene]}</small><b>{reading}</b></div>
    </section>
  )
}

function ModuleGlyph({ kind }: { kind: ModuleKind }) {
  return <span className={`fos-module-glyph glyph-${kind}`}><i /><i /><i /><i /></span>
}

function WaveIcon() {
  return <span className="fos-wave-icon">{Array.from({ length: 9 }, (_, index) => <i key={index} />)}</span>
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
  return values
}

function metricValue(metrics: RouteMetric[], label: string): string {
  return metrics.find(item => item.label === label)?.value || 'CALCULATING'
}

function extractBriefs(response: string): string[] {
  const clean = cleanResponse(response)
  if (!clean) return []
  return clean.split(/(?<=[.!?])\s+/).map(item => item.trim()).filter(item => item.length > 24).slice(0, 4)
}

function cleanResponse(value: string): string {
  const clean = value
    .replace(/```[\s\S]*?```/g, ' Code prepared on screen. ')
    .split('\n')
    .map(line => line.replace(/^[A-Z0-9 _-]+\s*\/\/\s*/i, '').replace(/[*#>`_]/g, '').trim())
    .filter(Boolean)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim()
  return clean.length > 560 ? `${clean.slice(0, 557).trimEnd()}…` : clean
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
  const seconds = totalSeconds % 60
  return hours > 0
    ? `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
    : `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

function stateLabel(mode: HelixState, operation: OperationState): string {
  if (operation.active) return operation.stage
  return mode === 'IDLE' ? 'ONLINE' : mode
}
