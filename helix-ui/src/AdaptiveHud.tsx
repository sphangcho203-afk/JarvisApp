import type { ComponentProps } from 'react'
import { Hud } from './Hud'
import type { TerminalLog } from './types'
import { WORKSPACES, type WorkspaceId } from './workspaces'

type HudProps = ComponentProps<typeof Hud>

interface AdaptiveHudProps extends HudProps {
  workspace: WorkspaceId
}

export function AdaptiveHud({ workspace, logs, ...hudProps }: AdaptiveHudProps) {
  const spec = WORKSPACES[workspace]
  const diagnostics = logs.slice(-30).reverse()

  return (
    <div
      className={`adaptive-workspace-shell workspace-${workspace.toLowerCase()} loading-${spec.loadingClass}`}
      data-workspace={workspace}
    >
      <div className="workspace-banner" aria-live="polite">
        <div className="workspace-banner-index">WS // {workspace}</div>
        <div className="workspace-banner-copy">
          <strong>{spec.label}</strong>
          <span>{spec.subtitle}</span>
        </div>
        <div className="workspace-banner-load">{spec.loadingClass.toUpperCase()} LOAD</div>
      </div>

      <Hud {...hudProps} logs={logs} />

      {workspace === 'DIAGNOSTICS' && (
        <section className="diagnostics-vault-panel" aria-label="Diagnostics Vault">
          <header>
            <div>
              <span>INTERNAL ENGINE EVENTS</span>
              <strong>DIAGNOSTICS VAULT</strong>
            </div>
            <b>SESSION ONLY</b>
          </header>
          <div className="diagnostics-vault-scroll">
            {diagnostics.length === 0 ? (
              <p className="diagnostics-empty">No diagnostic events are available.</p>
            ) : (
              diagnostics.map((log: TerminalLog) => (
                <div className="diagnostics-event" key={log.id}>
                  <time>{log.time}</time>
                  <b>{log.channel}</b>
                  <p>{log.text}</p>
                </div>
              ))
            )}
          </div>
        </section>
      )}
    </div>
  )
}
