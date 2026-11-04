---
title: Websites
description: The player website and the admin site, and how they run locally and in production.
---

Both websites are static Astro sites with Svelte islands. The cloud serves the built files itself, so pages and API
share one origin.

## Player website

`src/web/player`. The AppHost runs its dev server on `http://localhost:4321` and forwards `/v1` to the cloud, as in
production, where the cloud serves the built site (`dotnet publish` builds it into `wwwroot`). Set
`Heavymatica:WebsitePort` to use another port, or `Heavymatica:WithWebsite=false` to leave the website out.

Sign-in on the website needs a GitHub OAuth app; without one, the website says that sign-in is unavailable and
everything else works.

1. GitHub → Settings → Developer settings → OAuth Apps → New OAuth App.
2. Homepage URL `http://localhost:4321`, authorization callback URL
   `http://localhost:4321/v1/web/sign-in/github/callback`. GitHub matches host and port exactly, so the app works only
   through the dev server; a different website port needs these URLs changed too.
3. Generate a client secret.
4. Store both in the AppHost's user secrets, from `src/cloud`:
   ```
   dotnet user-secrets set Parameters:github-client-id <client id> --project Heavymatica.Cloud.AppHost
   dotnet user-secrets set Parameters:github-client-secret <client secret> --project Heavymatica.Cloud.AppHost
   ```
   Environment variables `Parameters__github-client-id` and `Parameters__github-client-secret` work as well.
5. Restart the AppHost and open `http://localhost:4321`.

The API types come from the contract: `npm run generate` writes `src/api/schema.d.ts` with `openapi-typescript`, and
`dev`, `build` and `check` run it first.

## Admin site

`src/web/admin`. The AppHost runs its dev server on `http://localhost:4322` and forwards `/admin/v1` to the admin
listener on port 5181. See [Cloud](/develop/cloud/) for the admin listener and the first admin's setup code.

## Commands

In either folder: `npm run dev`, `npm run build`, `npm run check` and `npm test`.
