/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
//
// Environment contract for the frontend (must match compose.yaml build args):
//   VITE_API_BASE_URL   absolute base URL for REST calls. Usually "" because
//                       the SPA talks to the same origin via the Nginx reverse
//                       proxy (/api → wyrdly-backend:8080).
//                       PROD-EXPECTED: ""
//   VITE_BACKEND_URL    absolute HTTP base URL used by the Vite dev server proxy.
//                       PROD-EXPECTED: https://api.wyrdly.yandro.tech
//   VITE_WS_URL         absolute WebSocket base URL.
//                       PROD-EXPECTED: wss://api.wyrdly.yandro.tech
export default defineConfig({
  plugins: [react(), !process.env.VITEST && tailwindcss()].filter(Boolean),
  server: {
    proxy: {
      "/api": {
        target: process.env.VITE_BACKEND_URL ?? "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws": {
        target: process.env.VITE_WS_URL ?? "ws://localhost:8080",
        ws: true,
      },
    },
  },
  build: {
    // Surface the env that matters most when auditing a prod bundle.
    sourcemap: process.env.VITE_SOURCEMAP === "true",
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
    },
  },
});