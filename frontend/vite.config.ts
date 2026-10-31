import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In local dev the API runs on :8080 and is proxied, so VITE_API_BASE_URL can stay empty.
// On hosted environments set VITE_API_BASE_URL to the backend URL (see .env.example).
// BACKEND_URL lets the local API run on another port (e.g. if 8080 is busy).
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

// Ownership notice kept at the top of every built file (see LICENSE).
const notice = '/*! Vindhyan Studio web app (c) 2026 Mahan Srivastava. All rights reserved. Licensed to MORVIN INTEGRATED SOLUTIONS for its own store only; copying, reuse or redistribution is prohibited. */'

export default defineConfig({
  plugins: [react(), tailwindcss(), {
    name: 'ownership-notice',
    apply: 'build',
    enforce: 'post',
    generateBundle(_, bundle) {
      for (const file of Object.values(bundle)) {
        if (file.type === 'chunk') file.code = notice + '\n' + file.code
        else if (file.fileName.endsWith('.css') && typeof file.source === 'string') file.source = notice + '\n' + file.source
      }
    },
  }],
  server: {
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/files': backend,
    },
  },
})
