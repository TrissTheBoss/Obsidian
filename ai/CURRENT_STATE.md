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

## Current dependency chain

### P2.6 / dev6 — corrected behavior proven, fixed-target chunk lifecycle coverage still open

- Branch: `phase2/section-lifecycle-rebuild`.
- PR: #25, open/draft against `main`.
- Corrected standalone version: `0.2.0-phase2-dev6`.
- User has standing dev6 merge authorization conditional on the existing fixed-target runtime gate.
- A-0080: lifecycle plan.
- A-0081: exact Minecraft 26.2 lifecycle/API inspection.
- A-0082: implementation/package.
- A-0083: initial event-ring runtime defect + target-scoped sticky/coalesced bridge correction.
- A-0084: corrected reference retest partial.

Corrected standalone dev6 package:

- JAR SHA-256 `486db6d80490170961d5a98debcb691e440e775e433283ab50b30c894821feb8`;
- sources SHA-256 `786d3c2f54bdc5f1e29b6ff10f5d4009eba7398dc215606fec3bbd4e86354f0e`.

Corrected retest proved edit rebuilds, resource reload, zero dropped events, zero stale installs, deterministic Phase 2 capture/build invariants, full staging/arena/deferred-resource reclamation and exit 0. It did **not** exercise the required fixed-target unload/return class:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

Therefore P2.6 remains formally open. Remaining closure requires the corrected standalone dev6 artifact to observe nonzero tracked-neighborhood chunk unload/load counters and end with `lifecycleGateReady=true`, zero dropped events, zero stale installs, full reclamation and exit 0.

### P2.7 / dev7 — runtime + human validated and merge-authorized; integrated into P2.6 branch

- Version: `0.2.0-phase2-dev7`.
- Original branch: `phase2/multi-section-scene`.
- PR #27 was runtime + human visual validated and explicitly merge-authorized by the user.
- Fresh exact evidence-head CI run `32512002699` passed Java 25 / Gradle 9.5.1, Build and artifact upload; release skipped.
- PR #27 was merged into its P2.6 base using merge commit `91eb704e769fff5d872c628a710cd8128a3314ee`.
- PR #25 now carries the combined corrected P2.6 + validated P2.7 stack.
- Combined documentation-synchronized branch head `2bc4ece2b88d85c2e49e957e93a2d5f076271fd0` passed exact run `32512405528`.

P2.7 evidence: A-0085 through A-0089.

Validated package:

- JAR SHA-256 `59dde49b210b802fdd88e1bbc2da7a9eae9b7be045b9c760ae1adc3827599725`;
- sources SHA-256 `c1ebbe080fa5262cb77d4a4ff48c805712880100126f05611ed99fa163d76c57`.

Validated runtime included `sceneGateReady=true`, 16 ready transitions, 15 rebuilds, 144 record installs, 9 maximum live records, 12 adjacent pairs, two camera recenters, zero dropped lifecycle events, zero stale scene/probe installs, zero upload/retirement failures, full reclamation and exit 0. Human oracle passed with no persistent duplicate/missing borders or stale old-window geometry reported.

## Forward stacked work: Phase 3 P3.1 / dev1 — first worker proof runtime validated; production integration active

Phase 3 work is proceeding forward on top of validated dev7 while the Phase 2 branch waits for final P2.6 fixed-target coverage. Phase 3 remains non-mergeable until the P2.6 -> P2.7 chain reaches `main`.

- Branch: `phase3/worker-job-architecture`.
- Canonical stacked PR: #29, base `phase2/multi-section-scene`, draft.
- Version: `0.3.0-phase3-dev1`.
- No Phase 3 merge authorization exists.
- A-0090: first-proof plan.
- A-0091: implementation + package/bytecode evidence.
- A-0092: reference runtime success for the first worker/job concurrency boundary.

### Proven first P3.1 concurrency boundary

Render/client thread ownership remains:

- live world/chunk capture;
- generalized model/material/light capture;
- GPU allocation;
- staging/upload;
- draw encoding/install;
- completion-gated GPU retirement.

Workers receive only immutable renderer-owned `SectionSnapshot` + `SectionBakedQuadSnapshot` inputs with renderer generation, lifecycle event-sequence identity and bounded priority metadata. They perform pure `BakedSectionMesh.build(...)` work only and receive no live world/chunk/model/GPU objects.

`SectionMeshWorkerPool` provides:

- 1..4 dedicated daemon workers;
- HIGH/NORMAL/LOW bounded priority lanes;
- 16 queued jobs maximum per worker;
- no fallback/unbounded growth;
- least-depth normal admission;
- peer work stealing;
- generation + lifecycle sequence tags;
- explicit cancellation;
- terminal COMPLETED/CANCELLED/FAILED states;
- deterministic duplicate pure mesh builds;
- bounded shutdown;
- queue/rejection/start/complete/cancel/fail/steal/depth/wait/execution metrics.

### First worker proof runtime success

Validated artifact:

- JAR SHA-256 `7cd00dbc0db9cfef9ef0a4afc381abf4691ca32899f5bd02e58f8727deffb093`;
- sources SHA-256 `63de62fa1a686f4c4ea029f8150aed98baf91c7fe41647e79932c5011b75ad2d`;
- final pre-runtime head `b4cd6a81a9f49ba37ad62ef1a38adb2983ad12bb`;
- exact CI run `32512330473`, artifact `9457491423`.

Reference runtime A-0092 passed:

- `phase3GateReady=true`;
- `workerGateReady=true`;
- `hardFailure=false`;
- submitted `12` jobs;
- started/completed `4/4`;
- cancelled `8` from `8` cancellation requests;
- stolen jobs `11`;
- queue-full rejections `0`;
- worker failures `0`;
- stale worker batches `0`;
- deterministic accepted worker matches `4`;
- `workerWorldReadsAfterCapture=0`;
- bounded priority queues/work stealing/generation tags all true.

Instrumentation recorded max queue depth `9`, total/max queue wait `462,200 / 167,700 ns`, and total/max execution `22,622,800 / 5,872,300 ns`. These validate instrumentation and concurrency behavior, not production performance.

Shutdown was fully clean:

- staging submitted/reclaimed `7,211,648 / 7,211,648` bytes;
- no staging backpressure/pending batch;
- arena allocations `196`, failures `0`, retired/reclaimed `196/196`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `98/98`, pending `0`;
- process exit code `0`.

`sceneGateReady=false` in A-0092 is expected because the run intentionally did not perform the P2.7 recenter requirement (`cameraRecenterEvents=0`). P2.7 was separately validated in A-0089. The dev1 shutdown explicitly retained `productionSceneInstallStillSynchronous=true`, so no production async install claim is made.

### Active next P3.1 step

The first concurrency proof is complete. P3.1 now moves to production integration:

1. persistent scene records must enqueue immutable mesh jobs rather than synchronously building their drawable mesh;
2. worker-result handoff must be consumed on the render thread;
3. result installation must re-check section generation + relevant lifecycle sequence before any GPU allocation/upload/install;
4. stale/invalidated jobs must be cancelled or rejected without stale drawing;
5. capture/model/material/light work stays on the render thread for now;
6. GPU allocation/upload/draw/retirement stays on the render thread;
7. priority must become relevance-aware rather than validation-only pinning;
8. worker-local reusable scratch/allocation reduction and production queue/latency/output-size evidence remain P3.1 before P3.2 activates.

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
