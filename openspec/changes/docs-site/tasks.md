# Tasks

## 1. Site skeleton

- [x] 1.1 Create `docs/` with Astro, Starlight, `@astrojs/react`, React, LikeC4 and the link validator pinned exactly; `npm run dev`, `build`, `check` scripts; `.gitignore` for `dist`, `.astro`, `node_modules`
- [x] 1.2 Configure Starlight: title, logo and favicon from `src/branding`, `site` = `https://docs.heavymatica.com`, explicit sidebar (player guide first, developer guide second), edit links to GitHub, `public/CNAME`
- [x] 1.3 Theme colours matching the branding, light and dark

## 2. Generated references

- [x] 2.1 Give each `KeyContext` a stable id
- [x] 2.2 Test platform in `common` tests with a recording `KeyRegistry`; test that compares the recorded keys with `docs/src/data/keys.json` and writes it with `-PwriteDocs`; generate the file
- [x] 2.3 Settings loader reading the golden TOML and the settings page rendering every section
- [x] 2.4 Keys page rendering `keys.json` with names from `en_us.json` and context descriptions

## 3. Architecture

- [x] 3.1 LikeC4 model in `docs/architecture/`: actors, mod (common, neoforge, fabric), cloud (slices, storage), websites, external services; views `index`, `mod`, `cloud`
- [x] 3.2 `LikeC4View` Astro wrapper and the architecture page using the three views

## 4. Player guide

- [x] 4.1 Start page and install (Fabric and NeoForge, Fabric API, optional Mod Menu, Jade, Iris)
- [x] 4.2 First build walkthrough
- [x] 4.3 Remote, placement, layers, comparison and mistake pointer
- [x] 4.4 Materials and swaps, Easy Place and placement restriction
- [x] 4.5 Capture, import and export, creative paste
- [x] 4.6 Library and cloud account, player website
- [x] 4.7 Settings and keys pages' prose, compatibility, troubleshooting
- [x] 4.8 `<NextRelease />` component and `unreleased` frontmatter

## 5. Developer guide

- [x] 5.1 Move `CONTRIBUTING.md` sections into `develop/` pages (layout, building, mod projects, releasing, local development, websites, admin site, dev aids) and add cloud and API pages
- [x] 5.2 Shrink `CONTRIBUTING.md` to contributing, local build and run tips and links; trim `README.md` and link the site

## 6. Publishing

- [x] 6.1 `.github/workflows/docs.yml`: build on pull requests and pushes, deploy on `main` with `actions/deploy-pages`, `likec4 validate`
- [x] 6.2 Developer page or CONTRIBUTING note on the owner setup (DNS, domain verification, Pages source)

## 7. Verification

- [x] 7.1 `npm run build` passes with the link validator; every sidebar page renders; diagrams render in the built site
- [x] 7.2 The keys test fails when a key's default changes and passes after regenerating; `./gradlew build` passes
- [x] 7.3 Every player-facing capability spec is covered by a player page (checklist against `openspec/specs/client` and `web/player-ui`)

## Workflow follow-up

- Owner: DNS record, domain verification, Pages source; first deploy check at `https://docs.heavymatica.com`.
