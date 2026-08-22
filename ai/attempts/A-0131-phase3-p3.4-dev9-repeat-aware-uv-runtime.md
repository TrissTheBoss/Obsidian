# A-0131 — Phase 3 P3.4 dev9 repeat-aware UV runtime closure

**Result: SUCCESS**

## Scope
Reference-machine runtime closure for `0.3.0-phase3-dev9` / PR #41 (`phase3/repeat-aware-uv-descriptor`). Dev9 remains sidecar-only and does not change emitted terrain geometry.

Canonical runtime JAR used by the reference run:
- `Obsidian-0.3.0-phase3-dev9.jar`
- 354,912 bytes
- SHA-256 `4f06323d7d60288a2c2bb48676918842e3e9cfa9bd604156c9e24aa1aedc0b46`
- source/package head `0bca09023876cf661171749f7ef86f7f287307c0`

Reference runtime:
- Minecraft 26.2
- Fabric Loader 0.19.3
- Java 25.0.1
- Vulkan
- AMD Radeon RX 6800 XT
- AMD proprietary driver 26.7.1 / Vulkan 1.4.315

## Frozen runtime gates
Final shutdown evidence reports:
- `phase3GateReady=true`
- `schedulerEvidenceReady=true`
- `binaryVisibilityEvidenceReady=true`
- `greedyRectangleEvidenceReady=true`
- `renderMergeKeyEvidenceReady=true`
- `renderMergeCandidateEvidenceReady=true`
- `ordinaryQuadEmissionSafetyEvidenceReady=true`
- `repeatAwareUvEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `productionSceneInstallStillSynchronous=false`
- `productionWorkerSceneIntegration=true`
- `renderThreadCaptureOwnership=true`
- `renderThreadGpuOwnership=true`
- `workerWorldReadsAfterCapture=0`
- `binaryVisibilitySidecarIntegrated=true`
- `greedyRectangleSidecarIntegrated=true`
- `renderMergeKeySidecarIntegrated=true`
- `renderMergeCandidateSidecarIntegrated=true`
- `ordinaryQuadEmissionSafetySidecarIntegrated=true`
- `repeatAwareUvDescriptorSidecarIntegrated=true`
- `greedyRectangleGpuEmission=false`
- `renderCorrectMergeKeyComplete=false`
- `synchronousSceneMeshBuilds=0`

`phase2ChunkLifecycleEvidenceReady=false` and `fixedAnchorReturnSceneReady=false` are expected/nonblocking because that fixed-anchor unload/return proof was already closed earlier and was not part of the dev9 frozen gate.

## Worker/scheduler evidence
- workers: 4, queue capacity 64
- submitted/started/completed: 261/261/261
- cancelled: 0
- cancellation requests: 0
- stolen: 193
- queue-full rejections: 0
- failed jobs: 0
- shutdown join failures: 0
- HIGH/NORMAL/LOW submitted: 29/116/116
- HIGH/NORMAL/LOW completed: 29/116/116
- output quads: 185,337
- output vertex bytes: 20,757,744
- output index bytes: 4,448,088
- worker scratch uses: 267
- determinism audits/matches: 6/6

## P3.2 visibility evidence
- builds: 261
- total faces: 77,748
- W/E/D/U/N/S: 9,742 / 5,079 / 631 / 49,144 / 6,406 / 6,746
- retained bytes: 801,792 = 261 * 3,072
- scratch uses: 267
- determinism audits/matches: 6/6
- reference audits/matches: 6/6

## P3.3 rectangle evidence
- builds: 261
- rectangles: 34,559
- covered faces: 77,748
- faces saved: 43,189
- reduction: 55.5%
- retained bytes: 138,236 = 34,559 * 4
- mask audits/matches: 261/261
- determinism audits/matches: 6/6
- reference audits/matches: 6/6

## Dev6 render-key evidence
- builds: 261
- visible faces: 77,748
- eligible: 54,290
- unmapped: 0
- ambiguous: 23,458
- exact partition: 54,290 + 0 + 23,458 = 77,748
- recognized canonical quads: 101,206
- ignored noncanonical quads: 84,131
- same-key adjacencies: 8,108
- different-key adjacencies: 51,557
- ineligible adjacencies: 10,228
- retained bytes: 12,828,672 = 261 * 49,152
- scratch uses: 267
- determinism audits/matches: 6/6

## Dev7 render-merge candidate evidence
- builds: 261
- candidates: 47,688
- covered eligible faces: 54,290
- passthrough canonical faces: 23,458
- singletons: 42,421
- multi-face: 5,267
- faces saved: 6,602
- reduction: 12.1% of eligible faces
- retained bytes: 286,128 = 47,688 * 6
- coverage audits/matches: 261/261
- determinism audits/matches: 6/6

## Dev8 ordinary four-vertex safety evidence
- builds: 261
- candidates: 47,688
- singletons: 42,421
- multi-face: 5,267
- color safe/unsafe: 5,266 / 1
- light safe/unsafe: 5,267 / 0
- ordinary atlas UV safe/unsafe: 0 / 5,267
- ordinary four-vertex safe/unsafe: 0 / 5,267
- repeat-aware required: 5,267
- retained bytes: 47,688
- classification audits/matches: 261/261
- determinism audits/matches: 6/6

## Dev9 repeat-aware UV descriptor result
This is the decisive dev9 result.

- builds: 261
- source multi-face candidates: 5,267
- representable: **5,267**
- unrepresentable: **0**
- exact representability rate: **100%** of observed multi-face candidates
- repeat-aware four-vertex safe: **5,266**
- repeat-aware four-vertex unsafe: **1**
- safe covered faces: 11,867
- safe faces saved: 6,601
- safe reduction: 12.1% of render-key eligible faces
- representable W/E/D/U/N/S: 55 / 291 / 5 / 4,796 / 53 / 67
- safe W/E/D/U/N/S: 55 / 291 / 4 / 4,796 / 53 / 67
- safe covered faces W/E/D/U/N/S: 120 / 1,006 / 8 / 10,409 / 162 / 162
- retained bytes: **100,073 = 5,267 * 19**
- scratch uses: 267
- max descriptors/build: 56
- classification audits/matches: 261/261
- determinism audits/matches: 6/6

The one repeat-aware four-vertex-unsafe candidate is explained by the dev8 color partition: light is safe for all 5,267 and the repeat-aware UV descriptor is representable for all 5,267, while color interpolation is unsafe for exactly one candidate. No UV descriptor failure remains in this observed runtime set.

## Scene/lifetime closure
- scene worker submitted/completed/installed: 261/261/261
- scene worker cancelled: 0
- stale worker discards: 0
- queue rejections: 0
- preinstall invalidations: 0
- scene READY transitions: 29
- scene rebuilds: 28
- record installs: 261
- camera recenter events: 4
- dirty events: 1,190
- resource reload events: 1
- dropped lifecycle events: 0
- unsafe stale scene installs: 0
- `workersClean=true`
- `stagingClean=true`
- `arenaClean=true`
- `resourcesClean=true`
- staging submitted/reclaimed: 25,216,272 / 25,216,272
- pending upload batches: 0
- arena used bytes: 0
- arena allocations/retired/reclaimed: 522 / 522 / 522
- pending arena retirement batches: 0
- retired/released resources: 261 / 261
- pending retirements: 0

Launcher shutdown is exact:
- `Process exited with code 0.`

## Conclusion
Dev9 frozen runtime evidence is complete and successful. The observed world proves that the atlas-UV blocker identified by dev8 is fully representable by the frozen sprite-local repeat descriptor contract: all 5,267 observed multi-face candidates carry a valid two-U/two-V affine square mapping with exact raw atlas bounds/orientation. After adding dev8 color/light constraints, 5,266 remain four-vertex-safe; the sole exclusion is color interpolation, not UV or light.

This **does not authorize immediate greedy GPU emission by itself**. The next P3.4 slice must separately freeze and prove actual repeat-aware transport/sampling semantics, atlas filtering/padding behavior, and relevant raster/T-junction obligations before changing emitted terrain geometry. Any geometry-changing runtime slice still requires renewed explicit human visual validation before promotion.
