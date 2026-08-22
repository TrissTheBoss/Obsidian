# A-0120 - Phase 3 P3.4 dev7 runtime success

**Date:** 2026-08-23  
**Branch:** `phase3/render-merge-candidate-sidecar`  
**Version:** `0.3.0-phase3-dev7`  
**Result:** `SUCCESS` — the frozen dev7 render-key-aware merge-candidate sidecar runtime gates passed on the reference Vulkan machine with clean shutdown.

## Objective

Close the real-machine runtime gate for the A-0117 dev7 contract after the integrated source/package and evidence-head CI checkpoints recorded in A-0119.

Dev7 remains sidecar-only: the existing generalized `BakedSectionMesh` is still the authoritative GPU drawable, `greedyRectangleGpuEmission=false`, and `renderCorrectMergeKeyComplete=false`.

## Runtime environment

- Windows 11 reference machine;
- Prism Launcher 10.0.5;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- Java 25.0.1;
- AMD Radeon RX 6800 XT;
- Vulkan backend;
- `Obsidian-0.3.0-phase3-dev7.jar` from canonical package head `cbb576836a304e4691e95eb395f624aefc8a2c5f`.

Canonical runtime JAR:

- size `320,735` bytes;
- SHA-256 `ef2ff6f1bc78469a9a65db486f735c178565c8982fd62aa1bb60901bf56ce1c7`.

## Final gate result

The final coordinator shutdown line reported:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- `renderMergeCandidateEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleSidecarIntegrated=true`;
- `renderMergeKeySidecarIntegrated=true`;
- `renderMergeCandidateSidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

`phase2ChunkLifecycleEvidenceReady=false` and `fixedAnchorReturnSceneReady=false` are expected and nonblocking. The fixed-target unload/return lifecycle proof was permanently closed by A-0101 and later P3.x sidecar tests do not repeat that far-travel sequence unless lifecycle semantics change.

## Worker / prior-gate evidence

Workers:

- submitted / started / completed `263 / 263 / 263`;
- cancelled `0`;
- stolen `206`;
- queue-full rejections `0`;
- failed jobs `0`;
- shutdown join failures `0`;
- HIGH/NORMAL/LOW submitted `30 / 117 / 116` and completed exactly the same;
- worker determinism `6/6`.

P3.2 visibility:

- builds `263`;
- visible faces `119,422`;
- W/E/D/U/N/S `10,643 / 16,386 / 8,170 / 49,200 / 17,514 / 17,509`;
- retained bytes `807,936 = 263 * 3,072`;
- determinism `6/6`;
- independent reference audits `6/6`.

P3.3 topology rectangles:

- builds `263`;
- rectangles `48,846`;
- covered faces `119,422` exact;
- faces saved `70,576` = `59.0%` topology reduction;
- retained bytes `195,384 = 48,846 * 4`;
- primary mask coverage `263/263`;
- determinism `6/6`;
- independent reference audits `6/6`.

Dev6 render keys:

- builds `263`;
- visible / eligible / unmapped / ambiguous faces `119,422 / 95,805 / 0 / 23,617` with exact accounting;
- recognized canonical baked quads `143,039`;
- ignored noncanonical baked quads `73,810`;
- same / different / ineligible adjacencies `12,566 / 84,264 / 17,278`;
- retained bytes `12,926,976 = 263 * 49,152`;
- determinism `6/6`.

## Dev7 merge-candidate proof

The new candidate sidecar passed its exact partition gate:

- builds `263`;
- candidate count `85,880`;
- covered eligible faces `95,805`, exactly equal to dev6 eligible faces;
- canonical passthrough faces `23,617`, exactly equal to visible minus eligible;
- singleton candidates `78,562`;
- multi-face candidates `7,318` (nonzero as required);
- faces saved by candidate merging `9,925`;
- reduction `103` permille = `10.3%` over eligible faces;
- candidate W/E/D/U/N/S counts `5,529 / 8,258 / 8,074 / 44,810 / 9,615 / 9,594`;
- candidate covered W/E/D/U/N/S faces `6,076 / 9,350 / 8,170 / 49,200 / 11,921 / 11,088`;
- retained bytes `515,280 = 85,880 * 6`;
- scratch uses `269 >= 263` builds;
- max candidate count/build `588`;
- primary exact coverage audits `263/263`;
- determinism audits `6/6`.

This proves exact no-gap/no-overlap coverage of the complete dev6-eligible canonical face set, exact canonical passthrough accounting, deterministic extraction, bounded representation, and real-terrain positive multi-face reduction.

## Scene / lifetime closure

- scene worker submitted/completed `263/263`;
- stale result discards / preinstall invalidations `20/20`;
- installs `243`;
- queue rejections `0`;
- scene READY transitions `25`;
- rebuilds `24`;
- record installs `243`;
- camera recenter events `1`;
- resource reload events `1`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- workers/staging/arena/resources clean `true/true/true/true`;
- staging submitted/reclaimed `26,797,232 / 26,797,232`;
- pending upload batches `0`;
- arena used bytes `0`;
- arena allocations/retired/reclaimed `486/486/486`;
- pending arena retirement batches `0`;
- resources retired/released `243/243`;
- pending retirements `0`.

The client reached the normal render-thread `Stopping!` path and Prism reported `Process exited with code 0`.

## Visual boundary

The supplied runtime log contains no separately written human visual verdict, so this attempt does not invent one. Dev7 did not change emitted GPU geometry: `BakedSectionMesh` remained the drawable throughout. A-0117 treats visual inspection here as a regression guard and reserves renewed explicit human visual validation as a hard requirement for the later slice that actually changes GPU geometry.

## Conclusion

All frozen dev7 compile/package/runtime correctness and lifetime gates are satisfied. The render-key-aware merge-candidate sidecar slice is complete and PR #39 is promotion-ready under the standing Phase 3 authorization.

P3.4 itself remains ACTIVE. The next slice must freeze and prove rectangle-level emission semantics — especially interpolation and atlas-UV repetition — before any greedy candidate can replace source GPU quads.