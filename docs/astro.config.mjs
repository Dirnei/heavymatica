import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { satteri } from '@astrojs/markdown-satteri';
import react from '@astrojs/react';
import starlightLinksValidator from 'starlight-links-validator';
import { LikeC4VitePlugin } from 'likec4/vite-plugin';

const play = (slugs) => slugs.map((slug) => `play/${slug}`);
const develop = (slugs) => slugs.map((slug) => `develop/${slug}`);

// Screenshots in the docs are at most one content column wide, so browsers fetch a size that fits it.
const SCREENSHOT_WIDTHS = [480, 760, 1140, 1520];
const SCREENSHOT_SIZES = '(min-width: 72rem) 44rem, (min-width: 50rem) calc(100vw - 22rem), calc(100vw - 2.5rem)';

// The first screenshot is usually in view when the page opens, so it loads at once; the rest lazily.
const responsiveImages = () => {
  let first = true;
  return {
    name: 'responsive-screenshots',
    element: {
      filter: ['img'],
      visit(node, ctx) {
        ctx.setProperty(node, 'sizes', SCREENSHOT_SIZES);
        if (first) {
          ctx.setProperty(node, 'loading', 'eager');
          first = false;
        }
      },
    },
  };
};

export default defineConfig({
  site: 'https://docs.heavymatica.com',
  // The player website's dev server uses 4321 and the admin site's 4322.
  server: { port: 4323 },
  prefetch: { prefetchAll: true, defaultStrategy: 'hover' },
  image: {
    layout: 'constrained',
    breakpoints: SCREENSHOT_WIDTHS,
    service: { entrypoint: 'astro/assets/services/sharp', config: { webp: { quality: 74, effort: 6 } } },
  },
  markdown: { processor: satteri({ hastPlugins: [responsiveImages] }) },
  integrations: [
    starlight({
      title: 'Heavymatica',
      description: 'Schematics for Minecraft with a simple placement UI and a cloud library.',
      favicon: '/favicon.svg',
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/Dirnei/heavymatica' }],
      editLink: { baseUrl: 'https://github.com/Dirnei/heavymatica/edit/main/docs/' },
      customCss: [
        '@fontsource-variable/atkinson-hyperlegible-next/wght.css',
        '@fontsource-variable/atkinson-hyperlegible-next/wght-italic.css',
        './src/styles/theme.css',
      ],
      components: {
        Head: './src/components/Head.astro',
        Hero: './src/components/Hero.astro',
        PageTitle: './src/components/PageTitle.astro',
        SiteTitle: './src/components/SiteTitle.astro',
      },
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
