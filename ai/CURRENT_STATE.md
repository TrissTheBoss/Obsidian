# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Active milestone: **P3.4 dev10 — repeat-aware transport/sampling correctness proof**.
- Active branch: `phase3/repeat-aware-transport-proof`.
- Draft PR: #42 — `Phase 3 dev10: repeat-aware transport proof`.
- Version: `0.3.0-phase3-dev10`.
- Status: **implementation/package CI GREEN; reference runtime pending**.
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

`RepeatAwareTransportProof` is the dev10 no-emission bridge from dev9 representation to a later large-quad path. It independently revalidates the representative UV endpoints/orientation, preserves source baked vertex order/diagonal, freezes candidate-local repeat/remap and explicit-gradient obligations, and records that a future path must bind the same live blocks-atlas view/sampler. It deliberately keeps internal repeat-line raster ownership as an explicit open later obligation.

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

A-0132 activates dev10. A-0133 freezes the dev10 contract. A-0134 records implementation and canonical package CI. P3.5 is not active.

### Frozen transport algebra and obligations

For candidate extent `W x H`, candidate-local repeat coordinates use a half-open cell rule plus explicit positive outer endpoint:

- `cellS = min(floor(s), W - 1)`;
- `cellT = min(floor(t), H - 1)`;
- local unit-cell coordinates are `x = s - cellS`, `y = t - cellT`.

Dev9's affine square orientation remaps `(x,y)` through exact raw `uLow/uHigh/vLow/vHigh` source atlas bounds. A future shader/path must use explicit gradients derived from the **unwrapped** candidate-local `s,t`, not derivatives of wrapped/`fract` coordinates, so repeat discontinuities do not create mip/filter derivative spikes.

The future geometry-changing path is required to bind the **same live blocks-atlas texture view and sampler** as the existing block path under the same resource epoch. Dev10 does not invent a parallel immutable sampler/filter/mip model.

Each retained transport proof record is exactly 4 logical bytes:

- unsigned-short dev7 candidate index;
- one byte source geometric-corner order in source baked vertex order;
- one byte obligation flags.

Flags prove/record:

- explicit gradient required;
- internal S reset when width > 1;
- internal T reset when height > 1;
- positive outer-edge endpoint policy required;
- same atlas view/sampler required;
- internal reset-line raster review remains open.

Every dev10 record is a multi-face candidate and therefore has at least one internal reset boundary. The open raster flag is **expected, nonblocking evidence in dev10** because dev10 emits no large quad.

### Production worker integration

Every successful worker job now builds:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. `RenderMergeCandidates`;
5. `OrdinaryQuadEmissionSafety`;
6. `RepeatAwareUvDescriptors`;
7. `RepeatAwareTransportProof`;
8. unchanged `BakedSectionMesh`.

Completed tickets retain dev10 proof records. Workers expose source/safe/record, coverage/savings, obligation, direction, retained byte, build timing/scratch, primary proof-audit and determinism metrics.

The duplicate determinism cadence rebuilds dev10 proof from the duplicate dev6/dev7/dev8/dev9 chain and requires `contentEquals` before the unchanged baked-mesh determinism check.

### Dev10 runtime gate

New required gate:

- `repeatAwareTransportEvidenceReady=true`.

It sits strictly after `repeatAwareUvEvidenceReady=true` and requires:

- transport builds > 0 and >= completed worker jobs;
- source multi-face == dev9 multi-face;
- source representable == dev9 representable;
- source four-vertex-safe == dev9 repeat-aware-four-vertex-safe;
- transport record count == source four-vertex-safe count;
- unsafe == source multi-face - records;
- explicit-gradient / outer-edge / same-atlas-sampler / raster-review obligation counts == records;
- `internalS + internalT - internalBoth == records`;
- directional records/covered/saved sums exact;
- faces saved == covered faces - records;
- retained bytes == `records * 4`;
- scratch uses >= builds;
- primary proof audits == builds and all match;
- determinism audits > 0 and all match;
- all prior worker/lifecycle/staging/arena/resource gates remain clean.

`repeatAwareTransportBoundaryRasterObligationOpen=true` is expected when records exist and is **not** a dev10 failure. It must be resolved/exercised by the later geometry-changing P3.4 slice.

### Dev10 package / CI

Pure core:

- commit `360726418f009713199686e89dd42b3ea7b6ad1a`;
- workflow `32605684776` SUCCESS.

Worker integration:

- commit `23f1d360bf2164657d41a55e3c5d985d0643bca7`;
- workflow `32605901656` SUCCESS.

Canonical integrated source/package head:

- `5dd6f04f3635f8c0436a49bf43396adcbc532bab`.

Integrated workflow:

- `32606020092`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

Artifact:

- id `9484142328`;
- wrapper `obsidian-9b408838882837ec7be8ae39cf56ad765e811f40`;
- size `546,484` bytes;
- digest `sha256:e907b9652dd0b7d54a21d8a76d96fbfec91d207f038f327dfec3b688eb8bb8cf`.

Canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev10.jar`;
- size **376,137 bytes**;
- SHA-256 **`f37531a48608d6a2e0c0143a7ef72dc6d0c8533f4871d21137ea85a69a8feaf9`**.

Package metadata confirms client environment, Minecraft `~26.2`, Fabric Loader `>=0.19.3`, Java `>=25`, and packaged dev10 proof/evidence/worker/coordinator/bootstrap classes.

### Reference runtime handoff

Use the canonical direct dev10 JAR on the reference Vulkan system:

1. let the initial 3x3 async scene reach READY;
2. perform a normal block break/place rebuild and let it reach READY;
3. press F3+T and let the scene reach READY again;
4. ordinary movement/recentering is useful but optional;
5. fully exit Minecraft/Prism and retain the complete shutdown tail through `Process exited with code 0`.

Required final flags include all prior dev9 gates plus:

- `repeatAwareTransportEvidenceReady=true`;
- `repeatAwareTransportSidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`.

`repeatAwareTransportBoundaryRasterObligationOpen=true` is expected/nonblocking. The old fixed-anchor far-travel sequence is not required.

Keep PR #42 draft/unmerged until runtime closure. Standing Phase 3 authorization covers promotion after the frozen runtime gates pass.

Dev10 changes no emitted terrain geometry, so no new visual verdict is required for dev10 promotion. The subsequent geometry-changing P3.4 slice must freeze its own emission contract and requires renewed explicit human visual validation before promotion.

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