import node from "@astrojs/node";
import { defineConfig } from "astro/config";

const siteUrl = new URL(
  process.env.SPEAIVE_SITE_URL ?? process.env.SITE_URL ?? "http://localhost:4321"
);
const allowedDomain = {
  hostname: siteUrl.hostname,
  protocol: siteUrl.protocol.slice(0, -1),
  ...(siteUrl.port ? { port: siteUrl.port } : {})
};
const backendUrl = process.env.SPEAIVE_BACKEND_URL ?? "http://127.0.0.1:8080";
const frontendHost = process.env.SPEAIVE_BIND_ADDRESS ?? "127.0.0.1";
const frontendPort = Number(process.env.SPEAIVE_PORT ?? "4321");

export default defineConfig({
  site: siteUrl.href,
  output: "server",
  adapter: node({ mode: "standalone" }),
  security: {
    allowedDomains: [allowedDomain]
  },
  server: {
    host: frontendHost,
    port: frontendPort
  },
  vite: {
    server: {
      proxy: {
        "/api/v1": {
          target: backendUrl,
          changeOrigin: true
        },
        "/media": {
          target: backendUrl,
          changeOrigin: true
        }
      }
    }
  },
  markdown: {
    shikiConfig: {
      theme: "github-light"
    }
  }
});
