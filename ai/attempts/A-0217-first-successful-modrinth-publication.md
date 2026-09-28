# A-0217 - First successful Modrinth publication

**Date:** 2026-09-28  
**Status:** `SUCCESS / MODRINTH MIRROR PROVEN`  
**Trigger:** authorized `/publish-preview` rerun on PR #57 after PR #70 merged  
**Workflow run:** `36434577563` / Publish Preview **#5**  
**Modrinth job:** `108969491205`

## Objective

Close the remaining first-publication obligation after A-0216 by proving that the merged 404-read fallback can reach the actual Modrinth VERSION_CREATE path and publish the already-existing GitHub Preview without changing renderer/package authority.

## Exact source and automation authority

- GitHub Preview source: `6af5174f054b272c65174fbf0c37d981a66aff31`
- GitHub Preview version: `0.4.0-phase4-dev1`
- 404-fallback merge on `main`: `87c51328ab1f1587bc544a186ee5fd55bf688977`
- correction PR: #70 `Allow Modrinth create after hidden-project 404`
- pinned publisher: `cloudnode-pro/modrinth-publish@203bc72a51ae39fba1194fc4097d9f0750c49bd3` (v2.5.1)

The publication flow checked out and rebuilt the exact Preview source rather than publishing the maintenance merge commit.

## Hosted result

Publish Preview run `36434577563` completed **SUCCESS**.

The downstream Modrinth job `108969491205` completed **SUCCESS**. Hosted evidence shows:

- secure publication configuration resolved;
- exact Preview source checkout succeeded;
- Java 25 / Gradle 9.5.1 setup succeeded;
- metadata resolution and exact-source build succeeded;
- runtime and sources JAR validation succeeded;
- the authenticated list-versions preflight returned HTTP 404;
- the workflow treated that 404 as unknown/not-listed and continued to the pinned create-version action;
- the pinned publisher reported `Successfully uploaded version on Modrinth.`;
- returned Modrinth version ID: `OhTxTs7Z`.

Published metadata:

- version number: `0.4.0-phase4-dev1`;
- Minecraft: `26.2`;
- loader: Fabric;
- environment: client only;
- Modrinth channel: `alpha`;
- source SHA: `6af5174f054b272c65174fbf0c37d981a66aff31`.

## Interpretation

The full distribution path is now proven end to end.

The earlier 404 was not sufficient evidence that the configured project identifier or PAT was invalid. Allowing the create-version path to decide write authorization was the correct behavior for this project state. Duplicate protection remains active whenever the authenticated list-versions request returns HTTP 200; unexpected statuses other than 200/404 still fail closed.

GitHub CI/releases remain package authority under D-0031. The Modrinth upload mirrors the existing Preview and does not promote P4.1 or change renderer/runtime behavior.

## Security

No PAT, project-ID secret value, cookie, authorization header, or other credential is persisted in this attempt or repository content. Hosted logs mask the secret-backed configuration values.

The Modrinth version ID `OhTxTs7Z` is publication metadata returned by the publisher and is not a credential.

## Continuity impact

The first-live-publication obligation from A-0214/A-0215/A-0216 is closed.

Repository/public documentation and `ai/context/ACTIVE_CONTEXT.json` must be synchronized so future agents do not retry an upload that already succeeded.

## Next action

Return the active engineering mission to the frozen P4.1 dev1 reference-machine runtime exercise. Keep PR #57 draft until its runtime, correctness, lifetime, scale and explicit human visual gates close.
