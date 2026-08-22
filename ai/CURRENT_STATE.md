# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.4 dev7 — render-key-aware merge-candidate sidecar**.
- Active branch: `phase3/render-merge-candidate-sidecar`.
- Active development version: `0.3.0-phase3-dev7`.
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
- P3.4 dev6 canonical render-key sidecar slice: COMPLETE — PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.

A-0101 remains the canonical proof for the already-closed Phase 2 fixed-target chunk unload/return lifecycle. Later Phase 3 runtime tests do not need to repeat that long-distance sequence unless lifecycle hooks or semantics materially change.

## Proven P3.2 / P3.3 topology foundation

`BinarySectionVisibility` is the immutable worker-side conservative canonical face representation:

- WEST/EAST/DOWN/UP/NORTH/SOUTH directional masks;
- 4,096 bits / 64 `long` words per direction;
- exactly 3,072 retained bytes per complete mask set;
- exact conservative rule `SUPPORTED_FULL_CUBE && neighbor == AIR`;
- deterministic construction and permanent independent `ReferenceFaceMesh` equivalence.

`GreedySectionRectangles` deterministically partitions those masks into packed topology-only rectangles with exact no-gap/no-overlap coverage. Dev5 runtime measured 21,286 rectangles over 48,261 faces, a 55.8% topology reduction, while the generalized `BakedSectionMesh` remained the GPU drawable.

## P3.4 dev6 render-key sidecar — COMPLETE SLICE

A-0112 froze dev6; A-0113 records implementation/package; A-0114 preserves the initially incomplete shutdown paste; A-0115 supplies the complete runtime closure; A-0116 records promotion and activates dev7.

`CanonicalFaceRenderKeys` consumes immutable `SectionSnapshot`, `BinarySectionVisibility`, and `SectionBakedQuadSnapshot`. A canonical face is render-key eligible only when exactly one accepted baked SOLID/CUTOUT quad from the same source block is proven to be the exact full unit-cube face for that direction.

The recognizer rejects offset/inset/partial/rotated/non-axis-aligned/duplicate-corner/ambiguous generalized geometry. Exact render equivalence requires:

- canonical direction/orientation;
- render layer;
- complete `MaterialIdentity` equality;
- geometric corner-order/winding signature;
- exact raw UV bits per geometric corner;
- exact ARGB per geometric corner;
- exact packed light per geometric corner.

Block/state ID equality alone is never sufficient.

The retained map is 24,576 `short` slots = exactly 49,152 bytes/build: `0` unmapped, positive unique source quad, `-1` ambiguous.

### Dev6 canonical runtime closure

Reference runtime on the Windows 11 / Radeon RX 6800 XT Vulkan machine ended with `Process exited with code 0` and proved:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- worker submitted/started/completed `229/229/229`, steals `172`, zero queue-full rejection/failure/shutdown-join failure;
- visibility builds/faces `229 / 96,038`, exact 3,072 bytes/build, determinism/reference `4/4` each;
- rectangle builds/count/covered faces `229 / 38,917 / 96,038`, faces saved `57,121` = 59.4%, mask audits `229/229`, determinism/reference `4/4` each;
- render-key builds `229`;
- render-key visible/eligible/unmapped/ambiguous `96,038 / 77,157 / 0 / 18,881`, exact accounting;
- recognized canonical / ignored noncanonical baked quads `114,919 / 73,977`;
- same/different/ineligible adjacencies `10,328 / 70,564 / 12,389`;
- retained render-key bytes `11,255,808 = 229 * 49,152`;
- render-key determinism `4/4`;
- dropped lifecycle / unsafe stale installs `0 / 0`;
- workers/staging/arena/resources all clean;
- staging submitted/reclaimed `25,244,616 / 25,244,616`;
- arena allocations/retired/reclaimed `450/450/450`, used bytes `0`;
- resources retired/released `225/225`.

The user reported visuals looked fine. Dev6 still did not change GPU geometry, so that is a regression guard.

The final A-0115 evidence head `efbaf7d15be5a4472700c861535ee6e4ef8fc038` passed exact CI run `32601469374`; build/upload succeeded and release publishing was skipped. PR #38 merged with `[no-release]` as `967c4511cd11cd721886feae6d146f4412790a6d`.

## ACTIVE: P3.4 dev7 — render-key-aware merge-candidate sidecar

Dev7 is the next correctness-first P3.4 slice. It remains **sidecar-only** and must not replace emitted GPU geometry.

Intended inputs:

1. proven `BinarySectionVisibility` canonical face topology;
2. proven `CanonicalFaceRenderKeys` eligibility and exact face render equivalence;
3. P3.3 `GreedySectionRectangles` retained as a topology/differential input, but not necessarily as a partition constraint if that would prevent a larger valid key-aware rectangle.

The dev7 product should deterministically partition the dev6-eligible canonical face set into same-render-key merge candidates while preserving exact canonical passthrough accounting for ambiguous/ineligible faces. Arbitrary generalized baked-model geometry stays on the existing `BakedSectionMesh` path.

Required correctness properties before dev7 can close:

- every candidate contains only dev6-eligible faces;
- every covered face is exactly render-equivalent to the candidate representative/source key;
- candidate rectangles have no overlap;
- candidate coverage equals the complete eligible canonical face set exactly, globally and by direction;
- visible canonical passthrough count equals visible minus eligible;
- deterministic extraction/order and bounded primitive storage/scratch;
- nonzero multi-face candidates and measurable candidate face reduction on real terrain;
- existing P3.2/P3.3/dev6 differential gates remain true;
- zero worker/lifecycle/lifetime regressions and clean process exit.

### Critical dev7 rendering boundary

Pairwise face render-key equality is necessary but is **not yet sufficient** to emit one large quad safely. Replacing repeated cell quads with one rectangle can change interpolation of per-corner light/color/AO, and identical cell-local atlas UVs do not by themselves define correct texture repetition over a larger rectangle.

Therefore dev7 must continue to report:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- the generalized `BakedSectionMesh` remains the authoritative GPU drawable.

A later P3.4 emission slice must separately freeze and prove rectangle-level UV and interpolation semantics before changing GPU geometry. That later geometry-changing slice requires renewed explicit human visual validation.

P3.5 border/halo correctness remains planned and is **not** active yet.

## Promotion authorization

Standing user merge authorization applies to this validated Phase 3 chain. A dev7 PR may be promoted without another authorization request only after its frozen exact CI/package/runtime gates pass. Promotion must use `[no-release]`.

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

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + worker-local binary/bitmask greedy meshing with complete output-affecting merge truth, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
