# A-0213 - Idempotent Modrinth backfill for existing GitHub Preview

**Date:** 2026-09-28  
**Status:** `SUCCESS / HOSTED-CI VALIDATED`  
**Validation head:** `d6cc10d790af1c3ab05a644f88ccf4862f8f9469`  
**Draft PR:** #68 `Allow idempotent Modrinth backfill`

## Objective

Allow Obsidian releases that already exist on GitHub to be mirrored to Modrinth without deleting, replacing, or recreating their immutable GitHub tags.

The immediate target is the existing Phase 4 Preview:

`v0.4.0-phase4-dev1`

which predates Modrinth publishing integration.

## Problem

A-0212 integrated the reusable Modrinth publisher and wired it into GitHub Stable/Preview publication, but the caller jobs only invoked the Modrinth mirror when the GitHub release step reported `created=true`.

For `v0.4.0-phase4-dev1`, GitHub correctly reports that the release already exists. That made the first Modrinth publication impossible through the intended Preview flow even though the reusable Modrinth publisher is already idempotent.

## Correction

The caller conditions are changed from:

- `needs.release.outputs.created == 'true'`;
- `needs.preview.outputs.release_created == 'true'`;

to successful flow checks:

- `needs.release.result == 'success'`;
- `needs.preview.result == 'success'`.

The existing `MODRINTH_PROJECT_ID != ''` gate remains required.

This means:

1. a newly created GitHub release still mirrors normally;
2. an already-existing GitHub release can be backfilled to Modrinth;
3. the reusable publisher performs its authenticated existing-version lookup;
4. if that Modrinth version already exists, it is left untouched;
5. GitHub release/tag identity remains immutable.

## Scope

This is release/distribution automation only.

It does not change:

- renderer source;
- runtime resources;
- P4.1 behavior;
- version/package identity;
- GitHub release/tag contents;
- P4.1 promotion or visual gates.

## Exact hosted validation

Validation head:

`d6cc10d790af1c3ab05a644f88ccf4862f8f9469`

Hosted checks:

- Context Governor run `36431790710` / **#15** — **SUCCESS**;
- normal Build run `36431790664` / **#785** — **SUCCESS**.

Build #785 compiled/packaged normally and completed all build/upload steps.

## Security

No Modrinth credential value is read, persisted, logged, hashed, or copied into repository content.

The existing D-0031 credential boundary remains unchanged. The live publication test after merge is expected to prove configuration only through workflow success/failure.

## Next action

Merge PR #68 `[no-release]`, then issue the existing authorized `/publish-preview` command on PR #57. Because the GitHub Preview already exists, the Preview job should leave the GitHub release untouched while the Modrinth mirror job attempts the idempotent backfill for `0.4.0-phase4-dev1`. Record the resulting live Modrinth publication evidence separately.
