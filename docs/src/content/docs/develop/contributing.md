---
title: Contributing
description: How changes to Heavymatica are made, from an issue to a merged commit.
---

Heavymatica is developed in the open at [github.com/Dirnei/heavymatica](https://github.com/Dirnei/heavymatica). Bug
reports and ideas go into [issues](https://github.com/Dirnei/heavymatica/issues). What is built and what comes next is
in [ROADMAP.md](https://github.com/Dirnei/heavymatica/blob/main/ROADMAP.md).

## How a change is made

Features and behaviour changes are planned with [OpenSpec](https://github.com/Fission-AI/OpenSpec) before code is
written:

- `openspec/specs/` describes what the mod, the cloud and the websites do today, one capability per folder.
- `openspec/changes/<name>/` holds a change in progress: a proposal (why), spec deltas (what changes), a design (how)
  and tasks. A finished change is archived and its deltas are merged into `openspec/specs/`.

Small fixes don't need a change.

## Commits

- [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): subject`, for example
  `feat(placement): add remote overlay` or `fix(library): keep selection after refresh`. Types: `feat`, `fix`,
  `refactor`, `test`, `docs`, `build`, `chore`.
- A subject line only, at most 74 characters.
- Release notes are built from `feat` and `fix` commits, so their subjects are written for players.

## Code comments

- Comment only what the code can't say itself: a non-obvious constraint, a workaround, an API quirk.
- No reasoning or history in comments, and no references to OpenSpec changes or requirements. That belongs in the
  change's design.

## Before a pull request

- `./gradlew build` from `src/mod` (both jars and all mod tests).
- `dotnet test src/cloud/Heavymatica.slnx` when the cloud changed.
- `npm run check` and `npm test` in `src/web/player` when the website changed.
- `npm run build` in `docs` when the documentation changed.

See [Building](/develop/building/) and [Local development](/develop/local-development/).
