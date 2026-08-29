# A-0149 - P3.6 dev13 proven-junction runtime success and promotion evidence

**Date:** 2026-08-29  
**Objective:** Close the frozen A-0147 P3.6 T-junction policy contract on real Vulkan hardware before deciding whether any baseline greedy-mesh mitigation is justified.  
**Action:** Ran the canonical `Obsidian-0.3.0-phase3-dev13.jar` on the reference Windows 11 / RX 6800 XT Vulkan system. The runtime first proved that drawn LIVE sections contained strict coplanar same-facing merged/merged T-junctions, armed the targeted visual gate, then exercised normal movement/camera inspection, ordinary block rebuild activity, section recentering, resource reload, repeated READY transitions and normal shutdown. The user explicitly reported that visually everything looked fine.  
**Result:** `SUCCESS` — every frozen automated P3.6 gate is green and the required visual verdict is PASS. On the proven reference path, **no baseline T-junction mitigation is required**. Retain the D-0024 revisit hook for cross-vendor/larger-scale evidence; do not add global splitting without new evidence.

## Exact tested package

- version: `0.3.0-phase3-dev13`
- implementation head: `1504c87c3ed42dc4b4c49a1cdbdb61c4b5d8c6fc`
- synchronized package head: `505a84b76854cd4e2d3e629be204876da3ef3ff1`
- direct JAR: `Obsidian-0.3.0-phase3-dev13.jar`
- size: `419,659` bytes
- SHA-256: `44f7d9bec8979ddad8eb741b7024ed7ff1cb921d70cb6baff98e2a147956adc7`
- authoritative synchronized workflow: `33262729983` — Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED
- later continuity-only head `909fc8741c79b39e0f7695b8e3fadefbf0f876e2` also passed workflow `33262810375`; it did not alter packaged source bytes

Runtime environment:

- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Windows 11
- AMD Radeon RX 6800 XT
- Vulkan backend / AMD proprietary driver 26.8.1

## Automated P3.6 gate

The final coordinator reported:

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
- `tJunctionPolicyEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `workerWorldReadsAfterCapture=0`
- `synchronousSceneMeshBuilds=0`

The historical Phase 2 fixed-anchor flags remained false, as expected and irrelevant to the frozen P3.6 contract.

## Proven strict T-junction population

Final installed topology evidence:

- proof records: `329`
- determinism audits/matches: `329 / 329`
- emitted merged candidates: `7,391`
- emitted candidate edges: `29,564`
- strict interior lattice incidences: `18,260`
- **strict T-junction points: `3,231`**
- bounds checks/matches: `29,564 / 29,564`
- plane checks/matches: `7,391 / 7,391`
- integer-lattice checks/matches: `29,564 / 29,564`
- camera-relative transform proof records: `329`
- junction-bearing transform proof records: `283`
- camera-relative transform failures: `0`
- `geometryChanged=false`
- `shaderChanged=false`
- `pipelineChanged=false`

This satisfies the central A-0147 condition that the visual verdict must be attached to a runtime known to contain real strict junctions in the actual dev11-emitted, dev10-safe merged path rather than to an unproven scene.

## Visual verdict

The targeted visual gate armed only after the LIVE scene contained proven strict T-junctions and a junction-bearing record had executed the real camera-relative draw transform.

**Explicit human verdict: PASS — “visually everything looked fine.”**

No cracks, pinholes, flickering seams, z-fighting/double edges or camera-motion-dependent gaps were reported during the requested targeted inspection.

Therefore the P3.6 decision rule resolves to:

> **No baseline T-junction mitigation required on the proven reference Vulkan path.**

This does not claim a universal mathematical no-crack guarantee across every vendor, driver, extreme coordinate scale or future renderer change. D-0024 remains authoritative: keep greedy meshing as default, retain a cross-vendor/scale revisit hook, and use targeted mitigation/selective splitting only if future evidence demonstrates a real artifact.

## Rebuild / reload / recenter coverage

The run materially exercised the surrounding runtime contract:

- READY transitions: `38`
- scene rebuilds: `37`
- installed records: `329`
- camera recenter events: `5`
- resource reload events: `2`
- world-change events: `3`
- rendered-core dirty events: `1,371`
- halo-only dirty events: `576`
- horizontal-halo dirty events: `468`
- vertical-halo dirty events: `360`
- observed reasons: `section-dirty|world-change|resource-reload|scene-recenter`
- dropped lifecycle events: `0`
- unsafe stale scene installs: `0`

P3.5 remained green throughout:

- border proof records: `329`
- outward / optimized visibility / independent-reference matches: `505,344 / 505,344 / 505,344`
- shared-border comparisons/matches: `221,184 / 221,184`
- border baked quads: `56,249`
- frozen light/color samples: `224,996`

## Worker and generation safety

- scene worker submitted/completed: `333 / 333`
- worker cancelled: `0`
- cancellation requests: `0`
- queue-full rejections: `0`
- worker failures: `0`
- shutdown join failures: `0`
- stale worker discards: `4`
- preinstall invalidations: `4`
- installed results: `329`
- unsafe stale installs: `0`

The four stale discards are expected generation-safe behavior: invalidated outputs were rejected before LIVE installation. They do not weaken the evidence gate.

## Greedy path continuity

The unchanged dev11 renderer remained exact:

- repeat-aware greedy installed records: `329`
- scene worker completed: `333`
- draw submissions: `56,068`
- actual/expected indirect calls: `224,272 / 224,272 = 56,068 * 4`
- resource epoch checks: `56,068`
- transport records: `7,461`
- transport covered faces: `17,209`
- transport faces saved: `9,748`
- install validation: PASS
- fixed four-class indirect contract: PASS

P3.6 therefore did not discover evidence requiring a geometry-policy rollback or mitigation.

## Lifetime / boundedness closure

- workers clean: true
- staging clean: true
- arena clean: true
- resources clean: true
- staging submitted/reclaimed: `28,188,008 / 28,188,008`
- pending upload batches: `0`
- arena allocations/retired/reclaimed: `987 / 987 / 987`
- arena used bytes: `0`
- arena allocation failures: `0`
- pending arena retirement batches: `0`
- retired/released resources: `329 / 329`
- pending retirements: `0`
- retirement backpressure events: `0`
- process exit code: `0`

## Promotion conclusion

P3.6 is promotion-ready under the standing Phase 3 authorization:

- actual emitted strict T-junctions were proven, not assumed;
- all exact integer plane/lattice/bounds identities passed;
- junction-bearing sections traversed the real camera-relative transform with zero failures;
- all prior P3.5/dev11 gates remained green;
- no geometry/shader/pipeline behavior changed in dev13;
- targeted real-hardware visual inspection passed;
- generation safety, worker isolation and bounded lifetime remained clean;
- normal process shutdown succeeded.

After this evidence head passes hosted CI, PR #47 may be promoted and merged `[no-release]`. Then activate **P3.7 — Differential correctness framework**. P3.7 remains a distinct correctness milestone: compare permanent independent reference-oracle truth against optimized output on representative immutable snapshots and preserve failing fixtures; do not treat optimized output as its own oracle.
