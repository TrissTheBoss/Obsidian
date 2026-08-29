# A-0146 - P3.5 dev12.1 corrected runtime success and promotion evidence

**Date:** 2026-08-29  
**Objective:** Close the corrected P3.5 reference-runtime gate after A-0145 without waiving the A-0144 cancellation-accounting failure, then establish promotion evidence for P3.5.  
**Action:** Ran the canonical `Obsidian-0.3.0-phase3-dev12.1.jar` on the reference Windows 11 / RX 6800 XT Vulkan system. The exercise reached repeated async 3x3 READY states, exercised ordinary block-driven rebuild activity, exercised resource reload/rebuild, moved/recentered the scene, and exited Minecraft/Prism normally. The user additionally reported that everything was visually fine; P3.5 did not require a new visual gate because dev12/dev12.1 changed no emitted geometry, shader, pipeline, vertex/index format, atlas/lightmap semantics, or native graphics behavior.  
**Result:** `SUCCESS` — corrected final coordinator evidence is fully green. P3.5's frozen contract is satisfied and may be promoted under the standing Phase 3 authorization.

## Exact tested package

The tested package is the A-0145 canonical corrected binary:

- source/package head: `9d52a0d71b73f1f148a0f672555a98d6c97fe83f`
- version: `0.3.0-phase3-dev12.1`
- direct runtime JAR: `Obsidian-0.3.0-phase3-dev12.1.jar`
- size: `410,243` bytes
- SHA-256: `2a11b6aff62f671e53b48b37db73f38c6e8ba2749294e2fa946267aec533a13b`
- package workflow: `33261260933` — Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED

Runtime environment:

- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Windows 11
- AMD Radeon RX 6800 XT
- Vulkan backend / AMD driver 26.8.1

## Final coordinator gate

The final `FrameCoordinator` shutdown line reported all required gates true:

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
- `borderHaloCorrectnessEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `workerWorldReadsAfterCapture=0`
- `synchronousSceneMeshBuilds=0`
- `unsafeStaleSceneInstalls=0`

The historical Phase 2 fixed-anchor booleans remained false, as expected and irrelevant: A-0101 permanently closed that old obligation and the frozen P3.5 contract explicitly did not require repeating it.

## P3.5 border/halo proof

Final immutable proof totals:

- border proof records: `248`
- proof determinism audits/matches: `248 / 248`
- outward checks: `380,928`
- binary visibility matches: `380,928`
- independent reference matches: `380,928`
- expected visible outward faces: `2,273`
- unsupported blockers observed: `485`
- generalized border baked quads fingerprinted: `46,913`
- exact frozen border light/color samples: `187,652`
- shared-border pair audits: `328`
- shared-border state/class comparisons: `167,936`
- shared-border matches: `167,936`
- lifecycle classifier self-test: PASS

Dirty dependency evidence exercised all relevant classes:

- rendered-core dirty events: `963`
- halo-only dirty events: `588`
- horizontal-halo dirty events: `578`
- vertical-halo dirty events: `334`
- dropped lifecycle events: `0`
- observed reasons included section-dirty, world-change, resource-reload, and scene-recenter
- resource reload events: `2`
- camera recenter events: `2`
- READY transitions: `28`
- rebuilds: `27`

This closes the exact defect identified by A-0142: halo-only and vertical dependencies demonstrably participate in generation invalidation while all installed immutable border/halo comparisons remain exact.

## Corrected cancellation evidence result

This run had no stage-boundary cancellation:

- worker submitted/started/completed/cancelled: `248 / 248 / 248 / 0`
- cancellation requests: `0`
- queue-full rejections: `0`
- worker failures: `0`
- shutdown join failures: `0`

All dev8/dev9/dev10 aggregate identities therefore reduce to the original exact equality case and remain green. A-0145's cancellation-aware residual rules remain necessary because A-0144 proved legitimate stage-boundary cancellation can occur; they are not a waiver and are not removed merely because this rerun happened to complete every job.

## Greedy emission / prior-gate continuity

The unchanged dev11 rendering path remained fully valid:

- repeat-aware greedy installed records: `248`
- draw submissions: `43,083`
- actual/expected indirect calls: `172,332 / 172,332 = 43,083 * 4`
- resource epoch checks: `43,083`
- install validation: PASS
- fixed four-class indirect contract: PASS
- transport multi-face / representable / four-vertex-safe: `4,623 / 4,623 / 4,623`
- transport records: `4,623`
- transport covered faces: `10,184`
- faces saved: `5,561`

The user reported the run looked visually fine. This is retained as supporting observation only; no new P3.5 visual requirement is invented because geometry did not change.

## Lifetime / boundedness closure

- workers clean: true
- staging clean: true
- arena clean: true
- resources clean: true
- staging submitted/reclaimed: `24,900,504 / 24,900,504`
- pending upload batches: `0`
- arena allocations/retired/reclaimed: `744 / 744 / 744`
- arena used bytes: `0`
- arena allocation failures: `0`
- pending arena retirement batches: `0`
- retired/released resources: `248 / 248`
- pending retirements: `0`
- retirement backpressure events: `0`
- process exit code: `0`

## Promotion conclusion

P3.5's frozen A-0142 contract is satisfied:

- immutable one-block halo truth is validated against both optimized visibility and the permanent independent reference oracle;
- exact supported border light/color payload is frozen and accounted;
- independently captured shared borders agree exactly;
- halo-only and vertical dirty dependencies advance scene validity;
- generation/stale-install safety remains intact;
- workers perform zero live-world reads after capture;
- dev11 rendering semantics are unchanged and remain green;
- bounded worker/staging/arena/resource lifetime closes cleanly;
- normal runtime shutdown succeeds.

Therefore **P3.5 — Border/halo correctness is promotion-ready**. PR #45 may be synchronized, marked ready, and merged under the standing Phase 3 authorization after the exact evidence head passes hosted CI.

## Next boundary

After P3.5 is merged, activate **P3.6 — T-junction policy**. Do not silently treat the dev11/P3.5 visual observations as a complete broad T-junction proof. P3.6 must first freeze its own evidence-driven contract against the actual greedy geometry, raster precision, section/local coordinate behavior, camera motion, repeat-reset lines, and the current target hardware before deciding whether no mitigation, targeted mitigation, or selective splitting is required.
