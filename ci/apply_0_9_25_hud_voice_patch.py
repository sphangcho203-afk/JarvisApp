from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        if new in text:
            return
        raise SystemExit(f"Required anchor missing in {path}: {old[:140]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


hud_path = "helix-ui/src/Hud.tsx"
replace_once(hud_path, "BUILD 0.9.24", "BUILD 0.9.25")
replace_once(
    hud_path,
    '        <span className="status-symbol">{weather.icon || weatherGlyph(weather.condition)}</span>',
    '        <span className="status-symbol"><WeatherGlyph condition={weather.condition} alert={weather.alert} /></span>',
)
replace_once(
    hud_path,
    '''        <p style={{ color: theme.hex, textShadow: `0 0 14px ${theme.glow}` }}>
          {operation.active ? operation.detail : scrambled}<span className="terminal-cursor">▌</span>
        </p>''',
    '''        <div
          className="response-scroll"
          role="region"
          aria-label="Full verified response"
          tabIndex={0}
        >
          <p style={{ color: theme.hex, textShadow: `0 0 14px ${theme.glow}` }}>
            {operation.active ? operation.detail : scrambled}<span className="terminal-cursor" aria-hidden="true">▌</span>
          </p>
        </div>''',
)
replace_once(
    hud_path,
    "  return upper.replace(/\\s+/g, ' ').slice(0, 76)",
    "  return stripDecorativeUnicode(upper).replace(/\\s+/g, ' ').trim().slice(0, 76)",
)
replace_once(
    hud_path,
    '''function weatherGlyph(condition: string): string {
  const value = condition.toLowerCase()
  if (value.includes('thunder') || value.includes('storm')) return '⛈'
  if (value.includes('rain') || value.includes('drizzle')) return '☂'
  if (value.includes('snow') || value.includes('sleet')) return '❄'
  if (value.includes('fog') || value.includes('mist')) return '◌'
  if (value.includes('cloud') || value.includes('overcast')) return '☁'
  return '☼'
}''',
    '''function WeatherGlyph({ condition, alert }: { condition: string; alert: string }) {
  const value = condition.toLowerCase()
  const kind = value.includes('thunder') || value.includes('storm')
    ? 'storm'
    : value.includes('rain') || value.includes('drizzle')
      ? 'rain'
      : value.includes('snow') || value.includes('sleet')
        ? 'snow'
        : value.includes('fog') || value.includes('mist')
          ? 'fog'
          : value.includes('cloud') || value.includes('overcast')
            ? 'cloud'
            : 'clear'
  const common = {
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.35,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  }
  const cloud = <path d="M5.1 14.2h11.7c1.7 0 3-1.2 3-2.8 0-1.5-1.1-2.7-2.6-2.9C16.6 6.4 14.8 5 12.6 5 9.9 5 7.8 7 7.5 9.5 5.6 9.6 4.1 11 4.1 12.8c0 .5.4 1.4 1 1.4Z" {...common} />
  let body: React.ReactNode
  if (kind === 'storm') {
    body = <>{cloud}<path d="M11.8 13.8 9.7 18h2.2l-1 3.1 4.1-5.1h-2.3l1.1-2.2" {...common} /></>
  } else if (kind === 'rain') {
    body = <>{cloud}<path d="m8 16.3-1 2.2m5-2.2-1 2.2m5-2.2-1 2.2" {...common} /></>
  } else if (kind === 'snow') {
    body = <>{cloud}<path d="M8 16.2v3m-1.3-2.3 2.6 1.5m-2.6 0 2.6-1.5M15.5 16.2v3m-1.3-2.3 2.6 1.5m-2.6 0 2.6-1.5" {...common} /></>
  } else if (kind === 'fog') {
    body = <><path d="M5.2 8.2h13.6M3.8 12h16.4M5.2 15.8h13.6" {...common} /><path d="M7.5 5.4h9" {...common} /></>
  } else if (kind === 'cloud') {
    body = cloud
  } else {
    body = <><circle cx="12" cy="11.7" r="3.3" {...common} /><path d="M12 4.3V2.8M12 20.6v-1.5M4.6 11.7H3.1m17.8 0h-1.5M6.8 6.5 5.7 5.4m12.6 12.6-1.1-1.1m0-10.4 1.1-1.1M5.7 18l1.1-1.1" {...common} /></>
  }
  const hasAlert = alert.trim().length > 0
  return (
    <svg className="weather-glyph" viewBox="0 0 24 24" role="img" aria-label={`${condition || 'weather'}${hasAlert ? ', alert active' : ''}`}>
      <path className="weather-frame" d="M12 1.6 21 6.8v10.4L12 22.4 3 17.2V6.8Z" {...common} />
      {body}
      {hasAlert ? (
        <g className="weather-alert-node">
          <path d="m18.2 1.8 3.1 3.1-3.1 3.1-3.1-3.1Z" {...common} />
          <path d="M18.2 3.3v2.1m0 .9v.1" {...common} />
        </g>
      ) : null}
    </svg>
  )
}

function stripDecorativeUnicode(value: string): string {
  return value
    .replace(/[\\u2600-\\u27BF]/gu, ' ')
    .replace(/[\\u{1F300}-\\u{1FAFF}]/gu, ' ')
    .replace(/\\s*(?:->|→)\\s*/g, ' // ')
}''',
)

replace_once(
    "app/build.gradle.kts",
    '        versionCode = 34\n        versionName = "0.9.24-gmail-image"',
    '        versionCode = 35\n        versionName = "0.9.25-hud-voice-polish"',
)

Path(__file__).unlink()
