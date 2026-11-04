---
title: Releasing
description: How a mod release is made, and how this site is published.
---

## Mod releases

A release is a tag push, nothing else. No file is edited and nothing is committed:

```
git tag -a heavymatica-v0.3.0 -m "Optional text above the notes"
git push origin heavymatica-v0.3.0
```

- The version comes from the tag (`heavymatica-v<major>.<minor>.<patch>`, optionally with a `-suffix`). Any other form
  fails. Builds without a tag report `0.0.0-dev`.
- The notes list the `feat` and `fix` commits since the previous `heavymatica-v*` tag that touch `src/mod/heavymatica`.
  An annotated tag's message goes above them; a lightweight tag (`git tag heavymatica-v0.3.0`) adds nothing. Preview
  them with `sh .github/scripts/release-notes.sh <tag>` after tagging locally.
- The first release has no previous tag, so its notes are the tag message alone: tag it annotated.
- With no `feat` or `fix` commit and no message, the release fails before anything is uploaded.
- Changes outside `src/mod/heavymatica` that alter the jar, such as `src/mod/build.gradle`, `src/mod/gradle.properties`
  or the contract in `src/api`, are not listed. Mention them in the tag message.
- `.github/workflows/release.yml` builds and tests the tagged commit, then creates the GitHub release with the Fabric
  and the NeoForge jar and uploads both to CurseForge and Modrinth, each marked with its loader. Files are named
  `Heavymatica 0.3.0 (Fabric)` and `Heavymatica 0.3.0 (NeoForge)`. On Modrinth the version numbers carry the loader
  (`0.3.0+fabric`, `0.3.0+neoforge`), since each jar is its own version there. A failing build of either jar stops the
  whole release.
- Versions 0.x are published as alpha; from 1.0 a version containing `alpha` or `beta` is published as that, anything
  else as release.
- Repository settings: variables `CURSEFORGE_ID_HEAVYMATICA` and `MODRINTH_ID_HEAVYMATICA` with secrets
  `CURSEFORGE_TOKEN` and `MODRINTH_TOKEN`. A platform without its project ID variable is skipped.
- If publishing fails, re-run the failed job in the Actions tab. To redo a wrong tag, delete its GitHub release, then
  the tag (`git tag -d <tag>` and `git push origin :refs/tags/<tag>`), and push it again.

## Documentation

`.github/workflows/docs.yml` builds this site on every pull request and every push to `main` that changes `docs/`, the
default settings file or the language file, and publishes it to GitHub Pages from `main`. A failing build, including a
broken internal link or an invalid architecture model, publishes nothing and leaves the current site online.

The site describes `main`, while players run the latest release. Mark what is not released yet:

- a whole page: `unreleased: true` in its frontmatter shows a note under the title; add
  `sidebar: { badge: { text: 'Next release', variant: 'caution' } }` to mark it in the sidebar as well;
- a section: put `<NextRelease />` (from `src/components/NextRelease.astro`) next to its heading in an `.mdx` page.

Remove the markers when the release is out.

### One-time setup

The site is served at `https://docs.heavymatica.com`. The repository owner sets this up once:

1. At the DNS provider of `heavymatica.com`: a `CNAME` record `docs` → `dirnei.github.io.`
2. GitHub → account Settings → Pages → Add a verified domain → `heavymatica.com`, with the TXT record GitHub shows.
   This stops anyone else's repository from serving a subdomain of it.
3. Repository Settings → Pages → Source: GitHub Actions. The custom domain comes from `docs/public/CNAME`; tick
   Enforce HTTPS once the certificate is issued.
