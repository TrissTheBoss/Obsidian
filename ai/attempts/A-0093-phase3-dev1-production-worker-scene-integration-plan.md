# A-0093 — Phase 3 dev1 production worker-result scene integration

Date: 2026-08-21
Status: **ACTIVE / PLAN FROZEN / IMPLEMENTATION PRE-CI**

## Starting evidence

A-0092 runtime-validated the first P3.1 concurrency boundary on the reference Vulkan/RX 6800 XT system:

- `phase3GateReady=true`;
- `workerGateReady=true`;
- 12 immutable real-section jobs submitted;
- 4 completed, 8 cancelled;
- 11 stolen;
- zero queue rejection/failure/stale batch;
- deterministic worker output;
- zero worker world reads after capture;
- render-thread GPU ownership preserved;
- full staging/arena/deferred-resource reclamation;
- exit code 0.

The A-0092 build deliberately logged `productionSceneInstallStillSynchronous=true`. This attempt replaces that remaining synchronous mesh-production handoff without expanding the graphics ownership boundary.

## Goal

Make the persistent P2.7 3x3 scene consume real `BakedSectionMesh` results produced by `SectionMeshWorkerPool`, while preserving all proven Phase 2 capture, lifecycle, upload, draw and completion-gated retirement semantics.

## Ownership boundary

Render/client thread continues to own:

1. live world/chunk access;
2. `SectionSnapshot` capture;
3. generalized vanilla model/material/light capture into `SectionBakedQuadSnapshot`;
4. reference-oracle checks;
5. lifecycle invalidation observation;
6. GPU arena allocation;
7. staging/upload encoding;
8. indirect command resources;
9. draw encoding/install;
10. completion-gated retirement.

Workers receive only:

- immutable `SectionSnapshot`;
- immutable `SectionBakedQuadSnapshot`;
- renderer scene generation;
- relevant lifecycle event-sequence identity;
- bounded relevance priority.

Workers perform only pure `BakedSectionMesh.build(...)` work. No live Minecraft or GPU object is permitted across the worker boundary.

## State-machine change

`RealSectionLifecycleProbe` gains explicit asynchronous pre-install states:

`WAITING_WORLD -> WAITING_WORKER -> READY_TO_INSTALL -> LIVE`

with existing terminal/cleanup states retained.

Rules:

- before enqueue: generation/event sequence must still match;
- while queued/running: invalidation requests cancellation and suppresses installation immediately;
- completed results are validated against immutable snapshot/baked identity;
- immediately before GPU allocation/install: generation/event sequence and resource epoch are checked again;
- immediately before staging submission: lifecycle sequence is checked again;
- stale/cancelled pre-install worker work is discarded safely and must not count as a stale GPU install;
- GPU replacement/retirement remains completion-gated exactly as before.

## Persistent-scene scheduling

The 3x3 scene keeps the correctness-first whole-window invalidation policy for this milestone.

Production integration policy:

- multiple immutable mesh jobs may be in flight simultaneously;
- at most one READY result is GPU-installed per render frame;
- installation waits while the bounded staging ring has pending work;
- job priority is relevance based:
  - center section: HIGH;
  - Manhattan distance 1: NORMAL;
  - outer corners: LOW;
- no validation-only pinned submission is used by the production scene;
- bounded queue saturation is backpressure: scene work is deferred, never expanded into an unbounded fallback queue.

The independent `WorkerMeshValidationProbe` source may remain as a diagnostic oracle, but the active FrameCoordinator no longer needs its synthetic 12-job workload once the real scene uses the pool.

## Metrics

Track both global worker-pool and scene-consumed worker metrics:

- job submissions/completions;
- cancellation requests/cancelled jobs;
- stale/pre-install result discards;
- successful worker-result installs;
- queue wait and execution time;
- stolen accepted results;
- queue depth/rejections/failures;
- queued/running jobs at shutdown;
- renderer resource lifetime/reclamation metrics.

Safe pre-install cancellation/discard may be nonzero. Unsafe stale installation must remain zero.

## Reference runtime gate

The first integration package should require:

- `phase3GateReady=true`;
- `productionWorkerIntegrationReady=true`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- at least two scene READY transitions;
- at least one worker-backed scene rebuild;
- at least three worker-result record installs;
- scene worker install count equals renderer record install count;
- accepted scene worker completions at least equal installs;
- nonzero exact dirty events;
- successful resource reload observation;
- zero lifecycle drops;
- zero stale scene installs;
- zero probe stale GPU installs;
- zero worker queue-full rejection/failure on the reference workload;
- zero queued/running worker jobs at shutdown;
- full staging/arena/deferred-resource cleanup;
- exit code 0.

Human runtime exercise:

1. enter normal surface terrain with supported SOLID + CUTOUT content;
2. wait for the worker-integrated 3x3 scene to become READY;
3. inspect for missing/duplicate borders or stale geometry;
4. break/place blocks in the visible scene and allow a worker-backed rebuild;
5. perform F3+T once and allow the scene to rebuild;
6. exit normally and inspect the complete Prism log.

A scene recenter is not required for this P3.1 integration gate because P2.7 already validated recenter behavior separately.

## Deliberate boundary

This attempt does not yet claim:

- greedy/bitmask meshing;
- worker-side live model/world reads;
- worker-side GPU allocation/upload;
- partial remeshing;
- final large-distance scheduler policy;
- worker-local reusable scratch/allocation optimization;
- production performance wins.

After this integration gate passes, P3.1 still needs streaming-pressure prioritization/cancellation and worker-local scratch/allocation reduction before P3.2 becomes active.
