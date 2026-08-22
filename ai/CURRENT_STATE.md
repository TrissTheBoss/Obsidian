# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Active milestone: **P3.4 dev10 — repeat-aware transport/sampling correctness proof**.
- Dev10 target branch: to be cut from synchronized `main`.
- Dev10 target version: `0.3.0-phase3-dev10`.
- Public release intent: keep the existing public checkpoint until a separate release decision.
- Runtime handoff: direct versioned `.jar`, never an Actions ZIP wrapper.

## Completed foundation

- Phase 0: COMPLETE.
- Phase 1: COMPLETE — closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- Phase 2 through P2.7: COMPLETE.
- P3.1 dev1/dev2/dev3: COMPLETE — PRs #29/#32/#34.
- P3.2 dev4 binary visibility: COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3 dev5 topology rectangles: COMPLETE — PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.
- P3.4 dev6 canonical render-key sidecar: COMPLETE — PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.
- P3.4 dev7 render-key-aware merge candidates: COMPLETE — PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`.
- P3.4 dev8 ordinary four-vertex emission-safety classifier: COMPLETE — PR #40 merge `7a15f857a081fba642fcc28811ce88363b5abb66`.
- P3.4 dev9 repeat-aware UV descriptor / representability: COMPLETE — PR #41 merge `59471127162aaf02c9c87e679e1c4c361f968fac`.

A-0101 remains the canonical fixed-target unload/return lifecycle proof. Later Phase 3 slices do not repeat the old far-travel sequence unless lifecycle semantics change.

## Durable P3.2–P3.4 truth

`BinarySectionVisibility` is the six-direction conservative canonical face topology: exactly 3,072 retained bytes/section, deterministic, and permanently checked against the independent `ReferenceFaceMesh` oracle.

`GreedySectionRectangles` deterministically partitions that topology into packed 4-byte rectangles with exact no-gap/no-overlap coverage. Those topology rectangles are not emitted to the GPU.

`CanonicalFaceRenderKeys` maps a canonical face only when exactly one accepted baked SOLID/CUTOUT quad from the same source block is proven to be the exact full unit-cube face. Render equality includes exact direction, layer, material/sprite/tint/shade/emission/animation identity, corner order/winding, raw UV bits, exact ARGB and packed light. Retained mapping: exactly 49,152 bytes/build.

`RenderMergeCandidates` scans the full dev6-eligible face set and forms deterministic same-render-key rectangles. P3.3 topology boundaries do not artificially cap candidates. Each candidate retains a packed rectangle plus representative source quad = 6 logical bytes/candidate, with exact eligible coverage and passthrough accounting.

`OrdinaryQuadEmissionSafety` classifies whether a dev7 candidate can be represented by one ordinary four-vertex quad under captured color/light/raw-atlas-UV fields. Dev8 proved ordinary atlas UV reset is the dominant blocker.

`RepeatAwareUvDescriptors` proves whether a multi-face candidate can preserve the representative source sprite by repeating in candidate-local sprite coordinates and remapping into the same exact raw atlas rectangle/orientation. Full-atlas sampler wrapping is not the correctness model.

The generalized `BakedSectionMesh` remains the authoritative GPU drawable.

## P3.4 dev9 closure — COMPLETE

Canonical runtime package:

- `Obsidian-0.3.0-phase3-dev9.jar`
- size `354,912` bytes
- SHA-256 `4f06323d7d60288a2c2bb48676918842e3e9cfa9bd604156c9e24aa1aedc0b46`
- package source head `0bca09023876cf661171749f7ef86f7f287307c0`

A-0131 records reference runtime SUCCESS. Final evidence head `378677a08f71c6b783750d47cfc3bac818705e60` passed workflow `32605212651`; build/upload succeeded and release publishing was skipped. PR #41 merged with `[no-release]` as `59471127162aaf02c9c87e679e1c4c361f968fac`. A-0132 records promotion and dev10 activation.

Reference runtime:

- all Phase 3/P3.2/P3.3/dev6/dev7/dev8/dev9 gates true, including `repeatAwareUvEvidenceReady=true`;
- workers submitted/started/completed `261/261/261`, 193 steals, zero cancellation/queue-full rejection/failure/shutdown-join failure;
- visibility: 261 builds / 77,748 faces, exact retained bytes, determinism/reference `6/6`;
- topology rectangles: 34,559 covering all 77,748 faces, 43,189 saved = 55.5%, coverage/determinism/reference exact;
- render keys: visible/eligible/unmapped/ambiguous `77,748 / 54,290 / 0 / 23,458`, determinism `6/6`;
- dev7 candidates: 47,688 covering 54,290 eligible faces exactly, passthrough 23,458, singleton/multi `42,421 / 5,267`, faces saved 6,602 = 12.1%, coverage `261/261`, determinism `6/6`;
- dev8 color-safe/unsafe multi-face `5,266 / 1`;
- dev8 light-safe/unsafe multi-face `5,267 / 0`;
- dev8 ordinary-atlas-UV-safe/unsafe multi-face `0 / 5,267`;
- dev9 repeat-aware UV representable/unrepresentable `5,267 / 0` = **100% representable** in the observed runtime set;
- dev9 repeat-aware four-vertex safe/unsafe `5,266 / 1`;
- dev9 safe covered faces 11,867; safe faces saved 6,601;
- dev9 retained bytes `100,073 = 5,267 * 19`, classification audits `261/261`, determinism `6/6`;
- the sole repeat-aware four-vertex exclusion is the single color-interpolation failure; UV and light are no longer blockers in the observed set;
- scene worker submitted/completed/installed `261/261/261`;
- scene READY transitions 29, rebuilds 28;
- dropped lifecycle events / unsafe stale installs `0 / 0`;
- workers/staging/arena/resources clean;
- staging submitted/reclaimed `25,216,272 / 25,216,272`;
- arena allocations/retired/reclaimed `522/522/522`, used bytes 0;
- resources retired/released `261/261`;
- render-thread `Stopping!` and Prism exit code 0.

No geometry changed in dev9, so no new visual verdict was required for dev9 promotion. Any geometry-changing P3.4 slice still requires renewed explicit human visual validation.

## ACTIVE: P3.4 dev10 — repeat-aware transport/sampling correctness proof

A-0132 activates dev10. P3.5 is not active.

Dev10 is **proof-first and no-emission**. It must freeze and validate the actual representation that a later geometry-changing slice could use, without yet replacing `BakedSectionMesh`.

Required correctness direction:

1. define the exact candidate-local repeat-coordinate transport representation;
2. preserve the dev9 raw source-atlas rectangle and orientation without normalization loss;
3. deterministically map candidate-local repeated coordinates into that source sprite rectangle;
4. define integer repeat-boundary and candidate-edge semantics;
5. inspect/prove atlas filtering, padding/inset, mip and edge assumptions needed to avoid bleeding/seams;
6. preserve render-layer/material/sprite/tint/shade/emission/animation identity and keep unsupported/generalized geometry on passthrough;
7. identify the raster/T-junction obligations relevant to the eventual large-quad path under D-0024, preferring stable positions and selective mitigation/splitting rather than global conforming subdivision;
8. keep primitive metadata bounded and deterministic;
9. preserve render-thread capture/GPU ownership, bounded workers/staging/arena/resource lifetime and zero worker live-world reads.

Dev10 must keep:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` authoritative.

A later geometry-changing P3.4 slice may consume dev10 proof only after its own frozen emission contract is established. That later slice requires renewed explicit human visual validation before promotion.

## Promotion authorization

Standing merge authorization applies to the Phase 3 milestone chain after each frozen slice's exact CI/package/runtime gates pass. Internal commits/merges use `[no-release]`.

## Continuity order

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
- AMD Radeon RX 6800 XT 16 GB
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan backend

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + worker-local binary/bitmask greedy meshing with complete output-affecting merge truth and targeted T-junction mitigation, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.