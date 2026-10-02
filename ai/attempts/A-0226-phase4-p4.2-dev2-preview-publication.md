# A-0226 - Phase 4 P4.2 dev2 Preview and Modrinth publication

**Date:** 2026-09-29  
**Status:** `SUCCESS` / **PUBLIC DEV2 PREVIEW PUBLISHED AND MIRRORED**  
**Milestone:** Phase 4 P4.2 reference-runtime handoff  
**Version:** `0.4.0-phase4-dev2`  
**Published Preview source:** `feac48607f74a665cf0040b618912e96a1f0f020`

## Objective

Publish the CI-green P4.2 dev2 package through the governed Preview channel, verify immutable GitHub assets and the downstream Modrinth mirror, then hand the exact public binary to the reference tester.

## Preconditions

A-0225 established source-bearing package authority at `bb4adfd12b48a1f3b03670d6fc4d25aed47ff61d`:

- Build #821 / run `36606137730` — SUCCESS;
- Context Governor #45 / run `36606137407` — SUCCESS;
- runtime JAR SHA-256 `ce699db92c05afb3d056c7fb30c5fede0d7fc066a75200129e3403fe948c9a3f`.

The later Preview target `feac48607f74a665cf0040b618912e96a1f0f020` differs from that source-bearing head only in:

- `ai/CURRENT_STATE.md`;
- `ai/attempts/A-0225-phase4-p4.2-dev2-ci-package-runtime-handoff.md`;
- `ai/context/ACTIVE_CONTEXT.json`.

No renderer, resource or version file changed.

The exact Preview target also passed:

- Build #824 / run `36606622961` — SUCCESS;
- Context Governor #48 / run `36606622557` — SUCCESS.

## GitHub Preview publication

The audited `/publish-preview` trigger was issued on draft PR #75.

Publish Preview:

- workflow run `36606727229` / **#12** — SUCCESS;
- exact Preview build/publish job `109537538609` — SUCCESS;
- exact resolved source: `feac48607f74a665cf0040b618912e96a1f0f020`;
- tag: `v0.4.0-phase4-dev2`;
- release ID: `399384457`;
- prerelease: true;
- draft: false;
- title: `Obsidian 0.4.0-phase4-dev2 - Preview`.

Published release assets:

- `Obsidian-0.4.0-phase4-dev2.jar`
  - size **519,756 bytes**;
  - SHA-256 **`ce699db92c05afb3d056c7fb30c5fede0d7fc066a75200129e3403fe948c9a3f`**;
- `Obsidian-0.4.0-phase4-dev2-sources.jar`
  - size **269,555 bytes**;
  - SHA-256 **`1c4e6b96410fa6838c1b6f405d2deeea6681351abba2f1da603db9fd5397130c`**;
- `SHA256SUMS`
  - size 202 bytes;
  - asset digest `sha256:a719aecf42ce6a20c19ccf42a10be04e3e045753538662e693b915429810f993`.

The published runtime/sources sizes and hashes are exactly identical to canonical Build #821 package authority. This proves the later continuity-only commits did not alter the tester binary.

## Modrinth mirror

Downstream job:

- `109537853446` — SUCCESS.

Observed publication behavior:

- exact source was checked out and rebuilt successfully;
- metadata resolved version `0.4.0-phase4-dev2`;
- authenticated list-versions lookup returned HTTP 404, matching the already-documented hidden-project read-path behavior;
- the governed workflow correctly continued to the direct create-version path;
- pinned `cloudnode-pro/modrinth-publish@203bc72a51ae39fba1194fc4097d9f0750c49bd3` reported **Successfully uploaded version on Modrinth**;
- runtime JAR remained primary and sources JAR supplementary;
- no credential value was exposed or persisted.

The Modrinth version identifier was not required for package authority and is not inferred when the compact job evidence does not expose it directly.

## Result

`SUCCESS`.

`0.4.0-phase4-dev2` is now the canonical public tester Preview on GitHub and is mirrored successfully to Modrinth.

PR #75 remains **DRAFT / DO NOT MERGE** because P4.2 still requires the frozen A-0224 reference runtime and explicit human visual verdict.

## Exact runtime artifact

Use:

`Obsidian-0.4.0-phase4-dev2.jar`

Expected SHA-256:

`ce699db92c05afb3d056c7fb30c5fede0d7fc066a75200129e3403fe948c9a3f`

## Runtime anchors

Useful log anchors:

- `Obsidian 0.4.0-phase4-dev2`;
- `P4.2 shadow column hierarchy configured`;
- `P4.2 structural hierarchy audit PASS`;
- `P4.2 coarse column visibility sample PASS`;
- `P4.2 final column hierarchy evidence`;
- inherited `P4.1 shadow visibility sample PASS`;
- inherited `P4.1 final shadow visibility evidence`.

## Next action

Run the exact public dev2 Preview under A-0224 on the reference machine. Use render distance **32 or higher when practical**, exercise camera/traversal/edit/reload/re-entry churn, exit normally, and supply the full Prism Launcher log plus explicit human visual PASS/FAIL.
