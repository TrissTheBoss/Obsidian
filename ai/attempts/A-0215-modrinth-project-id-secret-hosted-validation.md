# A-0215 - Secret-backed Modrinth project-ID support hosted validation

**Date:** 2026-09-28  
**Status:** `SUCCESS / HOSTED-CI VALIDATED`  
**Validation head:** `f1c82fbe8433c07e14d6ac7ef487bc9c8ef1b2c0`  
**Draft PR:** #69 `Support secret-backed Modrinth project ID`

## Objective

Correct the A-0214 first-live-publication configuration mismatch by allowing automatic Modrinth publication to resolve `MODRINTH_PROJECT_ID` from a repository secret as well as a repository variable or explicit workflow input.

## Change

The reusable publisher now resolves the Modrinth project ID in this order:

1. explicit/manual `project_id` input;
2. repository Actions variable `MODRINTH_PROJECT_ID`;
3. repository Actions secret `MODRINTH_PROJECT_ID`.

Automatic Stable/Preview callers no longer use a variable-only job-level gate. They invoke the reusable publisher after the corresponding GitHub release/Preview flow succeeds and pass the repository project-ID secret when present.

Variable-only skip-summary steps were removed because they would be misleading when the project ID is stored as a secret.

D-0031, Repository Hygiene, and public release-channel documentation were synchronized to permit either variable or secret storage for the non-sensitive project identifier.

## Security

No secret value is read, printed, hashed, committed, or persisted.

The workflow references only secret names through the GitHub Actions secrets context. The PAT boundary remains unchanged.

## Exact hosted validation

Validation head:

`f1c82fbe8433c07e14d6ac7ef487bc9c8ef1b2c0`

Hosted checks:

- Context Governor run `36432988805` / **#19** — **SUCCESS**;
- normal Build run `36432989214` / **#789** — **SUCCESS**.

The hosted Build accepted the reusable-workflow secret declarations and caller syntax and completed normal Java 25 / Gradle 9.5.1 build/package validation.

## Scope

Release/distribution automation and continuity only. No renderer source/runtime/package identity or P4.1 validation-gate changes.

## Next action

Merge PR #69 `[no-release]`, then rerun the exact authorized `/publish-preview` command on PR #57. Verify that the downstream Modrinth mirror job now runs instead of being skipped and record the live publication result separately.
