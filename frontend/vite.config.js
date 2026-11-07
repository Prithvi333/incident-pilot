import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Proxy API calls to Spring Boot during development
    proxy: {
      "/api/documents": {
        target: "http://localhost:8082",
        changeOrigin: true,
      },

      // Real-time events and notifications (STOMP over WebSocket) are served by the incident service.
      // The incident service allows http://localhost:* by default (ALLOWED_ORIGINS) for this handshake.
      "/ws": {
        target: "http://localhost:8080",
        ws: true,
        changeOrigin: true,
      },

      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
