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
        target: "http://10.0.0.11:8082",
        changeOrigin: true,
      },

      "/api": {
        target: "http://10.0.0.14:8080",
        changeOrigin: true,
      },
    },
  },
});
