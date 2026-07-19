import { useEffect, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import type { HelixState, NativeTelemetry, OperationState } from './types'

type SystemMode = 'STANDARD' | 'FOCUS' | 'LAB' | 'STEALTH' | 'SECURE' | 'ENERGY'
type PerformanceTier = 'cinematic' | 'balanced' | 'efficiency'

interface Props {
  mode: HelixState
  telemetry: NativeTelemetry
  operation: OperationState
  bridgeReady: boolean
  onOpen: (workspace: string) => void
  onCoreTap: () => void
}

const MODULES = [
  { route: 'xcamera', code: 'VISION', title: 'X-CAMERA', detail: 'OPTICAL INTELLIGENCE' },
  { route: 'image', code: 'CREATE', title: 'VISUAL LAB', detail: 'SYNTHESIS + ANALYSIS' },
  { route: 'memory', code: 'MEMORY', title: 'MEMORY VAULT', detail: 'ENCRYPTED KNOWLEDGE' },
  { route: 'diary', code: 'PRIVATE', title: 'PRIVATE DIARY', detail: 'OWNER-LOCKED JOURNAL' },
  { route: 'control', code: 'ANDROID', title: 'SYSTEM CONTROL', detail: 'DEVICE AUTOMATION' },
  { route: 'permissions', code: 'SECURITY', title: 'PERMISSION CENTER', detail: 'TRUST + ACCESS' },
] as const

const MODES: Array<{ id: SystemMode; note: string }> = [
  { id: 'STANDARD', note: 'BALANCED OPERATIONS' },
  { id: 'FOCUS', note: 'DISTRACTION SHIELD' },
  { id: 'LAB', note: 'MAXIMUM ANALYSIS' },
  { id: 'STEALTH', note: 'MINIMAL VISIBILITY' },
  { id: 'SECURE', note: 'PRIVACY HARDENED' },
  { id: 'ENERGY', note: 'LOW POWER' },
]

export function CinematicOs({ mode, telemetry, operation, bridgeReady, onOpen, onCoreTap }: Props) {
  const [launcherOpen, setLauncherOpen] = useState(false)
  const [booting, setBooting] = useState(true)
  const [systemMode, setSystemMode] = useState<SystemMode>('STANDARD')
  const [audioEnabled, setAudioEnabled] = useState(false)
  const [reducedMotion, setReducedMotion] = useState(false)
  const audio = useRef<AudioContext | null>(null)
  const previousState = useRef<HelixState>('IDLE')

  const performanceTier = useMemo<PerformanceTier>(() => {
    const cores = navigator.hardwareConcurrency || 4
    if (systemMode === 'ENERGY' || reducedMotion || telemetry.battery <= 20) return 'efficiency'
    if (cores >= 8 && telemetry.battery > 45) return 'cinematic'
    return 'balanced'
  }, [reducedMotion, systemMode, telemetry.battery])

  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)')
    const sync = () => setReducedMotion(query.matches)
    sync()
    query.addEventListener?.('change', sync)
    const timer = window.setTimeout(() => setBooting(false), query.matches ? 300 : 1450)
    return () => {
      query.removeEventListener?.('change', sync)
      window.clearTimeout(timer)
    }
  }, [])

  useEffect(() => {
    if (!audioEnabled || previousState.current === mode) return
    previousState.current = mode
    playCue(audio, cueForState(mode))
    vibrate(mode === 'ERROR' ? [35, 35, 70] : mode === 'PROCESSING' ? [12, 28, 12] : 18)
  }, [audioEnabled, mode])

  useEffect(() => {
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setLauncherOpen(false)
    }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [])

  const armSonicSystem = () => {
    if (!audioEnabled) {
      setAudioEnabled(true)
      playCue(audio, 'wake')
      vibrate([12, 24, 22])
    }
  }

  const openModule = (route: string) => {
    armSonicSystem()
    playCue(audio, 'open')
    vibrate(22)
    setLauncherOpen(false)
    if (route === 'permissions') window.JarvisCommandBridge?.openPermissionCenter?.()
    else onOpen(route)
  }

  const selectMode = (next: SystemMode) => {
    armSonicSystem()
    setSystemMode(next)
    playCue(audio, next === 'SECURE' ? 'secure' : 'select')
    vibrate(next === 'SECURE' ? [28, 24, 45] : 16)
  }

  return (
    <div className="fos-root" data-system-mode={systemMode.toLowerCase()} data-performance={performanceTier} data-ai-state={mode.toLowerCase()}>
      <AnimatePresence>
        {booting ? (
          <motion.button
            type="button"
            className="fos-boot"
            aria-label="Skip FRIDAY boot cinematic"
            onClick={() => { armSonicSystem(); setBooting(false) }}
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, filter: 'blur(18px)' }}
            transition={{ duration: reducedMotion ? .15 : .55 }}
          >
            <div className="fos-boot-grid" />
            <motion.div
              className="fos-boot-core"
              initial={{ scale: .34, opacity: 0, rotate: -18 }}
              animate={{ scale: 1, opacity: 1, rotate: 0 }}
              transition={{ duration: reducedMotion ? .2 : .8, ease: [0.16, 1, 0.3, 1] }}
            >
              <i /><i /><i />
              <strong>FRIDAY</strong>
            </motion.div>
            <div className="fos-boot-copy">
              <span>SECURE INTELLIGENCE CORE</span>
              <b>{bridgeReady ? 'NATIVE BRIDGE VERIFIED' : 'SYNCHRONIZING NATIVE BRIDGE'}</b>
              <small>TOUCH TO ENTER IMMEDIATELY</small>
            </div>
          </motion.button>
        ) : null}
      </AnimatePresence>

      <div className="fos-status-spine" aria-label="FRIDAY OS live status">
        <button type="button" className="fos-launcher-trigger" onClick={() => { armSonicSystem(); setLauncherOpen(true) }}>
          <span className="fos-mark"><i /><i /><i /></span>
          <span><b>FRIDAY OS</b><small>SYSTEM LAUNCHER</small></span>
        </button>
        <div className="fos-live-state">
          <i />
          <span>{operation.active ? operation.stage : mode}</span>
        </div>
        <div className="fos-tier">{performanceTier.toUpperCase()} MODE</div>
      </div>

      <button type="button" className="fos-voice-orb" aria-label="Activate FRIDAY voice" onClick={() => { armSonicSystem(); onCoreTap() }}>
        <span /><span /><span />
        <b>{mode === 'LISTENING' ? 'LISTENING' : 'VOICE'}</b>
      </button>

      <AnimatePresence>
        {launcherOpen ? (
          <motion.div
            className="fos-launcher-backdrop"
            onPointerDown={event => { if (event.target === event.currentTarget) setLauncherOpen(false) }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.section
              className="fos-launcher"
              role="dialog"
              aria-modal="true"
              aria-label="FRIDAY OS system launcher"
              initial={{ opacity: 0, scale: .94, y: 28, filter: 'blur(16px)' }}
              animate={{ opacity: 1, scale: 1, y: 0, filter: 'blur(0)' }}
              exit={{ opacity: 0, scale: .97, y: 18, filter: 'blur(12px)' }}
              transition={{ duration: reducedMotion ? .14 : .34, ease: [0.16, 1, 0.3, 1] }}
            >
              <header className="fos-launcher-header">
                <div>
                  <span>EXTREMELY INTELLIGENT ARTIFICIAL INTELLIGENCE</span>
                  <h2>FRIDAY SYSTEM LAUNCHER</h2>
                  <p>INTELLIGENCE · PRIVACY · CONTROL</p>
                </div>
                <button type="button" onClick={() => setLauncherOpen(false)} aria-label="Close system launcher">CLOSE</button>
              </header>

              <div className="fos-launcher-core-row">
                <button type="button" className="fos-mini-core" onClick={() => { setLauncherOpen(false); onCoreTap() }}>
                  <span><i /><i /><i /></span>
                  <b>{bridgeReady ? 'CORE ONLINE' : 'BRIDGE SYNC'}</b>
                  <small>{telemetry.device || 'ANDROID'} · {telemetry.battery}%</small>
                </button>
                <div className="fos-operation-card">
                  <span>ACTIVE PROCESS</span>
                  <strong>{operation.active ? operation.stage : 'AWAITING VERIFIED COMMAND'}</strong>
                  <p>{operation.active ? operation.detail : 'All local intelligence systems remain available.'}</p>
                  <div><i style={{ width: `${Math.max(operation.active ? 8 : 100, operation.progress * 100)}%` }} /></div>
                </div>
              </div>

              <div className="fos-module-grid">
                {MODULES.map((module, index) => (
                  <motion.button
                    type="button"
                    key={module.route}
                    onClick={() => openModule(module.route)}
                    disabled={!bridgeReady && module.route !== 'permissions'}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: reducedMotion ? 0 : .05 + index * .035 }}
                  >
                    <span>{module.code}</span>
                    <strong>{module.title}</strong>
                    <small>{module.detail}</small>
                    <i>OPEN</i>
                  </motion.button>
                ))}
              </div>

              <div className="fos-mode-panel">
                <div className="fos-mode-heading"><b>OPERATING MODE</b><span>{systemMode} · {performanceTier.toUpperCase()}</span></div>
                <div className="fos-mode-grid">
                  {MODES.map(item => (
                    <button type="button" key={item.id} className={item.id === systemMode ? 'active' : ''} onClick={() => selectMode(item.id)}>
                      <b>{item.id}</b><small>{item.note}</small>
                    </button>
                  ))}
                </div>
              </div>

              <footer className="fos-launcher-footer">
                <span>NETWORK {telemetry.networkQuality}</span>
                <span>VOICE {telemetry.voiceSource}</span>
                <span>HEAP {telemetry.heapMb} MB</span>
                <span>SONIC {audioEnabled ? 'ARMED' : 'TOUCH TO ARM'}</span>
              </footer>
            </motion.section>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

type Cue = 'wake' | 'open' | 'select' | 'secure' | 'listen' | 'process' | 'speak' | 'error'

function cueForState(state: HelixState): Cue {
  if (state === 'LISTENING') return 'listen'
  if (state === 'PROCESSING') return 'process'
  if (state === 'SPEAKING') return 'speak'
  if (state === 'ERROR') return 'error'
  return 'select'
}

function playCue(contextRef: { current: AudioContext | null }, cue: Cue) {
  try {
    const AudioCtor = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AudioCtor) return
    const context = contextRef.current ?? new AudioCtor()
    contextRef.current = context
    const now = context.currentTime
    const gain = context.createGain()
    const filter = context.createBiquadFilter()
    gain.connect(filter)
    filter.connect(context.destination)
    filter.type = 'lowpass'
    filter.frequency.setValueAtTime(cue === 'error' ? 1800 : 3600, now)
    const plan: Record<Cue, { notes: number[]; duration: number; volume: number }> = {
      wake: { notes: [145, 290, 580], duration: .42, volume: .035 },
      open: { notes: [220, 330, 660], duration: .28, volume: .028 },
      select: { notes: [410, 615], duration: .16, volume: .022 },
      secure: { notes: [96, 144, 192], duration: .42, volume: .036 },
      listen: { notes: [330, 440], duration: .2, volume: .025 },
      process: { notes: [180, 270, 405], duration: .3, volume: .02 },
      speak: { notes: [392, 494, 659], duration: .28, volume: .024 },
      error: { notes: [210, 164], duration: .44, volume: .035 },
    }
    const spec = plan[cue]
    gain.gain.setValueAtTime(.0001, now)
    gain.gain.exponentialRampToValueAtTime(spec.volume, now + .018)
    gain.gain.exponentialRampToValueAtTime(.0001, now + spec.duration)
    spec.notes.forEach((frequency, index) => {
      const oscillator = context.createOscillator()
      oscillator.type = index === 0 ? 'sine' : 'triangle'
      oscillator.frequency.setValueAtTime(frequency, now)
      oscillator.detune.setValueAtTime(index * 3, now)
      oscillator.connect(gain)
      oscillator.start(now + index * .018)
      oscillator.stop(now + spec.duration + .04)
    })
  } catch (error) {
    console.debug('FRIDAY_SONIC_CUE_UNAVAILABLE', error)
  }
}

function vibrate(pattern: number | number[]) {
  try { navigator.vibrate?.(pattern) } catch { /* haptics are optional */ }
}
