from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "helix-ui/src/FridayCinematicOS.tsx"
BRIDGE = ROOT / "helix-ui/src/nativeBridge.ts"

text = UI.read_text(encoding="utf-8")

text = text.replace("import { FridayMark } from './FridayMark'\n", "")
text = text.replace("<span><FridayMark /></span>", "<span><FosLogo /></span>")
text = text.replace(
    '<div className="fos-module-brand"><FridayMark /><b>F.R.I.D.A.Y.</b><small>HELIX OPS</small></div>',
    '<div className="fos-module-brand"><FosLogo /><b>F.R.I.D.A.Y.</b><small>HELIX OPS</small></div>',
)

old_response = '''        <ResponseCapsule
          scene={scene}
          mode={props.mode}
          response={summary}
          operation={props.operation}
        />'''
new_response = '''        {(scene === 'CORE' || voiceFocus || props.operation.active) ? (
          <ResponseCapsule
            scene={scene}
            mode={props.mode}
            response={summary}
            operation={props.operation}
          />
        ) : null}'''
if old_response in text:
    text = text.replace(old_response, new_response, 1)

old_location = '''          <div className="fos-location-check">✓</div>
          <div className="fos-coordinates">'''
new_location = '''          <div className="fos-location-check">✓</div>
          <div className="fos-radar-labels">
            {place.split(',').map((label, index) => <span key={`${label}-${index}`}>{label.trim().toUpperCase()}</span>)}
          </div>
          <div className="fos-coordinates">'''
if old_location in text:
    text = text.replace(old_location, new_location, 1)

text = text.replace(
    "if (duration?.[1]) values.push({ label: 'DURATION', value: duration[1].trim().toUpperCase() })",
    "if (duration?.[1] && /\\d/.test(duration[1])) values.push({ label: 'DURATION', value: duration[1].trim().toUpperCase() })",
)

logo = '''function FosLogo() {
  return (
    <svg className="fos-logo" viewBox="0 0 100 100" aria-hidden="true">
      <defs>
        <linearGradient id="fosLogoEdge" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#d8f8ff" />
          <stop offset=".36" stopColor="#4ac0ff" />
          <stop offset="1" stopColor="#126cff" />
        </linearGradient>
        <filter id="fosLogoGlow"><feGaussianBlur stdDeviation="2.8" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <g fill="none" stroke="url(#fosLogoEdge)" filter="url(#fosLogoGlow)">
        <path d="M50 4 86 25v50L50 96 14 75V25Z" strokeWidth="2.2" />
        <path d="M50 11 80 29v42L50 89 20 71V29Z" strokeWidth="1.2" opacity=".72" />
        <path d="M50 18 73 32v36L50 82 27 68V32Z" strokeWidth="1" opacity=".42" />
      </g>
      <path d="M34 28h34v11H46v9h18v10H46v20H34Z" fill="url(#fosLogoEdge)" filter="url(#fosLogoGlow)" />
      <circle cx="50" cy="50" r="3" fill="#dffbff" />
    </svg>
  )
}

'''
marker = "function WaveIcon() {"
if "function FosLogo()" not in text:
    if marker not in text:
        raise RuntimeError("FRIDAY cinematic logo anchor missing")
    text = text.replace(marker, logo + marker, 1)

UI.write_text(text, encoding="utf-8")

bridge = BRIDGE.read_text(encoding="utf-8")
bridge = bridge.replace(
    "return ['news', 'route', 'location', 'modules'].includes(value) ? value : ''",
    "return ['core', 'voice', 'news', 'route', 'location', 'modules'].includes(value) ? value : ''",
)
preview_mode = "const PREVIEW_MODE: HelixState = PREVIEW_SCENE === 'voice' ? 'LISTENING' : 'IDLE'\n\n"
if "const PREVIEW_MODE:" not in bridge:
    anchor = "const PREVIEW_TRANSCRIPT = PREVIEW_SCENE === 'news'"
    if anchor not in bridge:
        raise RuntimeError("FRIDAY preview-mode anchor missing")
    bridge = bridge.replace(anchor, preview_mode + anchor, 1)
bridge = bridge.replace("const [mode, setMode] = useState<HelixState>('IDLE')", "const [mode, setMode] = useState<HelixState>(PREVIEW_MODE)")
BRIDGE.write_text(bridge, encoding="utf-8")

print("FRIDAY cinematic OS reference fixes applied")
