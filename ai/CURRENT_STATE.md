# Obsidian Current State

Last updated: 2026-08-23

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`.
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.4 dev7 — render-key-aware merge-candidate sidecar**.
- Active branch: `phase3/render-merge-candidate-sidecar`.
- Canonical draft PR: #39 against `main`.
- Active development version: `0.3.0-phase3-dev7`.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- Phase 2 through P2.7: COMPLETE.
- P3.1 dev1/dev2/dev3: COMPLETE — PRs #29/#32/#34.
- P3.2 dev4 binary visibility masks: COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3 dev5 greedy rectangle extraction: COMPLETE — PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.
- P3.4 dev6 canonical render-key sidecar slice: COMPLETE — PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.

A-0101 remains the canonical proof for the already-closed Phase 2 fixed-target chunk unload/return lifecycle. Later Phase 3 runtime tests do not need to repeat that long-distance sequence unless lifecycle hooks or semantics materially change.

## Proven P3.2 / P3.3 / dev6 foundation

`BinarySectionVisibility` is the immutable worker-side conservative canonical face representation: six directional masks, exactly 3,072 retained bytes/section, conservative `SUPPORTED_FULL_CUBE && neighbor == AIR`, deterministic construction, and permanent independent `ReferenceFaceMesh` equivalence.

`GreedySectionRectangles` deterministically partitions those masks into packed topology-only rectangles with exact no-gap/no-overlap coverage. Dev5 runtime measured 21,286 rectangles over 48,261 faces, a 55.8% topology reduction, while `BakedSectionMesh` remained the GPU drawable.

`CanonicalFaceRenderKeys` maps a canonical face only when exactly one accepted baked SOLID/CUTOUT quad from the same source block is proven to be its exact full unit-cube face. Render equivalence requires exact direction/orientation, layer, complete `MaterialIdentity`, geometric corner-order/winding signature, raw per-corner UV bits, exact ARGB, and packed light. Offset/inset/partial/rotated/non-axis-aligned/duplicate-corner/ambiguous generalized geometry remains passthrough. The retained map is exactly 49,152 bytes/build.

### Dev6 canonical runtime closure

A-0115 proved on the reference Windows 11 / Radeon RX 6800 XT Vulkan system:

- all Phase 3/P3.2/P3.3/dev6 gates true, including `renderMergeKeyEvidenceReady=true`;
- workers `229/229/229` submitted/started/completed, 172 steals, zero queue-full rejection/failure/shutdown-join failure;
- visibility `229` builds / `96,038` faces, exact retained bytes, determinism/reference `4/4` each;
- topology rectangles `38,917` covering all `96,038` faces, `57,121` faces saved = 59.4%, audits `4/4`;
- render-key visible/eligible/unmapped/ambiguous `96,038 / 77,157 / 0 / 18,881`;
- same/different/ineligible render-key adjacencies `10,328 / 70,564 / 12,389`;
- render-key retained bytes `11,255,808 = 229 * 49,152`, determinism `4/4`;
- worker world reads after capture `0`, synchronous scene mesh builds `0`;
- workers/staging/arena/resources clean and process exit code `0`.

The user reported visuals looked fine. Final evidence-head CI run `32601469374` passed and PR #38 merged with `[no-release]`.

## ACTIVE: P3.4 dev7 — render-key-aware merge-candidate sidecar

A-0117 freezes the dev7 contract. A-0118 records the isolated pure-core compile checkpoint. A-0119 records completed production integration and the canonical runtime package.

`RenderMergeCandidates` consumes:

1. proven `BinarySectionVisibility` canonical face topology;
2. proven P3.3 `GreedySectionRectangles` source topology identity/differential evidence;
3. dev6 `CanonicalFaceRenderKeys` eligibility and exact face equivalence;
4. immutable `SectionBakedQuadSnapshot` render truth.

P3.3 rectangle boundaries are **not** mandatory candidate boundaries. Dev7 scans each direction/plane over the complete eligible face set and greedily forms deterministic rectangles only while every face is unconsumed, dev6-eligible, and exactly render-equivalent to the seed representative.

Each candidate retains one packed P3.3-layout `int` plus one unsigned `short` representative baked-quad index: **6 logical bytes/candidate**, maximum 24,576 candidates / 147,456 logical retained bytes per build. Worker scratch is fixed primitive storage.

Every build self-validates source fingerprints, bounds, representative identity, no overlap, visibility membership, dev6 eligibility, exact representative equivalence, complete eligible-face coverage globally/by direction, canonical passthrough accounting, singleton/multi-face accounting, faces-saved accounting, and retained bytes.

### Dev7 implementation/package status — GREEN; runtime pending

Production `SectionMeshWorkerPool` jobs now build, in order:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. `RenderMergeCandidates`;
5. existing generalized `BakedSectionMesh` drawable.

Completed tickets retain the dev7 sidecar. Workers expose candidate build/count/coverage/passthrough/singleton/multi-face/directional/retained-byte/build-time/scratch metrics, one exact coverage audit per primary successful candidate build, and duplicate candidate `contentEquals` determinism on the existing first/every-64-local-completions cadence.

`FrameCoordinator` now requires and logs `renderMergeCandidateEvidenceReady=true` after all prior gates. The dev7 gate requires:

- nonzero candidate builds covering completed production jobs;
- candidate-covered eligible faces exactly equal dev6 render-key eligible faces;
- canonical passthrough exactly equals visible minus eligible;
- positive candidate count not exceeding eligible faces;
- singleton + multi-face counts exactly equal candidate count;
- at least one multi-face candidate and positive faces saved;
- exact candidate-count and covered-face directional sums;
- retained bytes exactly `candidateCount * 6`;
- scratch use covering builds;
- primary exact coverage audits exactly equal builds and all match;
- nonzero deterministic audits and all match;
- all previous worker/lifecycle/lifetime gates clean.

Integrated source/package head: `cbb576836a304e4691e95eb395f624aefc8a2c5f`.

Exact CI run `32602196609`:

- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED;
- artifact id `9483192810`;
- wrapper digest `sha256:65312d0391607a48dfb6b3c89d7b1c15c0e7679b3f6ab9613ae03610847e706e`.

Canonical direct runtime package:

- `Obsidian-0.3.0-phase3-dev7.jar`;
- size `320,735` bytes;
- SHA-256 `ef2ff6f1bc78469a9a65db486f735c178565c8982fd62aa1bb60901bf56ce1c7`.

The package is verified as Minecraft `~26.2`, Fabric Loader `>=0.19.3`, Java `>=25`, client-only, and contains the dev7 candidate, dev6 key, worker, coordinator and bootstrap classes.

### Runtime closure still required

Run the canonical dev7 JAR on the reference Vulkan system. Ordinary validation is sufficient: initial 3x3 READY, visual regression check, block break/place followed by READY rebuild, F3+T followed by READY rebuild, ordinary recenter movement if convenient, then a full normal Minecraft/Prism process exit with the complete shutdown tail.

Required final flags include:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- `renderMergeCandidateEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- all four sidecars integrated;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- exact candidate accounting/determinism and clean workers/staging/arena/resources;
- launcher process exit code `0`.

The old fixed-anchor far-travel unload/return sequence is not required again.

### Critical dev7 rendering boundary

Dev7 remains **sidecar-only**. Pairwise/per-face render-key equality is necessary but not sufficient to emit one large GPU quad: a large quad can alter light/color/AO interpolation, and identical per-cell atlas UVs do not automatically define correct repetition over a rectangle.

Therefore the generalized `BakedSectionMesh` remains authoritative GPU geometry and dev7 must continue to report:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

A later P3.4 slice must separately freeze and prove rectangle-level interpolation/UV emission semantics before changing GPU geometry. P3.5 remains planned and is **not** active.

## Promotion authorization

Standing user merge authorization applies to this Phase 3 chain. PR #39 may be promoted without another authorization request only after its frozen exact CI/package/runtime closure gates pass. Promotion must use `[no-release]`.

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
