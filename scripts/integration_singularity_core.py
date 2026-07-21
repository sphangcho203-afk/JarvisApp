from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "helix-ui/src/FridayCinematicOS.tsx"
MAIN = ROOT / "helix-ui/src/main.tsx"

ui = UI.read_text(encoding="utf-8")

singularity_import = "import { SingularityCore } from './SingularityCore'\n"
if singularity_import not in ui:
    anchor = "import { AnimatePresence, motion } from 'motion/react'\n"
    if anchor not in ui:
        raise RuntimeError("FRIDAY singularity import anchor missing")
    ui = ui.replace(anchor, anchor + singularity_import, 1)

# The boot sequence and first interface use the engineered singularity reactor.
ui = ui.replace(
    "            <QuantumCore compact />\n            <b>F.R.I.D.A.Y.</b>",
    "            <SingularityCore compact mode={props.mode} energy={Math.max(props.metrics.rms, .12)} />\n            <b>F.R.I.D.A.Y.</b>",
    1,
)

old_home = '''function HomeScene({ mode, metrics }: { mode: HelixState; metrics: AudioMetrics }) {
  return (
    <div className="fos-home">
      <QuantumCore energy={Math.max(metrics.rms, mode === 'PROCESSING' ? .28 : .08)} />
    </div>
  )
}'''
new_home = '''function HomeScene({ mode, metrics }: { mode: HelixState; metrics: AudioMetrics }) {
  return (
    <div className="fos-home">
      <SingularityCore mode={mode} energy={Math.max(metrics.rms, mode === 'PROCESSING' ? .34 : .1)} />
    </div>
  )
}'''
if old_home in ui:
    ui = ui.replace(old_home, new_home, 1)
elif "<SingularityCore mode={mode}" not in ui:
    raise RuntimeError("FRIDAY home-scene anchor missing")

# Voice commands replace the visible navigation menu.
menu_block = '''        <button className="fos-menu" type="button" aria-label="Open FRIDAY modules" onClick={() => setModulesOpen(true)}>
          <i /><i /><i />
        </button>
'''
ui = ui.replace(menu_block, "", 1)

voice_command_effect = r'''
  useEffect(() => {
    const command = props.transcript.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim()
    if (/\b(open|show|display|launch)\b.*\b(modules?|workspace matrix|system matrix|helix ops)\b/.test(command)) {
      setModulesOpen(true)
      return
    }
    if (/\b(close|dismiss|hide)\b.*\b(modules?|workspace matrix|system matrix|helix ops)\b/.test(command)) {
      setModulesOpen(false)
    }
  }, [props.transcript])
'''
if "workspace matrix|system matrix|helix ops" not in ui:
    effect_anchor = '''  useEffect(() => {
    if (previousDesign.current !== props.designMode) {
      sonicDirector.changeTheme(props.designMode)
      previousDesign.current = props.designMode
    }
  }, [props.designMode])
'''
    if effect_anchor not in ui:
        raise RuntimeError("FRIDAY command-only module anchor missing")
    ui = ui.replace(effect_anchor, effect_anchor + voice_command_effect, 1)

mic_block = '''      <button className="fos-mic" type="button" aria-label="Speak to FRIDAY" onClick={activateVoice}>
        <i /><i /><i /><span />
      </button>
'''
if mic_block in ui:
    ui = ui.replace(
        mic_block,
        '''      {scene !== 'CORE' ? (
        <button className="fos-mic" type="button" aria-label="Recalibrate FRIDAY voice channel" onClick={activateVoice}>
          <i /><i /><i /><span />
        </button>
      ) : null}
''',
        1,
    )

footer_block = '''      <footer className="fos-footer">
        <span>{props.telemetry.time}</span>
        <span>{props.designMode} MATRIX</span>
        <span>{props.telemetry.networkQuality}</span>
        <span>{props.telemetry.battery}%</span>
      </footer>
'''
if footer_block in ui:
    ui = ui.replace(
        footer_block,
        '''      {scene !== 'CORE' ? (
        <footer className="fos-footer">
          <span>{props.telemetry.time}</span>
          <span>{props.designMode} MATRIX</span>
          <span>{props.telemetry.networkQuality}</span>
          <span>{props.telemetry.battery}%</span>
        </footer>
      ) : null}
''',
        1,
    )

ui = ui.replace("? 'F.R.I.D.A.Y. operational. Awaiting command.'", "? 'Awaiting command.'")
ui = ui.replace("{home ? 'PRIVATE INTELLIGENCE ONLINE'", "{home ? 'LIVE CONVERSATION'")

UI.write_text(ui, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
css_import = "import './singularityCore.css'\n"
if css_import not in main:
    preferred_anchor = "import './worldMapMaster.css'\n"
    fallback_anchor = "import './fridayCinematicReactor.css'\n"
    anchor = preferred_anchor if preferred_anchor in main else fallback_anchor
    if anchor not in main:
        raise RuntimeError("FRIDAY singularity stylesheet anchor missing")
    main = main.replace(anchor, anchor + css_import, 1)
MAIN.write_text(main, encoding="utf-8")

print("FRIDAY buttonless singularity core integration applied")
