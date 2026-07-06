import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  // The "server" block configures Vite's dev server (the one you get from
  // `npm run dev`, running on http://localhost:5173).
  server: {
    // A "proxy" forwards certain requests from the dev server to another server.
    // Our React app and our Spring Boot backend run on DIFFERENT ports (5173 vs
    // 8080). If the browser called http://localhost:8080 directly, the browser's
    // security model (CORS) would block it because the origin differs.
    //
    // Instead, the frontend calls a same-origin path like "/api/recipes", and
    // Vite transparently forwards ("proxies") that request to the backend. To the
    // browser it looks like one server, so there's no CORS problem at all.
    proxy: {
      // Any request whose path starts with "/api" gets sent to the backend.
      '/api': {
        target: 'http://localhost:8080', // where Spring Boot is listening
        changeOrigin: true, // rewrites the Host header to match the target
      },
    },
  },
})
