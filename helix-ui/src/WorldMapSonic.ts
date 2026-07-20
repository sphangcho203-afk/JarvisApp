type MapSound = 'boot' | 'focus' | 'route' | 'lock' | 'globe' | 'layer' | 'warning'

class WorldMapSonicEngine {
  private context: AudioContext | null = null
  private master: GainNode | null = null
  private lastPlayed = new Map<MapSound, number>()

  play(sound: MapSound): void {
    const now = performance.now()
    const last = this.lastPlayed.get(sound) || 0
    if (now - last < 180) return
    this.lastPlayed.set(sound, now)

    const context = this.ensureContext()
    if (!context || !this.master) return
    if (context.state === 'suspended') void context.resume()

    const start = context.currentTime + 0.012
    if (sound === 'boot') this.boot(context, start)
    if (sound === 'focus') this.focus(context, start)
    if (sound === 'route') this.route(context, start)
    if (sound === 'lock') this.lock(context, start)
    if (sound === 'globe') this.globe(context, start)
    if (sound === 'layer') this.layer(context, start)
    if (sound === 'warning') this.warning(context, start)
  }

  private ensureContext(): AudioContext | null {
    if (this.context && this.master) return this.context
    const AudioContextClass = window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AudioContextClass) return null
    const context = new AudioContextClass()
    const master = context.createGain()
    master.gain.value = .18
    master.connect(context.destination)
    this.context = context
    this.master = master
    return context
  }

  private tone(context: AudioContext, frequency: number, start: number, duration: number, volume: number, type: OscillatorType = 'sine', destination?: AudioNode): OscillatorNode {
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = type
    oscillator.frequency.setValueAtTime(frequency, start)
    gain.gain.setValueAtTime(0.0001, start)
    gain.gain.exponentialRampToValueAtTime(Math.max(.0002, volume), start + Math.min(.035, duration * .2))
    gain.gain.exponentialRampToValueAtTime(.0001, start + duration)
    oscillator.connect(gain)
    gain.connect(destination || this.master!)
    oscillator.start(start)
    oscillator.stop(start + duration + .03)
    return oscillator
  }

  private stereoBus(context: AudioContext, pan: number): StereoPannerNode {
    const panner = context.createStereoPanner()
    panner.pan.value = pan
    panner.connect(this.master!)
    return panner
  }

  private boot(context: AudioContext, start: number): void {
    const bus = this.stereoBus(context, 0)
    const low = this.tone(context, 58, start, 1.45, .16, 'sine', bus)
    low.frequency.exponentialRampToValueAtTime(92, start + 1.35)
    ;[392, 588, 880].forEach((frequency, index) => {
      const pan = index === 0 ? -.42 : index === 2 ? .42 : 0
      this.tone(context, frequency, start + .38 + index * .19, .62, .075, 'sine', this.stereoBus(context, pan))
    })
  }

  private focus(context: AudioContext, start: number): void {
    ;[980, 1240].forEach((frequency, index) => {
      this.tone(context, frequency, start + index * .075, .22, .06, 'triangle', this.stereoBus(context, index ? .28 : -.28))
    })
  }

  private route(context: AudioContext, start: number): void {
    const bus = this.stereoBus(context, 0)
    const oscillator = this.tone(context, 210, start, .9, .075, 'sine', bus)
    oscillator.frequency.exponentialRampToValueAtTime(1380, start + .72)
    ;[460, 690, 1035].forEach((frequency, index) => this.tone(context, frequency, start + .44 + index * .08, .42, .045, 'triangle', bus))
  }

  private lock(context: AudioContext, start: number): void {
    ;[720, 980, 1320].forEach((frequency, index) => this.tone(context, frequency, start + index * .12, .3, .055, 'sine', this.stereoBus(context, index === 0 ? -.3 : index === 2 ? .3 : 0)))
    this.tone(context, 1640, start + .39, .52, .07, 'triangle')
  }

  private globe(context: AudioContext, start: number): void {
    const left = this.tone(context, 180, start, .68, .055, 'sine', this.stereoBus(context, -.55))
    left.frequency.exponentialRampToValueAtTime(440, start + .63)
    const right = this.tone(context, 220, start + .05, .68, .055, 'sine', this.stereoBus(context, .55))
    right.frequency.exponentialRampToValueAtTime(520, start + .63)
  }

  private layer(context: AudioContext, start: number): void {
    this.tone(context, 870, start, .18, .04, 'square', this.stereoBus(context, -.18))
    this.tone(context, 1160, start + .07, .22, .04, 'square', this.stereoBus(context, .18))
  }

  private warning(context: AudioContext, start: number): void {
    ;[340, 300].forEach((frequency, index) => this.tone(context, frequency, start + index * .19, .3, .065, 'sawtooth'))
  }
}

export const worldMapSonic = new WorldMapSonicEngine()
