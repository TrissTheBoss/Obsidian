# A-0144 - P3.5 dev12 runtime exposed shutdown cancellation accounting skew

**Date:** 2026-08-29  
**Objective:** Close the frozen P3.5 dev12 reference runtime gate and promote P3.5 before activating P3.6.  
**Action:** Ran `Obsidian-0.3.0-phase3-dev12.jar` on the reference Windows 11 / RX 6800 XT system, reached repeated 3x3 READY states, exercised ordinary block rebuilds and resource reload, exited normally, and inspected the complete shutdown evidence.  
**Result:** `PARTIAL` — the P3.5 scene-local correctness proof succeeded, but the final inherited P3.4 evidence chain closed false because one generation-invalidated worker was cancelled after publishing an upstream sidecar stage and before publishing downstream stages. P3.5 is therefore **not promoted** by this attempt.

## Runtime evidence that succeeded

- Vulkan attached on the RX 6800 XT; Java 25.0.1 / Minecraft 26.2 / Fabric Loader 0.19.3.
- Repeated async scene READY transitions and rebuilds completed.
- `AsyncMultiSectionSceneProbe` final P3.5 evidence reported `borderHaloCorrectnessEvidenceReady=true`.
- border proof records: `286`.
- outward checks / visibility matches / independent reference matches: `439,296 / 439,296 / 439,296`.
- frozen border baked quads / exact light-color samples: `54,106 / 216,424`.
- shared-border pair audits: `336`.
- shared-border comparisons / matches: `172,032 / 172,032`.
- rendered-core / halo-only / horizontal-halo / vertical-halo dirty events: `1,563 / 975 / 864 / 723`.
- `workerWorldReadsAfterCapture=0`.
- `synchronousSceneMeshBuilds=0`.
- `unsafeStaleSceneInstalls=0`.
- dropped lifecycle events `0`.
- workers/staging/arena/resources all clean at final shutdown.
- staging submitted/reclaimed `27,374,440 / 27,374,440`.
- arena used bytes `0`; allocations/retired/reclaimed `858/858/858`.
- resources retired/released `286/286`, pending `0`.
- Prism process exit code `0`.

## Final gate failure

`FrameCoordinator` correctly refused promotion because the final line reported:

- `ordinaryQuadEmissionSafetyEvidenceReady=false`;
- `repeatAwareUvEvidenceReady=false`;
- `repeatAwareTransportEvidenceReady=false`;
- `repeatAwareGreedyEmissionEvidenceReady=false`;
- consequently `borderHaloCorrectnessEvidenceReady=false` at the coordinator level.

The direct cause is a partial worker-pipeline telemetry publication caused by cancellation:

- worker submitted/started/completed/cancelled: `322 / 322 / 321 / 1`;
- merge-candidate builds: `322`;
- emission-safety builds: `321`;
- repeat-aware UV builds: `321`;
- repeat-aware transport builds: `321`;
- merge candidates: `77,516` (`72,442` singleton + `5,074` multi-face);
- emission-safety candidates: `77,349` (`72,286` singleton + `5,063` multi-face).

Source inspection confirms `SectionMeshWorkerPool.Worker.execute()` publishes global stage counters immediately after each stage, then checks cancellation. The cancelled job completed and published its merge-candidate stage, then observed cancellation before emission-safety. The cancelled result never installed and all lifetime/stale-install safety remained clean, but the existing final evidence gates assume exact global cross-stage aggregate equality. That assumption is incompatible with intentional stage-boundary cancellation.

## Root cause

Evidence telemetry is not cancellation-transactional. A cancelled worker may leave valid but partial earlier-stage aggregate counters even though its ticket never reaches `COMPLETED` and never installs. This is an accounting defect in promotion evidence, not evidence that the P3.5 border/halo implementation produced an incorrect installed scene.

## Non-negotiable response

Do **not** waive or reinterpret the frozen gate. Fix evidence accounting so a cancelled ticket cannot poison exact promotion identities, compile/package the correction, and rerun the reference runtime. P3.6 remains inactive until the corrected P3.5 final coordinator gate is true.

## Next action

Make worker evidence publication cancellation-safe while retaining exact cross-stage identities; keep PR #45 draft; produce a corrected dev12 runtime package; rerun the same P3.5 reference exercise; only then record promotion and activate P3.6.
