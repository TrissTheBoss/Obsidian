# A-0084 - Phase 2 dev6 corrected retest: overflow fix proven, chunk-lifecycle coverage still missing

Status: **PARTIAL RUNTIME SUCCESS / HUMAN VISUAL PASS / CORRECTED EVENT BRIDGE PROVEN / CHUNK UNLOAD+LOAD COVERAGE NOT EXERCISED**

Date: 2026-08-21
Branch tested: `phase2/section-lifecycle-rebuild`
PR: #25
Tested package: `Obsidian-0.2.0-phase2-dev6-lifecycle-retest.jar`
Tested package SHA-256: `486db6d80490170961d5a98debcb691e440e775e433283ab50b30c894821feb8`

## Objective

Retest the A-0083 target-scoped lifecycle bridge on the reference Windows 11 / RX 6800 XT Vulkan machine and prove that the original lossy 256-entry ring/overflow defect is gone while preserving edit/resource rebuild correctness and GPU lifetime behavior.

## Human result

The user reported that the corrected build looked good.

No stale-geometry, rendering, or visual-regression complaint was reported during the corrected run.

## Runtime evidence

Environment remained the canonical reference profile:

- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- Java 25.0.1;
- Vulkan backend;
- AMD Radeon RX 6800 XT;
- Obsidian `0.2.0-phase2-dev6`.

Positive corrected-bridge evidence at shutdown:

- `hardFailure=false`;
- `installCount=18`;
- `rebuildInstalls=17`;
- `dirtyEvents=878`;
- `playerDirtyEvents=432`;
- `resourceReloadEvents=2`;
- `droppedLifecycleEvents=0`;
- `observedReasons=section-dirty|world-change|resource-reload` with no overflow/safety-invalidate reason;
- `staleInstallRejections=0`;
- repeated generation installs retained deterministic P2.5 cube/generalized/drawable semantics;
- `worldReadsAfterGeneralizedCapture=0`;
- exact vanilla dirty sink, immediate stale draw suppression and completion-gated replacement remained true;
- public indexed-indirect graphics remained active with `nativeGraphicsSeam=false`;
- staging submitted/reclaimed `1,588,928 / 1,588,928` bytes;
- arena allocations retired/reclaimed `36 / 36`, used bytes `0`, one full free span and fragmentation `0`;
- deferred resources retired/released `18 / 18`, pending `0`;
- process exited with code `0`.

This proves the A-0083 event transport correction fixed the original lifecycle overflow/drop defect under the exercised edit/resource workload.

## Remaining machine-gate gap

Shutdown still reported `lifecycleGateReady=false`, solely because the run did not exercise the required tracked-neighborhood chunk lifecycle:

- `chunkLoadEvents=0`;
- `chunkUnloadEvents=0`.

The player exited after edit/resource testing rather than travelling far enough for the tracked neighborhood to unload and then returning. There is no new evidence here of a chunk-hook implementation failure; the required event class was simply not exercised.

## Result and dependency rule

P2.6 remains **not COMPLETE and unmerged** until one corrected-package reference run exercises tracked-neighborhood unload + reload/return and closes with nonzero chunk unload/load counters and `lifecycleGateReady=true`.

Forward implementation work may be stacked on the corrected dev6 head so development does not stall, but dependent work must not be merged ahead of P2.6 closure.

This attempt is immutable once committed.
