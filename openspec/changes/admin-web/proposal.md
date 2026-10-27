# Proposal

## Why

Running Heavymatica Cloud needs a way to look up accounts and change their quota without opening a database shell.
Admin access is also the riskiest part of the service, so it needs its own host, its own sign-in and an audit trail
from the start. `admin-web` is change 5 of 5 in step 5 of `ROADMAP.md`, which records the decisions it follows.

## What Changes

- **An admin host and listener.** The cloud opens a second listener for the admin site and the admin API. Admin paths
  answer only there, and only for the configured admin host name. The public listener never routes to them, and the
  admin listener serves nothing of the player site or API.
- **Admins are their own principals.** They have no player account and no Minecraft link. They sign in with passkeys
  only and have no password.
  - **First admin:** a CLI command on the server prints a one-time setup code (15 minutes, single use). Entering it on
    the admin site registers the first passkey and creates the admin.
  - **Further admins:** an existing admin creates an invitation link (single use, 24 hours).
  - **Two passkeys:** an admin with only one passkey can do nothing but register a second one.
  - **Lost access:** another admin's recovery invitation, or a setup code from the CLI, adds a passkey to an existing
    admin.
- **Admin session:** a cookie bound to the admin host, 30 minutes idle and 8 hours at most, with the same-origin and
  header check the website uses.
- **Hardening for a public admin host:** rate limits on sign-in, setup codes and invitations; short-lived,
  single-use, hashed codes and invitation tokens; an audit log of every admin action and every new or removed passkey.
- **Admin work:**
  - find accounts by account ID, Minecraft name or UUID, or GitHub login
  - see an account's identities, usage, quota, schematic count and recent account audit entries
  - change an account's quota, with a required reason
  - manage admins: list them and their passkeys, invite, remove a passkey (never the last two of an admin), remove an
    admin (never the last one, never oneself)
  - read the admin audit log
- **A second website**, `src/web/admin` (Astro and Svelte like the player site), served by the cloud on the admin
  listener. Aspire runs its dev server next to the player site's.
- **Contract:** a separate admin contract, `src/api/heavymatica-admin.v1.yaml`, so the public contract and its
  `/openapi/v1.yaml` never describe admin operations.

Out of scope:
- moving the admin host behind WireGuard and switching certificates to DNS-01 (deployment steps; the design keeps
  them possible without code changes)
- suspending or deleting accounts, editing schematics, impersonation
- roles: every admin can do everything
- moving rate limits to Mongo for several instances

## Capabilities

### New Capabilities
- `cloud/admin-access`: the admin listener and host, admin principals, passkey registration and sign-in, setup codes,
  invitations, the admin session, rate limits and the admin audit log.
- `cloud/admin-accounts`: looking up accounts and changing their quota as an admin.
- `web/admin-ui`: the admin website.

### Modified Capabilities
None. Player accounts, identity and storage behave as before; admin actions on them are recorded in the admin audit
log.

## Impact

- **Contract:** new `src/api/heavymatica-admin.v1.yaml`, linted with the same Spectral rules; NSwag generates its
  controllers into their own namespace, and the admin site generates its types from it.
- **Cloud (`src/cloud/Heavymatica.Cloud`):**
  - new `Features/Admin`: admins and passkeys on ASP.NET Core Identity's passkey support (.NET 10) with a small
    Mongo user store, setup codes, invitations, the admin cookie, the admin audit log, account lookup and quota
  - a second Kestrel endpoint and a host and port filter that splits admin and player endpoints
  - a CLI verb `admin setup-code` on the cloud executable
  - the AppHost runs the admin site's dev server and passes the admin host settings
- **Data:** new collections `admins`, `admin_passkeys`, `admin_setup_codes`, `admin_invitations` and `admin_audit`.
- **Website:** new `src/web/admin`. The player site is unchanged.
- **Mod:** unchanged.
- **Tests:** xUnit integration tests for listener separation, setup codes, invitations, the two-passkey rule, session
  limits, rate limits, audit and quota changes, using a software authenticator for WebAuthn; Vitest tests for the
  admin site's pure logic; a manual passkey check in a browser.
