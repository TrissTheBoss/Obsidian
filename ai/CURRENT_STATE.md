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

Corrected retest proved:

- `hardFailure=false`;
- 18 installs / 17 rebuild installs;
- 878 exact dirty events, 432 player dirty events;
- 2 resource reload events;
- `droppedLifecycleEvents=0`;
- no overflow/safety-invalidate reason;
- zero stale install rejection;
- deterministic Phase 2 capture/build invariants;
- full staging, arena and deferred-resource reclamation;
- exit code 0.

It did **not** exercise the required fixed-target unload/return class:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

Therefore P2.6 remains formally open. This is a coverage gap, not evidence of a new bridge defect.

Remaining P2.6 closure requires running the corrected standalone dev6 artifact, traveling far enough that the tracked section's 3x3 halo chunk neighborhood unloads, returning so it loads/rebuilds, and ending with nonzero load/unload counters plus `lifecycleGateReady=true`, zero dropped events, zero stale installs, full reclamation and exit 0.

### P2.7 / dev7 — runtime + human validated and merge-authorized; integrated into P2.6 branch

- Version: `0.2.0-phase2-dev7`.
- Original branch: `phase2/multi-section-scene`.
- PR #27 was runtime + human visual validated and explicitly merge-authorized by the user.
- Fresh exact evidence-head CI run `32512002699` passed Java 25 / Gradle 9.5.1, Build and artifact upload; release skipped.
- PR #27 was merged into its existing P2.6 base using merge commit `91eb704e769fff5d872c628a710cd8128a3314ee`.
- This stacked merge does not put P2.6/P2.7 on `main` and does not waive P2.6's remaining runtime gate.
- PR #25 now carries the combined corrected P2.6 + validated P2.7 stack.

P2.7 evidence:

- A-0085 architecture;
- A-0086 implementation/package;
- A-0087 runtime-gate refinement;
- A-0088 refined package;
- A-0089 reference runtime success.

Validated package:

- JAR SHA-256 `59dde49b210b802fdd88e1bbc2da7a9eae9b7be045b9c760ae1adc3827599725`;
- sources SHA-256 `c1ebbe080fa5262cb77d4a4ff48c805712880100126f05611ed99fa163d76c57`.

Validated runtime:

- `sceneGateReady=true`;
- `sceneReadyTransitions=16`;
- `sceneRebuilds=15`;
- `recordInstalls=144`;
- `maxLiveRecords=9`;
- `maxAdjacentPairs=12`;
- `cameraRecenterEvents=2`;
- `dirtyEvents=709`, `playerDirtyEvents=342`;
- `resourceReloadEvents=1`;
- `droppedLifecycleEvents=0`;
- zero stale scene/probe installs;
- zero upload/retirement failures;
- full staging/arena/deferred-resource reclamation;
- exit code 0.

Human oracle: the user reported the multi-section scene looked completely fine, with no persistent duplicate/missing shared borders or stale old-window geometry.

## Forward stacked work: Phase 3 P3.1 / dev1 — worker/job architecture first proof

Phase 3 work is proceeding forward on top of validated dev7 while the Phase 2 branch waits for the final P2.6 fixed-target runtime coverage. Phase 3 remains non-mergeable until the P2.6 -> P2.7 chain reaches `main`.

- Branch: `phase3/worker-job-architecture`.
- Canonical stacked PR: #29, base `phase2/multi-section-scene`, draft.
- Temporary exact-CI PR: #30 against `main`, never merge.
- Version: `0.3.0-phase3-dev1`.
- No Phase 3 merge authorization exists.
- A-0090: P3.1 first-proof plan.
- A-0091: implementation + package/bytecode evidence.

### P3.1 first-proof boundary

This dev1 build validates the concurrency boundary before replacing the known-good Phase 2 scene installation path.

Render/client thread still owns:

- live world/chunk capture;
- generalized model/material/light capture;
- GPU allocation;
- staging/upload;
- draw encoding/install;
- completion-gated GPU retirement.

Workers receive only immutable renderer-owned data:

- `SectionSnapshot`;
- `SectionBakedQuadSnapshot`;
- renderer generation;
- lifecycle event-sequence identity;
- bounded priority metadata.

