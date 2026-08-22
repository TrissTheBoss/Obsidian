# A-0126 — Phase 3 P3.4 dev8 runtime success

Date: 2026-08-23
Result: **SUCCESS**
Milestone: P3.4 dev8 ordinary four-vertex emission-safety classification
Package: `Obsidian-0.3.0-phase3-dev8.jar`
Package SHA-256: `f7155754683c6f484356cc4e729bd5de262b4acd355df05a49e55122903f9f4e`
Canonical package source head: `cc7e4d64bdf000635ed765a6e68a6c30cc9c2a8f`

## Runtime environment

- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Vulkan
- AMD Radeon RX 6800 XT
- AMD proprietary driver 26.7.1 / Vulkan 1.4.315

## Frozen gate result

The complete shutdown tail reports:

- `phase3GateReady=true`
- `schedulerEvidenceReady=true`
- `binaryVisibilityEvidenceReady=true`
- `greedyRectangleEvidenceReady=true`
- `renderMergeKeyEvidenceReady=true`
- `renderMergeCandidateEvidenceReady=true`
- `ordinaryQuadEmissionSafetyEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `workerWorldReadsAfterCapture=0`
- `synchronousSceneMeshBuilds=0`
- all five sidecars integrated
- `greedyRectangleGpuEmission=false`
- `renderCorrectMergeKeyComplete=false`

The historical P2 fixed-anchor lifecycle gate remains false/nonblocking as expected and was already closed by A-0101.

## Production workers / scheduler

- workers: 4; queue capacity: 64
- submitted / started / completed: `234 / 234 / 234`
- cancelled: 0
- cancellation requests: 0
- stolen: 190
- queue-full rejections: 0
- failed jobs: 0
- shutdown join failures: 0
- HIGH / NORMAL / LOW submitted: `25 / 103 / 106`
- HIGH / NORMAL / LOW completed: `25 / 103 / 106`
- output quads: 176,532
- vertex bytes: 19,771,584
- index bytes: 4,236,768
- worker determinism audits: `5 / 5`

## P3.2 visibility

- builds: 234
- visible faces: 94,258
- W/E/D/U/N/S: `8,380 / 14,453 / 5,396 / 41,292 / 12,299 / 12,438`
- retained bytes: `718,848 = 234 * 3,072`
- determinism: `5 / 5`
- independent reference audits: `5 / 5`

## P3.3 topology rectangles

- builds: 234
- rectangle count: 38,884
- covered faces: 94,258 exact
- faces saved: 55,374
- reduction: 58.7%
- retained bytes: `155,536 = 38,884 * 4`
- primary coverage audits: `234 / 234`
- determinism: `5 / 5`
- independent reference audits: `5 / 5`

## Dev6 canonical render keys

- builds: 234
- visible / eligible / unmapped / ambiguous: `94,258 / 74,152 / 0 / 20,106`
- eligible rate: 78.6%
- recognized canonical baked quads: 114,364
- ignored noncanonical baked quads: 62,168
- same / different / ineligible adjacencies: `10,905 / 64,379 / 14,396`
- retained bytes: `11,501,568 = 234 * 49,152`
- determinism: `5 / 5`

## Dev7 render-key-aware merge candidates

- builds: 234
- candidate count: 65,533
- covered eligible faces: 74,152 exact
- canonical passthrough: 20,106 exact
- singleton / multi-face: `59,150 / 6,383`
- faces saved: 8,619
- reduction over eligible faces: 11.6%
- retained bytes: `393,198 = 65,533 * 6`
- coverage audits: `234 / 234`
- determinism: `5 / 5`

## Dev8 ordinary four-vertex emission-safety result

- builds: 234
- classified candidates: 65,533, exactly matching dev7
- singleton candidates: 59,150, exactly matching dev7
- multi-face candidates: 6,383, exactly matching dev7
- color-safe / color-unsafe multi-face: `6,352 / 31`
- light-safe / light-unsafe multi-face: `6,383 / 0`
- UV-safe / UV-unsafe multi-face: `0 / 6,383`
- ordinary-safe / ordinary-unsafe multi-face: `0 / 6,383`
- repeat-aware-required candidates: 6,383
- ordinary-safe covered faces: 0
- ordinary-safe faces saved: 0
- retained bytes: `65,533 = 65,533 * 1`
- classification audits: `234 / 234`
- determinism audits: `5 / 5`

### Interpretation

The frozen dev8 classifier intentionally allows a zero ordinary-safe multi-face result. This run demonstrates exactly that outcome. Light interpolation is safe for every multi-face candidate and color interpolation is safe for all but 31, but **ordinary atlas UV0 is unsafe for every one of the 6,383 multi-face candidates**. Therefore dev7 grouping remains valid topology/render-key evidence, but the current four-vertex atlas representation cannot exploit any multi-face candidate without a repeat-aware UV representation or equivalent exact mechanism.

This is evidence against ordinary-quad greedy emission, not a failed gate. The next P3.4 slice must preserve atlas/sprite identity while representing per-cell repetition explicitly rather than stretching or leaving the source sprite rectangle.

## Scene / lifecycle / lifetime closure

- scene worker submitted / completed / installed: `234 / 234 / 227`
- stale result discards / preinstall invalidations: `7 / 7`
- scene READY transitions: 26
- scene rebuilds: 25
- camera recenter events: 6
- dirty events: 1,237
- resource reload events: 1
- dropped lifecycle events: 0
- unsafe stale installs: 0
- workers clean: true
- staging clean: true
- arena clean: true
- resources clean: true
- staging submitted / reclaimed: `23,115,616 / 23,115,616`
- arena allocations / retired / reclaimed: `454 / 454 / 454`
- arena used bytes at closure: 0
- resources retired / released: `227 / 227`
- pending retirements: 0

Shutdown contains render-thread `Stopping!` followed by the complete `FrameCoordinator.close()` evidence line and `Process exited with code 0`.

## Visual boundary

No separate human visual verdict is claimed from this pasted log. Dev8 does not change GPU-emitted terrain geometry, so promotion is governed by the frozen sidecar/runtime gates. Any later slice that actually changes emitted terrain geometry still requires renewed explicit human visual validation before promotion.

## Promotion conclusion

All frozen dev8 CI/package/runtime correctness and lifetime gates pass. Under the standing Phase 3 authorization, PR #40 is eligible for promotion with `[no-release]` after an exact evidence-head CI run passes.
