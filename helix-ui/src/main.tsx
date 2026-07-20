import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'
import './referenceCinematic.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)

// Headless validation can select a real command scene through ?scene=news,
// ?scene=route or ?scene=location. Packaged Android behavior is unchanged.
const visualSmokePreview = navigator.webdriver || (
  window.location.hostname === '127.0.0.1' && window.location.port === '4173'
)
if (visualSmokePreview) {
  window.setTimeout(() => {
    document.querySelector<HTMLButtonElement>('.reference-boot')?.click()
    const preview = new URLSearchParams(window.location.search).get('scene')
    window.setTimeout(() => {
      if (preview === 'news') {
        window.jarvisHelix?.receive({ type: 'transcript', text: "What's happening around the world?" })
        window.jarvisHelix?.receive({ type: 'response', intent: 'research/news', confidence: 1, display: 'Global developments will appear here from FRIDAY’s verified live research response. Business, science, technology, and major events are organized into readable broadcast cards. No unverified headline is inserted into the interface.' })
        window.jarvisHelix?.receive({ type: 'state', mode: 'SPEAKING' })
      } else if (preview === 'route') {
        window.jarvisHelix?.receive({ type: 'transcript', text: 'Show me the routes from India to Japan' })
        window.jarvisHelix?.receive({ type: 'response', intent: 'navigation/route', confidence: 1, display: 'Route calculation is awaiting a verified maps provider. Distance and duration remain marked as calculating until live route data arrives.' })
        window.jarvisHelix?.receive({ type: 'state', mode: 'SPEAKING' })
      } else if (preview === 'location') {
        window.jarvisHelix?.receive({ type: 'transcript', text: 'Show me where I am' })
        window.jarvisHelix?.receive({ type: 'location', location: { available: true, acquiring: false, latitude: 26.7271, longitude: 93.1479, accuracyM: 8, altitudeM: 82, provider: 'GPS', placeName: 'Biswanath, Assam, India', updatedAtMs: Date.now(), error: '' } })
        window.jarvisHelix?.receive({ type: 'response', intent: 'location/current', confidence: 1, display: "YOU'RE HERE, SIR. Biswanath, Assam, India" })
        window.jarvisHelix?.receive({ type: 'state', mode: 'SPEAKING' })
      } else if (preview === 'modules') {
        document.querySelector<HTMLButtonElement>('.reference-menu')?.click()
      }
    }, 320)
  }, 420)
}
