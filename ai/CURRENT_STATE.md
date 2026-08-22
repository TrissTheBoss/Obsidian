# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.3 — greedy rectangle extraction**.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1 through P2.5: COMPLETE.
- P2.6 + P2.7: COMPLETE — PR #25 merge `794483f955c861cbf9e24ade2463ba51ab9ab284`.
- P3.1 dev1: COMPLETE — PR #29 merge `c39cf17b4864e7f7081007238117aea5be3c26e3`.
- P3.1 dev2: COMPLETE — PR #32 merge `58b2b8b8b1962f2809029e32d147a4a96a93b486`.
- P3.1 dev3: COMPLETE — PR #34 merge `1b6615eac2494a197cea86d314cf5b099d2418e8`.
- P3.2 dev4 binary visibility masks: COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.

A-0101 remains the canonical proof that the Phase 2 fixed-target chunk unload/return lifecycle is correct. That long-distance fixed-anchor sequence does not need to be repeated for later Phase 3 milestones unless a later change materially touches those hooks or semantics.

## Phase 3 P3.1 — COMPLETE

The merged production ownership flow is:

`render-thread immutable capture -> bounded relevance-aware worker job -> render-thread generation/event/resource validation -> GPU allocation/upload/install -> completion-gated replacement`

P3.1 proved:

- bounded HIGH/NORMAL/LOW work queues and stealing;
- immutable worker inputs and zero live-world reads after capture;
- cancellation/stale-result safety;
- worker-local reusable mesh scratch;
- production async 3x3 scene installation;
- bounded two-record scene admission/backpressure;
- deterministic periodic mesh audits;
- zero synchronous production scene mesh builds;
- clean worker/staging/arena/resource shutdown.

A-0101 final P3.1 runtime passed `phase3GateReady=true` and `schedulerEvidenceReady=true` with 208/208 completed worker jobs, 159 steals, zero queue-full rejection/failure/shutdown-join failure, all priority tiers exercised and clean shutdown.

## Phase 3 P3.2 — COMPLETE

Evidence chain:

- A-0103 — frozen P3.2 binary visibility plan;
- A-0104 — implementation, exact CI and package evidence;
- A-0105 — successful reference runtime;
- A-0106 — promotion and P3.3 activation.

### Proven representation

`BinarySectionVisibility` is a pure worker-side topology representation built only from immutable `SectionSnapshot`:

- six directions in permanent `ReferenceFaceMesh` order: WEST/EAST/DOWN/UP/NORTH/SOUTH;
- 4,096 bits / 64 `long` words per direction;
- 384 retained words = exactly 3,072 bytes per complete mask set;
- deterministic cell bit order `((y * 16) + z) * 16 + x`;
- reusable 18x18 supported/air halo-row scratch;
- machine-word directional visibility derivation;
- conservative semantics exactly `SUPPORTED_FULL_CUBE && neighbor == AIR`;
- unsupported neighbors suppress faces exactly as the independent oracle does.

Every primary build scalar-validates its bitset against the immutable source snapshot in the dev4 correctness path.

### Independent differential proof

On the worker audit cadence P3.2:

1. builds a second mask and requires exact deterministic equality;
2. independently builds the permanent simple `ReferenceFaceMesh`;
3. requires equal visible-face and unsupported-neighbor counts;
4. requires every reference face to exist in the optimized directional mask.

Equal count plus complete reference inclusion proves no missing or extra conservative cube faces. The reference oracle does not share the optimized construction algorithm.

### Production integration boundary

Every real `SectionMeshWorkerPool` job produces both:

- `BinarySectionVisibility` — proven P3.2 topology sidecar;
- existing `BakedSectionMesh` — still the authoritative generalized SOLID/CUTOUT drawable output.

P3.2 therefore did **not** change GPU-emitted geometry and did not claim greedy rectangle emission.

### Dev4 runtime closure

