# A-0225 - Phase 4 P4.2 dev2 implementation, hosted CI and package handoff

**Date:** 2026-09-29  
**Status:** `SUCCESS` / **CI-GREEN DEV2 PACKAGE READY FOR PREVIEW + REFERENCE RUNTIME**  
**Milestone:** Phase 4 P4.2 persistent column hierarchy + coarse GPU visibility  
**Version:** `0.4.0-phase4-dev2`  
**Source-bearing head:** `bb4adfd12b48a1f3b03670d6fc4d25aed47ff61d`  
**Draft PR:** #75

## Objective

Implement the frozen A-0224 P4.2 contract without changing production terrain draw ownership, then establish exact hosted package authority for the dev2 reference runtime.

## Implemented scope

P4.2 adds a second shadow hierarchy/visibility layer above promoted P4.1:

- `PersistentColumnHierarchy`
  - bounded chunk-column slots;
  - hard ceiling 131,072 columns;
  - exact active vertical section range;
  - dynamically sized occupancy bitset words per column;
  - stable identities and bounded free-slot reuse;
  - exact live-section count and min/max live section Y;
  - explicit membership/capacity/mutation accounting;
  - exact per-column occupancy audit;
  - no camera-frame world polling.
- `LargeSceneColumnHierarchyProbe`
  - structural audit against the promoted P4.1 section database;
  - budgeted section->column membership verification;
  - independent Minecraft `Frustum.isVisible(AABB)` CPU oracle;
  - conservative boundary ambiguity accounting;
  - async zero-timeout GPU readback;
  - coarse visible-column ratio and estimated fine-candidate upper-bound telemetry;
  - explicit `cameraOnlyFullHierarchyScan=false`;
  - explicit production/native-graphics/command-compaction/temporal/Hi-Z non-ownership markers.
- `VulkanLargeSceneColumnVisibilityProbe`
  - camera-relative column aggregate AABBs;
  - workgroup size 128;
  - transfer->compute and compute->transfer Synchronization2 dependencies;
  - atomic visible-column count + compacted stable column identities;
  - no graphics consumption.
- P4.1 lifecycle integration
  - section add/remove/chunk unload/world clear update both the section scene and column hierarchy from the same exact event/resync path;
  - cross-database disagreement records a P4.2 hierarchy mutation failure rather than silently passing;
  - P4.1 remains the fine section-level control.
- runtime/package identity updated to `0.4.0-phase4-dev2`.

P3.10 remains the only production SOLID/CUTOUT terrain draw owner.

## Hosted CI

Exact source-bearing head:

`bb4adfd12b48a1f3b03670d6fc4d25aed47ff61d`

Hosted checks:

- Build run `36606137730` / **#821** — SUCCESS;
- job `Java 25 / Gradle 9.5.1` / `109535514540` — SUCCESS;
- Context Governor run `36606137407` / **#45** — SUCCESS;
- release and Modrinth jobs correctly skipped for the ordinary PR build.

Earlier integrated source head `de4c06fac26b0a486139c488c40268a6452accdd` also passed Build #819 before the final dev2 version/bootstrap identity commits.

## Hosted artifact authority

Build #821 artifact:

- artifact ID: `11050872212`;
- wrapper: `obsidian-34750749b7bbee5c38ba1ed0bc44285058163d36`;
- wrapper size: `758,297` bytes;
- wrapper digest: `sha256:95fbb5b3b60e72297ebed2e45cc21c9c13d0e80a2dc61a9f4be1c1a8d9b9680c`.

Direct runtime JAR extracted from the hosted artifact:

- `Obsidian-0.4.0-phase4-dev2.jar`;
- size: **519,756 bytes**;
- SHA-256: **`ce699db92c05afb3d056c7fb30c5fede0d7fc066a75200129e3403fe948c9a3f`**.

Sources JAR:

- `Obsidian-0.4.0-phase4-dev2-sources.jar`;
- size: **269,555 bytes**;
- SHA-256: **`1c4e6b96410fa6838c1b6f405d2deeea6681351abba2f1da603db9fd5397130c`**.

Package-content sanity check confirms:

- `fabric.mod.json` version is exactly `0.4.0-phase4-dev2`;
- runtime contains `PersistentColumnHierarchy.class`;
- runtime contains `LargeSceneColumnHierarchyProbe.class`;
- runtime contains `VulkanLargeSceneColumnVisibilityProbe.class`.

## Intended effect

Create a CI-proven tester package that measures whether a conservative persistent column hierarchy can reduce fine-section candidate scale while preserving every P4.1/P3.10 correctness and ownership boundary.

## Actual effect

The complete dev2 implementation compiles/packages against exact Minecraft 26.2 / Fabric / Java 25 hosted dependencies with the frozen P4.2 scope intact.

No runtime correctness or performance claim is made yet. P4.2 remains **REFERENCE RUNTIME REQUIRED**.

## Reference runtime requirements

Publish the CI-green dev2 as a GitHub Preview, then run the frozen A-0224 exercise on the reference machine:

1. enter a world and allow P4.1 + P4.2 resync/audit to settle;
2. use render distance **32 or higher when practical** for the main scale sample;
3. obtain repeated P4.1 and P4.2 PASS samples;
4. rapid 360-degree turns;
5. horizontal load/unload churn;
6. vertical section-boundary traversal;
7. ordinary block edits;
8. F3+T and recovery;
9. leave/re-enter if practical;
10. normal exit;
11. explicit human visual PASS/FAIL.

Required P4.2 evidence includes zero hierarchy-audit failures, zero missing/duplicate coarse identities, `gpuColumnFalseCullCount=0`, bounded capacity/memory, no camera-only full hierarchy scan, clean P4.1 fine oracle, clean inherited P3.10/P3.7/worker/lifetime evidence and no production/native graphics expansion.

## Next action

Publish `0.4.0-phase4-dev2` as a Preview from the current PR #75 continuity head, verify GitHub/Modrinth publication, then hand that exact Preview JAR to the tester for the frozen reference runtime. Keep PR #75 draft.
