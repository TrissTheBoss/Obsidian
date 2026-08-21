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
- Evidence: A-0080 through A-0084.

Corrected dev6 already proved edit rebuilds, resource reload, zero dropped events, zero stale installs, deterministic capture/build invariants, full staging/arena/deferred-resource reclamation and exit 0. It did **not** exercise the required fixed-target unload/return class:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

P2.6 therefore remains formally open. Remaining closure is a reference run with nonzero tracked-neighborhood chunk unload/load counters and `lifecycleGateReady=true`, zero dropped events/stale installs, full reclamation and exit 0.

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

Phase 3 may proceed forward on top of validated P2.7 while P2.6 waits for its specific manual unload/return coverage. Phase 3 remains **non-mergeable** until the P2.6 -> P2.7 dependency chain reaches `main` and separate Phase 3 merge authorization exists.

No Phase 3 merge authorization currently exists.

### P3.1 dev1 — first worker/job concurrency boundary runtime validated

- Branch: `phase3/worker-job-architecture`.
- Canonical stacked PR: #29.
- Version: `0.3.0-phase3-dev1`.
- A-0090: first-proof plan.
- A-0091: implementation + package/bytecode evidence.
- A-0092: reference runtime success.
- A-0094: evidence summary.

A-0092 proved the bounded worker/job boundary with 12 submitted jobs, 4 completed, 8 cancelled from 8 requests, 11 stolen, zero queue-full rejection/failure/stale batch, deterministic accepted worker meshes, `workerWorldReadsAfterCapture=0`, full renderer cleanup and exit 0. It deliberately retained `productionSceneInstallStillSynchronous=true`; dev1 proved concurrency architecture only.

### P3.1 dev2 — production async scene implemented and exact-CI/package verified; reference runtime next

- Branch: `phase3/async-scene-integration`.
- Canonical stacked PR: #32, base `phase3/worker-job-architecture`, open/draft.
- Version: `0.3.0-phase3-dev2`.
- A-0093: production integration contract.
- A-0095: branch handoff.
- A-0096: first implementation + exact CI/package/bytecode evidence.
- A-0097: shutdown-gate accounting correction + corrected exact CI/package evidence.
- Temporary PR #33 targets `main` **only** to trigger exact CI and is NEVER MERGE.

Active dev2 production path:

- `AsyncMultiSectionSceneProbe` owns the persistent 3x3 scene;
- `WorkerBackedSectionLifecycleProbe` owns each async section record;
- `SectionMeshWorkerPool` performs pure drawable mesh construction;
- render-thread staging, device arena, indexed-indirect drawing and completion-gated retirement remain unchanged in ownership.

The validated P2.6/P2.7 probe classes remain in source as historical correctness oracles rather than being rewritten by this concurrency milestone.

#### Ownership flow

`render-thread immutable capture -> bounded worker mesh job -> render-thread generation/event/resource validation -> render-thread GPU allocation/upload/install -> completion-gated replacement`

Render thread continues to own:

1. live world/chunk access;
2. `SectionSnapshot` capture;
3. generalized vanilla model/material/light/tint capture into `SectionBakedQuadSnapshot`;
4. permanent `ReferenceFaceMesh` oracle checks;
5. worker ticket admission/acceptance;
6. lifecycle/resource identity validation;
7. GPU arena allocation;
8. staging upload;
9. indirect command creation and draw encoding;
10. GPU retirement/reclamation.

Workers receive only immutable `SectionSnapshot` + `SectionBakedQuadSnapshot` plus generation/event-sequence/priority metadata and perform pure deterministic `BakedSectionMesh.build(...)` work.

#### Active record state machine

`WAITING_WORLD -> WAITING_MESH -> READY_TO_INSTALL -> LIVE -> RETIRING -> RETIRED`

with `STALE`, `FAILED`, and `CLOSED` control/terminal states.

Identity is checked before accepting a completed worker result, before GPU allocation, and immediately before staging submission/install. Invalidation while queued/running requests cancellation. Completed stale pre-install work is discarded without GPU allocation. Safe stale/cancelled pre-install work is measured separately from unsafe stale GPU installation.

#### Production scheduling in dev2

