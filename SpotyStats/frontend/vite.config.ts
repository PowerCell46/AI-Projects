import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'


// The backend has no CORS config on purpose (BFF pattern) — the browser must
// see the SPA and the API as one origin. Vite proxies backend paths to :8080,
// including the Spotify OAuth callback under /login.
// Always browse via http://127.0.0.1:5173, never localhost (cookie scoping).
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/auth': 'http://127.0.0.1:8080',
      '/login': 'http://127.0.0.1:8080',
      '/oauth2': 'http://127.0.0.1:8080',
    },
  },
})
