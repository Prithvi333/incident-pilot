import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, loadEnv } from "vite";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, projectRoot, "");

  return {
    plugins: [react(), tailwindcss()],

    server: {
      port: 5173,

      proxy: {
        "/api/documents": {
          target: `http://${env.VITE_INGESTION_HOST}:${env.VITE_INGESTION_PORT}`,
          changeOrigin: true,
        },

        "/ws": {
          target: `http://${env.VITE_BACKEND_HOST}:${env.VITE_BACKEND_PORT}`,
          ws: true,
          changeOrigin: true,
        },

        "/api": {
          target: `http://${env.VITE_BACKEND_HOST}:${env.VITE_BACKEND_PORT}`,
          changeOrigin: true,
        },
      },
    },
  };
});
