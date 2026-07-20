import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'
import './operationalCore.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)

// Automated phone and desktop captures skip the short boot veil and open the
// actual module matrix so CI verifies the command interface rather than a mock.
const visualSmokePreview = navigator.webdriver || (
  window.location.hostname === '127.0.0.1' && window.location.port === '4173'
)
if (visualSmokePreview) {
  window.setTimeout(() => {
    document.querySelector<HTMLButtonElement>('.operational-boot')?.click()
    window.setTimeout(() => document.querySelector<HTMLButtonElement>('.operational-menu')?.click(), 320)
  }, 420)
}
