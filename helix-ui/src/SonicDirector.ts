import type { FridayDesignMode, HelixState, OperationalScene } from './types'

type Cue = 'wake' | 'listen' | 'think' | 'speak' | 'verified' | 'error' | 'location' | 'theme' | 'open'
type AudioContextConstructor = typeof AudioContext

class FridaySonicDirector {
  private context: AudioContext | null = null
  private master: GainNode | null = null
  private loopGain: GainNode | null = null
  private loopNodes: AudioScheduledSourceNode[] = []
  private armed = false
  private lastCue = ''
  private lastCueAt = 0

  arm() {
    const AudioCtor = window.AudioContext || (window as unknown as { webkitAudioContext?: AudioContextConstructor }).webkitAudioContext
    if (!AudioCtor) return false
    if (!this.context) {
      this.context = new AudioCtor()
      this.master = this.context.createGain()
      this.master.gain.value = .72
      this.master.connect(this.context.destination)
    }
    void this.context.resume()
    this.armed = true
    return true
  }

  transition(previous: HelixState, next: HelixState, scene: OperationalScene) {
    if (!this.armed || previous === next) return
    this.stopLoop()
    if (next === 'LISTENING') {
      this.play('listen')
      this.startLoop('listen')
    } else if (next === 'PROCESSING') {
      this.play(scene === 'LOCATION' || scene === 'NAVIGATION' ? 'location' : 'think')
      this.startLoop('think')
    } else if (next === 'SPEAKING') {
      this.play('speak')
      this.startLoop('speak')
    } else if (next === 'ERROR') {
      this.play('error')
    } else if (previous !== 'IDLE') {
      this.play('verified')
    }
  }

  changeTheme(mode: FridayDesignMode) {
    if (!this.armed) return
    this.play('theme', mode === 'STEALTH' || mode === 'ENERGY' ? .7 : 1)
  }

  wake() {
    if (this.arm()) this.play('wake')
  }

  open() {
    if (this.arm()) this.play('open')
  }

  stop() {
    this.stopLoop()
  }

  private play(cue: Cue, intensity = 1) {
    const context = this.context
    const master = this.master
    if (!context || !master) return
    const nowMs = performance.now()
    if (this.lastCue === cue && nowMs - this.lastCueAt < 140) return
    this.lastCue = cue
    this.lastCueAt = nowMs

    const plans: Record<Cue, { notes: number[]; duration: number; gain: number; sweep?: [number, number] }> = {
      wake: { notes: [145, 290, 580], duration: 1.25, gain: .085, sweep: [44, 82] },
      listen: { notes: [330, 440], duration: .34, gain: .055 },
      think: { notes: [180, 270, 405], duration: .62, gain: .042, sweep: [150, 620] },
      speak: { notes: [392, 494, 659], duration: .42, gain: .048 },
      verified: { notes: [440, 660, 990], duration: .62, gain: .065, sweep: [320, 1120] },
      error: { notes: [110, 156], duration: .88, gain: .075, sweep: [210, 82] },
      location: { notes: [523, 659, 784], duration: .78, gain: .06, sweep: [380, 1240] },
      theme: { notes: [360, 720, 960, 1240], duration: .92, gain: .055, sweep: [180, 1600] },
      open: { notes: [220, 330, 660], duration: .38, gain: .05 },
    }
    const plan = plans[cue]
    const bus = context.createGain()
    const filter = context.createBiquadFilter()
    const compressor = context.createDynamicsCompressor()
    filter.type = 'lowpass'
    filter.frequency.value = cue === 'error' ? 1800 : 5800
    bus.connect(filter)
    filter.connect(compressor)
    compressor.connect(master)
    bus.gain.setValueAtTime(.0001, context.currentTime)
    bus.gain.exponentialRampToValueAtTime(plan.gain * intensity, context.currentTime + .025)
    bus.gain.exponentialRampToValueAtTime(.0001, context.currentTime + plan.duration)

    plan.notes.forEach((frequency, index) => {
      const oscillator = context.createOscillator()
      const pan = context.createStereoPanner()
      oscillator.type = index === 0 ? 'sine' : 'triangle'
      oscillator.frequency.setValueAtTime(frequency, context.currentTime)
      oscillator.detune.value = (index - 1) * 2.5
      pan.pan.value = plan.notes.length === 1 ? 0 : -0.56 + (index / Math.max(1, plan.notes.length - 1)) * 1.12
      oscillator.connect(pan)
      pan.connect(bus)
      oscillator.start(context.currentTime + index * .035)
      oscillator.stop(context.currentTime + plan.duration + .04)
    })

    if (plan.sweep) {
      const sweep = context.createOscillator()
      const sweepGain = context.createGain()
      sweep.type = 'sine'
      sweep.frequency.setValueAtTime(plan.sweep[0], context.currentTime)
      sweep.frequency.exponentialRampToValueAtTime(Math.max(1, plan.sweep[1]), context.currentTime + plan.duration)
      sweepGain.gain.value = plan.gain * .42
      sweep.connect(sweepGain)
      sweepGain.connect(bus)
      sweep.start(context.currentTime)
      sweep.stop(context.currentTime + plan.duration)
    }
  }

  private startLoop(kind: 'listen' | 'think' | 'speak') {
    const context = this.context
    const master = this.master
    if (!context || !master) return
    const gain = context.createGain()
    gain.gain.setValueAtTime(.0001, context.currentTime)
    gain.gain.exponentialRampToValueAtTime(kind === 'think' ? .018 : .012, context.currentTime + .18)
    gain.connect(master)
    this.loopGain = gain

    const frequencies = kind === 'think' ? [146.83, 220, 293.66] : kind === 'listen' ? [84, 168] : [196, 294]
    frequencies.forEach((frequency, index) => {
      const oscillator = context.createOscillator()
      const panner = context.createStereoPanner()
      const oscillatorGain = context.createGain()
      oscillator.type = index === 0 ? 'sine' : 'triangle'
      oscillator.frequency.value = frequency
      oscillator.detune.value = index * 3
      oscillatorGain.gain.value = 1 / frequencies.length
      panner.pan.value = -0.45 + index * .45
      oscillator.connect(oscillatorGain)
      oscillatorGain.connect(panner)
      panner.connect(gain)
      oscillator.start()
      this.loopNodes.push(oscillator)
    })
  }

  private stopLoop() {
    const context = this.context
    const gain = this.loopGain
    if (context && gain) {
      gain.gain.cancelScheduledValues(context.currentTime)
      gain.gain.setValueAtTime(Math.max(.0001, gain.gain.value), context.currentTime)
      gain.gain.exponentialRampToValueAtTime(.0001, context.currentTime + .16)
    }
    window.setTimeout(() => {
      this.loopNodes.forEach(node => {
        try { node.stop() } catch { /* already stopped */ }
        try { node.disconnect() } catch { /* already disconnected */ }
      })
      this.loopNodes = []
      try { this.loopGain?.disconnect() } catch { /* optional */ }
      this.loopGain = null
    }, 190)
  }
}

export const sonicDirector = new FridaySonicDirector()
