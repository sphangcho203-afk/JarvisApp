const CAPABILITIES = [
  { id: 'VISION', route: 'xcamera', title: 'X-CAMERA', command: 'OPEN YOUR EYES', state: 'NATIVE' },
  { id: 'CREATE', route: 'image', title: 'VISUAL LAB', command: 'VISUALIZE...', state: 'GEMINI' },
  { id: 'PROVIDERS', route: 'providers', title: 'API MESH', command: 'OPEN API SETUP', state: 'ENCRYPTED' },
  { id: 'PRIVATE', route: 'diary', title: 'PRIVATE DIARY', command: 'OPEN MY DIARY', state: 'LOCKED' },
  { id: 'MEMORY', route: 'memory', title: 'MEMORY VAULT', command: 'OPEN MEMORY VAULT', state: 'LOCAL' },
  { id: 'ANDROID', route: 'control', title: 'CONTROL', command: 'ENABLE CONTROL', state: 'SYSTEM' },
] as const

/**
 * Visible capability map for the voice-first interface. These are real native
 * workspace controls and also advertise the matching natural voice phrases.
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
        <span>TOUCH + VOICE</span>
      </div>
      <div className="workspace-rail-track">
        {CAPABILITIES.map(capability => (
          <button
            key={capability.id}
            type="button"
            className="workspace-capability"
            disabled={!bridgeReady}
            data-workspace={capability.route}
            onClick={() => onOpen(capability.route)}
          >
            <div className="workspace-capability-head">
              <span>{capability.id}</span>
              <i>{capability.state}</i>
            </div>
            <strong>{capability.title}</strong>
            <small>SAY // {capability.command}</small>
          </button>
        ))}
      </div>
    </section>
  )
}