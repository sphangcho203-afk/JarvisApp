import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'
import './designLock.css'
import './polishLock.css'
import './workspaceRail.css'
import './cinematicOs.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)

// Localhost headless captures validate the operational launcher rather than
// freezing on the boot cinematic. Packaged Android WebView behavior is unchanged.
const visualSmokePreview = navigator.webdriver || (
  window.location.hostname === '127.0.0.1' && window.location.port === '4173'
)
if (visualSmokePreview) {
  window.setTimeout(() => {
    document.querySelector<HTMLButtonElement>('.fos-boot')?.click()
    document.querySelector<HTMLButtonElement>('.fos-launcher-trigger')?.click()
  }, 500)
}
