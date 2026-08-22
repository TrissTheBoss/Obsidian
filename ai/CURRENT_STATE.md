# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.2 — binary/bitmask visibility masks**.
- Active branch: `phase3/bitmask-visibility-masks`.
- Canonical draft PR: #36 against `main`.
- Active development version: `0.3.0-phase3-dev4`.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1 through P2.5: COMPLETE.
- P2.6 + P2.7: COMPLETE — PR #25 merge `794483f955c861cbf9e24ade2463ba51ab9ab284`.
- P3.1 dev1: COMPLETE — PR #29 merge `c39cf17b4864e7f7081007238117aea5be3c26e3`.
- P3.1 dev2: COMPLETE — PR #32 merge `58b2b8b8b1962f2809029e32d147a4a96a93b486`.
- P3.1 dev3: COMPLETE — PR #34 merge `1b6615eac2494a197cea86d314cf5b099d2418e8`.
- Class-A synchronization before P3.2: `b5914a7b383d8f1a27cfe542201d389da8477bb1`.

The P2.6 fixed-target chunk lifecycle gap was closed by A-0101 using the same grounded `ClientLevel.onChunkLoaded` / `ClientLevel.unload` hooks with a diagnostic-only fixed first-scene anchor. That proof does not need to be repeated for P3.2.

## P3.1 proven production boundary

The merged production flow remains:

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

A-0101 final P3.1 run passed `phase3GateReady=true` and `schedulerEvidenceReady=true` with 208/208 completed worker jobs, 159 steals, zero queue-full rejection/failure/shutdown-join failure, all priority tiers exercised and clean shutdown.

## ACTIVE: P3.2 dev4 — binary visibility sidecar integrated

Evidence:

- A-0103 — frozen P3.2 plan;
- A-0104 — implementation, exact CI and package evidence.

### Binary topology representation

`BinarySectionVisibility` is now implemented as a pure worker-side topology representation built only from immutable `SectionSnapshot`:

- six directions matching permanent `ReferenceFaceMesh` order: WEST/EAST/DOWN/UP/NORTH/SOUTH;
- 4,096 bits / 64 `long` words per direction;
- 384 retained words = exactly 3,072 bytes per complete mask set;
- deterministic cell bit order `((y * 16) + z) * 16 + x`;
- reusable 18x18 supported/air halo-row scratch;
- machine-word directional visibility derivation;
- conservative semantics exactly `SUPPORTED_FULL_CUBE && neighbor == AIR`;
- unsupported neighbors suppress faces exactly as the independent oracle does.

Every primary build scalar-validates its bitset against the immutable source snapshot during this correctness-first milestone.

### Independent differential validation

On the existing worker audit cadence, P3.2:

1. builds a second mask and requires exact deterministic equality;
2. independently builds the permanent simple `ReferenceFaceMesh` from the immutable snapshot;
3. requires equal visible-face and unsupported-neighbor counts;
4. requires every reference face to exist in the optimized directional mask.

Equal count plus complete reference inclusion proves no missing or extra optimized cube faces. The reference oracle does not share the optimized construction algorithm.

### Production integration boundary

Every real `SectionMeshWorkerPool` job now produces:

- `BinarySectionVisibility` — P3.2 topology sidecar;
- existing `BakedSectionMesh` — unchanged generalized SOLID/CUTOUT drawable output.

The current GPU renderer still consumes the validated generalized baked mesh. P3.2 therefore does **not** emit merged geometry or change visual geometry ownership.

Final runtime logs expose:

- `binaryVisibilityEvidenceReady`;
- visibility builds / total and per-direction faces;
- exact retained bytes;
- total/max visibility build time;
- scratch uses/high-water;
- determinism audits/matches;
- independent reference audits/matches;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleEmission=false`.

## Exact dev4 CI/package

Canonical tested code head:

- `ab394076853d2647340c8eb4f2983ec842823938`.

Exact final run:

- GitHub Actions `32583676238`;
- Java 25 / Gradle 9.5.1;
- Build SUCCESS;
- artifact upload SUCCESS;
- versioned release SKIPPED;
- artifact id `9478459893`;
- wrapper digest `sha256:09c68667008fa5d1071f298926b80801b6cf4031054c4a7159078efdc998260b`.

Canonical runtime JAR:

- `Obsidian-0.3.0-phase3-dev4.jar`;
- size `285,246` bytes;
- SHA-256 `93211c45bae44f927fc3946c30ec336d3ad41ea6a015992f395ab669b9a8d14e`.

Sources JAR SHA-256: `d75f495e9731d90266ef4334f8e8ef16fa0136441557e1b7756359ff81f4f346`.

Packaged metadata: Minecraft `~26.2`, Java `>=25`, version `0.3.0-phase3-dev4`.

## Reference runtime — NEXT / REQUIRED

Use the canonical dev4 JAR on the reference Vulkan machine:

1. enter ordinary surface terrain and wait for the async 3x3 scene to become READY;
2. visually inspect for missing/duplicate/stale geometry;
3. break/place blocks and wait for a READY rebuild;
4. perform F3+T and wait for another READY rebuild;
5. optional normal movement/recenter is useful;
6. exit normally and provide the complete Prism log.

The old far-travel fixed-anchor unload/return sequence is **not required again**.

Required P3.2 closure evidence:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- visibility builds > 0;
- visibility faces > 0;
- six direction totals sum exactly to total faces;
- retained bytes == builds * 3,072;
- visibility scratch use is nonzero and >= primary builds;
- determinism audits > 0 and matches == audits;
- independent reference audits > 0 and matches == audits;
- zero worker queue-full rejection/failure/shutdown join failure;
- zero unsafe stale scene installs;
- clean workers/staging/arena/resources;
- process exit code 0.

Individual direction totals do not all need to be nonzero for every terrain sample.

## Deliberate boundary / next phase

P3.2 is **not complete** until the reference runtime passes and is recorded.

Not P3.2:

- greedy rectangle extraction or merged quad emission;
- replacement of generalized `BakedSectionMesh` output;
- final material/light/AO merge-key construction;
- arbitrary model-quad merging;
- fluid/translucent terrain;
- partial remeshing;
- worker-thread live-world capture.

P3.3 greedy rectangle extraction remains PLANNED and must not begin before P3.2 is formally closed.

PR #36 remains draft. Starting P3.2 does not itself provide merge authorization for this new scope.

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