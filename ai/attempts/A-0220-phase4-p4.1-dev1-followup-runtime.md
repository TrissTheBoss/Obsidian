# A-0220 - Phase 4 P4.1 dev1 follow-up reference runtime

**Date:** 2026-09-29  
**Status:** `PARTIAL` / **AUTOMATED FROZEN RUNTIME CONTRACT CLOSED; HUMAN VISUAL VERDICT PENDING**  
**Milestone:** Phase 4 P4.1 shadow large-scene visibility  
**Test package:** public Preview `Obsidian-0.4.0-phase4-dev1.jar`  
**Preview source:** `6af5174f054b272c65174fbf0c37d981a66aff31`  
**Preview JAR SHA-256:** `8bf25f3aa6bfe6fb4392044ba0973692fc6a24fd9948f3fea51cf55b12e157f3`

## Objective

Close the exercise-coverage gaps left by A-0219 using the exact same published P4.1 dev1 Preview, without changing renderer source or weakening A-0203.

The missing automated evidence was:

- post-startup/in-world F3+T resource reload and recovery;
- world leave/re-entry if practical.

The human visual PASS remains a separate required promotion gate and cannot be inferred from a launcher log.

## Action

The user reran the exact published dev1 Preview on the reference Windows 11 / AMD Radeon RX 6800 XT / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 Vulkan setup.

The follow-up log demonstrates:

1. world/P4.1 readiness and continuing shadow PASS samples;
2. a real in-world F3+T at 17:19:46, including the Minecraft `Reloaded resource packs` message and a ResourceManager reload;
3. P3.5 `resource-reload` invalidation while the scene is bound, followed by worker rebuild and scene READY recovery;
4. continued P4.1 PASS samples after recovery;
5. a second in-world resource reload at 17:20:00;
6. a real leave/re-entry cycle: player disconnect/server stop at 17:20:08 and player rejoin at 17:20:11;
7. later normal disconnect and process shutdown.

No renderer/source correction was made.

## Result

`PARTIAL` only because the explicit human visual verdict has not yet been supplied.

The **automated frozen runtime contract is closed** for this package.

### F3+T / recovery

The measured P3.8 benchmark window reports:

- `benchmarkResourceReloadDelta=2`;
- `benchmarkReadyDelta=31`;
- `benchmarkRecenterDelta=11`;
- `benchmarkWorkerCompletedDelta=306`;
- `benchmarkWorkerCancelledDelta=0`;
- `benchmarkWorkerQueueRejectionDelta=0`.

The first post-startup F3+T is followed by P3.5 READY recovery on frame 2,930 and continuing P4.1 samples 470, 480, 490, 500 and beyond.

Final P3.5 accounting reports `resourceReloadEvents=3`: one startup reload plus the two measured in-world reloads.

### World leave/re-entry

The run records:

- player disconnect and single-player server stop at 17:20:08;
- world-change invalidation while unbound;
- player joins again at 17:20:11;
- new world-change invalidation and subsequent scene/P4.1 recovery.

This closes the A-0203 “leave/re-entry or replacement if practical” exercise item.

### Final P4.1 evidence

- `configured=true`;
- capacity `36,504`;
- live sections `10,564`;
- high-water sections `10,642`;
- `capacityFailures=0`;
- `lifecycleOverflow=0`;
- `worldChanges=5`;
- `resourceReloads=3`;
- `chunkLoads=2,482`;
- `chunkUnloads=368`;
- `fullResyncs=7`;
- `dispatches=1,937`;
- `candidatesTested=20,208,721`;
- `samplesCompleted=1,836`;
- `exactSamples=1,836`;
- `missingVisible=0`;
- `unexpectedVisible=0`;
- `duplicateVisible=0`;
- `gpuFalseCullCount=0`;
- `readbackPendingHighWater=1`;
- `hardFailure=false`;
- `abandonedForDeviceShutdown=false`;
- `cameraOnlyFullSceneScan=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`.

### Inherited P3.10 / correctness / lifetime evidence

P3.10 final production replacement remained clean:

- `duplicateClaims=0`;
- `claimOverflows=0`;
- `stalePlanFailures=0`;
- `executionWithoutClaim=0`;
- `executionRevalidationFailures=0`;
- `suppressionExecutionAccountingCoherent=true`;
- `productionCoordinatesExact=true`;
- `productionExactColor=true`;
- `sameOpaquePass=true`;
- `nativeGraphicsExpansion=false`;
- `partialRemeshing=false`;
- `partialGpuPatch=false`.

The final subsystem summaries immediately before coordinator teardown report:

- P3.5 `borderHaloCorrectnessEvidenceReady=true`, zero worker live-world reads, zero synchronous scene builds and zero unsafe stale installs;
- P3.6 `tJunctionPolicyEvidenceReady=true`, all bounds/plane/integer-lattice comparisons exact and `cameraRelativeTransformFailures=0`;
- P3.7 `differentialCorrectnessEvidenceReady=true`, `missing=0`, `duplicate=0`, `optimizedWithoutReference=0`, `realMismatches=0`;
- worker pool `314/314` completed, zero failures, cancellations, queue-full rejections or shutdown join failures;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- all staging bytes reclaimed;
- arena used bytes returned to zero;
- zero pending upload/arena/resource retirements.

The process exits with code 0.

The coordinator's final teardown snapshot is taken after the world has been disconnected and its live scene is intentionally unbound; its transient readiness booleans therefore reset. The dedicated final P3.5/P3.6/P3.7 summaries immediately preceding it preserve the actual correctness evidence, and the coordinator retains `hardFailure=false` plus clean worker/lifetime accounting.

## Intended effect

Close the exact automated evidence gaps named by A-0219 while preserving the frozen A-0203 contract.

## Actual effect

The same dev1 Preview passed real post-startup resource reload/recovery twice, a real leave/re-entry cycle, continued large-scene GPU/CPU oracle comparison, inherited production/correctness checks, and normal shutdown.

No automated P4.1 gate remains open.

## Remaining promotion gate

**Explicit human visual PASS/FAIL only.**

Because P4.1 is shadow-only, the required verdict is whether the world remained visually indistinguishable from the promoted P3.10 baseline throughout this run, including after F3+T and world re-entry.

A PASS requires no new:

- terrain holes or missing terrain;
- duplicate terrain;
- texture/light/cutout/depth regressions;
- stale popping or other rendering difference attributable to P4.1.

Do not infer PASS from the clean automated log.

## Side effects / lessons

- The first A-0219 exercise gap was test coverage, not a renderer defect.
- The exact same Preview closes the missing F3+T and world re-entry gates without code changes.
- No renderer-source change is justified.
- PR #57 remains draft until the human visual verdict is supplied.

## Next action

Obtain the user's explicit human visual PASS/FAIL for this exact run.

If PASS, record a new immutable promotion attempt, synchronize `CURRENT_STATE.md` and the active context capsule, run exact synchronized-head hosted validation, and proceed with P4.1 promotion/next-slice planning under the repository workflow.

If FAIL, capture the exact visual symptom and treat it as new runtime evidence before any renderer correction.