The reference run passed:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleEmission=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`.

Worker/mask evidence:

- worker submitted/started/completed `288/288/288`;
- stolen jobs `219`;
- queue-full rejections / failures / shutdown join failures `0/0/0`;
- HIGH/NORMAL/LOW completed `32/128/128`;
- worker scratch uses `295`, high-water `1,464` quads;
- worker determinism audits/matches `7/7`;
- visibility builds `288`;
- visibility total faces `102,367`;
- WEST/EAST/DOWN/UP/NORTH/SOUTH faces `7,159 / 11,145 / 4,424 / 56,663 / 15,272 / 7,704`;
- direction totals sum exactly to `102,367`;
- retained visibility bytes `884,736 = 288 * 3,072`;
- visibility scratch uses `295`;
- visibility determinism audits/matches `7/7`;
- independent reference audits/matches `7/7`.

Scene/lifetime evidence:

- scene worker submitted/completed/installed `288/288/288`;
- safe stale discards `0`;
- READY transitions/rebuilds `32/31`;
- max live records / adjacent pairs `9/12`;
- camera recenter events `2`;
- dirty events `1,809`, resource reload events `1`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- staging submitted/reclaimed `29,476,464 / 29,476,464` bytes;
- arena allocations/retired/reclaimed `576/576/576`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `288/288`, pending `0`;
- process exit code `0`.

The dev4 run intentionally did not repeat the already-closed Phase 2 fixed-anchor unload/return sequence, so its historical `phase2ChunkLifecycleEvidenceReady=false` is not a P3.2 failure.

No new human visual verdict was explicitly recorded for dev4. P3.2 kept the existing drawable path unchanged, so its closure is based on topology/differential/runtime evidence while existing P2/P3.1 human visual validation remains the visual baseline.

## Exact P3.2 CI/package evidence

Canonical runtime code/package head:

- `ab394076853d2647340c8eb4f2983ec842823938`;
- exact run `32583676238` — Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release skipped.

Canonical dev4 JAR:

- `Obsidian-0.3.0-phase3-dev4.jar`;
- size `285,246` bytes;
- SHA-256 `93211c45bae44f927fc3946c30ec336d3ad41ea6a015992f395ab669b9a8d14e`.

Later exact PR validation:

- `03ff120fe4996c5d3d1ac85d2d355180f0fa204b` — run `32583773383`, success;
- final evidence head `0c0d53ad59dd1d52e2a8ccc2e9194b770799ad6f` — run `32584015647`, success.

PR #36 merged with `[no-release]` as `54ca3cb2d64eda958579407728e757eb0c98b948`.

## ACTIVE: P3.3 — greedy rectangle extraction

P3.3 is now ACTIVE. Greedy rectangle emission does **not** exist merely because this milestone is active.

Immediate contract:

1. consume the proven P3.2 directional visibility masks rather than re-reading live world state;
2. retain immutable renderer-owned worker inputs and `workerWorldReadsAfterCapture=0`;
3. add worker-local machine-word rectangle extraction for mask-eligible canonical faces;
4. keep the P2.1/simple reference oracle independent and permanently available;
5. preserve exact visual merge-key truth: orientation, material/sprite, layer, tint/color, light, AO corner pattern/diagonal choice, UV behavior, fluid/special-face state, and any model-specific attributes required by a supported merged face;
6. never merge arbitrary generalized baked-model quads merely because they share a block/state ID;
7. keep non-mask-eligible / non-mergeable geometry on a safe exact passthrough path;
8. prove deterministic rectangle extraction and exact face coverage against P3.2/reference semantics before replacing production drawable geometry;
9. keep worker scratch/output bounded and expose source-face -> rectangle reduction plus build-time metrics;
10. preserve the existing scheduler, cancellation, generation/event/resource checks and completion-gated GPU lifetime behavior.

A likely first P3.3 dev milestone should validate rectangle extraction as a sidecar/differential product before it is allowed to replace `BakedSectionMesh` output. Any change that actually changes emitted GPU geometry requires renewed visual/runtime validation.

## Still planned after P3.3

Later Phase 3 work remains planned, including complete merge-key/material-light-AO integration as needed by supported greedy output, broader model compatibility, and later terrain throughput/partial rebuild improvements. Do not phase-jump past P3.3 evidence.

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

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + worker-local binary/bitmask greedy meshing, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.