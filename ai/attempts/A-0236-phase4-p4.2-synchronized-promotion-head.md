# A-0236 - Phase 4 P4.2 synchronized promotion head

**Date:** 2026-10-03  
**Status:** `SUCCESS / SYNCHRONIZED PROMOTION CANDIDATE CREATED; EXACT-HEAD CI REQUIRED`  
**Milestone:** Phase 4 P4.2 promotion  
**Tested dev2.2 source:** `c30c8686e65f7310ab047d88fadf2fe3b42d7415`  
**Current-main base:** `739ed7f734775cdbeafb63969b146951d8897a56`  
**Source-bearing synchronized commit:** `e88dc5db19cc852069993989b13bbe80ff939d26`  
**Promotion branch:** `promotion/p4.2-synchronized`  
**Promotion PR:** #77

## Objective

Promote the exact P4.2 implementation that closed A-0224/A-0232 without force-merging the stale/diverged PR #75 continuity base over newer `main`.

## Runtime precondition

A-0235 closes the final frozen runtime gate on the exact public dev2.2 package:

- render distance 32;
- 3,725 live columns;
- 35,273 live sections;
- 115 structural audits, zero failures;
- 3,650 completed coarse samples;
- zero missing coarse identities;
- zero duplicate coarse identities;
- `gpuColumnFalseCullCount=0`;
- clean P4.1 fine oracle;
- clean P3.10/P3.5/P3.7/worker/lifetime evidence;
- normal exit code 0.

A-0234 already closed the recenter-liveness defect and recorded the explicit human Visual PASS.

## Three-way synchronization audit

Compared across:

- P4.1 promotion base `59d130e17000b07e6ed8bbe226cb2f50eb795c74`;
- current `main` `739ed7f734775cdbeafb63969b146951d8897a56`;
- exact tested dev2.2 source `c30c8686e65f7310ab047d88fadf2fe3b42d7415`.

Every pre-existing P4.2-owned renderer/version path on current `main` is byte-identical to the P4.1 base. The three new P4.2 classes do not exist on either base/main. Therefore no renderer semantic conflict resolution was required.

Exact tested blobs copied onto current main:

- `gradle.properties` -> `4f5343056083e8c9f96bfc7780a1c00d85f5507e`;
- `src/client/java/dev/obsidian/bootstrap/ObsidianBootstrap.java` -> `0e2b0399b53f438bc37f07c7c8422e2a4c8bd035`;
- `src/client/java/dev/obsidian/render/terrain/AsyncMultiSectionSceneProbe.java` -> `5893425a0b8d08ce4182a42b6a2c74ab165dc255`;
- `src/client/java/dev/obsidian/render/visibility/LargeSceneColumnHierarchyProbe.java` -> `e66fa911285e9222a5d1d5adccd3b4f83df84bb6`;
- `src/client/java/dev/obsidian/render/visibility/LargeSceneVisibilityProbe.java` -> `a4feacd65c88913a9820226e0371f9730e77b967`;
- `src/client/java/dev/obsidian/render/visibility/PersistentColumnHierarchy.java` -> `e6f5fa035b17c22ad170ae959810dcbd5c6e9fbe`;
- `src/client/java/dev/obsidian/render/vulkan/VulkanLargeSceneColumnVisibilityProbe.java` -> `8b7ee518f15180ad2bee37aae052905e1059403a`.

The immutable P4.2 attempt chain A-0223 through A-0235 (excluding main-owned A-0227, already present) was also carried forward.

## Governance preservation

The branch starts from current `main`, so the newer A-0227 Actions wait guard and associated operating/context-governor changes remain present.

No credential, release secret, package binary, renderer semantic rewrite or new Phase 4 scope was introduced during synchronization.

## PR transition

- replacement non-draft promotion PR #77 opened from `promotion/p4.2-synchronized` to `main`;
- obsolete draft PR #75 closed as superseded after #77 existed;
- #75 is not a merge vehicle.

## Promotion gates

Before merge:

- final PR #77 head must pass hosted Build;
- final PR #77 head must pass Context Governor;
- Repository Hygiene must pass if triggered/required;
- exact implementation blob audit must remain unchanged;
- P4.2 remains shadow-only;
- P3.10 remains production terrain owner;
- P4.1 remains fine section-level shadow control;
- command compaction, temporal visibility, Hi-Z, native graphics expansion and indirect-count consumption remain out of scope.

## Next action

Synchronize authoritative continuity on PR #77, freeze the resulting exact final head, validate it with hosted CI, then merge if green.
