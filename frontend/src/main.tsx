// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

// Developer credit, visible only in the browser's developer console.
console.log(
  '%cVindhyan Studio%c\nDesigned & developed by Mahan Srivastava\nFor help, changes or new features: srilalmahaan@gmail.com · github.com/mahaansrivastav',
  'font-weight:700;font-size:13px', 'color:#888;line-height:1.6',
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
