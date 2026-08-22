# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.4 dev6 — render-correct merge-key sidecar**.
- Active branch: `phase3/render-correct-merge-key`.
- Canonical draft PR: #38 against `main`.
- Active development version: `0.3.0-phase3-dev6`.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- Phase 2 through P2.7: COMPLETE.
- P3.1 dev1: COMPLETE — PR #29 merge `c39cf17b4864e7f7081007238117aea5be3c26e3`.
- P3.1 dev2: COMPLETE — PR #32 merge `58b2b8b8b1962f2809029e32d147a4a96a93b486`.
- P3.1 dev3: COMPLETE — PR #34 merge `1b6615eac2494a197cea86d314cf5b099d2418e8`.
- P3.2 dev4 binary visibility masks: COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3 dev5 greedy rectangle extraction: COMPLETE — PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.

A-0101 remains the canonical proof for the already-closed Phase 2 fixed-target chunk unload/return lifecycle. Later Phase 3 runtime tests do not need to repeat that long-distance sequence unless lifecycle hooks or semantics materially change.

## P3.2 proven topology foundation

`BinarySectionVisibility` is the immutable worker-side conservative canonical face representation:

- WEST/EAST/DOWN/UP/NORTH/SOUTH directional masks;
- 4,096 bits / 64 `long` words per direction;
- exactly 3,072 retained bytes per complete mask set;
- exact conservative rule `SUPPORTED_FULL_CUBE && neighbor == AIR`;
- reusable worker-local primitive scratch;
- deterministic construction and independent `ReferenceFaceMesh` equivalence.

A-0105 closed P3.2 on real runtime evidence with `binaryVisibilityEvidenceReady=true`.

## P3.3 greedy rectangle extraction — COMPLETE

Evidence chain:

- A-0108 — frozen dev5 correctness-first rectangle contract;
- A-0110 — successful reference runtime and positive human visual regression verdict;
- A-0111 — promotion and P3.4 activation.

`GreedySectionRectangles` deterministically partitions P3.2 canonical faces into packed topology rectangles while preserving exact face coverage. It remains a topology representation; dev5 did not emit greedy GPU geometry.

Reference dev5 runtime proved:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- worker jobs `162/162/162` submitted/started/completed;
- visibility faces `48,261`;
- topology rectangles `21,286`;
- faces saved `26,975`;
- topology reduction `558` permille = **55.8%**;
- rectangle retained bytes `85,144 = 21,286 * 4`;
- primary exact mask audits `162/162`;
- rectangle determinism audits/matches `4/4`;
- independent rectangle/reference audits/matches `4/4`;
- zero worker rejection/failure/shutdown-join failure;
- zero dropped lifecycle events / unsafe stale installs;
- workers/staging/arena/resources clean;
- process exit code `0`.

The user reported that everything looked right. `BakedSectionMesh` remained the GPU drawable, so this visual result is a regression guard rather than proof of greedy GPU emission.

## ACTIVE: P3.4 dev6 — render-correct merge-key sidecar

A-0112 freezes the first P3.4 slice. Draft PR #38 implements a correctness-first sidecar before any production geometry replacement.

### Exact input truth already captured

`SectionBakedQuadSnapshot` freezes vanilla-emitted accepted SOLID/CUTOUT baked data into immutable primitive arrays, including:

- exact quad positions and UVs;
- exact per-corner ARGB colors and packed light;
- source block and state identity;
- face direction and render layer;
- material identity: atlas, sprite, material flags, tint index, shade, light emission and animation state;
- stable source snapshot/resource fingerprints.

### Dev6 canonical face mapping

`CanonicalFaceRenderKeys` is the active P3.4 sidecar. A P3.2 canonical face becomes render-key eligible only when exactly one baked quad from the same source block is proven to be the exact full unit-cube face for that direction.

The conservative recognizer:

- explicitly maps Minecraft `Direction` ordinals to P3.2 direction order;
- requires the P3.2 visible-face bit;
- requires all four baked positions to be exact integer-boundary corners of the expected unit face;
- rejects offset, inset, partial, rotated/non-axis-aligned, duplicate-corner and ambiguous generalized geometry;
- leaves zero-match or multi-match faces on the existing exact passthrough path.

### Render-equivalence contract

Two uniquely mapped canonical faces are considered render-equivalent only when all captured output-affecting truth agrees:

- canonical direction/orientation;
- render layer;
- complete `MaterialIdentity` equality;
- geometric corner order/winding signature;
- exact raw UV bits per geometric corner;
- exact ARGB color per geometric corner;
- exact packed light per geometric corner.

Block/state ID equality alone is never sufficient for merging.

### Current integration boundary

Every production `SectionMeshWorkerPool` job in dev6 is intended to build, in order:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. existing generalized `BakedSectionMesh` drawable.

The render-key sidecar uses a bounded 24,576-entry `short` map, exactly **49,152 retained bytes per build**. `0` means unmapped, positive values identify the unique source baked quad, and `-1` means ambiguous.

Existing P3.2/P3.3/reference/baked deterministic audits remain active. Dev6 adds duplicate render-key determinism audits and metrics for eligible/unmapped/ambiguous faces, recognized/ignored baked quads, and same-key/different-key/ineligible canonical adjacency pairs.

### Deliberate dev6 rendering boundary

Dev6 must continue to report:

- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleSidecarIntegrated=true`;
- `renderMergeKeySidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

The generalized SOLID/CUTOUT `BakedSectionMesh` remains the authoritative GPU drawable. Arbitrary generalized baked-model quads remain exact passthrough unless explicitly proven canonical and render-equivalent.

## Dev6 runtime closure — REQUIRED BEFORE MERGE

New required flag: `renderMergeKeyEvidenceReady=true`, together with the existing Phase 3/P3.2/P3.3 gates.

Closure requires:

- render-key builds cover completed production jobs;
- render-key visible-face count exactly equals P3.2 visibility faces;
- eligible + unmapped + ambiguous exactly equals visible faces;
- nonzero uniquely mapped canonical faces;
- nonzero recognized canonical baked quads;
- at least one same-key canonical adjacency and at least one different-key adjacency, proving the comparator is exercised both ways;
- retained bytes exactly equal builds * 49,152;
- render-key scratch use covers builds;
- nonzero deterministic audits with all matches;
- zero worker rejection/failure/shutdown-join failure;
- zero dropped lifecycle events / unsafe stale installs;
- workers/staging/arena/resources clean;
- process exit code `0`.

Runtime sequence remains ordinary terrain: initial 3x3 READY, visual regression check, break/place + READY rebuild, F3+T + READY rebuild, optional normal recenter movement, then normal exit. The old fixed-anchor far-travel sequence is not required again.

Because dev6 still does not change emitted GPU geometry, human visual inspection is a regression guard. Any later P3.4 step that begins emitting merged geometry requires renewed explicit human visual validation.

## Promotion authorization

Standing user merge authorization applies to this validated Phase 3 chain. PR #38 may be promoted without another authorization request only after its frozen exact CI/package and runtime closure gates pass. Promotion must use `[no-release]`.

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts are immutable.

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan backend

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + worker-local binary/bitmask greedy meshing with complete visual merge-key truth, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
