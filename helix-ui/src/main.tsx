import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'
import './referenceCinematic.css'
import './referenceCinematicFixes.css'
import './referenceCinematicExact.css'
import './fridayCinematicOS.css'

const preview = new URLSearchParams(window.location.search).get('scene')?.toLowerCase() || ''
const visualSmokePreview = navigator.webdriver || (
  window.location.hostname === '127.0.0.1' && window.location.port === '4173'
)
if (visualSmokePreview && preview) {
  document.documentElement.dataset.referencePreview = 'true'
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)

if (visualSmokePreview && preview === 'modules') {
  window.setTimeout(() => document.querySelector<HTMLButtonElement>('.fos-menu')?.click(), 260)
}
