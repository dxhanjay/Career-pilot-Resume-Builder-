import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * In production the API and this bundle are served by the same Spring Boot
 * process from the same origin, so every request goes to a relative /api path
 * and there is no base URL to configure, no CORS preflight, and no environment
 * variable that can be wrong in exactly one environment.
 *
 * Locally the two run apart — Vite on 5173, Spring on 8080 — so the dev server
 * proxies the same relative paths across. The application code is identical in
 * both cases, which is the point: nothing about the request layer is
 * environment-specific.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    // Source maps are shipped deliberately. This is a client bundle containing
    // no secrets, and a readable stack trace from a user's console is worth far
    // more than the obscurity of a minified one.
    sourcemap: true,
    chunkSizeWarningLimit: 900,
  },
})
