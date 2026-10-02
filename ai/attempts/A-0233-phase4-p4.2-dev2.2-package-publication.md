# A-0233 - P4.2 dev2.2 package + Preview publication

**Date:** 2026-09-29  
**Status:** `SUCCESS / DEV2.2 PUBLISHED`  
**Version:** `0.4.0-phase4-dev2.2`  
**Draft PR:** #75

## Objective

Record exact hosted package authority and end-to-end GitHub Preview + Modrinth publication for the A-0232 recenter-liveness correction.

## Exact validated source

`c30c8686e65f7310ab047d88fadf2fe3b42d7415`

Hosted validation:

- Build run `36634634353` / **#833** — SUCCESS;
- Context Governor run `36634634108` / **#57** — SUCCESS.

## Hosted artifact authority

Build #833 artifact:

- artifact ID: `11063553493`;
- wrapper: `obsidian-ff24c906b25b90f312f0e3cb4ab19c1cd60682c2`;
- wrapper size: **758,653 bytes**;
- wrapper digest: `sha256:526e27863cca6fa63c70d7bb7125f312d0ac4d159cb25c008ca06f335c22da8e`.

Runtime JAR:

- `Obsidian-0.4.0-phase4-dev2.2.jar`;
- size: **519,994 bytes**;
- SHA-256: **`571da7ea710d7e5376488b98b5e19e1edf005dd924a87b1db661948c79753d37`**.

Sources JAR:

- `Obsidian-0.4.0-phase4-dev2.2-sources.jar`;
- size: **269,659 bytes**;
- SHA-256: **`7c105cc9715232d0abd7b370cf615436d5427073740f7d24d63dfa298f8b7213`**.

## GitHub Preview

Publish Preview run `36635510377` / **#14** completed **SUCCESS**.

Published release:

- release ID: `399552665`;
- tag: `v0.4.0-phase4-dev2.2`;
- name: `Obsidian 0.4.0-phase4-dev2.2 - Preview`;
- prerelease: true;
- exact target: `c30c8686e65f7310ab047d88fadf2fe3b42d7415`;
- published runtime JAR: **519,994 bytes**;
- published runtime digest: **`sha256:571da7ea710d7e5376488b98b5e19e1edf005dd924a87b1db661948c79753d37`**.

The published runtime is byte-identical to Build #833 package authority.

## Modrinth mirror

Downstream job `109635372697` completed **SUCCESS**.

The pinned `cloudnode-pro/modrinth-publish` action reported:

`Successfully uploaded version on Modrinth.`

Returned Modrinth version ID:

`yDBeoQge`

Published metadata remains Fabric / Minecraft 26.2 / client-only / alpha.

## Interpretation

The corrected dev2.2 binary is now immutable and publicly available from the canonical GitHub Preview channel, with a successful Modrinth mirror.

This publication does not promote P4.2. Runtime promotion gates remain authoritative.

## Security

No PAT, project-ID secret, authorization header, cookie or other credential is stored here. GitHub Actions remains the only runtime secret boundary.

## Next action

Evaluate the exact dev2.2 Preview under A-0232 and A-0224. If the liveness defect is closed but the corrected package has not produced its own 32+ scale sample, preserve that remaining gate rather than inheriting it silently from dev2.1.
