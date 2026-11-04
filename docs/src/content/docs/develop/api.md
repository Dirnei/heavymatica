---
title: API
description: The OpenAPI contracts and the code generated from them.
---

The cloud's API is contract-first. The contracts in `src/api` are the source of truth, and code on every side is
generated from them:

| Contract | Generated into |
|---|---|
| `heavymatica-cloud.v1.yaml` (`/v1`) | the service's controller bases (NSwag), the mod's Java client (OpenAPI Generator) and the player website's types (openapi-typescript) |
| `heavymatica-admin.v1.yaml` (`/admin/v1`) | the service's admin controller bases (NSwag) and the admin site's types |

A change to the API starts in the contract. The service then fails to compile until the new or changed operations are
implemented, and the clients pick up the new types on their next build.

## Lint

`src/api/.spectral.yaml` holds the lint rules. From the repository root:

```
npx @stoplight/spectral-cli lint src/api/heavymatica-cloud.v1.yaml
```

The mod's Gradle build runs it too, so CI fails on a contract that breaks the rules.

## Problems

Errors are `application/problem+json` with a `code` from the contract's `ProblemCode` list. Clients branch on the code,
never on the message text. The conformance files use the same codes for files a reader must reject.
