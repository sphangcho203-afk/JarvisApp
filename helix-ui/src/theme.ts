import type { HelixState, ThemeSpec } from './types'

export const THEMES: Record<HelixState, ThemeSpec> = {
  IDLE: { label: 'STANDBY', note: 'Passive orbital stabilization', hex: '#22d3ee', rgb: '34 211 238', glow: 'rgba(34,211,238,.55)', energy: .3, speed: .45 },
  LISTENING: { label: 'ACQUIRING VOICE', note: 'Acoustic aperture open', hex: '#2563eb', rgb: '37 99 235', glow: 'rgba(37,99,235,.62)', energy: .65, speed: 1.15 },
  PROCESSING: { label: 'SYNTHESIZING', note: 'Inference lattice under load', hex: '#f59e0b', rgb: '245 158 11', glow: 'rgba(245,158,11,.6)', energy: .82, speed: 1.75 },
  SPEAKING: { label: 'VOICE OUTPUT', note: 'Synthetic voice carrier active', hex: '#10b981', rgb: '16 185 129', glow: 'rgba(16,185,129,.6)', energy: .75, speed: 1.25 },
  ERROR: { label: 'FAULT STATE', note: 'Recovery interlock engaged', hex: '#ef4444', rgb: '239 68 68', glow: 'rgba(239,68,68,.68)', energy: 1, speed: 2.1 },
}
