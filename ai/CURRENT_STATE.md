# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`.
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.4 dev8 — ordinary four-vertex rectangle emission-safety classification**.
- Active branch: `phase3/rectangle-emission-safety`.
- Active development version: `0.3.0-phase3-dev8`.
- Canonical draft PR: #40.
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

- all Phase 3/P3.2/P3.3/dev6/dev7 gates true;
- workers submitted/started/completed `263/263/263`, zero queue-full rejection/failure/shutdown-join failure;
- visibility `263` builds / `119,422` faces, determinism/reference `6/6` each;
- topology rectangles `48,846`, all `119,422` faces covered, `70,576` faces saved = 59.0%, primary coverage `263/263`, determinism/reference `6/6` each;
- render-key visible/eligible/unmapped/ambiguous `119,422 / 95,805 / 0 / 23,617`, determinism `6/6`;
- merge candidates `85,880`, covered eligible `95,805` exact, canonical passthrough `23,617` exact;
- candidate singleton/multi-face `78,562 / 7,318`;
- candidate faces saved `9,925` = 10.3% over eligible faces;
- candidate retained `515,280 = 85,880 * 6`;
- candidate coverage `263/263`, determinism `6/6`;
- workers/staging/arena/resources clean and Prism exit code `0`.

Final dev7 evidence head `ae53eb6c9a0deaa86f4e92f70bfd029ab1c2e579` passed workflow `32602740308`; PR #39 merged as `cec4ecb2432ec92f17a94a358895de6c2f21257e` with `[no-release]`.

The dev7 log contains no separately written visual verdict, so none is claimed. This does not weaken dev7 correctness because emitted GPU geometry did not change. Any later geometry-changing slice still requires explicit human visual validation.

## ACTIVE: P3.4 dev8 — ordinary four-vertex emission-safety classification

A-0121 activated dev8. A-0122 froze its exact mathematical/representation contract. A-0123 recorded the isolated classifier compile checkpoint. A-0124 removed an unnecessary validation-only scratch allocation and revalidated CI. A-0125 records full worker/coordinator integration and the canonical runtime package.

### Source-grounded problem

The current authoritative drawable stores exactly four vertices per source quad with float3 position, RGBA8 color, float2 atlas UV0 and packed light UV2. `SectionBakedQuadSnapshot` captures the exact per-corner values.

Dev7 render-key equality proves repeated unit faces have the same four-corner payload, but one large rectangle would interpolate that payload only once across the whole rectangle instead of restarting it at every cell. Ordinary atlas UV0 likewise cannot generally reproduce per-cell sprite resets without additional repeat metadata.

### Frozen exact continuity rule

For geometric corners `0=(uLow,vLow)`, `1=(uHigh,vLow)`, `2=(uLow,vHigh)`, `3=(uHigh,vHigh)`, a repeated field `P[0..3]` is one-large-quad compatible only when:

- width > 1: `P0 == P1` and `P2 == P3`;
- height > 1: `P0 == P2` and `P1 == P3`.

Apply independently to:

- exact ARGB color;
- packed light;
- raw atlas `(u,v)` float-bit pairs.

Both-axis merges therefore require a constant four-corner field for each attribute.

### Implemented dev8 sidecar

`OrdinaryQuadEmissionSafety` consumes only `RenderMergeCandidates`, `CanonicalFaceRenderKeys`, and `SectionBakedQuadSnapshot`.

It independently reconstructs each representative baked quad into canonical geometric corners and retains one byte/candidate:

- color interpolation safe;
- light interpolation safe;
- UV field safe;
- ordinary attribute safe = all three.

Maximum logical retained payload is 24,576 bytes/build. Worker scratch is fixed primitive storage.

Production pipeline is now:

`BinarySectionVisibility -> GreedySectionRectangles -> CanonicalFaceRenderKeys -> RenderMergeCandidates -> OrdinaryQuadEmissionSafety -> BakedSectionMesh`.

Completed worker tickets retain the safety sidecar. Metrics cover color/light/UV safe/unsafe multi-face candidates, ordinary-safe/unsafe multi-face candidates, safe covered faces/faces saved, per-direction safe accounting, exact retained bytes, build timing/scratch high-water, primary classification audits and first/every-64 determinism audits.

### Dev8 runtime gate

`ordinaryQuadEmissionSafetyEvidenceReady=true` requires all prior dev7 gates plus exact dev8 classifier accounting, one-byte retained accounting, primary classification audits matching builds, nonzero/matching determinism audits, and clean lifetime closure.

The gate deliberately does **not** require ordinary-safe multi-face candidates to be nonzero. Zero is valid evidence that the current ordinary atlas/block vertex representation cannot directly exploit dev7 grouping and a repeat-aware representation is required.

Dev8 remains sidecar-only:

- `ordinaryQuadEmissionSafetySidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` remains authoritative.

### Canonical dev8 package

Integrated runtime source/package head:

- `cc7e4d64bdf000635ed765a6e68a6c30cc9c2a8f`.

Exact workflow `32603270509`:

- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release publishing SKIPPED.

Canonical runtime JAR:

- `Obsidian-0.3.0-phase3-dev8.jar`;
- size `337,502` bytes;
- SHA-256 `f7155754683c6f484356cc4e729bd5de262b4acd355df05a49e55122903f9f4e`.

Package metadata: Minecraft `~26.2`, Fabric Loader `>=0.19.3`, Java `>=25`, client environment.

### Remaining dev8 closure

Real reference runtime evidence is still required. Run ordinary READY/rebuild/resource-reload activity, fully exit, and capture the complete shutdown tail plus Prism exit code. Promotion requires all prior gates plus `ordinaryQuadEmissionSafetyEvidenceReady=true` and clean workers/staging/arena/resources.

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