- whole-window invalidation remains inherited from correctness-first P2.7;
- center record uses HIGH priority;
- neighboring records use NORMAL priority;
- LOW is reserved for later background relevance work;
- one new record is admitted per render frame;
- multiple mesh jobs may be queued/running concurrently;
- install remains serialized by the bounded staging pending-batch contract;
- no fallback/unbounded queue exists.

The dev1 synthetic `WorkerMeshValidationProbe` is no longer part of the active `FrameCoordinator`; real scene records now exercise the worker pool.

#### Shutdown gate correction

A-0097 supersedes the first A-0096 runtime package for testing.

The first dev2 coordinator sampled cleanup status before its bounded shutdown waits had drained scene-close retirements, so a correct live scene could falsely report `phase3GateReady=false`. The corrected coordinator now:

1. snapshots scene evidence;
2. closes the scene and registers completion-gated retirements;
3. closes/joins workers;
4. drains/closes staging with its bounded fence wait;
5. drains/closes the device arena with its bounded retirement wait;
6. drains/closes deferred GPU resources;
7. evaluates the runtime gate from post-drain state.

The gate explicitly requires `workersClean=true`, `stagingClean=true`, `arenaClean=true`, and `resourcesClean=true`, plus no staging/arena shutdown abandonment.

#### Corrected exact CI/package evidence

Corrected source head tested:

- `da4bd615a7de0bf90ac42c39ab945bb4903ae194`

Exact GitHub Actions:

- temporary CI PR #33;
- run `32521379106`;
- Java 25 / Gradle 9.5.1 job `96894077235`;
- Build success;
- artifact upload success;
- release publishing correctly skipped;
- artifact id `9460674755`;
- artifact wrapper digest `sha256:0762557f387ae3e42b6d604ff4f6a052b0ea00e8d2ee609ac6b8a494b6ed628a`.

**Use these A-0097 artifacts for reference runtime:**

- `Obsidian-0.3.0-phase3-dev2.jar`
  - size `269,557` bytes;
  - SHA-256 `0f1cc8f2aa50da277c8b6bacb531d065ba7ecf489c9e406a2e15fa7c8a455044`.
- `Obsidian-0.3.0-phase3-dev2-sources.jar`
  - size `140,377` bytes;
  - SHA-256 `fc51609e5523620796bd78b6bd4c572e23958c8a920d1b241b48d9737380c497`.

A-0096 package hashes are historical pre-A-0097 evidence and are superseded for runtime testing.

Bytecode ownership evidence from A-0096 remains structurally valid: render-thread worker-backed records submit/cancel worker tickets and own lifecycle checks/GPU allocation/staging/indexed-indirect draws, while `SectionMeshWorkerPool$Worker` owns the deterministic `BakedSectionMesh.build(snapshot, bakedSnapshot)` calls. The render-thread record does not call `BakedSectionMesh.build(...)`.

This closes dev2 implementation/compile/package evidence only. It is **not** reference-runtime validation.

#### Dev2 reference runtime gate

Run the corrected A-0097 `0.3.0-phase3-dev2` artifact on the reference Vulkan system and exercise:

1. ordinary surface terrain with supported SOLID + CUTOUT content;
2. initial async 3x3 scene READY;
3. visual border/stale-geometry inspection;
4. break/place blocks and allow a worker-backed rebuild;
5. F3+T once and allow another rebuild;
6. optional movement/recenter for additional cancellation/relevance churn;
7. normal exit with the complete Prism log.

Desired final evidence includes:

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
- zero queue-full rejection/worker failure;
- zero queued/running jobs at shutdown;
- nonzero dirty + resource-reload events;
- zero dropped lifecycle events;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- no staging/arena shutdown abandonment;
- exit code 0.

Safe pre-install cancellation/stale-discard counts may be nonzero and are not failures. Camera recenter is optional for dev2 because P2.7 already proved that behavior separately.

### Remaining P3.1 after dev2 runtime success

Before P3.2 activates, P3.1 still needs:

1. stronger relevance-aware prioritization/cancellation under streaming pressure;
2. worker-local reusable scratch/allocation reduction;
3. production queue latency, execution latency and output-size evidence;
4. scheduler/backpressure tuning justified by measurements.

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
