# A-0199 — Phase 3 P3.10 dev24.2 runtime: blockers closed, F3+T pending

Date: 2026-09-02
Status: **PARTIAL SUCCESS / DEV24.1 BLOCKERS CLOSED / PROMOTION STILL BLOCKED BY UNEXERCISED F3+T**

## Authority

- Branch: `phase3/p3.10-production-terrain-replacement`
- Exact renderer source/package authority: `debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`
- Runtime artifact: `Obsidian-0.3.0-phase3-dev24.2.jar`
- Runtime artifact SHA-256: `7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`
- Hosted package authority: A-0198 / Build #723 / artifact `9853678809`
- Reference runtime: Minecraft 26.2, Fabric Loader 0.19.3, Java 25.0.1, Windows, Vulkan, AMD Radeon RX 6800 XT.

## Purpose

Retest the two demonstrated dev24.1 correctness defects after the frozen A-0197 dev24.2 correction:

1. leaves/kelp disappearing when an incomplete generalized capture nevertheless authorized whole-layer vanilla suppression;
2. a 3x3x1 managed scene failing to follow the player across section-Y boundaries while X/Z stayed inside the same horizontal window.

The inherited P3.10 canary also still requires edit recovery, F3+T fallback/recovery, real recenter recovery, exact production accounting, permanent P3.7 exactness, clean bounded lifetime, normal exit, and human visual acceptance.

## Human visual result

Tester report for dev24.2:

- **kelp: PASS — visible / fine**;
- **leaves: PASS — visible / fine**.

This is the expected conservative outcome from A-0197: sections containing rejected capture content remain vanilla; dev24.2 does not claim new Obsidian leaf/fluid rendering support.

No new blocker was reported for the previously noted thin/coplanar 2D grass/leaf-litter overlap behavior. That behavior remains outside this correction.

A full general visual PASS was not explicitly re-stated in this report, so this attempt records only the two targeted visual blockers as closed.

## Vertical section-Y recenter result

**PASS.**

The runtime initially reached READY at center `(67,4,-19)`. Later, without changing the center X/Z identity, the scene submitted and installed generation 31 records at Y=3 and reached READY at center `(67,3,-19)`. This directly demonstrates that the dev24.2 Y-aware recenter condition moved the managed 3x3x1 scene to the lower vertical section while retaining center X/Z `(67,-19)`.

The run ultimately recorded:

- `cameraRecenterEvents=20`;
- `observedReasons=section-dirty|world-change|resource-reload|scene-recenter`;
- `sceneReadyTransitions=106`;
- `sceneRebuilds=105`;
- `unsafeStaleSceneInstalls=0`.

The previously demonstrated same-column stale-Y defect is therefore closed by this runtime.

## P3.10 production replacement result

Final dev24.2 production telemetry:

- `prepareCalls=16,738`;
- `supportedVanillaCandidates=14,901,944`;
- `vanillaFallbacks=14,808,182`;
- SOLID suppressions/executions `63,376 / 63,376`;
- CUTOUT suppressions/executions `30,386 / 30,386`;
- `framesWithReplacement=13,517`;
- `maxClaimsPerPrepare=15`;
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
- `sameOpaquePassExecutions=93,762`;
- `nativeGraphicsExpansion=false`;
- `partialRemeshing=false`;
- `partialGpuPatch=false`.

This proves both real Obsidian production replacement on clean supported sections and large-scale conservative vanilla fallback on incomplete/unsupported sections without suppression/execution drift.

## P3.5 / P3.6 / P3.7 correctness

Final evidence remained exact and clean:

- P3.5 `borderHaloCorrectnessEvidenceReady=true`;
- P3.6 `tJunctionPolicyEvidenceReady=true`;
- P3.6 `cameraRelativeTransformFailures=0`;
- P3.7 `differentialCorrectnessEvidenceReady=true`;
- P3.7 proof records `974`, determinism `974/974`;
- material `18,790/18,790`;
- direction `18,790/18,790`;
- geometry `18,790/18,790`;
- UV `75,160/75,160`;
- color `75,160/75,160`;
- light `75,160/75,160`;
- missing source coverage `0`;
- duplicate source coverage `0`;
- optimized canonical without reference `0`;
- real mismatches `0`;
- fixture self-tests `974/974`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale scene installs `0`.

## Edit/rebuild and lifetime evidence

The run exercised substantial dirty/rebuild traffic:

- `playerDirtyEvents=2,241`;
- `renderedCoreDirtyEvents=5,011`;
- `sceneReadyTransitions=106`;
- `sceneRebuilds=105`.

Bounded lifetime closed cleanly:

- worker submitted/started/completed `999/999/999`;
- worker failed jobs `0`;
- worker queue rejections `0`;
- worker shutdown join failures `0`;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed bytes `45,629,616 / 45,629,616`;
- arena allocations/retired/reclaimed `2,922 / 2,922 / 2,922`;
- pending upload batches `0`;
- pending arena retirement batches `0`;
- pending deferred retirements `0`;
- process exited with code `0`.

## Remaining failed/unexercised gate

**F3+T fallback/recovery is not proven by this run.**

Final lifecycle telemetry reports `resourceReloadEvents=1`. One resource reload already occurs during startup before world entry, and the log contains no second runtime `resource-reload` invalidation. Therefore the frozen P3.10 requirement to perform F3+T, observe conservative fallback during invalidation, and recover production replacement afterward was not exercised.

This is an evidence gap, not a renderer defect. Do not change renderer source for it.

Because A-0191/A-0197 require F3+T as a promotion gate, dev24.2 cannot yet be promoted or merged from this attempt alone.

## Conclusion

**PARTIAL SUCCESS.** The two dev24.1 blockers are closed:

- leaves and kelp remain visible through conservative vanilla fallback;
- same-column vertical section-Y tracking now follows the player and returns READY.

Production suppression/execution accounting, P3.7 exactness, worker ownership, stale rejection, bounded lifetime and normal exit are all clean at substantial runtime volume.

The only identified frozen promotion gate still missing from this run is an explicit post-startup F3+T fallback/recovery cycle. PR #55 must remain DRAFT / DO NOT MERGE until that evidence is captured and the tester supplies the final visual verdict for that run.

## Next action

Run the exact same dev24.2 JAR again or continue with an equivalent clean reference session, wait for P3.10 replacement to become active, press F3+T, let resource reload complete, verify terrain remains visually correct through vanilla fallback and then returns to READY/replacement, and exit normally. Return the complete relevant log plus any visual issue. No renderer-source change is justified before that focused evidence run.