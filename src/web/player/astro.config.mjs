import { defineConfig } from 'astro/config';
import svelte from '@astrojs/svelte';

const cloud = process.env.HEAVYMATICA_CLOUD_URL || 'http://localhost:5180';

export default defineConfig({
  output: 'static',
  build: { format: 'directory' },
  integrations: [svelte()],
  server: {
    host: true,
    port: Number(process.env.PORT || 4321),
  },
  vite: {
    server: {
      strictPort: true,
      proxy: {
        // The cloud builds the GitHub callback URL from the Host header, so the browser's host must reach it unchanged.
        '/v1': { target: cloud, changeOrigin: false },
      },
    },
  },
});
