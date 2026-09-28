# A-0216 - Live Modrinth retry reached API but read preflight returned 404

**Date:** 2026-09-28  
**Status:** `FAILED / READ-PREFLIGHT TOO STRICT; CORRECTION STAGED`  
**Trigger:** second authorized `/publish-preview` on PR #57  
**Workflow run:** `36433438631` / Publish Preview **#4**  
**Modrinth job:** `108965539055`

## Objective

Retry the existing `v0.4.0-phase4-dev1` Modrinth backfill after A-0215 added secret-backed `MODRINTH_PROJECT_ID` support.

## Proven progress

The Preview flow rebuilt and handled the exact source `6af5174f054b272c65174fbf0c37d981a66aff31` successfully.

Unlike A-0214, the downstream Modrinth job **ran**.

Inside the Modrinth job:

- pinned publisher image/action preparation — SUCCESS;
- secure configuration resolution — SUCCESS;
- secret-backed project ID resolution — SUCCESS;
- PAT presence validation — SUCCESS;
- exact P4.1 checkout — SUCCESS;
- Java/Gradle setup — SUCCESS;
- Modrinth metadata resolution — SUCCESS;
- exact source build — SUCCESS;
- publishable runtime/sources JAR validation — SUCCESS;
- existing-version lookup — **FAILED HTTP 404**;
- create-version action — SKIPPED because the preflight failed.

No Modrinth version was created.

## Evidence

The masked hosted log shows both:

- `SECRET_PROJECT_ID: ***`;
- `MODRINTH_TOKEN: ***`;

and then:

`Modrinth project/version lookup failed with HTTP 404.`

No secret values are present in the log or repository evidence.

## Interpretation

The Modrinth v2 endpoint used by the preflight is the documented `GET /project/{id|slug}/version` route.

Modrinth documents that private/draft resources may be hidden from read paths and authentication/authorization can affect visibility. The Marketplace publisher only requires version-create permission for its actual write operation.

Therefore the read preflight must not prevent the create-version path solely because the list-versions request returns 404.

A 404 may also mean an invalid configured identifier; in that case the subsequent create-version call will fail safely without creating state.

## Correction

The reusable publisher is changed to:

1. normalize a configured full Modrinth project URL to its final ID/slug path component;
2. trim query/fragment/trailing slash;
3. fail closed if the project-ID field appears to contain a PAT rather than an ID/slug;
4. preserve the existing duplicate check when list-versions returns 200;
5. treat list-versions HTTP 404 as `exists=false` and continue to the pinned create-version action;
6. continue failing for other unexpected lookup HTTP statuses.

This preserves idempotence whenever Modrinth permits the version-list read while allowing the actual VERSION_CREATE path to decide authorization for hidden/draft projects.

## Security

No secret values are read into durable context or printed. Classification/normalization occurs only inside GitHub Actions runtime.

## Renderer/runtime impact

None.

## Next action

Validate the 404-fallback workflow through hosted CI, merge `[no-release]`, and rerun `/publish-preview` on PR #57. If the create-version action then reports the project identifier itself is invalid, the maintainer must correct `MODRINTH_PROJECT_ID`; otherwise the first Modrinth version should be created.
