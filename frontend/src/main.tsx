// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
/*! Vindhyan Studio web app (c) 2026 Mahan Srivastava. All rights reserved. Licensed to MORVIN INTEGRATED SOLUTIONS for its own store only; copying, reuse or redistribution is prohibited. */
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
