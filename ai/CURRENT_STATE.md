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
- A-0199 — dev24.2 reference runtime PARTIAL SUCCESS: leaves/kelp and same-column vertical tracking fixed; automated accounting/correctness/lifetime clean; F3+T evidence missing.
- A-0200 — dev24.2 focused F3+T run PARTIAL SUCCESS: automated reload/recovery gate PASS; explicit post-reload human visual verdict pending.
- PR #55 — remains DRAFT / **DO NOT MERGE** until the final human post-F3+T visual verdict closes.

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

## Dev24.2 correction and product behavior

A-0197 changed only two behaviors plus version/banner metadata:

1. same-column section-Y transitions now trigger the existing safe scene recenter/invalidation path;
2. production suppression/revalidation requires a non-null immutable generalized capture with `rejectedBlocks() == 0`.

This is deliberately conservative. Dev24.2 does **not** add native Obsidian rendering for leaves, kelp/fluids, block entities, translucent output, unsupported materials/layers, missing models or non-block-atlas geometry. Any section containing rejected capture content stays vanilla rather than suppressing vanilla with an incomplete replacement.

No mesher algorithm, P3.7 proof, shader, pipeline, same-OPAQUE-pass seam, native Vulkan graphics ownership, lifetime rule, partial remeshing or partial GPU patching changed.

## A-0199 reference runtime — demonstrated blockers closed

The exact dev24.2 JAR ran on the reference Vulkan/AMD environment.

Human targeted visual verdict:

- **kelp: PASS — visible / fine**;
- **leaves: PASS — visible / fine**.

Same-column vertical section tracking also passed: the scene first reached READY at `(67,4,-19)` and later reached READY at `(67,3,-19)` while retaining center X/Z `(67,-19)`. Final `cameraRecenterEvents=20`, `sceneReadyTransitions=106`, `sceneRebuilds=105`, and `unsafeStaleSceneInstalls=0`.

A-0199 production replacement accounting was exact at scale:

- SOLID suppressions/executions `63,376 / 63,376`;
- CUTOUT suppressions/executions `30,386 / 30,386`;
- duplicate/overflow/stale-plan/unclaimed/revalidation failures all `0`;
- `suppressionExecutionAccountingCoherent=true`;
- `completeCaptureRequired=true`;
- production coordinates/color exact;
- same OPAQUE pass true;
- native graphics expansion false.

P3.7 closed exact across `974` proof records with missing/duplicate/optimized-without-reference/real mismatches all `0`; worker world reads after capture `0`; synchronous scene mesh builds `0`; unsafe stale installs `0`. Workers/staging/arena/resources drained cleanly and process exit was `0`.

A-0199 remained partial only because final `resourceReloadEvents=1` showed no post-startup F3+T cycle.

## A-0200 focused F3+T runtime — automated gate PASS

The exact same dev24.2 JAR was used for a focused reload test.

The scene reached READY before reload. Minecraft then logged the explicit debug message `Reloaded resource packs` and started a second resource-manager reload. Obsidian observed a new `resource-reload` invalidation at generation `7`, invalidated the active scene, rescanned eligibility, rebuilt through bounded workers, and returned READY afterward.

Final lifecycle telemetry:

- `resourceReloadEvents=2` — startup plus required in-world F3+T reload;
- `sceneReadyTransitions=29`;
- `sceneRebuilds=28`;
- `recordInstalls=261`;
- `unsafeStaleSceneInstalls=0`;
- process exit `0`.

Final P3.10 production accounting after the reload:

- `prepareCalls=3,537`;
- `supportedVanillaCandidates=3,298,206`;
- `vanillaFallbacks=3,287,442`;
- SOLID suppressions/executions `8,852 / 8,852`;
- CUTOUT suppressions/executions `1,912 / 1,912`;
- `framesWithReplacement=2,466`;
- duplicate claims `0`;
- claim overflows `0`;
- stale-plan failures `0`;
- execution without claim `0`;
- execution revalidation failures `0`;
- `suppressionExecutionAccountingCoherent=true`;
- `completeCaptureRequired=true`;
- production coordinates/color exact;
- post-world comparison disabled;
- same OPAQUE pass true;
- `sameOpaquePassExecutions=10,764`;
- native graphics expansion false;
- partial remeshing false;
- partial GPU patch false.

Final correctness after F3+T:

- P3.5 `borderHaloCorrectnessEvidenceReady=true`;
- P3.6 `tJunctionPolicyEvidenceReady=true`, determinism `261/261`, camera-relative transform failures `0`;
- P3.7 `differentialCorrectnessEvidenceReady=true`, determinism `261/261`;
- P3.7 material/direction/geometry `5,302/5,302`;
- P3.7 UV/color/light `21,208/21,208`;
- P3.7 missing `0`;
- P3.7 duplicate `0`;
- P3.7 optimized-without-reference `0`;
- P3.7 real mismatches `0`;
- fixture self-tests `261/261`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale installs `0`.

Final lifetime after F3+T:

- worker submitted/started/completed `261/261/261`;
- worker failures/rejections/shutdown-join failures `0`;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed `12,850,392 / 12,850,392` bytes;
- arena allocations/retired/reclaimed `783/783/783`;
- no pending upload, arena-retirement or deferred-resource batches;
- process exit `0`.

A-0200 therefore closes the **automated** F3+T invalidation/fallback/rebuild/replacement-recovery gate. No renderer source change is justified.

## Remaining promotion gate — human post-F3+T visual verdict only

The log cannot prove visual correctness. The only remaining frozen P3.10 runtime gate is an explicit tester verdict that terrain looked correct after the F3+T reload and recovery.

A PASS means no new blocker such as holes/missing terrain or faces, unexpected duplicates/z-fighting beyond the already-known thin/coplanar 2D behavior, wrong textures, tint/light/AO regression, cracks/pinholes, cutout/depth artifact, or stale popping attributable to reload/recovery.

Do **not** infer this verdict from automated counters.

## Current handoff point

Ask only for the tester's post-F3+T visual verdict from the A-0200 focused run.

- If visual PASS: create a new immutable final runtime-closure attempt, synchronize continuity, then run hosted CI on the exact evidence/continuity head and prepare P3.10 promotion. Do not change renderer source.
- If visual FAIL: keep PR #55 draft, record the defect, and freeze a narrow correction contract before changing renderer source.

PR #55 remains DRAFT / **DO NOT MERGE** until the human visual verdict is explicit.