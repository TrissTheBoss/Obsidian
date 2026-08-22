# A-0098 - Phase 3 dev2 reference runtime success and merge authorization

**Date:** 2026-08-21  
**Objective:** Close the P3.1 dev2 production asynchronous scene integration reference-runtime gate and record the user's merge authorization without violating the existing Phase 2 dependency order.  
**Action:** Reviewed the complete user-supplied Prism Launcher runtime log for the corrected A-0097 `Obsidian-0.3.0-phase3-dev2` package on the reference Windows 11 / Radeon RX 6800 XT Vulkan system. Verified initial worker-backed scene readiness, repeated dirty-event rebuilds, resource reload coverage, recenter churn, worker accounting, stale-install safety, bounded GPU-resource cleanup, and normal process exit. The user then explicitly stated: "You have my authorization to merge." The user also requested that future runtime-test artifacts be handed off as a direct `.jar`, not a workflow `.zip` wrapper.  
**Result:** `SUCCESS` for the P3.1 dev2 reference-runtime gate and merge authorization. Merge execution remains dependency-blocked by the still-open P2.6 fixed-target chunk unload/return gate.  

## Runtime evidence

Reference environment reached the expected active Vulkan path:

- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- Java 25.0.1;
- AMD Radeon RX 6800 XT;
- Vulkan backend;
- Obsidian `0.3.0-phase3-dev2`.

The production async scene became READY and remained rebuildable under real world dirtiness. Final shutdown evidence reported:

- `phase3GateReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- `renderThreadCaptureOwnership=true`;
- `renderThreadGpuOwnership=true`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `sceneReadyTransitions=25`;
- `sceneRebuilds=24`;
- `recordInstalls=131`;
- `sceneWorkerInstalls=131`;
- `maxLiveRecords=8`;
- `maxAdjacentPairs=10`;
- `cameraRecenterEvents=2`;
- `resourceReloadEvents=2`;
- `dirtyEvents=1052`;
- `droppedLifecycleEvents=0`;
- `unsafeStaleSceneInstalls=0`.

Worker production accounting:

- submitted/started/completed `131/131/131`;
- cancelled `0`;
- cancellation requests `0`;
- stolen jobs `97`;
- queue-full rejections `0`;
- failed jobs `0`;
- stale result discards `0`;
- maximum observed queue depth `1`;
- total/max queue wait `13,682,000 / 4,099,400 ns`;
- total/max execution `41,874,100 / 5,950,000 ns`.

These timings are runtime instrumentation evidence for this correctness integration milestone, not broad production performance claims.

Post-drain cleanup was fully clean:

- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed `9,553,256 / 9,553,256` bytes;
- staging backpressure `0`, pending batches `0`, abandonment `false`;
- arena used bytes `0`;
- arena allocations `262`, failures `0`, retired/reclaimed `262/262`;
- arena retirement backpressure `0`, stale handles `0`, pending retirement batches `0`, abandonment `false`;
- arena returned to one full `33,554,432` byte free span with fragmentation `0`;
- deferred resources retired/released `131/131`, pending `0`;
- process exit code `0`.

## Dependency qualification

This runtime closes the dev2 async-scene runtime gate. It does **not** close the older P2.6 fixed-target chunk unload/return gate: this dev2 run itself ended with `chunkLoadEvents=0` and `chunkUnloadEvents=0`, and it is not the standalone corrected dev6 artifact required by that gate.

`CURRENT_STATE.md` already requires the P2.6 -> P2.7 dependency chain to reach `main` before Phase 3 may merge. The user's new authorization satisfies the separate human merge-authorization condition, but it does not by itself provide the missing P2.6 runtime evidence. Therefore PR #32 must remain unmerged until the Phase 2 dependency closes.

## User testing handoff preference

For future runtime-test packages, provide the direct versioned Obsidian `.jar` as the user-facing artifact whenever possible. Do not make the user unpack a GitHub Actions ZIP wrapper merely to obtain the test JAR.

**Intended effect:** Prove that persistent multi-section scene mesh construction is genuinely worker-backed in the production validation scene while preserving render-thread capture/GPU ownership, stale-result safety, bounded queues/memory, and complete shutdown reclamation.  
**Actual effect:** The full machine gate passed with extensive real rebuild/recenter/resource-reload churn, zero unsafe stale installs, zero synchronous scene builds, zero worker failures/rejections, and complete post-drain cleanup.  
**Evidence:** User-supplied Prism Launcher log captured 2026-08-21 22:14-22:16 local time; corrected A-0097 package SHA-256 `0f1cc8f2aa50da277c8b6bacb531d065ba7ecf489c9e406a2e15fa7c8a455044`; source evidence head `da4bd615a7de0bf90ac42c39ab945bb4903ae194`; exact CI run `32521379106`; documentation-synchronized CI run `32521543712`.  
**Why:** The worker-backed scene state machine, generation/event/resource checks, serialized bounded staging admission, completion-gated GPU retirement, and corrected post-drain shutdown gate all behaved as designed on the reference system.  
**Side effects / lessons:** `maxSimultaneousSceneJobs=1` and worker max queue depth `1` show this specific run did not create meaningful production queue pressure despite four workers; later P3.1 relevance/scheduling work still needs a deliberate streaming-pressure scenario before scheduler-performance claims. User-facing test artifact handoff should be the direct JAR.  
**Next action:** Keep PR #32 draft/unmerged while P2.6 remains open. Obtain the required standalone corrected dev6 fixed-target chunk unload/return evidence; once P2.6 closes, merge the already-authorized dependency chain in order. Continue remaining P3.1 scheduler relevance, worker-local scratch/allocation reduction, and measured queue/latency/output-size work without activating P3.2 prematurely.
