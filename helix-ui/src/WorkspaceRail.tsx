const CAPABILITIES = [
  { id: 'VISION', title: 'X-CAMERA', command: 'OPEN YOUR EYES', state: 'NATIVE' },
  { id: 'CREATE', title: 'VISUAL LAB', command: 'VISUALIZE...', state: 'GEMINI' },
  { id: 'PRIVATE', title: 'DIARY', command: 'OPEN MY DIARY', state: 'LOCKED' },
  { id: 'MEMORY', title: 'VAULT', command: 'OPEN MEMORY VAULT', state: 'LOCAL' },
  { id: 'ANDROID', title: 'CONTROL', command: 'ENABLE CONTROL', state: 'SYSTEM' },
]

/**
 * Visible capability map for the voice-first interface. These are not decorative
 * buttons: each phrase is routed by the native command core to a dedicated
 * Android workspace before generic model dialogue is allowed.
 */
export function WorkspaceRail() {
  return (
    <section className="workspace-rail" aria-label="FRIDAY native workspace commands">
      <div className="workspace-rail-label">
        <b>NATIVE WORKSPACES</b>
        <span>VOICE ROUTED</span>
      </div>
      <div className="workspace-rail-track">
        {CAPABILITIES.map(capability => (
          <article key={capability.id} className="workspace-capability">
            <div className="workspace-capability-head">
              <span>{capability.id}</span>
              <i>{capability.state}</i>
            </div>
            <strong>{capability.title}</strong>
            <small>SAY // {capability.command}</small>
          </article>
        ))}
      </div>
    </section>
  )
}
