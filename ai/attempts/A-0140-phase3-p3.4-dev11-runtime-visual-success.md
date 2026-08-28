# A-0140 — Phase 3 P3.4 dev11 reference runtime + visual validation

**Date:** 2026-08-29  
**Result:** SUCCESS — automated runtime gate passed and explicit human visual PASS received.

## Artifact under test
Canonical cleaned runtime from A-0139:
- `Obsidian-0.3.0-phase3-dev11.jar`
- size `399,361` bytes
- SHA-256 `89520af731dbfb48c35071de809d75db1f0c98cdd289e123a9c77f2bacc46418`

Reference system:
- Minecraft 26.2
- Fabric Loader 0.19.3
- Java 25.0.1
- Vulkan backend
- AMD Radeon RX 6800 XT
- AMD proprietary Vulkan driver 1.4.315 / driver package 26.8.1

## Human visual gate
The user explicitly reported: **“Everything looked visually fine.”**

This closes the mandatory dev11 human visual PASS gate for the geometry-changing canary. No visual failure was reported for repeat-reset lines, rectangle/section T-junctions, camera motion, rebuilds, resource reload, stretching, atlas bleed, mip/repeat shimmer, seams/cracks, winding/culling, color/light mismatch, double-draw/z-fighting, or holes.

## Final automated gates
Complete shutdown reported:
- `phase3GateReady=true`
- `schedulerEvidenceReady=true`
- `binaryVisibilityEvidenceReady=true`
- `greedyRectangleEvidenceReady=true`
- `renderMergeKeyEvidenceReady=true`
- `renderMergeCandidateEvidenceReady=true`
- `ordinaryQuadEmissionSafetyEvidenceReady=true`
- `repeatAwareUvEvidenceReady=true`
- `repeatAwareTransportEvidenceReady=true`
- `repeatAwareGreedyEmissionEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `productionSceneInstallStillSynchronous=false`
- `productionWorkerSceneIntegration=true`
- `renderThreadCaptureOwnership=true`
- `renderThreadGpuOwnership=true`
- `workerWorldReadsAfterCapture=0`
- all sidecar/integration flags through `repeatAwareGreedyMeshIntegrated=true`
- `repeatAwareGreedyGpuEmission=true`
- `renderCorrectMergeKeyComplete=false`
- `synchronousSceneMeshBuilds=0`

`phase2ChunkLifecycleEvidenceReady=false` and `fixedAnchorReturnSceneReady=false` are expected/nonblocking because A-0101 permanently closed that earlier lifecycle obligation.

## Worker/scheduler evidence
- workers: 4; queue capacity: 64
- submitted/started/completed: `284/284/284`
- cancelled/cancellation requests: `0/0`
- stolen: `208`
- queue-full rejections: `0`
- failed jobs: `0`
- shutdown join failures: `0`
- HIGH/NORMAL/LOW submitted: `36/128/120`
- HIGH/NORMAL/LOW completed: `36/128/120`
- output quads: `230,453`
- output vertex bytes: `25,810,736`
- output index bytes: `5,530,872`
- determinism audits/matches: `6/6`

## Proven topology/render sidecars
Visibility:
- builds `284`
- total visible faces `123,047`
- retained bytes `872,448 = 284 * 3,072`
- determinism `6/6`
- independent reference audits `6/6`

Greedy topology rectangles:
- builds `284`
- rectangles `50,836`
- covered faces `123,047`
- faces saved `72,211`
- reduction `58.6%`
- retained bytes `203,344 = 50,836 * 4`
- coverage/determinism/reference audits all `284/284`, `6/6`, `6/6`

Render-key sidecar remains exact and reports no unmapped visible face (`renderKeyUnmappedFaces=0`).

## Dev9/dev10 transport set feeding dev11
Repeat-aware UV/transport proof:
- transport builds `284`
- source multi-face `6,565`
- source representable `6,565`
- source four-vertex-safe `6,541`
- transport records `6,541`
- unsafe `24`
- covered faces `15,800`
- faces saved `9,259`
- explicit-gradient required `6,541`
- internal-S reset `2,705`
- internal-T reset `4,071`
- internal-both reset `235`
- internal reset union `6,541`
- outer-edge policy required `6,541`
- same atlas/sampler required `6,541`
- raster review required `6,541`
- proof audits/matches `284/284`
- determinism `6/6`

The historical dev10 flag `repeatAwareTransportBoundaryRasterObligationOpen=true` remains present as a record of the transport proof's obligation. Dev11 closes the promotion requirement for the tested canary by actually emitting the geometry and receiving the explicit real-hardware visual PASS; this does not claim that the later broader P3.6 T-junction policy is complete.

## Dev11 actual GPU-emission evidence
- `repeatAwareGreedyGpuEmission=true`
- installed validated records `261`
- scene worker installs `261`
- completed scene worker jobs `284`
- draw submissions `43,044`
- actual indirect calls `172,176`
- expected indirect calls `172,176`
- indirect classes per draw `4`
- resource epoch checks `43,044`
- transport records feeding emission `6,541`
- transport covered faces `15,800`
- transport faces saved `9,259`
- `repeatAwareGreedyInstallValidationPassed=true`
- `repeatAwareGreedyFixedFourClassDrawContract=true`
- `repeatAwareGreedyVisualValidationRequired=true`
- `repeatAwareGreedyVisualValidationAutomated=false`

Therefore actual indirect calls exactly equal `43,044 * 4 = 172,176` and every installed record passed the frozen render-thread hybrid validation before becoming LIVE.

## Scene/lifecycle evidence
- scene worker submitted/completed `284/284`
- stale discards/preinstall invalidations `23/23` with no unsafe stale install
- scene installs/record installs `261/261`
- queue rejections `0`
- scene READY transitions `29`
- scene rebuilds `28`
- max live records `9`
- max adjacent pairs `12`
- camera recenters `11`
- resource reload events `1`
- dropped lifecycle events `0`
- unsafe stale scene installs `0`

## Lifetime closure
- `workersClean=true`
- `stagingClean=true`
- `arenaClean=true`
- `resourcesClean=true`
- staging submitted/reclaimed `28,434,232 / 28,434,232`
- pending uploads `0`
- arena used bytes `0`
- arena allocations/retired/reclaimed `783/783/783`
- arena allocation failures `0`
- pending arena retirement batches `0`
- retired/released resources `261/261`
- pending retirements `0`
- retirement backpressure/registration failures `0/0`

Launcher result:
- `Process exited with code 0`

## Conclusion
Both frozen dev11 promotion gates are closed:
1. automated runtime evidence: PASS;
2. explicit human visual validation: PASS.

P3.4 dev11 is eligible for promotion under standing Phase 3 authorization. This success validates the bounded repeat-aware greedy GPU emission canary on the tested Vulkan reference system while keeping `renderCorrectMergeKeyComplete=false` and without prematurely closing the broader later P3.6 T-junction policy.