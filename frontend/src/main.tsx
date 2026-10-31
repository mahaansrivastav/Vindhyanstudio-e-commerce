import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

// Developer credit, visible only in the browser's developer console.
console.log(
  '%cVindhyan Studio%c  designed & developed by Mahan Srivastava · github.com/mahaansrivastav',
  'font-weight:800;font-size:13px;color:#c8892e', 'color:#888',
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
