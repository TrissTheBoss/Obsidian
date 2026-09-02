# A-0200 — Phase 3 P3.10 dev24.2 focused F3+T automated pass; visual verdict pending

**Date:** 2026-09-02  
**Result:** `PARTIAL` — automated F3+T/resource-reload recovery PASS; explicit post-reload human visual verdict still required.  
**Branch:** `phase3/p3.10-production-terrain-replacement`  
**Exact renderer-source/package authority:** `debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`  
**Runtime artifact:** `Obsidian-0.3.0-phase3-dev24.2.jar`  
**Artifact size:** `466,654` bytes  
**SHA-256:** `7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`

## Objective

Close the single evidence gap left by A-0199: perform a real in-world `F3+T` resource reload after P3.10 production replacement has reached READY, prove safe invalidation/fallback and exact recovery, then exit normally without changing renderer source.

A-0199 already proved the demonstrated dev24.1 defects were closed: leaves and kelp were visually correct through conservative vanilla fallback, same-column vertical section-Y tracking worked, horizontal recenter/edit/rebuild traffic was healthy, production suppression/execution accounting was exact, P3.7 was exact, and lifetime drained cleanly. This focused run does not need to repeat those unrelated visual/recenter exercises.

## F3+T evidence

The exact dev24.2 build entered the world and reached P3.5 READY at center `(67,4,-20)` before the reload.

At `17:34:35` Minecraft logged:

`[System] [CHAT] [Debug]: Reloaded resource packs`

and immediately started a second `Reloading ResourceManager` cycle.

At `17:34:36` Obsidian observed the in-world reload and invalidated the active scene:

`reasons=resource-reload ... generation=7, center=(67,4,-20)`

The generation-7 eligibility scan then returned `eligibleRecords=9/9`; bounded worker capture/submission and render-thread installation resumed with `worldReadsAfterGeneralizedCapture=0` and exact production-coordinate/color flags.

The scene subsequently returned READY repeatedly after the reload, including later READY generations at the same managed center. Final lifecycle telemetry reported:

- `resourceReloadEvents=2` — startup reload plus the required post-startup F3+T reload;
- `sceneReadyTransitions=29`;
- `sceneRebuilds=28`;
- `recordInstalls=261`;
- `unsafeStaleSceneInstalls=0`;
- `observedReasons=section-dirty|world-change|resource-reload`.

This closes the automated F3+T invalidation/rebuild/recovery requirement.

## Final P3.10 production accounting

Final `ProductionTerrainReplacementPlan` telemetry:

- `prepareCalls=3,537`;
- `supportedVanillaCandidates=3,298,206`;
- `vanillaFallbacks=3,287,442`;
- SOLID suppressions/executions `8,852 / 8,852`;
- CUTOUT suppressions/executions `1,912 / 1,912`;
- `framesWithReplacement=2,466`;
- `maxClaimsPerPrepare=5`;
- `duplicateClaims=0`;
- `claimOverflows=0`;
- `stalePlanFailures=0`;
- `executionWithoutClaim=0`;
- `executionRevalidationFailures=0`;
- `suppressionExecutionAccountingCoherent=true`;
- `completeCaptureRequired=true`;
- `productionCoordinatesExact=true`;
- `productionExactColor=true`;
- `postWorldComparisonDrawDisabled=true`;
- `sameOpaquePass=true`;
- `sameOpaquePassExecutions=10,764`;
- `nativeGraphicsExpansion=false`;
- `partialRemeshing=false`;
- `partialGpuPatch=false`.

Production replacement therefore resumed after F3+T with exact suppression/execution accounting and without weakening the dev24.2 completeness fallback.

## Correctness evidence

Final P3.5:

- `borderHaloCorrectnessEvidenceReady=true`;
- `borderProofRecords=261`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`.

Final P3.6:

- `tJunctionPolicyEvidenceReady=true`;
- proof determinism `261 / 261`;
- `cameraRelativeTransformFailures=0`;
- no geometry/shader/pipeline change.

Final P3.7:

- `differentialCorrectnessEvidenceReady=true`;
- proof records/determinism `261 / 261`;
- `referenceFaces=50,757`;
- `mapped=40,820`, `unmapped=0`, `ambiguous=9,937`;
- `sourceQuads=95,731`;
- `passthroughIdentities=90,429`;
- `mergedCandidates=2,012`;
- `mergedExpandedFaces=5,302`;
- material/direction/geometry `5,302 / 5,302`;
- UV/color/light `21,208 / 21,208`;
- `missing=0`;
- `duplicate=0`;
- `optimizedWithoutReference=0`;
- `realMismatches=0`;
- fixture self-tests `261 / 261`;
- `workerWorldReadsAfterCapture=0`.

## Worker / staging / arena / resource lifetime

Final lifetime evidence remained bounded and fully drained:

- worker submitted/started/completed `261 / 261 / 261`;
- worker cancelled `0`;
- worker queue-full rejections `0`;
- worker failed jobs `0`;
- worker shutdown join failures `0`;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed bytes `12,850,392 / 12,850,392`;
- pending upload batches `0`;
- arena used bytes `0`;
- arena allocations/retired/reclaimed `783 / 783 / 783`;
- arena allocation failures `0`;
- pending arena retirement batches `0`;
- retired/released resources `261 / 261`;
- pending deferred retirements `0`;
- process exit `0`.

## Interpretation

The focused run closes the **automated** F3+T promotion gate. No renderer defect was observed in telemetry and no source change is justified.

The run does **not** by itself close the mandatory human visual gate. A log cannot prove absence of holes, missing faces/blocks, unexpected duplicate/z-fighting, wrong textures, tint/light/AO regressions, cutout/depth artifacts, or stale popping during/after the reload.

## Handoff / next action

Keep PR #55 **DRAFT / DO NOT MERGE** until the tester explicitly reports the post-F3+T visual result.

If the tester reports a clean visual PASS after this exact focused run, record that closure in a new immutable attempt, synchronize `CURRENT_STATE`, then run hosted CI on the exact evidence head before preparing P3.10 promotion. If the tester reports any visual defect, keep the PR draft and freeze a narrow correction contract before changing renderer source.
