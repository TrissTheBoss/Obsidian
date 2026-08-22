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

Separate Phase 3 merge authorization now exists from the user as of 2026-08-21. That satisfies the human-authorization half of the merge condition; it does **not** waive the still-open P2.6 -> P2.7 dependency gate.

### P3.1 dev1 — first worker/job concurrency boundary runtime validated

- Branch: `phase3/worker-job-architecture`.
- Canonical stacked PR: #29.
- Version: `0.3.0-phase3-dev1`.
- A-0090: first-proof plan.
- A-0091: implementation + package/bytecode evidence.
- A-0092: reference runtime success.
- A-0094: evidence summary.

A-0092 proved the bounded worker/job boundary with 12 submitted jobs, 4 completed, 8 cancelled from 8 requests, 11 stolen, zero queue-full rejection/failure/stale batch, deterministic accepted worker meshes, `workerWorldReadsAfterCapture=0`, full renderer cleanup and exit 0. It deliberately retained `productionSceneInstallStillSynchronous=true`; dev1 proved concurrency architecture only.

### P3.1 dev2 — production async scene reference-runtime validated; merge-authorized but dependency-blocked

- Branch: `phase3/async-scene-integration`.
- Canonical stacked PR: #32, base `phase3/worker-job-architecture`, open/draft.
- Version: `0.3.0-phase3-dev2`.
- A-0093: production integration contract.
- A-0095: branch handoff.
- A-0096: first implementation + exact CI/package/bytecode evidence.
- A-0097: shutdown-gate accounting correction + corrected exact CI/package evidence.
- A-0098: reference runtime success + explicit Phase 3 merge authorization.
- Temporary main-targeting CI PR #33 was closed unmerged after exact CI.

#### Proven ownership flow

`render-thread immutable capture -> bounded worker mesh job -> render-thread generation/event/resource validation -> render-thread GPU allocation/upload/install -> completion-gated replacement`

`AsyncMultiSectionSceneProbe` owns the persistent 3x3 validation scene, `WorkerBackedSectionLifecycleProbe` owns async section records, and `SectionMeshWorkerPool` performs pure deterministic mesh construction from immutable `SectionSnapshot` + `SectionBakedQuadSnapshot` inputs. Live world/model/material/light capture and all GPU allocation/staging/draw/retirement ownership remain render-thread-only.

The validated P2.6/P2.7 probe classes remain in source as historical correctness oracles. Greedy/bitmask meshing remains downstream P3.2 work.

#### Dev2 corrected package evidence

Corrected source head tested before documentation synchronization:

- `da4bd615a7de0bf90ac42c39ab945bb4903ae194`.

Exact GitHub Actions evidence:

- corrected run `32521379106`, Java 25 / Gradle 9.5.1, build + artifact upload success, release skipped;
- documentation-synchronized run `32521543712`, success;
- corrected runtime JAR: `Obsidian-0.3.0-phase3-dev2.jar`;
- size `269,557` bytes;
- SHA-256 `0f1cc8f2aa50da277c8b6bacb531d065ba7ecf489c9e406a2e15fa7c8a455044`.

A-0096 package hashes are historical pre-A-0097 evidence and are superseded for runtime testing.

#### Dev2 reference runtime success

The corrected A-0097 package was run on the reference Windows 11 / Prism Launcher / Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.158.0+26.2 / Java 25.0.1 / Radeon RX 6800 XT Vulkan system. The production asynchronous scene gate passed under real dirty-event rebuilds, resource reload, camera recenter churn, and normal shutdown.

Final evidence:

- `phase3GateReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- `renderThreadCaptureOwnership=true`;
- `renderThreadGpuOwnership=true`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- worker submitted/started/completed `131/131/131`;
- worker cancelled `0`, queue-full rejections `0`, failures `0`;
- worker stolen jobs `97`;
- worker maximum queue depth `1`;
- scene worker installs / record installs `131/131`;
- scene READY transitions `25`;
- scene rebuilds `24`;
- maximum live records `8`;
- maximum adjacent pairs `10`;
- camera recenter events `2`;
- resource reload events `2`;
- dirty events `1,052`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed `9,553,256 / 9,553,256` bytes;
- pending staging batches `0`, staging abandonment `false`;
- arena used bytes `0`, allocations `262`, retired/reclaimed `262/262`, fragmentation `0`, arena abandonment `false`;
- deferred resources retired/released `131/131`, pending `0`;
- process exit code `0`.

This closes the dev2 production async-scene runtime gate. The same dev2 run recorded `chunkLoadEvents=0` and `chunkUnloadEvents=0`; those counters do not substitute for the standalone P2.6 fixed-target unload/return gate.

The user explicitly authorized Phase 3 merging after this successful run. PR #32 still remains draft/unmerged because its recorded merge contract requires the P2.6 -> P2.7 dependency chain to reach `main` first.

#### Runtime artifact handoff preference

For future user runtime testing, provide the direct versioned Obsidian `.jar` as the user-facing artifact whenever possible rather than requiring the user to unpack the GitHub Actions ZIP wrapper.

### Remaining P3.1 after dev2 runtime success

Before P3.2 activates, P3.1 still needs:

1. stronger relevance-aware prioritization/cancellation under streaming pressure;
2. worker-local reusable scratch/allocation reduction;
3. production queue latency, execution latency and output-size evidence;
4. scheduler/backpressure tuning justified by measurements.

The dev2 run's `maxSimultaneousSceneJobs=1` / maximum worker queue depth `1` is useful correctness evidence but is not sufficient streaming-pressure scheduler evidence. A deliberate pressure scenario is still required before scheduler-performance claims.

Do not begin greedy/bitmask meshing merely because dev2 passed; P3.2 remains downstream of P3.1 closure.

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
