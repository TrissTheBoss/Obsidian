# A-0205 - Phase 4 P4.1 dev1 hosted-CI package and runtime handoff

**Date:** 2026-09-02  
**Status:** `SUCCESS` / **REFERENCE RUNTIME REQUIRED**  
**Milestone:** Phase 4 P4.1 shadow large-scene visibility  
**Branch:** `phase4/p4.1-persistent-scene-visibility`  
**Draft PR:** #57

## Objective

Package the first testable Phase 4 P4.1 canary after the A-0203 contract freeze and A-0204 exact Minecraft 26.2 seam inspection, without changing the promoted P3.10 production terrain draw ownership.

This handoff authorizes reference-machine runtime validation only. It does not promote P4.1 and PR #57 must remain draft until the frozen runtime, scale, correctness, lifetime and human visual gates pass.

## Exact source/package authority

Version:

`0.4.0-phase4-dev1`

Exact renderer/source package authority:

`fd58b9f2e915462f665b7d85f5d993456d5f930e`

The preceding fully integrated source head `8c63c478691605dddc577b572b461e83a1384a8c` passed hosted Build #756. The only subsequent source-tree change before the canonical dev1 package was the `gradle.properties` version change to `0.4.0-phase4-dev1`.

## Hosted CI

Canonical package workflow:

- workflow: `Build`;
- run ID: `33653778087`;
- run number: **#757**;
- branch source head: `fd58b9f2e915462f665b7d85f5d993456d5f930e`;
- Java: **25**;
- Gradle: **9.5.1**;
- Gradle build step: **SUCCESS**;
- artifact upload step: **SUCCESS**;
- workflow conclusion: **SUCCESS**.

Because this is a pull-request workflow, GitHub built synthetic merge commit:

`c6fa00d824beec44d5010103c38478306d2c0d43`

The synthetic merge commit and exact branch package head have the same tree SHA:

`ab82fd3908d174df668754c80ddec633da3bfb00`

Therefore the built artifact's source tree is exactly the `fd58b9f2e915462f665b7d85f5d993456d5f930e` package tree; the synthetic commit differs only in commit parentage.

## Canonical hosted artifact

GitHub Actions artifact:

- artifact ID: `9855845429`;
- artifact name: `obsidian-c6fa00d824beec44d5010103c38478306d2c0d43`;
- wrapper ZIP size: `718,756` bytes;
- wrapper digest: `sha256:b480c700f6b2b88ab1b0aa57136b43d55f9bd1d6d6fb99295f8abfbfc4f2ef9b`.

Canonical direct runtime JAR extracted from that hosted artifact:

- file: `Obsidian-0.4.0-phase4-dev1.jar`;
- size: **493,377 bytes**;
- SHA-256: **`39c4bb4932bd6e7c00a4190c3514ef29eb926c337bba488f9a04bbef27120458`**.

Sources JAR from the same artifact:

- file: `Obsidian-0.4.0-phase4-dev1-sources.jar`;
- size: `255,354` bytes;
- SHA-256: `55b9a7ce230db01b74c38d58023f91739ec74a0262344b4d6b40eaab4c17e03d`.

The direct versioned runtime JAR, not the Actions ZIP wrapper, is the user handoff artifact.

## What dev1 does

P4.1 remains shadow-only. P3.10 dev24.2 remains the sole production SOLID/CUTOUT terrain replacement owner.

Dev1 adds, beside that renderer:

- a bounded primitive persistent non-empty-section scene database;
- exact Minecraft 26.2 chunk-load/unload and section empty/non-empty lifecycle observation;
- a hard scene ceiling of `2,500,000` section slots with explicit conservative disable/failure behavior;
- bounded initial/full resync (`128` chunk columns per frame);
- bounded changed-scene candidate snapshot construction (`16,384` slots per frame);
- camera-relative section AABB transport using integer section coordinates;
- scalable native Vulkan compute frustum classification in workgroups of `128`;
- transfer reset -> compute and compute -> transfer/readback Synchronization2 dependencies;
- atomic visible-identity front compaction plus GPU visible count;
- asynchronous completion-gated readback with zero-timeout normal polling;
- independent conservative CPU oracle work bounded to `8,192` scene slots per frame;
- sampled exact identity-set comparison with missing/unexpected/duplicate/false-cull failure counters;
- explicit `cameraOnlyFullSceneScan=false`, `productionDrawOwnershipChanged=false` and `nativeGraphicsExpansion=false` evidence.

A P4.1 shadow validation failure disables/flags the shadow experiment rather than granting it production terrain ownership. The promoted P3.10 renderer remains the visual authority.

## Required first reference runtime

Exercise the exact dev1 JAR on the reference Vulkan machine with:

1. world entry and settled large-scene population;
2. a stable camera after population;
3. rapid full camera turns;
4. horizontal traversal far enough to cause chunk load/unload churn;
5. vertical movement across section boundaries;
6. ordinary block break/place while P3.10 remains active;
7. F3+T resource reload and recovery;
8. world leave/re-entry or replacement if practical;
9. normal process exit.

Human visual expectation is strict: because P4.1 is shadow-only, terrain should remain visually indistinguishable from the promoted P3.10 baseline. Any new visible terrain difference is a regression.

## Runtime evidence required for promotion

The log must establish, at minimum:

- `Obsidian 0.4.0-phase4-dev1` startup identity;
- `P4.1 shadow large-scene visibility configured` with explicit bounded capacity/memory;
- bounded scene resync completion;
- useful real-world-scale live/candidate counts;
- one or more `P4.1 shadow visibility sample PASS` records;
- zero missing visible identities;
- zero unexpected visible identities;
- zero duplicate visible identities;
- `gpuFalseCullCount=0`;
- no scene-capacity failure;
- nonblocking readback/lifetime behavior;
- `cameraOnlyFullSceneScan=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- inherited P3.10/P3.7 worker/lifetime correctness still clean;
- normal process exit;
- explicit human visual PASS.

No runtime result may be used to weaken A-0203's oracle, ambiguity, capacity or inherited P3.10 gates.

## Result

The first P4.1 runtime canary is package-ready from real hosted Minecraft/Fabric CI. Reference runtime evidence is now the handoff point. PR #57 remains **DRAFT / DO NOT MERGE**.