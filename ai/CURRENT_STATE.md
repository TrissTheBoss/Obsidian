# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`.
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.4 dev8 — rectangle emission-safety classification**.
- Dev8 branch target: `phase3/rectangle-emission-safety`.
- Active development version target: `0.3.0-phase3-dev8`.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- Phase 2 through P2.7: COMPLETE.
- P3.1 dev1/dev2/dev3: COMPLETE — PRs #29/#32/#34.
- P3.2 dev4 binary visibility masks: COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3 dev5 greedy rectangle extraction: COMPLETE — PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.
- P3.4 dev6 canonical render-key sidecar: COMPLETE SLICE — PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.
- P3.4 dev7 render-key-aware merge-candidate sidecar: COMPLETE SLICE — PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`.

A-0101 remains the canonical proof for the already-closed Phase 2 fixed-target chunk unload/return lifecycle. Later Phase 3 tests do not repeat that far-travel sequence unless lifecycle semantics change.

## Proven P3.2–P3.4 foundations

`BinarySectionVisibility` is the immutable six-direction conservative canonical face representation: exactly 3,072 retained bytes/section, deterministic construction, and permanent independent `ReferenceFaceMesh` equivalence.

`GreedySectionRectangles` deterministically partitions P3.2 masks into topology-only rectangles with exact no-gap/no-overlap coverage. P3.3 does not emit those rectangles to the GPU.

`CanonicalFaceRenderKeys` maps a canonical face only when exactly one accepted baked SOLID/CUTOUT quad from the same source block is proven to be its exact full unit-cube face. Exact equivalence includes direction, layer, complete material/sprite identity, winding/corner signature, raw UV bits, exact ARGB and packed light. Its retained map is exactly 49,152 bytes/build.

`RenderMergeCandidates` deterministically groups the complete dev6-eligible canonical face set into same-render-key rectangles. P3.3 rectangle boundaries are not mandatory candidate boundaries. Each candidate retains one packed `int` rectangle plus one unsigned `short` source-quad representative = 6 logical bytes/candidate. Every build independently validates exact eligible-face coverage, no overlap, exact representative equivalence and canonical passthrough accounting.

The generalized `BakedSectionMesh` remains the authoritative GPU drawable.

## P3.4 dev7 closure — COMPLETE SLICE

A-0117 froze dev7, A-0118 recorded the pure classifier compile checkpoint, A-0119 recorded worker/coordinator integration and packaging, A-0120 recorded real runtime success, and A-0121 recorded promotion/dev8 activation.

Canonical dev7 runtime package:

- `Obsidian-0.3.0-phase3-dev7.jar`;
- size `320,735` bytes;
- SHA-256 `ef2ff6f1bc78469a9a65db486f735c178565c8982fd62aa1bb60901bf56ce1c7`.

Reference runtime closure:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- `renderMergeCandidateEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- workers submitted/started/completed `263/263/263`, steals `206`, zero queue-full rejection/failure/shutdown-join failure;
- visibility `263` builds / `119,422` faces, exact retained bytes, determinism/reference `6/6` each;
- topology rectangles `48,846`, all `119,422` faces covered, `70,576` faces saved = 59.0%, primary coverage `263/263`, determinism/reference `6/6` each;
- render-key visible/eligible/unmapped/ambiguous `119,422 / 95,805 / 0 / 23,617`, retained `12,926,976 = 263 * 49,152`, determinism `6/6`;
- merge candidates `85,880`, covered eligible `95,805` exact, canonical passthrough `23,617` exact;
- candidate singleton/multi-face `78,562 / 7,318`;
- candidate faces saved `9,925` = 10.3% over eligible faces;
- candidate retained `515,280 = 85,880 * 6`;
- candidate coverage `263/263`, determinism `6/6`;
- worker world reads after capture `0`, synchronous scene mesh builds `0`;
- dropped lifecycle events / unsafe stale installs `0 / 0`;
- workers/staging/arena/resources all clean;
- staging submitted/reclaimed `26,797,232 / 26,797,232`;
- arena allocations/retired/reclaimed `486/486/486`, used bytes `0`;
- resources retired/released `243/243`;
- normal render-thread `Stopping!` and Prism exit code `0`.

Final dev7 evidence head `ae53eb6c9a0deaa86f4e92f70bfd029ab1c2e579` passed workflow `32602740308`; build/upload succeeded and release publishing was skipped. PR #39 merged as `cec4ecb2432ec92f17a94a358895de6c2f21257e` with `[no-release]`.

The dev7 log contains no separately written visual verdict, so none is claimed. This does not weaken dev7 correctness because emitted GPU geometry did not change. Any later geometry-changing slice still requires explicit human visual validation.

## ACTIVE: P3.4 dev8 — rectangle emission-safety classification

A-0121 activates the next correctness-first slice. Dev8 remains **sidecar-only** and must not replace emitted GPU geometry.

Exact repository source establishes the problem:

- `BakedSectionMesh` uses four vertices/quad with float3 position, RGBA8 color, float2 atlas UV0 and packed light UV2;
- `SectionBakedQuadSnapshot` captures exact atlas UV floats plus exact per-corner color/light;
- dev7 render-key equality repeats the same four-corner payload per unit face, but one large rectangle would interpolate those values once across the whole rectangle instead of restarting them per cell;
- ordinary atlas UV0 on one large quad cannot generally represent per-cell sprite repetition without either stretching a sprite or leaving its atlas rectangle.

Dev8 must therefore classify dev7 candidates by **ordinary four-vertex emission safety** before any emission is attempted.

The frozen dev8 contract should distinguish at least:

1. **Interpolation-safe by merge axis** — source per-corner color/light values satisfy exact edge constraints needed for one large quad to match repeated unit-quad interpolation.
2. **Current UV representable** — one ordinary four-UV atlas quad can exactly reproduce the source UV field without per-cell reset metadata.
3. **Ordinary-quad safe** — both interpolation and UV conditions hold.
4. **Repeat-aware path required** — candidate grouping is valid but exact emission requires a future custom sprite-local repeat representation/shader/metadata path.

Dev8 must measure these classes on real terrain, retain deterministic bounded primitive metadata, and keep all prior differential/lifetime gates intact.

Until a later slice proves a replacement representation:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` remains authoritative.

P3.5 border/halo correctness remains planned and is not active.

## Promotion authorization

Standing user merge authorization applies to this validated Phase 3 chain once each frozen slice's exact CI/package/runtime gates pass. Internal commits/merges use `[no-release]`.

A slice that actually changes GPU-emitted terrain geometry requires renewed explicit human visual validation before promotion.

## Continuity model

Read in order before architecture or milestone changes:

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
