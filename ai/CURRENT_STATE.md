# Obsidian Current State

Last updated: 2026-08-28

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Active milestone: **P3.4 dev11 — repeat-aware greedy GPU emission canary**.
- Dev11 version target: `0.3.0-phase3-dev11`.
- Dev11 branch: to be cut from synchronized `main`.
- P3.5 is **not active**.
- Public release intent: keep the existing public checkpoint until a separate release decision.
- Runtime handoff: direct versioned `.jar`, never an Actions ZIP wrapper.

## Completed foundation

- Phase 0 COMPLETE.
- Phase 1 COMPLETE.
- Phase 2 through P2.7 COMPLETE.
- P3.1 dev1/dev2/dev3 COMPLETE — PRs #29/#32/#34.
- P3.2 dev4 binary visibility COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3 dev5 topology rectangles COMPLETE — PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.
- P3.4 dev6 canonical render-key sidecar COMPLETE — PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.
- P3.4 dev7 render-key-aware merge candidates COMPLETE — PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`.
- P3.4 dev8 ordinary four-vertex emission-safety COMPLETE — PR #40 merge `7a15f857a081fba642fcc28811ce88363b5abb66`.
- P3.4 dev9 repeat-aware UV descriptors COMPLETE — PR #41 merge `59471127162aaf02c9c87e679e1c4c361f968fac`.
- **P3.4 dev10 repeat-aware transport/sampling proof COMPLETE — PR #42 merge `3f75cf4d7e4a65aa6b12053fd75507d1cd292b34`.**

A-0101 permanently closes the old fixed-target unload/return lifecycle proof. Later Phase 3 slices do not repeat that far-travel sequence unless lifecycle semantics change.

## Durable P3.2–P3.4 truth

`BinarySectionVisibility` is the six-direction conservative canonical face topology: exactly 3,072 retained bytes/section, deterministic, and checked against the permanent independent `ReferenceFaceMesh` oracle.

`GreedySectionRectangles` partitions proven topology into deterministic packed 4-byte rectangles with exact no-gap/no-overlap coverage. Raw P3.3 topology rectangles are not themselves the render-equivalence contract.

`CanonicalFaceRenderKeys` admits only exact full source-cell canonical baked faces. Render equality includes direction, render layer, material/sprite/tint/shade/emission/animation identity, source corner order/winding/diagonal, raw UV bits, exact ARGB and packed light. Retained mapping is exactly 49,152 bytes/build.

`RenderMergeCandidates` forms deterministic same-render-equivalence rectangles across the full dev6-eligible set; P3.3 rectangle boundaries do not cap render candidates. Each dev7 candidate retains 6 logical bytes and exact eligible/passthrough accounting.

`OrdinaryQuadEmissionSafety` proves whether one ordinary four-vertex block-format rectangle can preserve repeated captured color/light/raw atlas UV fields. Ordinary atlas-UV reset is the dominant blocker; zero ordinary-safe multi-face candidates is valid.

`RepeatAwareUvDescriptors` proves an exact two-U by two-V source atlas rectangle and affine geometric-corner-to-UV orientation for multi-face candidates. Repetition is candidate-local then remapped into the exact source atlas rectangle; full-atlas wrapping is not the correctness model.

`RepeatAwareTransportProof` freezes the no-emission transport representation for dev9-safe candidates: candidate-local repeat/remap, positive outer-edge endpoint policy, explicit gradients from **unwrapped** repeat coordinates, source baked vertex order/diagonal preservation, and a requirement to bind the same live blocks-atlas view/sampler under the same resource epoch. It explicitly leaves internal repeat-line primitive-edge ownership/raster behavior open for the geometry-changing slice.

The existing generalized `BakedSectionMesh` remains the exact passthrough/oracle drawable for unsupported, noncanonical, ambiguous and unsafe geometry.

## P3.4 dev10 closure — COMPLETE

Canonical runtime package:

- `Obsidian-0.3.0-phase3-dev10.jar`
- size `376,137` bytes
- SHA-256 `f37531a48608d6a2e0c0143a7ef72dc6d0c8533f4871d21137ea85a69a8feaf9`
- integrated package source head `5dd6f04f3635f8c0436a49bf43396adcbc532bab`

A-0135 records reference runtime SUCCESS. Exact evidence head `b126b3ac6621ec510005581ecaf570018f9dfee4` passed workflow `33211921903`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, versioned release publishing SKIPPED. PR #42 merged with `[no-release]` as `3f75cf4d7e4a65aa6b12053fd75507d1cd292b34`. A-0136 records promotion and dev11 activation.

Reference dev10 runtime:

- every required gate through `repeatAwareTransportEvidenceReady=true` was true;
- `hardFailure=false`;
- workers submitted/started/completed `92/92/92`, steals `69`, queue-full rejection/failure/shutdown-join failure `0/0/0`;
- visibility: `92` builds / `43,967` faces, retained `282,624`, determinism/reference `4/4`;
- topology rectangles: `17,752` covering all `43,967` faces, `26,215` saved = `59.6%`, exact coverage/determinism/reference;
- render keys: visible/eligible/unmapped/ambiguous `43,967 / 35,380 / 0 / 8,587`;
- dev7 candidates: `32,129` covering `35,380` eligible faces exactly, singleton/multi `29,900 / 2,229`, faces saved `3,251` = `9.1%`;
- dev8 multi-face color safe/unsafe `2,219 / 10`, light safe/unsafe `2,229 / 0`, ordinary atlas-UV safe/unsafe `0 / 2,229`;
- dev9 representable/unrepresentable `2,229 / 0` = **100% representable**;
- dev9 repeat-aware four-vertex safe/unsafe `2,219 / 10`;
- dev10 transport records `2,219` exactly equal the dev9-safe set;
- dev10 covered faces `5,460`, faces saved `3,241`;
- explicit-gradient / outer-edge / same-atlas-sampler / raster-review obligations all `2,219`;
- internal S/T/both/union reset counts `900 / 1,409 / 90 / 2,219`;
- dev10 retained bytes `8,876 = 2,219 * 4`, proof audits `92/92`, determinism `4/4`;
- `repeatAwareTransportBoundaryRasterObligationOpen=true` is expected and was deliberately **not** treated as closed;
- scene workers submitted/completed `92/92`, installs `90`, READY transitions `10`, rebuilds `9`;
- dropped lifecycle / unsafe stale installs `0/0`;
- workers/staging/arena/resources clean;
- staging submitted/reclaimed `10,773,984 / 10,773,984`;
- arena allocations/retired/reclaimed `180/180/180`, used bytes `0`;
- resources retired/released `90/90`;
- normal render-thread `Stopping!`, Prism exit code `0`.

No GPU terrain geometry changed in dev10, so no new human visual verdict was required for promotion.

## ACTIVE: P3.4 dev11 — repeat-aware greedy GPU emission canary

A-0136 activates dev11. This is the first **geometry-changing** slice after dev6-dev10 proof work.

Objective:

> Replace only the exact dev10 transport-safe source-face groups with one repeat-aware large quad per admitted candidate in a bounded canary/render-validation path, while preserving every unsupported/generalized/ambiguous/unsafe source face on the existing exact path.

Before freezing implementation, inspect the exact repository/Minecraft 26.2 graphics path for:
- `RepeatAwareTransportProof`, `RepeatAwareUvDescriptors`, `BakedSectionMesh`;
- source-quad identity/removal and hybrid mesh construction;
- worker result install, staging and arena upload ownership;
- current block comparison shader/pipeline and texture/sampler binding;
- the public Blaze3D facilities available for candidate-local repeat coordinates and explicit-gradient texture sampling.

Public Blaze3D graphics remain preferred under D-0023. Do not widen D-0025's native seam for convenience.

### Dev11 non-negotiable correctness boundary

- Eligibility may only narrow the exact dev10 transport records; it may not widen them.
- Dev6/dev7 material/render equivalence remains authoritative.
- Dev8 color/light safety remains authoritative.
- Dev9 exact raw atlas bounds/orientation remain authoritative.
- Dev10 repeat/remap, explicit-gradient, same-atlas/sampler, positive outer-edge and source-order/diagonal obligations remain authoritative.
- Unsupported/generalized/ambiguous/unsafe faces remain exact passthrough.
- No source face may be both source-emitted and merged-emitted.
- No covered source face may disappear without exactly one merged replacement.
- Render-thread live capture/GPU ownership remains render-thread-only; worker live-world reads after capture remain zero.
- Staging/arena/resource memory remains bounded and completion-gated.
- `renderCorrectMergeKeyComplete=false` remains until a later explicit completion proof says otherwise.
- Use a new explicit emission flag (`repeatAwareGreedyGpuEmission=true` or equivalent); do not misuse `greedyRectangleGpuEmission` to imply raw P3.3 topology rectangles are drawn.

### Mandatory dev11 visual/raster gate

Dev11 cannot be promoted from counters alone. It requires a fresh, explicit human visual verdict because emitted geometry changes.

The reference-machine exercise must intentionally inspect:
- repeated planar textures at close and oblique angles across internal integer reset lines;
- rectangle T-junctions (long edge meeting shorter edges) and section boundaries;
- camera movement/rotation for cracks, shimmer and mip/filter instability;
- block break/place rebuild;
- F3+T resource reload/rebuild;
- clean shutdown/lifetime accounting.

Explicitly check for texture stretch/atlas bleed, repeat-line seams, T-junction cracks/z-fighting, wrong winding/diagonal/culling, color/light mismatch, duplicate source faces and missing faces.

If a raster artifact is observed, follow D-0024: prefer targeted selective splitting/mitigation or exclude the affected candidate class. Do not globally conform the mesh or abandon greedy meshing without evidence.

P3.6 remains the broader T-junction policy milestone; dev11 may solve only the concrete canary requirements without falsely completing P3.6.

## Promotion authorization

Standing merge authorization applies to the Phase 3 chain only after each frozen slice's exact gates pass. Internal commits/merges use `[no-release]`.

**Dev11 additionally requires an explicit human visual PASS before promotion.** Do not infer a PASS from a clean log or absence of complaints.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.

## Reference machine

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

D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + complete render-equivalence greedy key + targeted T-junction mitigation, D-0025 narrow native compute/storage seam, D-0026 explicit compute-write/indirect-read synchronization, and D-0027 public fixed-count indirect baseline remain active.