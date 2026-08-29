# A-0142 — Phase 3 P3.5 border/halo correctness plan

**Date:** 2026-08-29  
**Result:** PLAN FROZEN — implementation/runtime evidence pending.

## Milestone identity
- milestone: **P3.5 — border/halo correctness**
- implementation version: `0.3.0-phase3-dev12`
- branch: `phase3/border-halo-correctness`
- base: synchronized `main` `dc82d1bcb9f264a940186497bf94fb36d8e84d86`

## Objective
Validate face visibility, light/AO capture and rebuild invalidation across section boundaries while preserving the existing dev11 repeat-aware greedy GPU emission semantics and keeping worker live-world reads at zero.

## Source finding that motivates this slice
`SectionSnapshot` already captures an immutable one-block halo in X/Y/Z, and `ReferenceFaceMesh` plus `BinarySectionVisibility` already consume that halo. However `SectionLifecycleEvents.sectionDirty(...)` currently treats only the rendered 3x3 sections at the exact tracked section Y as dirty-relevant.

That is narrower than the true dependency domain. A 3x3 scene of one-block-halo snapshots depends on:
- X/Z section coordinates through radius 2 around the scene center; and
- section Y through `targetY - 1 .. targetY + 1`.

A dirty event in that halo-only dependency region can therefore change captured visibility or exact vanilla light/AO input without advancing the scene event sequence. P3.5 must close that gap conservatively.

## Frozen scope
P3.5 dev12 is a **correctness/invalidation proof slice only**:
- no emitted geometry changes;
- no vertex/index format changes;
- no shader changes;
- no graphics pipeline changes;
- no native Vulkan seam expansion;
- dev11 hybrid eligibility/emission semantics stay exactly as validated.

Because this slice does not change emitted geometry, a new explicit human visual verdict is not required unless implementation scope later changes. Any such scope change must be recorded separately before promotion.

## Dirty dependency contract
`SectionLifecycleEvents.sectionDirty(...)` must conservatively treat a dirty section as relevant when all are true:
- tracked scene is known;
- `abs(sectionX - targetSectionX) <= 2`;
- `abs(sectionZ - targetSectionZ) <= 2`;
- `abs(sectionY - targetSectionY) <= 1`.

Chunk load/unload relevance remains X/Z radius 2 because chunk lifecycle covers all section Y values.

The bridge must retain allocation-free sticky counters and expose enough diagnostics to distinguish:
- rendered-core dirty events;
- halo-only dirty events;
- horizontal outer-ring halo dirty events;
- vertical halo dirty events.

A pure deterministic classifier self-test must exercise core, horizontal halo, vertical halo, combined/corner halo, and irrelevant outside-domain coordinates.

## Immutable per-section border proof
Add a pure summary-only `BorderHaloCorrectnessProof` consuming only:
- immutable `SectionSnapshot`;
- immutable `SectionBakedQuadSnapshot`.

For each installed section it must validate all six outward section boundaries:
- WEST `x=0`;
- EAST `x=15`;
- DOWN `y=0`;
- UP `y=15`;
- NORTH `z=0`;
- SOUTH `z=15`.

There are exactly `6 * 16 * 16 = 1,536` outward boundary source cells per proof build.

For every outward cell:
1. compute expected conservative canonical visibility from the immutable source classification and its halo neighbor;
2. require `BinarySectionVisibility` to match that expected result;
3. require the independent `ReferenceFaceMesh` set to match the same result.

The proof must also count/hash exact generalized baked quads whose source blocks lie on any section boundary and count/hash their frozen per-vertex packed-light and exact-ARGB samples. This proves that actual vanilla-captured border lighting/AO/color payload is present in the immutable render input without introducing worker world reads.

The proof retains summary values only and must support deterministic `contentEquals` validation.

## Shared-border scene proof
For horizontally adjacent LIVE records in the 3x3 scene, audit both independently captured snapshots across their common boundary.

For an X-adjacent pair:
- left snapshot halo `x=16` must equal right interior `x=0` for all 256 Y/Z cells;
- right snapshot halo `x=-1` must equal left interior `x=15` for all 256 Y/Z cells.

For a Z-adjacent pair:
- north snapshot halo `z=16` must equal south interior `z=0` for all 256 X/Y cells;
- south snapshot halo `z=-1` must equal north interior `z=15` for all 256 X/Y cells.

Each adjacent pair therefore performs exactly **512** state-ID/classification comparisons. A full 3x3 window has 12 horizontal adjacent pairs and therefore 6,144 comparisons per complete audit.

All comparisons must match. This audit is immutable data vs immutable data only.

## Generation/stale-output contract
The existing event-sequence checks in `WorkerBackedSectionLifecycleProbe` remain authoritative. Once halo-only dirty sections advance `SectionLifecycleEvents.latestSequence()`, queued/running/preinstall stale outputs are rejected by the same generation-safe path already proven in P3.1/dev11.

No worker may read `ClientLevel`, chunks, models, light engines, resources or GPU objects after capture. `workerWorldReadsAfterCapture=0` remains mandatory.

## Runtime evidence
Add explicit P3.5 evidence with final flag:
- `borderHaloCorrectnessEvidenceReady=true`.

The P3.5 gate requires, in addition to all prior dev11 gates and clean lifetime:
- lifecycle dependency classifier self-test PASS;
- at least one installed border proof;
- exactly 1,536 outward checks per proof;
- all optimized visibility checks match expected immutable halo truth;
- all independent reference checks match;
- observed border generalized baked quads > 0;
- observed frozen border light/color samples > 0;
- adjacent LIVE pair audits > 0;
- every shared-border state/class comparison matches;
- halo-only dirty dependency events are classified/invalidation-capable by the bridge;
- stale outputs cannot install after event-sequence advancement;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `hardFailure=false`;
- workers/staging/arena/resources clean;
- normal process exit code 0.

The runtime should exercise an ordinary block break/place rebuild and F3+T/resource rebuild so generation/rebuild/resource paths remain covered. The old A-0101 fixed-anchor far-travel exercise remains unnecessary.

## Promotion boundary
P3.5 may merge under standing Phase 3 authorization only after exact package/evidence CI and the complete reference runtime gate pass.

P3.6 broader T-junction policy remains PLANNED and must not be claimed or consumed by this slice.