Workers perform pure `BakedSectionMesh.build(...)` work only. They receive no live `ClientLevel`, chunk, model-manager, block-position or GPU objects.

### Implemented worker pool

`SectionMeshWorkerPool` currently provides:

- 1..4 dedicated daemon workers, default `availableProcessors - 2` capped at four;
- HIGH/NORMAL/LOW bounded priority lanes;
- 16 queued jobs maximum per worker;
- no fallback/unbounded queue growth;
- least-depth normal admission;
- peer work stealing;
- generation + lifecycle sequence tags on every ticket;
- explicit cancellation;
- terminal COMPLETED/CANCELLED/FAILED states;
- duplicate pure mesh builds for deterministic worker validation;
- bounded interrupt/join shutdown;
- submission, queue rejection, start/complete/cancel/fail/steal, queue depth, queue wait and execution metrics.

### Real-section worker validation probe

`WorkerMeshValidationProbe` captures one useful real section on the render thread and then:

- preserves duplicate reference-oracle and generalized-capture determinism checks;
- submits 12 real immutable mesh jobs initially pinned to worker 0 so peers must steal;
- mixes all three priorities;
- leaves the first four jobs as intended completions;
- immediately requests cancellation for jobs 4..11;
- accepts completed output only while lifecycle event sequence is still current;
- validates each completed mesh against immutable capture identity;
- requires equal deterministic mesh fingerprints across accepted completions.

The current Phase 2 3x3 scene remains active as a continuity oracle and still uses its synchronous mesh installation path. The dev8 shutdown log explicitly carries `productionSceneInstallStillSynchronous=true`; this first proof is not a performance claim and does not claim production async scene integration is complete.

### Exact dev8 compile/package evidence

Hardened behavior head `fca4d0c309c8eaacd8159394303b5d6b746f2c49` passed GitHub Actions run `32511869600`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- artifact upload: SUCCESS;
- public release: SKIPPED.

Artifact `9457318316`:

- `Obsidian-0.3.0-phase3-dev1.jar` SHA-256 `7cd00dbc0db9cfef9ef0a4afc381abf4691ca32899f5bd02e58f8727deffb093`;
- sources SHA-256 `63de62fa1a686f4c4ea029f8150aed98baf91c7fe41647e79932c5011b75ad2d`;
- metadata exactly `obsidian 0.3.0-phase3-dev1`.

Packaged bytecode confirms the bounded worker pool, real-section validation probe, immediate cancellation subset, `phase3GateReady`, `workerGateReady`, `workerWorldReadsAfterCapture=0`, `boundedPriorityQueues=true`, `workStealing=true`, `generationTaggedJobs=true`, and `productionSceneInstallStillSynchronous=true`.

A-0091 and this state synchronization are documentation-only changes after the inspected behavior package. Final exact-head CI/package equality check remains before runtime handoff.

### Dev8 runtime gate

The reference runtime only needs to enter a normal surface world containing supported SOLID + CUTOUT terrain, wait for the worker proof, then exit normally.

Expected closure:

- `phase3GateReady=true`;
- `workerGateReady=true`;
- `workerSubmittedJobs=12`;
- at least four accepted completed jobs;
- at least one actually cancelled job;
- at least eight cancellation requests;
- nonzero stolen jobs;
- zero queue-full rejection;
- zero worker failures;
- zero queued/running jobs at shutdown;
- full staging/arena/deferred-resource cleanup;
- process exit 0.

`sceneGateReady` does not need to pass again for this P3.1 worker proof because the P2.7 scene was already separately runtime/human validated. No edit/F3+T/recenter exercise is required for dev8.

### Remaining P3.1 after the first proof

Even after a successful dev8 runtime, P3.1 remains broader than this concurrency proof. Follow-on work must:

- move persistent scene mesh production/install handoff onto worker results;
- add production relevance/staleness priority policy;
- harden version/cancellation behavior under streaming pressure;
- reduce hot-path allocation and introduce reusable worker-local scratch;
- collect production queue/latency/output-size evidence.

Only then should P3.2 binary/bitmask visibility masks become the active production optimization milestone.

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
