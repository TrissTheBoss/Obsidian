# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.2 — binary/bitmask visibility masks**.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1: COMPLETE — `a714e19ce871bf73136d52f85a1780109aa851dd`.
- P2.2: COMPLETE — `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.
- P2.3: COMPLETE — `667230f51222746083efe89c72265d80ac9d3929`.
- P2.4: COMPLETE — `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`.
- P2.5: COMPLETE — `c17f7c6146678e18cacabc44d85c67413a040f73`.
- P2.6 + P2.7 integration: COMPLETE — PR #25 merge `794483f955c861cbf9e24ade2463ba51ab9ab284`.
- P3.1 dev1 worker/job architecture: COMPLETE — PR #29 merge `c39cf17b4864e7f7081007238117aea5be3c26e3`.
- P3.1 dev2 production asynchronous scene integration: COMPLETE — PR #32 merge `58b2b8b8b1962f2809029e32d147a4a96a93b486`.
- P3.1 dev3 scheduler/backpressure + reusable scratch + production metrics: COMPLETE — PR #34 merge `1b6615eac2494a197cea86d314cf5b099d2418e8`.

All promotion merges used `[no-release]`; the public release remains `v0.0.2-phase0`.

## Phase 2 — COMPLETE

Phase 2 now provides the correctness foundation required by D-0024 before optimized greedy meshing:

- immutable real 16^3 section snapshots with one-block halo;
- permanent independent deterministic reference oracle;
- correct supported position/material/UV/tint/light/AO semantics;
- generalized accepted SOLID/CUTOUT vanilla-emitted model quads;
- event-driven dirty/resource/world/chunk lifecycle invalidation;
- generation/version identity and stale-result rejection;
- completion-gated live GPU replacement/reclamation;
- persistent neighboring multi-section scene ownership;
- human-validated multi-section border/camera behavior.

### P2.6 fixed-target lifecycle closure

A-0084's corrected standalone dev6 run proved edit/reload/reclamation correctness but did not actually unload its fixed target (`chunkLoadEvents=0`, `chunkUnloadEvents=0`).

A-0101 closed that remaining evidence gap using the downstream production runtime's diagnostic-only first-scene anchor on the same exact grounded `ClientLevel.onChunkLoaded` / `ClientLevel.unload` hooks. Final evidence included:

- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- fixed-anchor chunk loads/unloads `9/9`;
- active-scene chunk loads/unloads `30/35`;
- zero dropped lifecycle events;
- zero unsafe stale scene installs;
- LIVE/READY async scene after returning to the unloaded anchor area;
- full worker/staging/arena/resource cleanup;
- exit code 0.

That stronger downstream proof supersedes only A-0084's missing observation; it does not rewrite the old immutable attempt.

### P2.7 validation

The validated 3x3 scene proof reached `sceneGateReady=true`, 16 READY transitions, 15 rebuilds, 144 installs, max 9 live records / 12 adjacent pairs, two recenter events, zero dropped/stale installs, full reclamation and exit 0. Human visual validation reported no persistent duplicate/missing borders or stale old-window geometry.

## Phase 3 P3.1 — COMPLETE

### Dev1 — bounded worker/job architecture

A-0092 proved dedicated bounded priority workers, work stealing, immutable inputs, generation/event identity, cancellation, deterministic accepted outputs, `workerWorldReadsAfterCapture=0`, clean shutdown and exit 0.

### Dev2 — production asynchronous scene integration

A-0098 proved the production ownership flow:

`render-thread immutable capture -> bounded worker mesh job -> render-thread generation/event/resource validation -> render-thread GPU allocation/upload/install -> completion-gated replacement`

Runtime evidence included `phase3GateReady=true`, `productionWorkerIntegrationReady=true`, `productionSceneInstallStillSynchronous=false`, 131/131 completed worker jobs, zero queue-full rejection/failure, 97 steals, 131/131 record installs, 25 READY transitions / 24 rebuilds and clean shutdown.

### Dev3 — relevance scheduler, scratch reuse and production evidence

A-0101 is the canonical final P3.1 runtime result for `0.3.0-phase3-dev3`.

Final gate:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- render-thread capture/GPU ownership preserved;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`.

Worker/scheduler metrics:

- worker submitted/started/completed `208/208/208`;
- stolen jobs `159`;
- queue-full rejections `0`;
- worker failures `0`;
- shutdown join failures `0`;
- HIGH/NORMAL/LOW completed `29/89/90`;
- output quads `151,898`;
- output vertex/index bytes `17,012,576 / 3,645,552`;
- scratch build uses `212`, scratch high-water `1,464` quads;
- determinism audits/matches `4/4`;
- `maxAdmissionBurst=2`.

Scene/lifetime metrics:

- scene worker submitted/completed `208/208`;
- record installs / worker installs `203/203`;
- safe stale completed results `5`, preinstall invalidations `5`;
- READY transitions/rebuilds `29/28`;
- max live records / adjacent pairs `9/12`;
- camera recenter events `6`;
- dirty events `1,988`, resource reload events `3`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- staging submitted/reclaimed `20,025,552 / 20,025,552` bytes;
- arena allocations/retired/reclaimed `406/406/406`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `203/203`, pending `0`;
- process exit code `0`.

The maximum queue depth stayed at 1 because bounded two-record admission plus four workers kept work responsive; all three relevance tiers, stealing, output accounting, scratch reuse and determinism audits were nevertheless exercised.

## Current active milestone — P3.2 binary/bitmask visibility masks

P3.2 is now allowed to begin. It is **ACTIVE**, not complete.

Immediate contract:

1. keep the P2.1 reference oracle independent and permanently available;
2. retain immutable renderer-owned worker inputs and zero live world reads after capture;
3. introduce compact machine-word occupancy/face-visibility masks suitable for worker-local reuse;
4. prove deterministic mask construction and directional face coverage against the existing reference semantics;
5. preserve material/light/AO merge-key truth for downstream P3.3 greedy rectangle extraction;
6. keep queues, scratch and output bounded/observable;
7. do not claim greedy rectangle meshing until P3.3 actually implements and validates it.

P3.3 greedy rectangle extraction and later Phase 3 work remain PLANNED.

## Promotion / CI evidence

A-0102 records the completed promotion chain.

Fresh exact Java 25 / Gradle 9.5.1 retarget CI before Phase 3 merges:

- dev1 run `32582746431` — success;
- dev2 run `32582829896` — success;
- dev3 run `32582906074` — success.

Each artifact upload passed and release publishing was skipped.

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