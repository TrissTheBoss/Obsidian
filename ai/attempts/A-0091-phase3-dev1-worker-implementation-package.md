# A-0091 - Phase 3 dev1 worker/job implementation and package

Status: **IMPLEMENTED / EXACT CI SUCCESS / PACKAGE + BYTECODE VERIFIED / REFERENCE RUNTIME PENDING**

Date: 2026-08-21
Branch: `phase3/worker-job-architecture`
Canonical stacked PR: #29
Temporary exact-CI PR: #30 (never merge)
Version: `0.3.0-phase3-dev1`

## Context

A-0090 froze the first P3.1 proof boundary: validate bounded asynchronous worker/job architecture using real immutable Phase 2 terrain inputs before replacing the known-good synchronous Phase 2 scene installation path.

## Implementation

### `SectionMeshWorkerPool`

Implemented a dedicated bounded pool with:

- 1..4 daemon workers (`availableProcessors - 2`, capped at four, minimum one);
- three priority lanes per worker: HIGH / NORMAL / LOW;
- 16 queued jobs maximum per worker;
- normal least-depth/round-robin admission;
- peer work stealing from idle workers;
- immutable `SectionSnapshot` + `SectionBakedQuadSnapshot` job inputs only;
- renderer generation + lifecycle event-sequence identity on every ticket;
- explicit cancellation requests and terminal COMPLETED/CANCELLED/FAILED states;
- duplicate worker mesh builds with deterministic equality validation;
- bounded shutdown through interrupt + 500 ms join per worker;
- metrics for submissions, queue-full rejection, started/completed/cancelled/failed/stolen jobs, cancellation requests, queue depth, queue wait and execution time.

No worker receives live Minecraft world/chunk/model objects or GPU resources.

### `WorkerMeshValidationProbe`

The render thread captures one useful real section after the startup settle delay and preserves duplicate Phase 2 reference-oracle and generalized-capture checks. The probe then:

- pins 12 real immutable jobs initially to worker 0 so peers must steal;
- mixes all three priorities;
- leaves the first four jobs intended to complete;
- immediately requests cancellation for jobs 4..11;
- accepts results only while lifecycle event-sequence identity remains current;
- validates every completed mesh against the immutable snapshot/capture;
- requires identical fingerprints across all accepted completed meshes;
- requires terminal accounting for all 12 jobs, at least four completions, at least one actual cancellation, all eight cancellation requests, nonzero stealing, zero queue rejection/failure and zero remaining queued/running jobs.

The cancellation request was hardened after the first compile-clean candidate so a fast CPU cannot finish the whole batch before cancellation is even requested.

### `FrameCoordinator`

The dev8 coordinator now owns the worker pool and validation probe beside the validated P2.7 scene. Shutdown records a separate `phase3GateReady` and keeps `sceneGateReady` as continuity diagnostics. It explicitly logs:

- `workerWorldReadsAfterCapture=0`;
- `boundedPriorityQueues=true`;
- `workStealing=true`;
- `generationTaggedJobs=true`;
- `productionSceneInstallStillSynchronous=true`.

The latter is an intentional non-claim: this first proof validates the concurrency boundary, not production scene installation or performance.

## Exact CI

Initial compile-clean candidate run `32511683233` succeeded. Runtime-proof cancellation accounting was then hardened without changing the worker architecture.

Final hardened behavior head: `fca4d0c309c8eaacd8159394303b5d6b746f2c49`.

GitHub Actions run `32511869600`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- Upload build artifacts: SUCCESS;
- Publish versioned release: SKIPPED.

Artifact ID: `9457318316`.

## Package

Artifact contents:

- `Obsidian-0.3.0-phase3-dev1.jar`;
- SHA-256 `7cd00dbc0db9cfef9ef0a4afc381abf4691ca32899f5bd02e58f8727deffb093`;
- `Obsidian-0.3.0-phase3-dev1-sources.jar`;
- SHA-256 `63de62fa1a686f4c4ea029f8150aed98baf91c7fe41647e79932c5011b75ad2d`;
- metadata exactly `obsidian 0.3.0-phase3-dev1`.

## Packaged-bytecode verification

Inspection of the built JAR confirms:

- `FrameCoordinator` owns `SectionMeshWorkerPool` and `WorkerMeshValidationProbe`;
- the worker proof submits pinned immutable real-section jobs and requests cancellation immediately for its cancellation subset;
- the verification string records completed/cancelled/cancellation-request/stolen/queue/execution metrics;
- the final coordinator records `phase3GateReady`, `workerGateReady`, `workerWorldReadsAfterCapture=0`, `boundedPriorityQueues=true`, `workStealing=true`, `generationTaggedJobs=true`, and `productionSceneInstallStillSynchronous=true`;
- Phase 2 public graphics ownership remains unchanged.

## Runtime gate

Reference runtime closure for this first proof requires:

- `phase3GateReady=true`;
- `workerGateReady=true`;
- `workerSubmittedJobs=12`;
- at least four completed jobs;
- at least one actually cancelled job;
- at least eight cancellation requests;
- nonzero stolen jobs;
- zero queue-full rejection;
- zero worker failures;
- zero queued/running jobs at shutdown;
- full staging/arena/deferred-resource cleanup;
- process exit code 0.

No camera edits/reload/recenter are required for this worker proof. The P2.7 scene remains active as a continuity oracle but `sceneGateReady` does not need to pass again for P3 dev1.

## Remaining P3.1 work after this proof

Even if runtime passes, P3.1 remains broader than this first proof. Follow-on work must move the persistent scene's mesh production/install handoff onto worker results, add production relevance/staleness priority policy, harden cancellation/versioning under streaming pressure, and reduce hot-path allocations/reuse worker-local scratch before P3.2 bitmask visibility work is considered production-ready.

## Merge rule

PR #29 remains stacked and unmergeable until the P2.6 -> P2.7 dependency chain reaches `main`. No Phase 3 merge authorization exists.

This attempt is immutable once committed.
