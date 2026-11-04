---
title: Local development
description: Run the cloud, the websites and a dev client on your machine.
---

1. Start the cloud: `aspire run` (or `dotnet run --project Heavymatica.Cloud.AppHost`) from `src/cloud`. MongoDB,
   MinIO, the service and the websites' dev servers start together; the service listens on `http://localhost:5180`.
2. Point the dev client at the cloud's DevMojang, since dev clients have no Microsoft session: in
   `src/mod/heavymatica/neoforge/runs/client/config/heavymatica-client.toml` (or `fabric/runs/...`) set
   `devSessionJoinUrl = "http://localhost:5180/dev/mojang"`.
3. Start the client: `./gradlew :heavymatica:neoforge:runClient` or `./gradlew :heavymatica:fabric:runClient` from
   `src/mod`, then open the library with <kbd>H</kbd> in a world. Each loader has its own run folder,
   `heavymatica/<loader>/runs`.

Dev client runs always talk to `http://localhost:5180`, whatever the config file says: released mods default to
`https://api.heavymatica.com`, and the dev run overrides it so it never reaches production by accident. Set
`HEAVYMATICA_CLOUD_URL` to use another local address. The game asks the cloud whether it is open only when the library
opens; the local cloud always says open. To test against a cloud that says closed, set `access = "ON"` in the `[cloud]`
section of the client config.

DevMojang (`/dev/mojang`) stands in for Mojang's session server and accepts every join. Its counterpart for the
website, `/dev/web/sign-in?minecraft=<profile id>&returnTo=/library/`, signs a browser in to the account of a Minecraft
identity without GitHub, for example `http://localhost:5180/dev/web/sign-in?minecraft=<uuid>` for the player of
`HEAVYMATICA_DEV_USER`. Open the website afterwards on the cloud's origin or the website's dev server; browsers send
localhost cookies to every port. Both exist only when the service runs in the Development environment.

The websites' local setup, including GitHub sign-in, is on [Websites](/develop/websites/). The dev client's scripting
and other aids are on [Development aids](/develop/dev-aids/).
