import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In local dev the API runs on :8080 and is proxied, so VITE_API_BASE_URL can stay empty.
// On hosted environments set VITE_API_BASE_URL to the backend URL (see .env.example).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/files': 'http://localhost:8080',
    },
  },
})
