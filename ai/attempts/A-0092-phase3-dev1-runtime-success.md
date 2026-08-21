# A-0092 — Phase 3 dev1 worker/job runtime success

Date: 2026-08-21
Status: SUCCESS / REFERENCE RUNTIME VALIDATED / P3.1 WORKER BOUNDARY PROVEN

## Scope

Validate the first P3.1 concurrency boundary from `0.3.0-phase3-dev1` without claiming production asynchronous scene installation.

The build under test was the exact final package from branch `phase3/worker-job-architecture`, final documentation-synchronized head `b4cd6a81a9f49ba37ad62ef1a38adb2983ad12bb`.

Validated package SHA-256:

- `Obsidian-0.3.0-phase3-dev1.jar`: `7cd00dbc0db9cfef9ef0a4afc381abf4691ca32899f5bd02e58f8727deffb093`
- sources: `63de62fa1a686f4c4ea029f8150aed98baf91c7fe41647e79932c5011b75ad2d`

## Reference runtime

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Vulkan backend
- AMD Radeon RX 6800 XT
- AMD proprietary driver 26.7.1
- Prism Launcher 10.0.5

## Runtime result

The first real-section worker batch was submitted after startup settling with 12 immutable jobs, all initially pinned to worker 0. Eight cancellation requests were issued immediately for the cancellation subset.

The next frame logged worker/job architecture VERIFIED with:

- completed jobs: 4
- cancelled jobs: 8
- cancellation requests: 8
- stolen jobs: 11
- workers: 4
- max observed queue depth: 9
- total queue wait: 462,200 ns
- max queue wait: 167,700 ns
- total execution: 22,622,800 ns
- max execution: 5,872,300 ns
- queue-full rejections: 0
- failed jobs: 0
- stale batches: 0
- worker world reads after capture: 0
- bounded priority queues: true
- work stealing: true
- generation-tagged jobs: true
- render-thread GPU ownership preserved: true

The accepted four completed meshes all matched the same deterministic mesh fingerprint for the captured immutable section.

## Shutdown gate

Normal shutdown reported:

- `phase3GateReady=true`
- `workerGateReady=true`
- `hardFailure=false`
- `workerCount=4`
- `workerQueueCapacity=64`
- `workerSubmittedJobs=12`
- `workerStartedJobs=4`
- `workerCompletedJobs=4`
- `workerCancelledJobs=8`
- `workerCancellationRequests=8`
- `workerStolenJobs=11`
- `workerQueueFullRejections=0`
- `workerFailedJobs=0`
- `workerAcceptedCompleted=4`
- `workerAcceptedCancelled=8`
- `workerDeterministicMatches=4`
- `workerStaleBatches=0`
- `workerWorldReadsAfterCapture=0`
- `boundedPriorityQueues=true`
- `workStealing=true`
- `generationTaggedJobs=true`
- `productionSceneInstallStillSynchronous=true`
- `droppedLifecycleEvents=0`
- staging submitted/reclaimed: `7,211,648 / 7,211,648` bytes
- pending upload batches: 0
- arena allocations: 196
- arena allocation failures: 0
- arena retired/reclaimed: `196 / 196`
- arena used bytes: 0
- arena fragmentation: 0
- pending arena retirements: 0
- deferred resources retired/released: `98 / 98`
- pending deferred retirements: 0
- process exit code: 0

`sceneGateReady=false` is not a dev1 failure. This build deliberately retained the already-proven Phase 2 scene install path and the dev1 contract explicitly excluded a repeat of P2.7 camera-recenter/edit/F3+T validation. Runtime logs continue to mark `productionSceneInstallStillSynchronous=true`.

## Conclusion

The first P3.1 worker/job architecture proof is successful on the reference runtime. The immutable Phase 2 capture boundary survives bounded prioritized scheduling, peer stealing, explicit cancellation, generation/event tagging and deterministic multi-threaded pure mesh construction without live world reads or GPU ownership crossing to worker threads.

This is not yet production asynchronous scene installation and is not a meshing-performance claim.

## Next step

Continue P3.1 with production integration on a new stacked development branch:

1. capture generalized immutable section inputs on the render thread;
2. enqueue per-section mesh jobs with scene generation/event-sequence identity;
3. keep old live geometry until replacement worker output is accepted or invalidated;
4. reject/cancel stale jobs before GPU allocation/install;
5. upload/install accepted worker results on the render thread only;
6. replace whole-window synchronous mesh construction with per-record asynchronous lifecycle while preserving bounded staging and completion-gated retirement;
7. add relevance/staleness priority and reusable worker-local scratch before P3.2 becomes active.

Phase 3 remains unmerged and requires separate merge authorization.