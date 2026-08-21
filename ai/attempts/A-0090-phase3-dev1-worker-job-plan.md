# A-0090 - Phase 3 dev1 worker/job architecture first proof

Status: **ACTIVE / FIRST P3.1 PROOF IMPLEMENTED / EXACT CI PENDING**

Date: 2026-08-21
Branch: `phase3/worker-job-architecture`
Version: `0.3.0-phase3-dev1`
Base: validated dev7 head `143ab76df2b465274ee3075d45d34ddb9dd24a29`

## Context

P2.7/dev7 is runtime + human visual validated and now has standing user merge authorization, but it remains stacked behind P2.6 because P2.6 still lacks its mandatory fixed-target chunk unload/load runtime evidence. Forward Phase 3 work is therefore allowed only as a stacked, non-mergeable continuation.

The Phase 3 roadmap starts with P3.1 worker/job architecture: dedicated workers, work stealing, bounded priority queues, immutable snapshot jobs, generation/version checks, stale cancellation, worker-local reusable state, no worker live-world reads, and queue/execution/cancellation metrics.

## Dev1 proof boundary

This first proof validates the concurrency boundary independently before replacing the known-good Phase 2 scene installation path.

Render/client thread retains ownership of:

- live world/chunk reads;
- `SectionSnapshot` capture;
- generalized vanilla/model/material/light capture into `SectionBakedQuadSnapshot`;
- GPU allocation, staging, draw encoding, installation and retirement.

Dedicated mesh workers receive only:

- immutable `SectionSnapshot`;
- immutable `SectionBakedQuadSnapshot`;
- renderer generation;
- lifecycle event-sequence identity;
- bounded priority metadata.

Workers perform only pure `BakedSectionMesh.build(...)` work and deterministic duplicate-build validation. No worker receives `ClientLevel`, chunks, `BlockPos`, model-manager objects, GPU buffers, encoders, fences or renderer state.

## Worker pool architecture

`SectionMeshWorkerPool` provides:

- 1..4 dedicated daemon workers, defaulting to `availableProcessors - 2` capped at four;
- three bounded priority lanes per worker (HIGH/NORMAL/LOW);
- 16 queued jobs maximum per worker, no unbounded fallback queue;
- round-robin/least-depth normal admission;
- peer work stealing when a worker's local lanes are empty;
- generation + lifecycle sequence tags carried in every ticket;
- explicit cancellation requests;
- terminal ticket states: COMPLETED/CANCELLED/FAILED;
- bounded shutdown using interruption + 500 ms join per worker;
- queue-wait, execution, queue-depth, submitted/started/completed/cancelled/failed/stolen/rejected metrics.

The current P2.5 mesh builder produces owned output arrays. This dev1 proof does not yet claim final worker-local scratch reuse or zero temporary allocation for the production greedy mesher; those remain active P3.1/P3.2 optimization work. It does preserve the stronger existing invariant that no per-face Java object graph is constructed by the renderer's reference mesh representation.

## Runtime validation probe

`WorkerMeshValidationProbe`:

1. captures one useful real section after the normal startup settle delay;
2. preserves duplicate reference-oracle and generalized-capture determinism checks;
3. pins 12 real immutable mesh jobs initially to worker 0 so idle peers must steal;
4. mixes HIGH/NORMAL/LOW priorities;
5. requests cancellation for three jobs;
6. accepts completed worker meshes only while lifecycle sequence identity remains current;
7. validates every accepted mesh against its immutable snapshots;
8. requires identical mesh fingerprints across accepted completed jobs;
9. rejects worker failures, queue overflow, missing work stealing or inconsistent accounting.

The validated Phase 2 3x3 scene continues rendering through its existing synchronous mesh installation path in this dev1 build. This is deliberate. The runtime log carries `productionSceneInstallStillSynchronous=true` so the first worker proof cannot be confused with completed production integration.

## Dev1 machine gate

`phase3GateReady=true` requires:

- worker validation probe success;
- at least one stolen job;
- at least one cancelled job;
- zero bounded-queue rejection;
- zero worker failure;
- zero queued/running worker jobs at shutdown;
- zero pending staging/arena/resource retirement after shutdown cleanup;
- no hard failure.

The proof log also records `workerWorldReadsAfterCapture=0`, bounded-priority-queue identity, work-stealing identity and generation-tagged jobs.

## Deliberate non-claims

This dev1 proof does **not** yet claim:

- worker-produced meshes are installed into the persistent scene;
- final worker-local reusable greedy-mesher scratch;
- P3.2 binary visibility masks;
- P3.3 greedy rectangle extraction;
- production priority heuristics tied to camera distance/latency;
- production cancellation latency under streaming pressure;
- performance wins versus the synchronous path.

Those claims require later implementation/runtime evidence.

## Merge rule

This Phase 3 branch is stacked on validated dev7 and cannot merge until the P2.6 -> P2.7 dependency chain is closed. No Phase 3 merge authorization exists yet.

This attempt is immutable once committed.
