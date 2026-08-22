# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Active milestone: **P3.4 dev9 — repeat-aware UV descriptor / representability sidecar**.
- Target branch: `phase3/repeat-aware-uv-descriptor`.
- Target version: `0.3.0-phase3-dev9`.
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

A-0101 remains the canonical fixed-target unload/return lifecycle proof. Later Phase 3 slices do not repeat the old far-travel sequence unless lifecycle semantics change.

## Durable P3.2–P3.4 truth

`BinarySectionVisibility` is the six-direction conservative canonical face topology: exactly 3,072 retained bytes/section, deterministic, and permanently checked against the independent `ReferenceFaceMesh` oracle.

`GreedySectionRectangles` deterministically partitions that topology into packed 4-byte rectangles with exact no-gap/no-overlap coverage. Those topology rectangles are not emitted to the GPU.

`CanonicalFaceRenderKeys` maps a canonical face only when exactly one accepted baked SOLID/CUTOUT quad from the same source block is proven to be the exact full unit-cube face. Render equality includes exact direction, layer, material/sprite/tint/shade/emission/animation identity, corner order/winding, raw UV bits, exact ARGB and packed light. Retained mapping: exactly 49,152 bytes/build.

`RenderMergeCandidates` scans the full dev6-eligible face set and forms deterministic same-render-key rectangles. P3.3 topology boundaries do not artificially cap candidates. Each candidate retains a packed rectangle plus representative source quad = 6 logical bytes/candidate, with exact eligible coverage and passthrough accounting.

`OrdinaryQuadEmissionSafety` classifies whether a dev7 candidate can be represented by one ordinary four-vertex quad under the current captured block-vertex fields. For a repeated geometric-corner field `P[0..3]`, width > 1 requires `P0==P1 && P2==P3`; height > 1 requires `P0==P2 && P1==P3`. The rule is applied independently to exact ARGB, packed light and raw atlas UV pairs.

The generalized `BakedSectionMesh` remains the authoritative GPU drawable.

## P3.4 dev8 closure — COMPLETE

Canonical runtime package:

- `Obsidian-0.3.0-phase3-dev8.jar`
- size `337,502` bytes
- SHA-256 `f7155754683c6f484356cc4e729bd5de262b4acd355df05a49e55122903f9f4e`
- package source head `cc7e4d64bdf000635ed765a6e68a6c30cc9c2a8f`

A-0126 records reference runtime SUCCESS. Final evidence head `f4b8028cb46708a8990b1c4456bc29e5bd993fa9` passed workflow `32604062038`; build/upload succeeded and release publishing was skipped. PR #40 merged with `[no-release]` as `7a15f857a081fba642fcc28811ce88363b5abb66`.

Reference runtime:

- all Phase 3/P3.2/P3.3/dev6/dev7/dev8 gates true, including `ordinaryQuadEmissionSafetyEvidenceReady=true`;
- workers submitted/started/completed `234/234/234`, 190 steals, zero queue-full rejection/failure/shutdown-join failure;
- visibility: 234 builds / 94,258 faces, exact bytes, determinism/reference `5/5`;
- topology rectangles: 38,884 covering all 94,258 faces, 55,374 saved = 58.7%, coverage/determinism/reference exact;
- render keys: visible/eligible/unmapped/ambiguous `94,258 / 74,152 / 0 / 20,106`, determinism `5/5`;
- dev7 candidates: 65,533 covering 74,152 eligible faces exactly, passthrough 20,106, singleton/multi `59,150 / 6,383`, faces saved 8,619 = 11.6%, coverage `234/234`, determinism `5/5`;
- dev8 color-safe/unsafe multi-face `6,352 / 31`;
- dev8 light-safe/unsafe multi-face `6,383 / 0`;
- dev8 ordinary-atlas-UV-safe/unsafe multi-face `0 / 6,383`;
- dev8 combined ordinary-safe/unsafe multi-face `0 / 6,383`;
- repeat-aware-required `6,383`;
- dev8 retained bytes `65,533 = 65,533 * 1`, classification audits `234/234`, determinism `5/5`;
- worker world reads after capture 0; synchronous scene mesh builds 0;
- dropped lifecycle events / unsafe stale installs `0 / 0`;
- workers/staging/arena/resources clean;
- staging submitted/reclaimed `23,115,616 / 23,115,616`;
- arena allocations/retired/reclaimed `454/454/454`, used bytes 0;
- resources retired/released `227/227`;
- render-thread `Stopping!` and Prism exit code 0.

No separate human visual verdict is claimed from the pasted dev8 log. Dev8 did not change emitted terrain geometry, so its frozen sidecar/runtime gates govern promotion. Any geometry-changing slice still requires renewed explicit human visual validation.

### Dev8 architectural conclusion

The current ordinary atlas UV0 representation cannot directly exploit **any** observed multi-face dev7 candidate. Light interpolation was safe for every multi-face candidate and color interpolation for all but 31, but UV repetition failed for all 6,383.

This is a representation limitation, not a failure of render-key grouping. Full-atlas sampler repeat is not an acceptable fix because it would wrap across the atlas rather than within the source sprite. A future exact path needs sprite-local repeat coordinates followed by deterministic remapping into that sprite's atlas rectangle/orientation.

## ACTIVE: P3.4 dev9 — repeat-aware UV descriptor / representability

A-0127 activates dev9. P3.5 is not active.

Dev9 is **sidecar-only**. It must freeze and prove a compact deterministic descriptor for exact per-cell sprite repetition on a dev7 candidate without changing GPU geometry.

Required correctness direction:

1. consume immutable dev7 candidates, dev6 render keys, dev8 safety evidence and exact `SectionBakedQuadSnapshot` truth;
2. use the representative canonical baked quad mapped to geometric corners;
3. prove its four raw atlas UV corners form exactly two U values × two V values — an axis-aligned source sprite rectangle;
4. preserve the exact geometric-corner-to-UV-corner permutation, including flip/rotation rather than silently normalizing orientation;
5. define repeat in candidate-local cell coordinates, then remap the repeated local coordinate into the proven source atlas rectangle; never rely on wrapping the full atlas;
6. measure repeat-aware UV representability for all multi-face candidates;
7. combine repeat-aware UV representability with dev8 color/light interpolation safety to measure `repeatAwareFourVertexSafe` candidates;
8. keep color-unsafe or UV-unrepresentable candidates on split/passthrough evidence;
9. retain bounded primitive metadata with exact source fingerprints, accounting and determinism audits;
10. preserve all previous worker/lifecycle/lifetime gates.

Dev9 must keep:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` authoritative.

A later slice may implement a custom vertex/shader representation only after dev9 proves its descriptor. That geometry-changing slice must separately address raster/T-junction obligations relevant to its emission path and requires renewed explicit human visual validation before promotion.

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

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + worker-local binary/bitmask greedy meshing with complete output-affecting merge truth, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
