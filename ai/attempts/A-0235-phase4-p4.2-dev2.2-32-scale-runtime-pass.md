# A-0235 - P4.2 dev2.2 corrected-package 32+ scale runtime PASS

**Date:** 2026-10-03  
**Status:** `SUCCESS / FINAL FROZEN RUNTIME GATE CLOSED`  
**Milestone:** Phase 4 P4.2  
**Version:** `0.4.0-phase4-dev2.2`  
**Exact published source:** `c30c8686e65f7310ab047d88fadf2fe3b42d7415`

## Objective

Close the only remaining gate from A-0234: prove that the exact corrected dev2.2 package remains clean at render distance 32+.

No source change was requested between A-0234 and this run.

## Runtime identity

The supplied Prism Launcher log identifies:

- Obsidian `0.4.0-phase4-dev2.2`;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25.0.1;
- Windows 11;
- Vulkan on AMD Radeon RX 6800 XT;
- exact view distance changed to **32**;
- normal process exit code 0.

The human Visual PASS from A-0234 remains valid; the user reported no new visual regression for this scale-only follow-up.

## P4.2 scale evidence

P4.2 configured at render distance 32 with:

- column capacity: 5,041;
- live/high-water columns: 3,725 / 3,725;
- live sections: 35,273;
- exact column membership: 35,273;
- hierarchy audit runs: 115;
- hierarchy audit failures: 0;
- capacity failures: 0;
- mutation failures: 0;
- completed coarse samples: 3,650;
- exact samples: 3,639;
- missing visible column identities: 0;
- duplicate visible column identities: 0;
- GPU column false culls: 0;
- conservative extra visible columns: 11 accumulated;
- readback pending high-water: 1;
- final visible columns: 1,252;
- final coarse-visible ratio: 336 permille;
- final estimated fine-candidate upper bound: 10,523;
- flat fine-candidate count: 35,273;
- camera-only frames: 3,650;
- `cameraOnlyFullHierarchyScan=false`;
- `hardFailure=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- command compaction / temporal visibility / Hi-Z remained disabled.

The conservative extra-visible count is safe overdraw, not a false-cull correctness failure.

## P4.1 inherited control

Final P4.1 evidence at the same render-distance-32 run:

- live/high-water sections: 35,273 / 35,273;
- capacity failures: 0;
- lifecycle overflow: 0;
- completed samples: 227;
- exact samples: 227;
- missing visible: 0;
- unexpected visible: 0;
- duplicate visible: 0;
- GPU false culls: 0;
- readback pending high-water: 1;
- `cameraOnlyFullSceneScan=false`;
- `hardFailure=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`.

## Inherited production/correctness gates

P3.10 production replacement remained coherent:

- duplicate claims: 0;
- claim overflows: 0;
- stale plan failures: 0;
- execution without claim: 0;
- execution revalidation failures: 0;
- `suppressionExecutionAccountingCoherent=true`;
- `productionCoordinatesExact=true`;
- `productionExactColor=true`;
- `sameOpaquePass=true`;
- `nativeGraphicsExpansion=false`.

P3.5/P3.7 remained exact:

- border/halo correctness evidence ready;
- all 195,072 outward visibility/reference comparisons matched;
- shared-border comparisons 80,896 / 80,896;
- differential correctness evidence ready;
- differential determinism 127 / 127;
- differential missing=0;
- differential duplicate=0;
- differential real mismatches=0;
- worker world reads after capture=0.

## Lifetime/shutdown

- worker submitted/started/completed: 127 / 127 / 127;
- worker failed jobs: 0;
- worker shutdown join failures: 0;
- workers clean;
- staging clean;
- arena clean;
- resources clean;
- pending upload batches: 0;
- pending arena retirement batches: 0;
- pending retirements: 0;
- process exit code 0.

## Promotion decision

Every frozen A-0224 promotion gate is now closed when combined with:

- A-0233 exact dev2.2 package/publication authority;
- A-0234 recenter-liveness runtime PASS and explicit human Visual PASS;
- this corrected-package render-distance-32 scale PASS.

P4.2 is therefore **eligible for promotion**.

Because PR #75 diverged from newer `main` maintenance state while runtime validation was underway, do not merge the stale branch directly. Follow the A-0222 synchronization precedent:

1. create a clean promotion branch from current `main`;
2. copy the exact tested dev2.2 renderer/version blobs byte-for-byte;
3. preserve the P4.2 attempt history and update authoritative continuity;
4. require exact synchronized-head hosted CI;
5. merge only that synchronized promotion PR;
6. close PR #75 as superseded after the replacement promotion PR exists.
