# A-0206 - Repository hygiene, README and release-channel governance overhaul

**Date:** 2026-09-28  
**Status:** `PARTIAL` / **IMPLEMENTED ON MAINTENANCE BRANCH; CI, MERGE AND FIRST AUTOMATED SWEEP PENDING**  
**Branch:** `maintenance/repository-hygiene`

## Objective

Restore a clean, understandable GitHub public surface and add durable rules/automation so branch clutter, stale README status and hard-to-find tester binaries do not recur.

## Pre-change audit

- repository branches: **56**;
- pull requests: **57 total**, only **#57** open;
- most surviving branches were merged milestone heads, closed experiments, API-inspection branches, diagnostics or old status-sync branches;
- root README still said Phase 3 even though Phase 3 P3.10 had already merged and Phase 4 P4.1 dev1 was packaged;
- `main` continuity had not yet received A-0202 through A-0205, so the default-branch current-state/roadmap view lagged the active engineering branch;
- previous release automation published development-looking versions as normal releases rather than clearly separating test builds from validated checkpoints.

## Research / inspiration

Reviewed GitHub's branch/release guidance and several mature open-source repository landing/contributor patterns. Useful principles retained:

- GitHub recommends deleting merged/stale branches and supports automatic deletion of merged PR heads;
- GitHub prereleases provide a native separation between development/test builds and normal releases; prereleases do not replace the normal latest-release channel;
- strong project READMEs such as ripgrep, Starship and Zellij lead with a concise identity, install/get-started path, clear documentation links and explicit development-build warnings rather than embedding internal engineering history;
- contributor/process detail belongs in dedicated documentation while the README remains the public landing page.

## Implemented policy

Created `ai/REPOSITORY_HYGIENE.md` and made it part of the mandatory AI continuity read order. Core rules:

- `main` is the only permanent branch;
- branches are disposable work state, not archives;
- active work expected to live beyond a session should have an open PR;
- closed/merged internal PR branches are deleted after evidence is preserved;
- temporary inspection branches are short-lived and removed with their temporary tooling;
- normal branch-count target is `<=5`; exceptions must be documented;
- root README is a maintained public surface with explicit update triggers;
- releases use Stable checkpoint / Preview prerelease / CI artifact channels;
- each milestone reaching real-machine testing should publish at least one discoverable Preview prerelease;
- release tags are immutable package identities and changed tester binaries require version bumps.

## Implemented automation / public surface

- added `.github/workflows/repository-hygiene.yml` to delete closed internal PR heads and perform a guarded legacy stale-branch sweep while preserving `main`, protected branches and open PR heads;
- added `.github/workflows/publish-preview.yml` to build and publish prereleases from a confirmed workflow dispatch or the `publish-preview` label on an internal PR;
- changed `.github/workflows/build.yml` so development version markers (`dev`, `alpha`, `beta`, `rc`, `preview`) publish as GitHub prereleases rather than normal releases;
- rebuilt the root README with compact Obsidian ASCII identity, badges, maturity warning, Phase 4 status, compatibility table, build channels, test/install instructions, architecture, roadmap and contributor links;
- added `docs/RELEASE_CHANNELS.md` and `CONTRIBUTING.md`;
- integrated the hygiene policy into `ai/README.md`, `ai/OPERATING_MANUAL.md`, `AGENTS.md`, `ai/DECISIONS.md` (D-0028), `ai/MASTER_ROADMAP.md` and `ai/CURRENT_STATE.md`;
- synchronized immutable A-0202 through A-0205 continuity records onto the maintenance branch so `main` can describe current Phase 4 truth without depending on hidden branch-only history.

## Result so far

The governance/documentation/automation implementation is complete on the maintenance branch, but this attempt is intentionally not marked final success until:

1. the maintenance PR passes hosted CI;
2. it merges to `main` with `[no-release]`;
3. the new hygiene workflow performs the first real branch cleanup and the surviving branch count is verified;
4. the current P4.1 tester binary is made discoverable through the Preview prerelease channel or an explicit reason for deferral is recorded.

## Safety

No renderer source or canonical P4.1 runtime package was changed by this maintenance work. P4.1 dev1 source/package authority remains `fd58b9f2e915462f665b7d85f5d993456d5f930e` and its reference runtime test remains pending.

## Next action

Open the maintenance PR, validate hosted CI, merge `[no-release]`, verify automatic branch cleanup, then record the final repository-health evidence in a new immutable attempt.
