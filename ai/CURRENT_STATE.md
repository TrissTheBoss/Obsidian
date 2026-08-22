# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- The user has standing authorization to merge the validated Phase 2 / P3.1 dependency chain once technical gates pass.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Merged baseline before current promotion

- Phase 1: COMPLETE — closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1: COMPLETE — `a714e19ce871bf73136d52f85a1780109aa851dd`.
- P2.2: COMPLETE — `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.
- P2.3: COMPLETE — `667230f51222746083efe89c72265d80ac9d3929`.
- P2.4: COMPLETE — `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`.
- P2.5: COMPLETE — `c17f7c6146678e18cacabc44d85c67413a040f73`.
- P2.5 Class-A synchronization: `306d74fdf2428af93feac2ce5e49296d508d9d2d`.

## Technical dependency gate: CLOSED

### P2.6 / dev6

Historical corrected P2.6 already proved edit rebuilds, resource reload, deterministic capture/build behavior, zero dropped/stale installs, full reclamation and exit 0. A-0084 left one coverage gap because it did not actually unload the fixed tracked neighborhood:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

The exact Minecraft 26.2 hooks were already grounded as `ClientLevel.onChunkLoaded` / `ClientLevel.unload`.

A-0101 now closes that missing observation using the current production runtime's diagnostic-only fixed first-scene anchor, which uses those same grounded hooks without driving active scene invalidation. The successful dev3 run produced:

- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- fixed-anchor chunk loads/unloads `9 / 9`;
- active-scene chunk loads/unloads `30 / 35`;
- zero dropped lifecycle events;
- zero unsafe stale scene installs;
- LIVE/READY async scene after the unload/load sequence;
- clean shutdown and exit 0.

This stronger downstream evidence supersedes A-0084's missing fixed-target observation. P2.6 is technically qualified for merge.

### P2.7 / dev7

- Original PR #27 was runtime + human visual validated and merge-authorized.
- It is already incorporated into P2.6 branch PR #25 via merge `91eb704e769fff5d872c628a710cd8128a3314ee`.
- Combined evidence-synchronized Phase 2 head: `2bc4ece2b88d85c2e49e957e93a2d5f076271fd0`.
- Exact CI run `32512405528` passed.
- Runtime: `sceneGateReady=true`, 16 READY transitions, 15 rebuilds, 144 installs, max 9 live records / 12 adjacent pairs, zero dropped/stale installs, full reclamation, exit 0.
- Human visual validation reported no persistent duplicate/missing borders or stale old-window geometry.

PR #25 is now technically and human-authorized for promotion to `main` with `[no-release]`.

## Phase 3 P3.1 status

### Dev1 — worker/job boundary

- Branch `phase3/worker-job-architecture`, PR #29.
- Runtime validated in A-0092.
- Bounded worker queues, work stealing, cancellation, immutable-input deterministic meshes, zero worker world reads after capture and clean shutdown were proven.

### Dev2 — production asynchronous scene integration

- Branch `phase3/async-scene-integration`, PR #32.
- Runtime validated in A-0098.
- `phase3GateReady=true`, `productionWorkerIntegrationReady=true`.
- `productionSceneInstallStillSynchronous=false`.
- 131 worker jobs completed, zero queue-full rejection/failure, 97 steals, 131/131 scene installs, 25 READY transitions / 24 rebuilds, clean workers/staging/arena/resources, exit 0.
- Phase 3 merge authorization already exists.

### Dev3 — scheduler/backpressure + scratch + combined lifecycle closure: RUNTIME PASSED

- Branch `phase3/scheduler-backpressure-tuning`, PR #34.
- Version `0.3.0-phase3-dev3`.
- A-0099: frozen plan.
- A-0100: implementation / exact CI / package evidence.
- A-0101: combined reference runtime success and dependency closure.
- Canonical tested JAR SHA-256 `182bac20d44de88705d5549ab5c1dd596aeef1aba53571ee7a121d472c3cc131`.
- Exact code-head CI run `32582141208` passed Java 25 / Gradle 9.5.1.

Final reference runtime gate:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- render-thread capture/GPU ownership preserved;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- worker submitted/started/completed `208/208/208`;
- worker steals `159`;
- queue-full rejections `0`;
- worker failures `0`;
- shutdown join failures `0`;
- HIGH/NORMAL/LOW completed `29/89/90`;
- output quads `151,898`, vertex bytes `17,012,576`, index bytes `3,645,552`;
- scratch build uses `212`, max scratch quads `1,464`;
- determinism audits/matches `4/4`;
- `maxAdmissionBurst=2`;
- scene worker submitted/completed `208/208`;
- record installs / scene worker installs `203/203`;
- safe stale completed results `5`, preinstall invalidations `5`;
- scene READY transitions/rebuilds `29/28`;
- max live records / adjacent pairs `9/12`;
- camera recenters `6`;
- dirty events `1,988`, resource reload events `3`;
- fixed-anchor loads/unloads `9/9`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- staging submitted/reclaimed `20,025,552 / 20,025,552` bytes;
- arena allocations/retired/reclaimed `406/406/406`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `203/203`, pending `0`;
- process exit code `0`.

The observed maximum queue depth remained 1 because two-record admission plus four workers kept the production queue responsive. This does not invalidate scheduler evidence: all relevance tiers were exercised, stealing was heavy, admission burst 2 was observed, output/latency/scratch metrics are populated, and every audit matched.

## Authorized promotion sequence now in progress

No new merge authorization is required.

1. merge PR #25 to `main` with `[no-release]`;
2. retarget/revalidate PR #29 against the advanced `main`, then merge `[no-release]`;
3. retarget/revalidate PR #32 against `main`, then merge `[no-release]`;
4. retarget/revalidate PR #34 against `main`, then merge `[no-release]`;
5. perform Class-A status synchronization on `main` and update roadmap state before starting P3.2.

P3.2 greedy/bitmask meshing must not begin until this promotion and synchronization are complete.

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