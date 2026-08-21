# A-0092 — Phase 3 dev1 worker/job reference runtime success

Date: 2026-08-21
Status: **SUCCESS / FIRST P3.1 CONCURRENCY BOUNDARY RUNTIME VALIDATED / PRODUCTION SCENE INTEGRATION NEXT**

## Scope

Validate the first P3.1 worker/job proof from `0.3.0-phase3-dev1` without claiming production asynchronous scene installation.

Validated artifact:

- `Obsidian-0.3.0-phase3-dev1.jar`
- SHA-256 `7cd00dbc0db9cfef9ef0a4afc381abf4691ca32899f5bd02e58f8727deffb093`
- sources SHA-256 `63de62fa1a686f4c4ea029f8150aed98baf91c7fe41647e79932c5011b75ad2d`
- final evidence-synchronized source head before runtime: `b4cd6a81a9f49ba37ad62ef1a38adb2983ad12bb`
- exact CI run: `32512330473`

Reference runtime:

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT
- Vulkan backend

## Runtime result

The worker proof verified immediately after the five-second startup settle delay on a real surface section `(65,4,13)`.

Worker result:

- `completedJobs=4`
- `cancelledJobs=8`
- `cancellationRequests=8`
- `stolenJobs=11`
- `workerCount=4`
- `maxQueueDepth=9`
- `queueFullRejections=0`
- `failedJobs=0`
- `staleBatches=0`
- `workerWorldReadsAfterCapture=0`
- `boundedPriorityQueues=true`
- `workStealing=true`
- `generationTaggedJobs=true`
- `renderThreadGpuOwnershipPreserved=true`

Deterministic immutable identity was preserved end-to-end:

- snapshot fingerprint `10530425810497096744`
- reference fingerprint `15464505433696038540`
- baked fingerprint `13195846961920455472`
- worker mesh fingerprint `7025038833750373640`
- `workerDeterministicMatches=4`

Queue/execution timing evidence from this proof:

- total queue wait `462,200 ns`
- maximum queue wait `167,700 ns`
- total worker execution `22,622,800 ns`
- maximum worker execution `5,872,300 ns`

These values are evidence that the instrumentation works; this validation-only workload is not a production performance benchmark.

## Shutdown gate

The final coordinator shutdown passed the intended dev1 machine gate:

- `phase3GateReady=true`
- `workerGateReady=true`
- `hardFailure=false`
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

All renderer resources were clean at shutdown:

- staging submitted/reclaimed `7,211,648 / 7,211,648` bytes
- staging backpressure `0`
- pending upload batches `0`
- arena used bytes `0`
- arena allocations `196`, failures `0`
- arena retired/reclaimed `196 / 196`
- arena fragmentation `0`
- pending arena retirement batches `0`
- deferred resources retired/released `98 / 98`
- pending retirements `0`
- process exit code `0`

## Scene-gate interpretation

Shutdown reported `sceneGateReady=false`, with `cameraRecenterEvents=0`. This is expected and does not invalidate P3.1 dev1: A-0090/A-0091 explicitly made the already-validated P2.7 scene gate diagnostic for this proof. The P2.7 scene had already passed its own runtime + human gate in A-0089.

The same shutdown explicitly retained:

- `productionSceneInstallStillSynchronous=true`
- `droppedLifecycleEvents=0`
- `staleSceneRejections=0`
- `probeStaleInstallRejections=0`
- zero retirement backpressure/registration failures.

Therefore this attempt validates only the immutable worker/job concurrency boundary, exactly as intended.

## Conclusion

The first P3.1 worker/job architecture proof is runtime validated.

Proven:

1. bounded priority queues;
2. dedicated workers;
3. peer work stealing;
4. explicit cancellation;
5. generation/lifecycle-tagged immutable jobs;
6. deterministic real-section mesh construction off the render thread;
7. zero worker world reads after capture;
8. render-thread GPU ownership preserved;
9. clean bounded shutdown/resource reclamation.

Not yet proven and therefore still P3.1 scope:

- production persistent-scene mesh jobs sourced from the worker pool;
- worker-result install handoff;
- relevance/distance/staleness priority policy under streaming pressure;
- production cancellation/replacement during scene churn;
- reusable worker-local mesh scratch / allocation reduction;
- production queue/latency/output-size benchmark evidence.

Next attempt should integrate worker-produced `BakedSectionMesh` results into persistent scene ownership while keeping capture and GPU upload/install on the render thread and retaining generation/event-sequence rejection before install.
