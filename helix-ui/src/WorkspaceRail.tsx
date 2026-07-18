const CAPABILITIES = [
  { id: 'VISION', title: 'X-CAMERA', command: 'OPEN YOUR EYES', state: 'NATIVE' },
  { id: 'CREATE', title: 'VISUAL LAB', command: 'VISUALIZE...', state: 'GEMINI' },
  { id: 'PRIVATE', title: 'PRIVATE DIARY', command: 'OPEN MY DIARY', state: 'LOCKED' },
  { id: 'MEMORY', title: 'MEMORY VAULT', command: 'OPEN MEMORY VAULT', state: 'LOCAL' },
  { id: 'ANDROID', title: 'CONTROL', command: 'ENABLE CONTROL', state: 'SYSTEM' },
]

/**
 * Visible capability map for the voice-first interface. These are not decorative
 * buttons: each phrase is routed by the native command core to a dedicated
 * Android workspace before generic model dialogue is allowed.
 */
export function WorkspaceRail({
  bridgeReady,
  onOpen,
}: {
  bridgeReady: boolean
  onOpen: (workspace: string) => void
}) {
  return (
    <section className="workspace-rail" aria-label="FRIDAY native workspace commands">
      <div className="workspace-rail-label">
        <b>NATIVE WORKSPACES</b>
        <span>VOICE ROUTED</span>
      </div>
      <div className="workspace-rail-track">
        {CAPABILITIES.map(capability => (
          <button
            key={capability.id}
            type="button"
            className="workspace-capability"
            disabled={!bridgeReady}
            onClick={() => onOpen(capability.id.toLowerCase())}
          >
            <div className="workspace-capability-head">
              <span>{capability.id}</span>
              <i>{capability.state}</i>
            </div>
            <strong>{capability.title}</strong>
            <small>OPEN // {capability.command}</small>
          </button>
        ))}
      </div>
    </section>
  )
}
