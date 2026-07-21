import type { CSSProperties } from 'react'
import type { HelixState } from './types'

type Props = {
  mode: HelixState
  energy?: number
  compact?: boolean
}

const STATE_LABEL: Record<HelixState, string> = {
  IDLE: 'CORE STABLE',
  LISTENING: 'VOICE CHANNEL OPEN',
  PROCESSING: 'COGNITIVE LOAD ACTIVE',
  SPEAKING: 'RESPONSE TRANSMISSION',
  ERROR: 'CORE PROTECTION ACTIVE',
}

export function SingularityCore({ mode, energy = .08, compact = false }: Props) {
  const scale = 1 + Math.min(.09, Math.max(0, energy) * .13)
  const intensity = Math.max(.12, Math.min(1, energy * 1.8 + (mode === 'PROCESSING' ? .28 : 0)))

  return (
    <div
      className={`sg-core ${compact ? 'sg-compact' : ''}`}
      data-mode={mode.toLowerCase()}
      style={{ '--sg-scale': scale, '--sg-intensity': intensity } as CSSProperties}
      aria-label={`FRIDAY singularity reactor. ${STATE_LABEL[mode]}`}
    >
      <div className="sg-field" aria-hidden="true" />
      <div className="sg-energy-spine" aria-hidden="true"><i /><i /><i /></div>

      <svg className="sg-reactor" viewBox="0 0 620 720" aria-hidden="true">
        <defs>
          <linearGradient id="sgEdge" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#dffaff" />
            <stop offset=".28" stopColor="#74d7ff" />
            <stop offset=".68" stopColor="#178dff" />
            <stop offset="1" stopColor="#06347d" />
          </linearGradient>
          <linearGradient id="sgArmor" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#123a66" stopOpacity=".82" />
            <stop offset=".5" stopColor="#061a34" stopOpacity=".92" />
            <stop offset="1" stopColor="#020916" stopOpacity=".98" />
          </linearGradient>
          <radialGradient id="sgPower" cx="50%" cy="50%" r="50%">
            <stop offset="0" stopColor="#ffffff" />
            <stop offset=".06" stopColor="#dffcff" />
            <stop offset=".16" stopColor="#8be8ff" />
            <stop offset=".34" stopColor="#1da2ff" />
            <stop offset=".58" stopColor="#0759d6" stopOpacity=".82" />
            <stop offset="1" stopColor="#031431" stopOpacity="0" />
          </radialGradient>
          <filter id="sgGlow" x="-80%" y="-80%" width="260%" height="260%">
            <feGaussianBlur stdDeviation="8" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>

        <g className="sg-shell">
          <path d="M310 44 526 169 552 218v284l-26 49L310 676 94 551l-26-49V218l26-49Z" />
          <path d="M310 70 500 180 522 222v276l-22 42L310 650 120 540 98 498V222l22-42Z" />
          <path d="M310 108 466 198 484 232v256l-18 34L310 612 154 522l-18-34V232l18-34Z" />
        </g>

        <g className="sg-chassis-lines">
          <path d="M310 44v112M310 564v112M68 360h118M434 360h118" />
          <path d="M119 180 220 239M400 481l100 59M500 180l-100 59M220 481l-101 59" />
          <path d="M153 126 238 211M382 509l85 85M467 126l-85 85M238 509l-85 85" />
        </g>

        <g className="sg-segment-ring sg-ring-outer">
          <circle cx="310" cy="360" r="238" pathLength="100" />
          <circle cx="310" cy="360" r="224" pathLength="100" />
        </g>
        <g className="sg-segment-ring sg-ring-middle">
          <circle cx="310" cy="360" r="190" pathLength="100" />
          <circle cx="310" cy="360" r="177" pathLength="100" />
        </g>
        <g className="sg-segment-ring sg-ring-inner">
          <circle cx="310" cy="360" r="139" pathLength="100" />
          <circle cx="310" cy="360" r="126" pathLength="100" />
        </g>

        <g className="sg-stabilizers">
          <g className="sg-stabilizer sg-top"><path d="M274 106h72l18 28-18 28h-72l-18-28Z" /><line x1="310" y1="106" x2="310" y2="162" /></g>
          <g className="sg-stabilizer sg-right"><path d="M508 324v72l-28 18-28-18v-72l28-18Z" /><line x1="452" y1="360" x2="508" y2="360" /></g>
          <g className="sg-stabilizer sg-bottom"><path d="M274 558h72l18 28-18 28h-72l-18-28Z" /><line x1="310" y1="558" x2="310" y2="614" /></g>
          <g className="sg-stabilizer sg-left"><path d="M112 324v72l28 18 28-18v-72l-28-18Z" /><line x1="112" y1="360" x2="168" y2="360" /></g>
        </g>

        <g className="sg-reactor-housing">
          <path className="sg-housing-outer" d="M310 188 452 270v180l-142 82-142-82V270Z" />
          <path className="sg-housing-inner" d="M310 226 418 288v144l-108 62-108-62V288Z" />
          <path className="sg-housing-lock" d="M310 255 382 297v126l-72 42-72-42V297Z" />
        </g>

        <g className="sg-armor-plates">
          <path className="sg-plate sg-plate-top" d="M254 276h112l-24 54h-64Z" />
          <path className="sg-plate sg-plate-right-top" d="m371 294 52 31v70l-57-13-25-43Z" />
          <path className="sg-plate sg-plate-right-bottom" d="m423 395-52 31-30-45 25-43Z" />
          <path className="sg-plate sg-plate-bottom" d="M254 444h112l-24-54h-64Z" />
          <path className="sg-plate sg-plate-left-bottom" d="m249 426-52-31 57-13 25 43Z" />
          <path className="sg-plate sg-plate-left-top" d="m197 325 52-31 30 45-25 43Z" />
        </g>

        <g className="sg-core-lines">
          <line x1="310" y1="156" x2="310" y2="285" />
          <line x1="310" y1="435" x2="310" y2="564" />
          <line x1="168" y1="360" x2="250" y2="360" />
          <line x1="370" y1="360" x2="452" y2="360" />
          <line x1="238" y1="288" x2="274" y2="324" />
          <line x1="346" y1="396" x2="382" y2="432" />
          <line x1="382" y1="288" x2="346" y2="324" />
          <line x1="274" y1="396" x2="238" y2="432" />
        </g>

        <g className="sg-power-chamber" filter="url(#sgGlow)">
          <circle className="sg-power-halo" cx="310" cy="360" r="94" fill="url(#sgPower)" />
          <path className="sg-power-cage" d="M310 284 376 322v76l-66 38-66-38v-76Z" />
          <path className="sg-power-cage-inner" d="M310 314 350 337v46l-40 23-40-23v-46Z" />
          <circle className="sg-power-light" cx="310" cy="360" r="26" fill="url(#sgPower)" />
        </g>

        <g className="sg-lock-nodes">
          {[
            [310, 122], [478, 192], [530, 360], [478, 528],
            [310, 598], [142, 528], [90, 360], [142, 192],
          ].map(([x, y], index) => <circle key={index} cx={x} cy={y} r={index % 2 === 0 ? 5 : 3.5} />)}
        </g>
      </svg>

      <div className="sg-state">
        <span>HELIX SINGULARITY</span>
        <b>{STATE_LABEL[mode]}</b>
      </div>
    </div>
  )
}
