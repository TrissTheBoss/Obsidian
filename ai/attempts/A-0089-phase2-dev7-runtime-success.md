# A-0089 - Phase 2 dev7 reference runtime success

Status: **SUCCESS / RUNTIME + HUMAN VISUAL VALIDATED / MACHINE GATE PASSED / MERGE AUTHORIZATION NOT YET GRANTED**

Date: 2026-08-21
Branch: `phase2/multi-section-scene`
Canonical stacked PR: #27
Version: `0.2.0-phase2-dev7`
Validated package SHA-256: `59dde49b210b802fdd88e1bbc2da7a9eae9b7be045b9c760ae1adc3827599725`

## Objective

Close the P2.7 multi-section scene runtime gate on the reference Windows 11 / RX 6800 XT Vulkan system by proving simultaneous neighboring scene ownership, camera-driven scene recenter, exact edit/resource invalidation, lossless lifecycle transport, bounded GPU ownership, completion-gated retirement and clean shutdown while preserving the P2.5 deterministic rendering semantics.

## Reference runtime

- Prism Launcher 10.0.5;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- Java 25.0.1;
- Windows 11;
- AMD Radeon RX 6800 XT;
- Vulkan backend, AMD proprietary driver 26.7.1 / Vulkan 1.4.315;
- `obsidian 0.2.0-phase2-dev7`.

## Human visual result

User verdict: **"looks completley fine."**

This is the required human oracle for P2.7: no persistent duplicate/missing shared borders were observed among simultaneous neighboring overlay sections and no stale old-window geometry was observed after scene movement/rebuild behavior.

## Runtime behavior evidence

The 3x3 scene bound at center `(64,4,8)` with all `9/9` records eligible and `12` adjacent pairs. The first scene reached READY with `liveRecords=9` and `adjacentPairs=12`.

Camera movement produced real scene ownership transitions, including:

- `(64,4,8) -> (64,4,10)`;
- `(64,4,10) -> (64,4,12)`.

Old records stopped drawing before replacement ownership was admitted and completion-gated retirement reclaimed their arena/resource ownership before reuse.

Exact section dirtiness produced repeated whole-window invalidation/rebuild cycles. Successful resource reload was also observed. Every generalized capture retained the permanent deterministic cube oracle and reported `worldReadsAfterGeneralizedCapture=0`, `nativeGraphicsSeam=false`, public indexed-indirect SOLID/CUTOUT drawing, blocks atlas + lightmap bindings and the exact cutout threshold.

## Final machine gate

Shutdown reported:

- `sceneGateReady=true`;
- `hardFailure=false`;
- `sceneGeneration=30`;
- `sceneReadyTransitions=16`;
- `sceneRebuilds=15`;
- `recordInstalls=144`;
- `maxLiveRecords=9`;
- `maxAdjacentPairs=12`;
- `cameraRecenterEvents=2`;
- `invalidationBatches=27`;
- `coalescedEvents=686`;
- `dirtyEvents=709`;
- `playerDirtyEvents=342`;
- `worldChangeEvents=3`;
- `resourceReloadEvents=1`;
- `chunkLoadEvents=0`, `chunkUnloadEvents=0` (diagnostic only for P2.7 by A-0087; P2.6 separately owns the mandatory fixed-target chunk lifecycle proof);
- `droppedLifecycleEvents=0`;
- observed reasons `section-dirty|world-change|resource-reload|scene-recenter`;
- `eligibilityScans=144`, `eligibilitySkips=0`;
- `uploadAdmissionDeferrals=0`;
- `staleSceneRejections=0`;
- `probeStaleInstallRejections=0`;
- `maxSceneQuads=6251`;
- `maxSceneVertexBytes=700112`;
- `maxSceneIndexBytes=150024`;
- `retirementBackpressureEvents=0`;
- `retirementRegistrationFailures=0`;
- `wholeWindowInvalidation=true`;
- `boundedOneRecordAdmission=true`;
- `sceneRecordCapacity=9`;
- `chunkLifecycleCountersDiagnostic=true`;
- `nativeGraphicsSeam=false`;
- `indexedIndirect=true`.

GPU lifetime/reclamation at shutdown:

- staging submitted/reclaimed `11087992 / 11087992` bytes;
- `stagingBackpressureEvents=0`;
- `pendingUploadBatches=0`;
- arena used bytes `0`;
- arena high-water `850136` bytes out of the fixed 32 MiB arena;
- arena allocations `288`, allocation failures `0`;
- arena retired/reclaimed `288 / 288`;
- arena retirement backpressure `0`;
- arena stale-handle rejections `0`;
- one fully coalesced free span, largest free `33554432`, fragmentation `0`;
- `pendingArenaRetirementBatches=0`;
- deferred resources retired/released `144 / 144`;
- `pendingRetirements=0`;
- process exit code `0`.

## Result

P2.7's refined runtime gate is fully satisfied and the human visual oracle passed. The multi-section scene integration is therefore **runtime + human visual validated**.

This does not authorize merge. PR #27 remains stacked on P2.6 and must not merge before P2.6 closes its separate mandatory fixed-target chunk unload/load gate and PR #25 is merged. Dev7 also still requires explicit user merge authorization after that dependency is resolved.

This attempt is immutable once committed.
