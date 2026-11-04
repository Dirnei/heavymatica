import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import react from '@astrojs/react';
import starlightLinksValidator from 'starlight-links-validator';
import { LikeC4VitePlugin } from 'likec4/vite-plugin';

const play = (slugs) => slugs.map((slug) => `play/${slug}`);
const develop = (slugs) => slugs.map((slug) => `develop/${slug}`);

export default defineConfig({
  site: 'https://docs.heavymatica.com',
  // The player website's dev server uses 4321 and the admin site's 4322.
  server: { port: 4323 },
  integrations: [
    starlight({
      title: 'Heavymatica',
      description: 'Schematics for Minecraft with a simple placement UI and a cloud library.',
      logo: { src: '../src/branding/heavymatica.svg' },
      favicon: '/favicon.svg',
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/Dirnei/heavymatica' }],
      editLink: { baseUrl: 'https://github.com/Dirnei/heavymatica/edit/main/docs/' },
      customCss: ['./src/styles/theme.css'],
      components: { PageTitle: './src/components/PageTitle.astro' },
      plugins: [starlightLinksValidator()],
      sidebar: [
        { label: 'Getting started', items: play(['install', 'first-build']) },
        {
          label: 'Building',
          items: play(['remote', 'placement', 'layers', 'comparison', 'materials', 'easy-place', 'creative-paste']),
        },
        { label: 'Schematics', items: play(['library', 'website', 'capture', 'import-export']) },
        { label: 'Reference', items: play(['keys', 'settings', 'compatibility', 'troubleshooting']) },
        {
          label: 'For developers',
          collapsed: true,
          items: develop([
            'contributing',
            'architecture',
            'layout',
            'building',
            'mod-projects',
            'cloud',
            'websites',
            'api',
            'local-development',
            'dev-aids',
            'screenshots',
            'releasing',
          ]),
        },
      ],
    }),
    react(),
  ],
  vite: {
    plugins: [LikeC4VitePlugin({ workspace: 'architecture' })],
    // The settings and keys pages read the mod's settings and language files.
    server: { fs: { allow: ['..'] } },
  },
});
