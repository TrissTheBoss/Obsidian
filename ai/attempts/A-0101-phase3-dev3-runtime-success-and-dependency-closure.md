# A-0101 - Phase 3 dev3 combined runtime success and dependency closure

**Date:** 2026-08-22  
**Branch:** `phase3/scheduler-backpressure-tuning`  
**Canonical stacked PR:** #34  
**Version:** `0.3.0-phase3-dev3`  
**Result:** `SUCCESS` - combined P3.1 dev3 runtime gate passed and the stronger downstream fixed-anchor proof closes the missing P2.6 fixed-target lifecycle evidence.

## Reference runtime

Reference machine / launcher remained the established Windows 11 / Prism Launcher 10.0.5 / Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.158.0+26.2 / Java 25.0.1 / Radeon RX 6800 XT Vulkan environment.

Tested canonical JAR:

- `Obsidian-0.3.0-phase3-dev3.jar`
- SHA-256 `182bac20d44de88705d5549ab5c1dd596aeef1aba53571ee7a121d472c3cc131`

The run exercised repeated dirty-section rebuilds, resource reload, camera/scene recentering, real chunk unload/load streaming, return to the first anchored scene neighborhood, post-return async READY state, and normal shutdown.

## Final machine gate

Final coordinator output reported:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- `renderThreadCaptureOwnership=true`;
- `renderThreadGpuOwnership=true`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`.

The process exited with code 0.

## Worker / scheduler evidence

- worker count `4`, queue capacity `64`;
- submitted / started / completed `208 / 208 / 208`;
- cancelled `0`, cancellation requests `0`;
- stolen jobs `159`;
- queue-full rejections `0`;
- failed jobs `0`;
- shutdown join failures `0`;
- maximum queue depth `1`;
- total / maximum queue wait `7,030,800 / 431,000 ns`;
- total / maximum execution time `32,969,500 / 4,222,700 ns`;
- HIGH submitted/completed `29 / 29`;
- NORMAL submitted/completed `89 / 89`;
- LOW submitted/completed `90 / 90`;
- per-priority queue wait: HIGH `1,163,800 ns`, NORMAL `3,301,200 ns`, LOW `2,565,800 ns`;
- output quads `151,898`;
- output vertex bytes `17,012,576`;
- output index bytes `3,645,552`;
- maximum single output bytes `199,104`;
- worker scratch build uses `212`;
- maximum scratch quads `1,464`;
- determinism audits / matches `4 / 4`;
- `maxAdmissionBurst=2`;
- admitted HIGH/NORMAL/LOW `29 / 89 / 90`;
- scheduler admission deferrals `0`.

This is enough to close the dev3 scheduler/scratch/output evidence contract. The low observed queue depth is not a defect: the bounded two-record admission policy and four workers kept production work responsive while still exercising every relevance tier and work stealing.

## Production async scene evidence

- scene worker submitted/completed `208 / 208`;
- scene worker installs `203`;
- record installs `203`;
- safe stale completed results `5`;
- preinstall invalidations `5`;
- scene worker queue rejections `0`;
- scene READY transitions `29`;
- scene rebuilds `28`;
- maximum live records `9`;
- maximum adjacent pairs `12`;
- camera recenter events `6`;
- invalidation batches `81`;
- dirty events `1,988`;
- player dirty events `594`;
- resource reload events `3`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- maximum scene quads `7,913`;
- maximum scene vertex bytes `886,256`;
- maximum scene index bytes `189,912`.

Safe stale/preinstall discards are expected during streaming and did not produce unsafe installs.

## P2.6 fixed-target lifecycle gap closure

A-0084's corrected standalone dev6 runtime left one evidence gap because it did not travel far enough to unload the fixed tracked neighborhood (`chunkUnloadEvents=0`, `chunkLoadEvents=0`, `lifecycleGateReady=false`).

Dev3 retained the same exact grounded Minecraft lifecycle hooks but added a diagnostic-only fixed anchor at the first successfully bound scene center. Anchor-only events never advance active scene validity and never drive renderer invalidation.

This reference run produced:

- active-scene `chunkLoadEvents=30`;
- active-scene `chunkUnloadEvents=35`;
- fixed-anchor `fixedAnchorChunkLoadEvents=9`;
- fixed-anchor `fixedAnchorChunkUnloadEvents=9`;
- `fixedAnchorReturnSceneReady=true`;
- `phase2ChunkLifecycleEvidenceReady=true`;
- `droppedLifecycleEvents=0`;
- `unsafeStaleSceneInstalls=0`;
- a LIVE/READY async scene after the anchor unload/load sequence.

Therefore this stronger downstream proof supersedes A-0084's missing observation and closes the P2.6 fixed-target unload/return coverage requirement without changing P2.6 behavior or weakening its correctness contract.

## Lifetime / shutdown evidence

- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed `20,025,552 / 20,025,552` bytes;
- pending upload batches `0`;
- staging abandonment `false`;
- arena used bytes `0`;
- arena high-water bytes `1,076,168`;
- arena allocations `406`, failures `0`;
- arena retired/reclaimed `406 / 406`;
- arena stale handle rejections `0`;
- arena fragmentation `0`;
- pending arena retirement batches `0`;
- arena abandonment `false`;
- deferred resources retired/released `203 / 203`;
- pending retirements `0`;
- retirement backpressure events `0`;
- retirement registration failures `0`.

## Merge qualification

The user already provided standing authorization to merge the validated dependency chain. With this runtime, the last technical dependency gate is closed.

Merge sequence is now authorized and technically qualified:

1. synchronize evidence/status;
2. merge PR #25 to `main` with `[no-release]` (P2.6 + already-integrated validated P2.7);
3. retarget/revalidate and merge PR #29 to `main` with `[no-release]`;
4. retarget/revalidate and merge PR #32 to `main` with `[no-release]`;
5. retarget/revalidate and merge PR #34 to `main` with `[no-release]`;
6. perform Class-A status synchronization on `main` before activating P3.2.

No new merge authorization is required unless scope materially changes.