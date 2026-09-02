# Obsidian Current State

Last updated: 2026-09-02

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Production-replacement work branch: `phase3/p3.10-production-terrain-replacement`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- P3.5 border/halo correctness: COMPLETE through `0.3.0-phase3-dev12.1`.
- P3.6 T-junction policy: COMPLETE through `0.3.0-phase3-dev13`.
- P3.7 differential correctness: COMPLETE through `0.3.0-phase3-dev14`.
- P3.8 meshing benchmark: COMPLETE through `0.3.0-phase3-dev15`.
- Synchronized P3.8-complete `main`: `169274b468d2a278d39043938efff19844bec9ba`.

## P3.9 partial remeshing — REJECTED / DEFERRED

P3.9 completed as a fixed four-Y-slice shadow experiment and was rejected/deferred by A-0188: projected-upload P95 was `807` permille versus the frozen `<=800` threshold. Do not weaken that threshold, revive the same experiment, merge the P3.9 branch wholesale, or treat partial GPU patching as a prerequisite for production replacement.

## P3.10 production opaque/cutout terrain replacement — ACTIVE

Continuity authority:

- A-0189 — P3.10 parent contract.
- A-0190 — exact Minecraft 26.2 terrain seam result.
- A-0191 — frozen dev24 production replacement canary contract.
- A-0192 — dev24 hosted-CI/package handoff.
- A-0193 — dev24 reference runtime FAILED on recenter.
- A-0194 — frozen dev24.1 recenter/admission correction contract.
- A-0195 — dev24.1 hosted-CI/package handoff.
- A-0196 — dev24.1 reference runtime FAILED visual + vertical-scene gates.
- A-0197 — frozen dev24.2 vertical/capture-completeness correction contract.
- A-0198 — dev24.2 hosted-CI/package runtime handoff.
- A-0199 — dev24.2 reference runtime PARTIAL SUCCESS: demonstrated blockers closed; F3+T evidence still missing.
- PR #55 — remains DRAFT / **DO NOT MERGE** until the final reload/recovery + visual gate closes.

## Dev24.2 package authority

Source version: `0.3.0-phase3-dev24.2`.

Exact renderer-source/package authority:

`debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`

Later continuity-only commits do not change renderer source or package authority.

Hosted Build run `33648273131` / #723 passed on that exact source head.

Canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev24.2.jar`
- size **466,654 bytes**
- SHA-256 **`7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`**

Use the direct versioned JAR, not the Actions ZIP wrapper.

## A-0199 dev24.2 runtime result — PARTIAL SUCCESS

The exact dev24.2 JAR ran on the reference Vulkan/AMD environment and closed both demonstrated dev24.1 blockers.

### Targeted visual blockers

Tester verdict:

- **kelp: PASS — visible / fine**;
- **leaves: PASS — visible / fine**.

This is the expected conservative result. Dev24.2 does not add native Obsidian leaf/fluid rendering support. Production suppression now requires a generalized capture with `rejectedBlocks() == 0`, so sections containing leaves, kelp/fluid-bearing blocks, block entities, unsupported materials/layers, missing models, translucent output or non-block-atlas output remain vanilla instead of being replaced by an incomplete mesh.

The earlier thin/coplanar 2D grass/leaf-litter z-fighting observation remains outside this correction and was not promoted to a new blocker.

### Same-column vertical section tracking — PASS

The runtime first reached READY at center `(67,4,-19)`.

Later, while retaining center X/Z `(67,-19)`, generation 31 submitted Y=3 records and reached READY at center `(67,3,-19)`. This directly proves the managed 3x3x1 scene now follows same-column section-Y transitions instead of remaining stuck on the prior vertical section.

Final scene evidence:

- `cameraRecenterEvents=20`;
- `sceneReadyTransitions=106`;
- `sceneRebuilds=105`;
- `observedReasons=section-dirty|world-change|resource-reload|scene-recenter`;
- `unsafeStaleSceneInstalls=0`.

The dev24.1 stale-Y defect is closed.

### Production replacement accounting — PASS

Final dev24.2 P3.10 telemetry:

- `prepareCalls=16,738`;
- `supportedVanillaCandidates=14,901,944`;
- `vanillaFallbacks=14,808,182`;
- SOLID suppressions/executions `63,376 / 63,376`;
- CUTOUT suppressions/executions `30,386 / 30,386`;
- `framesWithReplacement=13,517`;
- `maxClaimsPerPrepare=15`;
- duplicate claims `0`;
- claim overflows `0`;
- stale-plan failures `0`;
- execution without claim `0`;
- execution revalidation failures `0`;
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

This proves real production replacement still occurs on clean supported sections while incomplete/unsupported sections fall back to vanilla conservatively.

### Correctness and lifetime — PASS

Final evidence:

- P3.5 `borderHaloCorrectnessEvidenceReady=true`;
- P3.6 `tJunctionPolicyEvidenceReady=true`;
- P3.6 camera-relative transform failures `0`;
- P3.7 `differentialCorrectnessEvidenceReady=true`;
- P3.7 proof records/determinism `974 / 974`;
- P3.7 material/direction/geometry/UV/color/light all exact;
- P3.7 missing `0`;
- P3.7 duplicate `0`;
- P3.7 optimized-without-reference `0`;
- P3.7 real mismatches `0`;
- fixture self-tests `974 / 974`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale scene installs `0`.

The run also exercised substantial dirty/rebuild traffic (`playerDirtyEvents=2,241`, `renderedCoreDirtyEvents=5,011`) and repeatedly returned READY.

Lifetime closed cleanly:

- workers submitted/started/completed `999 / 999 / 999`;
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
- process exit `0`.

## Remaining promotion gate — focused F3+T run required

The dev24.2 run is **not yet promotion-complete** because the frozen runtime resource-reload gate was not exercised.

Final lifecycle telemetry reports `resourceReloadEvents=1`. One resource reload already occurs at startup before world entry. The runtime log contains no second post-startup `resource-reload` invalidation, so there is no evidence that F3+T was performed after production replacement became active.

Therefore the required sequence remains unproven:

1. production replacement active;
2. press `F3+T`;
3. resource invalidation forces safe vanilla fallback;
4. reload completes;
5. scene rebuilds and returns READY;
6. production replacement resumes with exact accounting and no visual regression;
7. normal exit.

This is an evidence gap, not a demonstrated renderer defect. **Do not change renderer source to address it.**

## Current handoff point

Run the exact same canonical dev24.2 JAR for one focused reload test. Wait until P3.10 replacement is active, press `F3+T`, let the reload finish, verify terrain remains visually correct through fallback and recovery, keep playing until the scene returns READY/replacement, then exit normally.

Return the complete relevant log and a short visual verdict. The log must show at least one post-startup resource reload (final `resourceReloadEvents >= 2`) plus clean P3.10 accounting, P3.7 exactness, bounded lifetime and process exit `0`.

If that focused run passes, record the final immutable runtime attempt, synchronize continuity, validate the evidence head in hosted CI, and only then prepare P3.10 promotion. Keep PR #55 DRAFT / DO NOT MERGE until that gate closes.