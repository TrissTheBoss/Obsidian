# A-0222 - Phase 4 P4.1 synchronized promotion head

**Date:** 2026-09-29  
**Status:** `SUCCESS` / **SYNCHRONIZED IMPLEMENTATION HEAD CREATED; HOSTED CI REQUIRED**  
**Milestone:** Phase 4 P4.1 promotion  
**Tested Preview source:** `6af5174f054b272c65174fbf0c37d981a66aff31`  
**Current-main base:** `9ff415325c956c8f9bb678eda95343afdb209c66`  
**Source-bearing synchronized commit:** `3fb81bf6053cebbed7e6e4b058635d3653ddb6c3`  
**Branch:** `promotion/p4.1-synchronized`

## Objective

Promote the exact P4.1 implementation that passed A-0220/A-0221 without merging the stale/diverged continuity state from the original development branch over newer `main`.

## Precondition

A-0221 closed the final human visual gate. All frozen A-0203 runtime and visual gates are passed.

The original P4.1 development branch had diverged from `main` because repository hygiene, Context Governor, timeline and Modrinth maintenance landed while runtime validation was in progress. PR #57 therefore became an unsuitable direct merge vehicle even though its renderer implementation was validated.

## Three-way synchronization audit

The exact P4.1-owned implementation/version paths were compared across:

- synchronized Phase 3 base `01547b55f68690a5d0aac8405fc0fe91cdf440f9`;
- current `main` after P4.1 runtime evidence merge;
- tested P4.1 branch.

For all 11 implementation/version paths, current `main` was unchanged from the Phase 3 base while the P4.1 branch carried the intended change. Therefore no semantic conflict resolution was required.

The following tested blobs were copied byte-for-byte onto current `main`:

- `gradle.properties` -> `001299b6455a632d3ad362081149bf3198c3122f`;
- `src/client/java/dev/obsidian/bootstrap/ObsidianBootstrap.java` -> `4ae9f54a3256c531d2b58ebc4209afb74b61bb38`;
- `src/client/java/dev/obsidian/mixin/ClientChunkCacheMixin.java` -> `c204218041a5d40cc313009229b5d05375b1011a`;
- `src/client/java/dev/obsidian/mixin/ClientLevelMixin.java` -> `5a6cf6325dceb18f942017a96506dbc1f0356ec9`;
- `src/client/java/dev/obsidian/mixin/LevelExtractorMixin.java` -> `e8b329b023dc31b9b9084e5e20583a4e8bce072a`;
- `src/client/java/dev/obsidian/mixin/ModelManagerMixin.java` -> `66c41354228c4f2676bbde777ee61734b490532f`;
- `src/client/java/dev/obsidian/render/visibility/LargeSceneLifecycleEvents.java` -> `7e26b0b45e060c547141ee9ebdd0f0b03f170993`;
- `src/client/java/dev/obsidian/render/visibility/LargeSceneVisibilityProbe.java` -> `7cbb5d74ef2c2ed11ae8d78e1236c6b77f2a4b82`;
- `src/client/java/dev/obsidian/render/visibility/PersistentSectionScene.java` -> `354f10d83d758318930b2cdede8c50e2571d5c0f`;
- `src/client/java/dev/obsidian/render/vulkan/VulkanLargeSceneVisibilityProbe.java` -> `6d479e18baa2de33f7dee46aab5bf6f61d352a2b`;
- `src/main/resources/obsidian.mixins.json` -> `247a4ca8a0766d89a6d29509a110acd4fa845e11`.

No renderer/resource/version line was manually rewritten during synchronization.

## Intended effect

Create a clean promotion candidate that contains current repository governance/maintenance state plus the exact tested P4.1 implementation.

## Actual effect

Source-bearing synchronized commit `3fb81bf6053cebbed7e6e4b058635d3653ddb6c3` was created directly on current `main`.

The original P4.1 branch remains historical working state only. Promotion should occur through this synchronized branch after exact-head hosted CI.

## Promotion gates

Before merge:

- hosted Build must pass on the final promotion head;
- Context Governor must pass;
- Repository Hygiene must pass if triggered/required;
- compare must show no unintended implementation drift;
- P4.1 remains shadow-only; P3.10 remains the production terrain draw owner.

## Next action

Open a non-draft promotion PR from `promotion/p4.1-synchronized` to `main`, close PR #57 as superseded, validate the exact final promotion head, then merge if green.
