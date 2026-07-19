import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'
import './designLock.css'
import './polishLock.css'
import './workspaceRail.css'
import './cinematicOs.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
