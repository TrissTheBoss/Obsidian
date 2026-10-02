# A-0234 - P4.2 dev2.2 recenter-liveness runtime PASS / 32+ scale still required

**Date:** 2026-09-29  
**Result:** `PARTIAL PASS / LIVENESS CLOSED, PROMOTION BLOCKED ONLY BY DEV2.2 32+ SAMPLE`  
**Version:** `0.4.0-phase4-dev2.2`  
**Tester verdict:** explicit human **Visual PASS**

## Objective

Evaluate the A-0232 correction that allows exact-player-section recentering while the managed scene is SCANNING or BUILDING, specifically the previously failing case where an air-heavy section never reaches LIVE.

## Exact runtime

The supplied Prism Launcher log identifies:

- Obsidian `0.4.0-phase4-dev2.2`;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25.0.1;
- Vulkan on AMD Radeon RX 6800 XT;
- normal process exit code 0.

The user supplied an explicit human **Visual PASS** for ordinary world rendering.

## A-0232 liveness gate closure

Initial scene:

- exact-player-section anchor bound at center `(40,4,2)`;
- scene reached READY with 9 live records / 12 adjacent pairs.

Vertical exercise:

1. exact recenter `(40,4,2) -> (40,5,2)`;
2. Y=5 reached READY;
3. exact recenter `(40,5,2) -> (40,6,2)`;
4. Y=6 remained intentionally non-live with only **2 eligible records / 1 adjacent pair**, below the frozen 3-record / 2-pair requirement;
5. while still in that non-live state, exact recenter fired back down: `(40,6,2) -> (40,5,2)`;
6. Y=5 recovered to READY with 5 live records / 5 adjacent pairs.

This is the exact liveness case that failed in dev2.1. The corrected code no longer strands the scene in SCANNING.

Additional later vertical and horizontal recenter events also completed and recovered to READY. Final telemetry recorded **29 camera recenter events**.

## P4.1 / P4.2 / P3.10 correctness

Final P4.2 evidence at render distance 16:

- liveColumns=1,057;
- liveSections=10,843;
- capacityFailures=0;
- mutationFailures=0;
- hierarchyAuditFailures=0;
- missingVisibleColumns=0;
- duplicateVisibleColumns=0;
- gpuColumnFalseCullCount=0;
- readbackPendingHighWater=1;
- hardFailure=false;
- cameraOnlyFullHierarchyScan=false;
- productionDrawOwnershipChanged=false;
- nativeGraphicsExpansion=false.

The 55 accumulated `unexpectedVisibleColumns` are conservative extra GPU-visible columns, not false culls; samples remained PASS and missing/duplicate/false-cull counts stayed zero.

Final P4.1 evidence remained clean:

- capacityFailures=0;
- lifecycleOverflow=0;
- missingVisible=0;
- unexpectedVisible=0;
- duplicateVisible=0;
- gpuFalseCullCount=0;
- readbackPendingHighWater=1;
- hardFailure=false;
- cameraOnlyFullSceneScan=false;
- productionDrawOwnershipChanged=false;
- nativeGraphicsExpansion=false.

Final P3.10 replacement accounting remained coherent:

- duplicateClaims=0;
- claimOverflows=0;
- stalePlanFailures=0;
- executionWithoutClaim=0;
- executionRevalidationFailures=0;
- suppressionExecutionAccountingCoherent=true;
- sameOpaquePass=true;
- nativeGraphicsExpansion=false.

Lifetime/worker closure was clean:

- workerFailedJobs=0;
- workerShutdownJoinFailures=0;
- workersClean=true;
- stagingClean=true;
- arenaClean=true;
- resourcesClean=true;
- pendingRetirements=0;
- process exit code 0.

## Remaining frozen gate

This dev2.2 run stayed at **render distance 16**.

A-0232 explicitly requires the **corrected package** to show P4.1/P4.2 correctness and the **32+ scale sample** remains clean.

The earlier dev2.1 runtime produced clean 32+ scale evidence, but that cannot silently replace the corrected-package requirement after a source change.

Therefore:

- A-0232 recenter-liveness defect: **CLOSED**;
- human visual gate: **PASS**;
- corrected-package 32+ scale gate: **OPEN**;
- P4.2 promotion: **BLOCKED only by that final dev2.2 32+ sample**.

## Next action

Run the exact published dev2.2 Preview again with render distance **32 or higher** long enough for the hierarchy to populate and repeated P4.1/P4.2 PASS samples to accumulate.

No source change is requested. A short scale-only run is sufficient if:

- P4.2 reaches real 32+ scale;
- structural audits remain clean;
- missing/duplicate/false-cull counts remain zero;
- P4.1 remains clean;
- no capacity/hard/lifetime failure occurs;
- normal exit is clean.

The existing dev2.2 Visual PASS remains valid unless the tester observes a new visual regression in that scale-only run.
