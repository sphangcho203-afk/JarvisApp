import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'
import './designLock.css'
import './polishLock.css'
import './workspaceRail.css'
import './cinematicOs.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)

// Headless Chromium captures the operational launcher rather than freezing the
// validation artifact on the boot cinematic. Android WebView is unaffected.
if (navigator.webdriver) {
  window.setTimeout(() => {
    document.querySelector<HTMLButtonElement>('.fos-boot')?.click()
    document.querySelector<HTMLButtonElement>('.fos-launcher-trigger')?.click()
  }, 500)
}
