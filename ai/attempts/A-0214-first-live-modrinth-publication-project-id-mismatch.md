# A-0214 - First live Modrinth publication attempt skipped by project-ID storage mismatch

**Date:** 2026-09-28  
**Status:** `FAILED / CONFIGURATION-MISMATCH CORRECTED`  
**Trigger:** authorized `/publish-preview` comment on PR #57  
**Workflow run:** `36432267102` / Publish Preview **#3**

## Objective

Use the integrated Modrinth mirror to publish the already-existing GitHub Preview `v0.4.0-phase4-dev1` without replacing its immutable GitHub release.

## Trigger

After PR #68 merged the idempotent backfill caller correction as `67e5a19d92e9e4f746ee5632b95e6aef6fb12c0b`, the maintainer-authorized command:

`/publish-preview`

was posted to active draft PR #57.

The issue-comment event resolved the expected P4.1 source:

`6af5174f054b272c65174fbf0c37d981a66aff31`

## Result

Publish Preview run `36432267102` / **#3** started successfully.

The main Preview job:

- resolved the exact source — SUCCESS;
- checked out the exact source — SUCCESS;
- configured Java 25 / Gradle 9.5.1 — SUCCESS;
- validated the prerelease version — SUCCESS;
- rebuilt the exact P4.1 source — SUCCESS;
- prepared release assets — SUCCESS;
- handled the existing immutable GitHub Preview release — SUCCESS;
- completed normally.

The downstream **Mirror preview to Modrinth** job `108961448887` was **SKIPPED**.

## Root cause

The caller condition required:

`vars.MODRINTH_PROJECT_ID != ''`

The maintainer had configured the project identifier as a **repository secret**, not a repository variable.

GitHub Actions does not expose repository secrets through the `vars` context. Therefore the Modrinth job-level condition evaluated false before the reusable publisher could run.

This was not a PAT failure, Modrinth API failure, build failure, package failure, or source mismatch. No Modrinth request was made and no external Modrinth state changed.

## Correction

The publication system is being changed so:

- automatic Modrinth mirror jobs run after a successful GitHub release/Preview flow without a variable-only job gate;
- the reusable publisher resolves the project ID in this order:
  1. explicit/manual `project_id` input;
  2. repository variable `MODRINTH_PROJECT_ID`;
  3. repository secret `MODRINTH_PROJECT_ID`;
- the PAT remains available only through repository secret `MODRINTH_PAT`;
- variable-only skip-summary text is removed because it would be misleading with secret-backed configuration.

D-0031, Repository Hygiene, and public release-channel documentation are synchronized to support either variable or secret storage for the non-sensitive project ID.

## Security

No secret value was read, exposed, logged, copied, hashed, or persisted.

The failure was inferred only from workflow control flow: the Preview job succeeded and the Modrinth job was skipped by its public-variable condition.

## Renderer/runtime impact

None.

## Next action

Validate the secret-backed project-ID correction through hosted Build + Context Governor CI, merge `[no-release]`, then rerun the exact same authorized `/publish-preview` command on PR #57 and inspect the Modrinth mirror job.
