---
title: Cloud
description: The Heavymatica Cloud service, its slices, storage and settings.
---

The Heavymatica Cloud is one ASP.NET Core service in `src/cloud/Heavymatica.Cloud`. It is the source of truth for
accounts and schematics: the game and the player website both talk to it, and nothing streams from one client to
another.

## Slices

The code is organised in vertical slices under `Features/`:

| Slice | What it does |
|---|---|
| `Identity` | Game sign-in through Mojang's session server, access tokens, website sessions and GitHub sign-in. |
| `Account` | Accounts, linked sign-ins, quotas. |
| `Links` | Link codes that connect a game sign-in to a website account. |
| `Schematics` | Upload, listing, folders, tags, details, delete, sharing links, the blob sweeper and thumbnails. |
| `CloudStatus` | Whether the cloud is open, which the game asks before it shows the library. |
| `Web` | Serves the built player website. |
| `Admin` | The admin API and site on a separate listener. |

Uploads stream through Akka.Streams, and thumbnail rendering and the blob sweeper run as Akka.NET workers. Data lives
in MongoDB, schematic files and thumbnails in S3-compatible object storage (MinIO locally). The
[architecture page](/develop/architecture/) shows the slices as a diagram.

## Thumbnails

The cloud renders a thumbnail for every version that has none: flat-shaded isometric blocks coloured from
`Features/Schematics/Thumbnails/block-colors.json` (exported from the game by the mod's `exportBlockColors` dev step).
Website uploads are rendered within seconds; game uploads wait a grace period first, because the game attaches its
own, sharper thumbnail right after uploading, and that always replaces a rendered one. Versions stored before this
are rendered in the background, newest first. The `Thumbnails` section configures it:

| Setting | Default | Effect |
|---|---|---|
| `Thumbnails:Enabled` | `true` | `false` stops rendering; rendered thumbnails stay. |
| `Thumbnails:GracePeriod` | `00:01:00` | How long a game upload waits for its own thumbnail. |
| `Thumbnails:PollInterval` | `00:00:05` | How often an idle worker looks for due versions. |
| `Thumbnails:ClaimTime` | `00:05:00` | How long a version stays with one worker before another may retry it. |
| `Thumbnails:RenderTimeout` | `00:00:30` | How long one rendering may take; a timeout counts as a failed attempt. |
| `Thumbnails:MaxAttempts` | `3` | Failed attempts before a version is left without a thumbnail. |
| `Thumbnails:MaxDrawnBlocks` | `2000000` | Above this many blocks only every k-th block per axis is drawn. |

## Linking sign-ins

Linking sign-ins (link codes, and the first sign-in choice between creating an account and joining one) is on only
while `Accounts:LinkBaseUrl` is set to the website origin that redeems the codes; link codes point at
`<LinkBaseUrl>/link/?code=...`. The AppHost sets it to the dev server, `http://localhost:4321` (or
`Heavymatica:WebsitePort`). Without it, link codes answer `linking_unavailable`, `/v1/me` reports
`linkingAvailable: false`, an unknown Minecraft identity gets an account at once, and "Sign in with GitHub" without
an account says there is none yet.

## Admin listener

Admins work on a separate admin host: the cloud opens a second listener for the admin API (`/admin/v1`) and the admin
site, and answers there only for the admin host name. The public listener never routes to admin paths, and the admin
listener serves nothing of the player site or API. Admins have no player account; they sign in with passkeys only and
need two of them before they can do anything else.

| Setting | Effect |
|---|---|
| `Admin:Url` | The admin listener, for example `http://localhost:5181`. Without it and `Admin:Host`, nothing admin exists. |
| `Admin:Host` | The only host name the admin listener answers for, and the passkeys' relying party ID. |
| `Admin:SiteUrl` | Where admins open the site, for invitation links and the setup command; defaults to `https://<Admin:Host>/`. |
| `Admin:SiteRoot` | The built admin site the cloud serves on the admin listener (`wwwroot-admin`, filled on publish). |

The first admin registers with a setup code from the server, valid for 15 minutes and once. A new code replaces the
previous one; `--admin <id>` makes it a recovery code that adds a passkey to that admin. From `src/cloud`:

```
dotnet run --project Heavymatica.Cloud -- admin setup-code
```

It reads the same configuration as the service, so run it where the service's connection string is set (with the
AppHost running, copy `ConnectionStrings__heavymatica` from the dashboard's cloud resource). Further admins are
invited from the admin site with a link valid for 24 hours and once.
