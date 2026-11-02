import { defineConfig } from 'astro/config';
import svelte from '@astrojs/svelte';

const admin = process.env.HEAVYMATICA_ADMIN_URL || 'http://localhost:5181';

export default defineConfig({
  output: 'static',
  build: { format: 'directory' },
  integrations: [svelte()],
  server: {
    host: true,
    port: Number(process.env.PORT || 4322),
  },
  vite: {
    server: {
      strictPort: true,
      proxy: {
        // Passkeys and the same-origin check compare the browser's origin with the Host header, so it must reach the
        // cloud unchanged.
        '/admin/v1': { target: admin, changeOrigin: false },
      },
    },
  },
});
