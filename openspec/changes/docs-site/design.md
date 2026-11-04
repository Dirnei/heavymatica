# Design

## Context

- Player docs today are `README.md`; developer docs are `CONTRIBUTING.md` (layout, building, mod projects, releasing,
  local development, player website, admin site, development aids) and `ROADMAP.md`.
- The websites in `src/web/player` and `src/web/admin` are Astro 7 static sites with Svelte. CI uses Node 22.
- The settings file has a golden copy, `src/mod/heavymatica/common/src/test/resources/settings/heavymatica-client.default.toml`,
  which tests on both loaders compare with what the mod writes. Its comments are the settings' descriptions.
- Keys are created in Java (`HeavymaticaClient.OPEN_LIBRARY`, `RemoteKeys`, `BuildKeys`) through
  `Platform.get().keys().create(name, context, keyCode, category)`, with contexts such as `HOLDING_REMOTE`,
  `LAYER_KEYS`, `WITH_SELECTION` and `NO_SCREEN`. Key names and descriptions are in `assets/heavymatica/lang/en_us.json`.
- The 32 capability specs in `openspec/specs` describe every feature and are the checklist for the player pages.

## Goals / Non-Goals

**Goals:**

- One static site, built in CI without Gradle or Minecraft.
- References that cannot drift: settings and keys come from files the mod's tests guard.
- Diagrams from a model, not from drawings.

**Non-Goals:**

- Screenshots and their scripting (separate change).
- Versioned docs per release, translations.
- Documenting the admin site for admins; it is covered in the developer pages only.

## Decisions

### D1: Astro with Starlight in `docs/`

Starlight 0.42 (Astro 7) gives navigation, search (Pagefind), dark mode and Markdown/MDX pages. The repository already
uses Astro twice, so there is one toolchain. `docs/` holds its own `package.json` and lock file, like each website.

*Alternative:* VitePress. Works as well, but adds Vue next to Astro and Svelte.

### D2: Content layout

```
docs/src/content/docs/
  index.mdx                      start page (hero, what it is, install link)
  play/                          player guide, first in the sidebar
    install, first-build, remote, placement, layers, comparison, materials, easy-place,
    capture, import-export, creative-paste, library, website, settings, keys,
    compatibility, troubleshooting
  develop/                       developer guide
    architecture, layout, building, mod-projects, cloud, websites, api, local-development,
    dev-aids, releasing, contributing
```

Pages are written from the capability specs, in player language, not copied from them. The sidebar is configured
explicitly so the order is a reading order, not alphabetical.

### D3: Settings reference read from the golden file

A small line reader in `docs/src/lib/settings.ts` parses the golden TOML at build time: sections, keys, the comment
lines (description, `Range:`, `Allowed Values:`) and the values. A TOML library would drop the comments, which are the
descriptions. The settings page renders one table
per section. The file is read in place from `src/mod/...`, so there is no second copy.

*Alternative:* copy the file into `docs/`. Rejected: a second copy needs its own sync check.

### D4: Key list written by a mod test

A test in `common` installs a test `Platform` whose `KeyRegistry` records each created key (name, context, default key,
category) instead of creating a real mapping, loads the key classes, and compares the recorded list with
`docs/src/data/keys.json`. With `./gradlew :heavymatica:common:test -PwriteDocs` it writes the file instead. The key
registration moves into `HeavymaticaClient.registerKeys(KeyRegistry)` so the test registers exactly what the game does. The test fails naming
each key that differs. Each `KeyContext` gets a stable id (`toString`) the JSON stores; the docs map ids to text such
as "while holding the remote". Key names come from `en_us.json`, read at docs build time.

*Alternative:* parse the Java sources from the docs build. Rejected: brittle and silent when it breaks.

### D5: LikeC4 through its Vite plugin

`likec4/vite-plugin` reads `docs/architecture/*.c4` and provides `likec4:react`; an Astro wrapper renders
`LikeC4View` as a React island (`@astrojs/react`). The model has views `index` (landscape), `mod` and `cloud`.
The LikeC4 CLI also validates the model in CI (`likec4 validate`).

*Alternative:* `likec4 build` to static images. Kept as the fallback if the plugin breaks with a future Astro.

### D6: Unreleased marker

A Starlight `Badge`-based component `<NextRelease />` for headings and an `unreleased: true` frontmatter flag that
shows a note under the page title (a `PageTitle` override). A sidebar badge uses Starlight's own `sidebar.badge`
frontmatter.

### D7: Publishing

`.github/workflows/docs.yml`:

- Triggers: push to `main` and pull requests, filtered to `docs/**`, the golden settings file, `en_us.json`, the logo
  files the start page uses and the workflow itself; plus manual dispatch.
- Build job: Node 22, `npm ci`, `npm run check` (types and `likec4 validate`), `npm run build` (Starlight's link
  checker `starlight-links-validator` fails the build on broken internal links); upload `docs/dist` as the Pages
  artifact.
- Deploy job (push to `main` only): `actions/deploy-pages`, environment `github-pages`, concurrency `pages`.
- `docs/public/CNAME` holds `docs.heavymatica.com`; `site` in the Astro config is `https://docs.heavymatica.com`.
- The dev server uses port 4323, next to the player website (4321) and the admin site (4322).
- The favicon is the logo's small variant, written by `src/branding/generate.py` like the other exports.

The key list does not trigger the workflow from Gradle: a key change touches `keys.json`, which is under `docs/`.

## Risks / Trade-offs

- [The pages drift from the mod in prose] → The references that change most are generated; pages link to them instead
  of repeating defaults.
- [LikeC4 or Starlight breaks with an Astro update] → Versions pinned exactly; static export fallback (D5).
- [Docs describe `main`, players run a release] → `<NextRelease />` marker (D6).
- [The test platform in `common` hides wiring bugs] → It only records keys; feature behavior stays covered by the
  existing tests and the loader runs.

## Migration Plan

1. Build the site locally and check every page and diagram.
2. Shrink `CONTRIBUTING.md` and `README.md` in the same commit that adds the pages.
3. Owner steps: DNS `docs CNAME dirnei.github.io.`, verify `heavymatica.com` under GitHub account Pages settings, set
   the repository's Pages source to GitHub Actions. Until then the workflow's deploy job fails while the build passes.
