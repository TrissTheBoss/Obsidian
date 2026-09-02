# Obsidian Current State

Last updated: 2026-09-02

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Active promotion branch: `phase3/p3.10-production-terrain-replacement`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.8: COMPLETE.
- P3.9 fixed four-Y-slice partial remeshing experiment: **REJECTED / DEFERRED** by A-0188; projected-upload P95 was `807` permille versus frozen `<=800`.
- P3.10 production opaque/cutout terrain replacement: **PROMOTION READY** after A-0201; exact evidence-head hosted CI + merge remain before Phase 3 can be marked COMPLETE.

## P3.10 continuity authority

- A-0189 — P3.10 parent contract.
- A-0190 — exact Minecraft 26.2 terrain seam.
- A-0191 — frozen dev24 replacement canary contract.
- A-0192 — dev24 hosted-CI/package handoff.
- A-0193 — dev24 reference runtime FAILED on deterministic-empty-reference recenter path.
- A-0194 — frozen dev24.1 correction.
- A-0195 — dev24.1 hosted-CI/package handoff.
- A-0196 — dev24.1 reference runtime FAILED visual + vertical-scene gates.
- A-0197 — frozen dev24.2 vertical/capture-completeness correction.
- A-0198 — dev24.2 hosted-CI/package handoff.
- A-0199 — dev24.2 reference runtime PARTIAL SUCCESS: leaves/kelp + same-column vertical tracking fixed; F3+T evidence missing.
- A-0200 — focused F3+T automated PASS; human post-reload visual verdict pending.
- A-0201 — **SUCCESS**: tester supplied explicit post-F3+T visual PASS; frozen P3.10 runtime + visual contract is closed.
- PR #55 remains the promotion vehicle until exact final evidence-head CI passes and it is merged `[no-release]`.

## Canonical P3.10 package authority

Source version: `0.3.0-phase3-dev24.2`.

Exact renderer-source/package authority:

`debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`

Hosted Build run `33648273131` / #723 passed on that exact renderer source.

Canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev24.2.jar`
- size **466,654 bytes**
- SHA-256 **`7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`**

Later continuity-only commits do not change renderer/package authority.

## P3.10 proven production behavior

Production replacement uses the exact Minecraft 26.2 public-Blaze3D OPAQUE seam proven by A-0190:

- claim exact vanilla SOLID/CUTOUT `SectionDraw` units only when the matching Obsidian record is LIVE, generation/resource-current, P3.7 exact, GPU-installed and non-empty for the requested layer;
- suppress that exact vanilla unit during `prepareChunkRenders`;
- encode the matching Obsidian command into the same active OPAQUE `RenderPass` before close;
- keep native graphics ownership unchanged;
- keep unsupported/incomplete content on vanilla fallback.

Dev24.2 adds two narrow safety corrections only:

1. the managed 3x3x1 scene recenters whenever player section Y differs from the current center, even if X/Z stay inside the horizontal window;
2. production suppression/revalidation additionally requires a non-null immutable generalized capture with `rejectedBlocks() == 0`.

This does **not** add native Obsidian rendering for leaves, kelp/fluids, block entities, translucent geometry, unsupported materials/layers, missing models or non-block-atlas output. Such sections remain vanilla.

## Final runtime evidence — PASS

A-0199 proved the previously demonstrated dev24.1 blockers were closed:

- leaves visible / fine — PASS;
- kelp visible / fine — PASS;
- same-column vertical scene tracking moved from center `(67,4,-19)` to `(67,3,-19)` while retaining X/Z `(67,-19)` and returned READY;
- SOLID suppressions/executions `63,376 / 63,376`;
- CUTOUT suppressions/executions `30,386 / 30,386`;
- all production-plan failure counters `0`;
- P3.7 exact across `974` proof records;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale installs `0`;
- worker/staging/arena/resource lifetime clean;
- process exit `0`.

A-0200 then exercised a real post-startup F3+T cycle:

- Minecraft logged `Reloaded resource packs`;
- Obsidian observed a second `resource-reload` invalidation;
- final `resourceReloadEvents=2`;
- scene rebuilt through bounded workers and returned READY;
- SOLID suppressions/executions `8,852 / 8,852`;
- CUTOUT suppressions/executions `1,912 / 1,912`;
- duplicate/overflow/stale-plan/unclaimed/revalidation failures all `0`;
- `suppressionExecutionAccountingCoherent=true`;
- `completeCaptureRequired=true`;
- P3.7 determinism `261/261`, all mismatch counters `0`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- workers/staging/arena/resources clean;
- process exit `0`.

A-0201 closes the only remaining gate: the tester explicitly reported **visual PASS** after F3+T recovery. No new holes/missing terrain or faces, unexpected duplicates/z-fighting beyond the previously accepted thin/coplanar 2D behavior, texture/tint/light/AO regressions, cracks/pinholes, cutout/depth artifacts or stale popping were reported.

## Current handoff point — P3.10 promotion

Do **not** change renderer source. P3.10 runtime/visual evidence is complete.

Required promotion sequence:

1. run hosted CI on the exact final evidence/continuity head containing A-0201 and this state synchronization;
2. if CI passes, update PR #55 to promotion-ready and merge `[no-release]` into `main`;
3. verify synchronized `main` contains the P3.10 renderer and continuity evidence;
4. update the roadmap status so P3.9 is REJECTED/DEFERRED, P3.10 and Phase 3 are COMPLETE, and Phase 4 becomes ACTIVE;
5. create a new Phase 4 feature branch from synchronized `main`;
6. freeze the first Phase 4 GPU-driven-visibility-at-scale contract before any source change.

## Next phase direction

The roadmap's next product phase is **Phase 4 — GPU-driven visibility at real-world scale**.

Existing durable constraints already apply:

- D-0004: large render distance is a core workload; avoid frame-critical CPU work linear in every loaded/visible section when GPU/hierarchy can replace it.
- D-0008: long-term terrain direction is async CPU meshing + large GPU arenas + GPU visibility/culling + draw compaction + indirect rendering.
- D-0025: native Vulkan interop remains narrow and justified only for compute/storage capability absent from public Blaze3D.
- D-0026: compute-written indirect data requires an explicit compute-write -> indirect-read synchronization edge.
- D-0027: baseline graphics stays public fixed-count indexed indirect with zeroed tail until measured evidence justifies native indirect-count graphics consumption.

Do not widen native graphics ownership, introduce Hi-Z, change the proven production mesher, revive P3.9 partial remeshing, or add optional LOD in the first Phase 4 slice without a separately frozen evidence-driven contract.
