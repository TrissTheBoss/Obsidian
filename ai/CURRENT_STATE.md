# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- `main` remains at the P2.5-complete Class-A state until the remaining P2.6 runtime gate closes.

## Completed merged milestones

- Phase 1: COMPLETE — closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1: COMPLETE — `a714e19ce871bf73136d52f85a1780109aa851dd`.
- P2.2: COMPLETE — `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.
- P2.3: COMPLETE — `667230f51222746083efe89c72265d80ac9d3929`.
- P2.4: COMPLETE — `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`.
- P2.5: COMPLETE — `c17f7c6146678e18cacabc44d85c67413a040f73`.
- P2.5 Class-A synchronization: `306d74fdf2428af93feac2ce5e49296d508d9d2d`.

## Dependency chain still blocking merge to `main`

### P2.6 / dev6 — corrected behavior proven, fixed-target chunk lifecycle coverage still open

- Branch: `phase2/section-lifecycle-rebuild`.
- PR: #25, open/draft against `main`.
- Version: `0.2.0-phase2-dev6`.
- User merge authorization exists only after the standing fixed-target runtime gate passes.
- A-0080 through A-0084 contain the current dev6 evidence.

Corrected dev6 already proved edit rebuilds, resource reload, zero dropped events, zero stale installs, deterministic capture/build invariants, full staging/arena/deferred-resource reclamation and exit 0. It did **not** exercise the required fixed-target unload/return class:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

P2.6 therefore remains formally open. The remaining closure is a reference run with nonzero tracked-neighborhood chunk unload/load counters and `lifecycleGateReady=true`, zero dropped events/stale installs, full reclamation and exit 0.

### P2.7 / dev7 — runtime + human validated and merge-authorized; already stacked into P2.6

- Version: `0.2.0-phase2-dev7`.
- Original branch: `phase2/multi-section-scene`.
- PR #27 was runtime + human visual validated and explicitly merge-authorized.
- Merge into P2.6 base: `91eb704e769fff5d872c628a710cd8128a3314ee`.
- Combined evidence-synchronized head: `2bc4ece2b88d85c2e49e957e93a2d5f076271fd0`, exact CI run `32512405528`.
- Evidence: A-0085 through A-0089.

Validated runtime included `sceneGateReady=true`, 16 READY transitions, 15 rebuilds, 144 record installs, 9 maximum live records, 12 adjacent pairs, two camera recenters, zero dropped lifecycle events, zero stale scene/probe installs, zero upload/retirement failures, full reclamation and exit 0. Human visual oracle passed without persistent duplicate/missing borders or stale old-window geometry.

P2.7 cannot reach `main` until P2.6 closes.

## Forward stacked work: Phase 3 P3.1

Phase 3 is allowed to proceed forward on top of validated P2.7 while P2.6 waits for its specific manual unload/return coverage. Phase 3 remains **non-mergeable** until the P2.6 -> P2.7 dependency chain reaches `main` and separate Phase 3 merge authorization exists.

No Phase 3 merge authorization currently exists.

### P3.1 dev1 — first worker/job concurrency boundary runtime validated

- Branch: `phase3/worker-job-architecture`.
- Canonical stacked PR: #29.
- Version: `0.3.0-phase3-dev1`.
- A-0090: first-proof plan.
- A-0091: implementation + package/bytecode evidence.
- A-0092: reference runtime success.
- A-0094: evidence summary.

Proven dev1 worker boundary:

- 1..4 dedicated daemon mesh workers;
- HIGH/NORMAL/LOW bounded lanes;
- 16 queued jobs maximum per worker;
- no fallback/unbounded growth;
- least-depth admission and peer stealing;
- generation + lifecycle-sequence tagged tickets;
- explicit cancellation;
- COMPLETED/CANCELLED/FAILED terminal states;
- deterministic duplicate pure `BakedSectionMesh.build(...)` worker builds;
- no worker live world/chunk/model/GPU ownership;
- bounded shutdown and queue/wait/execution metrics.

A-0092 reference run passed with 12 submitted jobs, 4 completed, 8 cancelled from 8 requests, 11 stolen, zero queue-full rejection/failure/stale batch, four deterministic accepted matches, `workerWorldReadsAfterCapture=0`, full resource reclamation and exit 0.

The dev1 runtime deliberately logged `productionSceneInstallStillSynchronous=true`; it proved the concurrency boundary only.

### P3.1 dev2 — production async scene integration implemented; exact CI/package verified; runtime next

- Branch: `phase3/async-scene-integration`.
- Canonical stacked PR: #32, base `phase3/worker-job-architecture`, open/draft.
- Version: `0.3.0-phase3-dev2`.
- A-0093 freezes the production async integration contract.
- A-0095 is the branch handoff marker.
- A-0096 records implementation, exact CI, package and bytecode evidence.
- Temporary PR #33 targets `main` **only** to trigger exact CI and is marked NEVER MERGE.

Active dev2 implementation now uses:

- `AsyncMultiSectionSceneProbe` as the persistent 3x3 production scene owner;
- `WorkerBackedSectionLifecycleProbe` as the per-record async state machine;
- the existing shared `SectionMeshWorkerPool` for pure mesh construction;
- the existing render-thread staging, arena, indexed-indirect drawing and completion-gated retirement path.

The validated P2.6/P2.7 probe classes remain in source unchanged as historical correctness oracles rather than being rewritten by the new concurrency milestone.

#### Ownership flow now implemented

`render-thread immutable capture -> bounded worker mesh job -> render-thread generation/event/resource validation -> render-thread GPU allocation/upload/install -> completion-gated replacement`

Render thread still owns:

1. live `ClientLevel`/chunk access;
2. `SectionSnapshot` capture;
3. generalized vanilla model/material/light/tint capture into `SectionBakedQuadSnapshot`;
4. permanent `ReferenceFaceMesh` oracle checks;
5. worker ticket admission/acceptance;
6. lifecycle/resource identity validation;
7. GPU arena allocation;
8. staging upload;
9. indirect command creation and draw encoding;
10. GPU retirement/reclamation.

Workers still receive only immutable `SectionSnapshot` + `SectionBakedQuadSnapshot` plus generation/event-sequence/priority metadata and perform pure `BakedSectionMesh.build(...)` work.

#### Active record state machine

`WAITING_WORLD -> WAITING_MESH -> READY_TO_INSTALL -> LIVE -> RETIRING -> RETIRED`

with `STALE`, `FAILED`, and `CLOSED` control/terminal states.

Identity is checked before accepting a completed worker result, before GPU allocation, and immediately before staging submission/install. Invalidation while a job is queued/running requests cancellation; completed stale pre-install work is discarded without allocating GPU memory. Safe stale/cancelled pre-install work is measured separately from unsafe stale GPU installation.

#### Production scheduling in dev2

- whole-window invalidation remains intentionally inherited from the correctness-first P2.7 scene;
- center record uses HIGH priority;
- all neighboring records use NORMAL priority;
- LOW remains reserved for later background/relevance work;
- one new scene record is admitted per render frame;
- multiple worker jobs may be queued/running simultaneously;
- install remains serialized by the existing bounded staging pending-batch contract;
- no fallback/unbounded queue exists.

The dev1 synthetic `WorkerMeshValidationProbe` is no longer part of the active `FrameCoordinator`; real scene jobs now exercise the worker pool.

#### Exact CI/package evidence

Source head tested before A-0096 documentation synchronization:

- `c34f92a968b26ef5cebb16c441d69ae6ba28c337`

Exact GitHub Actions evidence:

- temporary CI PR #33;
- run `32520955461`;
- Java 25 / Gradle 9.5.1 job `96892802461`;
- Build success;
- artifact upload success;
- release job correctly skipped;
- artifact id `9460525228`.

Verified artifacts:

- `Obsidian-0.3.0-phase3-dev2.jar`
  - size `269,298` bytes;
  - SHA-256 `bb7d703599e561d2d3cbdf2bd027adcdd968831bc1c3126b8078dca75c2cd812`.
- `Obsidian-0.3.0-phase3-dev2-sources.jar`
  - size `140,231` bytes;
  - SHA-256 `742c415657dc07657920384ed85f4f2b7823ef0fb5f4497ccc0fbeeefb2a73ab`.

Bytecode inspection of the exact production JAR proves:

- `FrameCoordinator` creates the worker pool and async scene owner;
- async scene records create/drive `WorkerBackedSectionLifecycleProbe`;
- the render-thread record calls worker `submit`/`cancel` and owns lifecycle/resource checks, GPU arena allocation, staging submission and indexed-indirect draws;
- the render-thread record contains no `BakedSectionMesh.build(...)` invocation;
- `SectionMeshWorkerPool$Worker` contains the two deterministic `BakedSectionMesh.build(snapshot, bakedSnapshot)` calls.

This closes dev2 implementation/compile/package evidence only. It is **not** runtime validation.

#### Dev2 reference runtime gate now required

Run the exact `0.3.0-phase3-dev2` artifact on the reference Vulkan system and exercise:

1. ordinary surface terrain with supported SOLID + CUTOUT content;
2. initial async 3x3 scene READY;
3. visual border/stale-geometry inspection;
4. break/place blocks and allow a worker-backed rebuild;
5. F3+T once and allow another rebuild;
6. normal exit with complete Prism log.

Desired shutdown evidence includes:

- `phase3GateReady=true`;
- `productionWorkerIntegrationReady=true`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- at least two scene READY transitions;
- at least one rebuild;
- at least three worker-result installs;
- scene worker installs equal renderer record installs;
- zero synchronous scene mesh builds;
- zero unsafe stale scene installs;
- zero worker queue-full rejection/failure;
- zero queued/running jobs at shutdown;
- nonzero exact dirty + resource-reload events;
- zero dropped lifecycle events;
- full staging/arena/deferred-resource cleanup;
- exit code 0.

Safe pre-install cancellation/stale-discard counts may be nonzero and are not failures.

Camera recenter is useful optional churn but is not required to re-prove P2.7 for this dev2 gate.

### Remaining P3.1 after dev2 runtime success

Before P3.2 activates, P3.1 still needs:

1. stronger relevance-aware prioritization/cancellation under streaming pressure;
2. worker-local reusable scratch/allocation reduction;
3. production queue latency, execution latency and output-size evidence;
4. any scheduler/backpressure tuning justified by measured runtime evidence.

Do not begin greedy/bitmask meshing merely because dev2 compiles; P3.2 remains downstream of P3.1 closure.

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

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + later worker-local binary/bitmask greedy meshing, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
