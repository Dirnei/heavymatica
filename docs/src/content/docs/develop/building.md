---
title: Building and testing
description: Build and test the mod, the cloud, the websites and this site.
---

## Requirements

- JDK 25 for the mod. Gradle runs on Java 25 too (`src/mod/gradle/gradle-daemon-jvm.properties`).
- .NET SDK (version in `src/cloud/global.json`) and Docker for the cloud.
- Node 22 for the websites, the contract lint and this site.

## Mod

From `src/mod`:

```
./gradlew build
```

builds and tests both jars, `heavymatica/neoforge/build/libs` and `heavymatica/fabric/build/libs`. Builds without a
release tag report the version `0.0.0-dev`. Tests of the loader-free code run in `:heavymatica:common:test`.

## Cloud

```
dotnet build src/cloud/Heavymatica.slnx
dotnet test src/cloud/Heavymatica.slnx
```

The tests start MongoDB and MinIO in containers, so Docker has to run.

## Contract lint

```
npx @stoplight/spectral-cli lint src/api/heavymatica-cloud.v1.yaml
```

from the repository root. The Gradle build runs it as well.

## Websites

In `src/web/player` and `src/web/admin`: `npm run dev`, `npm run build`, `npm run check` and `npm test`.

## This site

In `docs`:

```
npm ci
npm run dev      # http://localhost:4323
npm run build    # fails on broken internal links
npm run check    # type check and LikeC4 model validation
```

The settings and keys pages read the mod's files, so they need the whole repository, not only `docs/`. See
[Mod projects](/develop/mod-projects/) for how they stay in sync.

## CI

Every push and pull request is built and tested by `.github/workflows/build.yml`. `.github/workflows/docs.yml` builds
this site on pull requests and publishes it on pushes to `main`.
