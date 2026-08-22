# A-0115 - Phase 3 P3.4 dev6 runtime success

**Date:** 2026-08-23  
**Branch:** `phase3/render-correct-merge-key`  
**Canonical PR:** #38 against `main`  
**Version:** `0.3.0-phase3-dev6`  
**Result:** `SUCCESS` — the complete reference runtime shutdown tail supersedes A-0114's evidence gap and satisfies the frozen A-0112 runtime gate.

## Objective

Close the P3.4 dev6 correctness-first render-key sidecar only after the complete `FrameCoordinator.close()` metrics and launcher process exit are available.

## Runtime package

Canonical CI-built package:

- `Obsidian-0.3.0-phase3-dev6.jar`;
- size `308,439` bytes;
- SHA-256 `2d2664d1eb6fc844cf70cefabb11400752da20866f4e1f1a79ca3873ea55019a`.

The runtime used the reference Windows 11 / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 / Vulkan / Radeon RX 6800 XT environment. The user reported that visuals looked fine.

## Final shutdown proof

The previously missing tail is now supplied verbatim in the conversation and records:

- `[Render thread/INFO]: Stopping!`;
- `Phase 3 dev6 P3.4 frame coordinator closed after 7159 frame(s)`;
- `Process exited with code 0`.

All required gates are true:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- `renderThreadCaptureOwnership=true`;
- `renderThreadGpuOwnership=true`;
- `workerWorldReadsAfterCapture=0`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleSidecarIntegrated=true`;
- `renderMergeKeySidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `synchronousSceneMeshBuilds=0`.

The historical Phase 2 fixed-anchor flags remain false by design (`phase2ChunkLifecycleEvidenceReady=false`, `fixedAnchorReturnSceneReady=false`); A-0101 remains the already-closed lifecycle proof and these are not P3.4 blockers.

## Worker / scheduler closure

- worker count `4`, queue capacity `64`;
- submitted / started / completed `229 / 229 / 229`;
- cancelled `0`, cancellation requests `0`;
- stolen jobs `172`;
- queue-full rejections `0`;
- failed jobs `0`;
- shutdown join failures `0`;
- HIGH/NORMAL/LOW submitted `27 / 102 / 100`;
- HIGH/NORMAL/LOW completed `27 / 102 / 100`;
- worker determinism audits/matches `4 / 4`.

## P3.2 visibility closure remains exact

- visibility builds `229`;
- total faces `96,038`;
- WEST/EAST/DOWN/UP/NORTH/SOUTH `7,464 / 11,417 / 6,221 / 45,165 / 13,410 / 12,361`;
- retained bytes `703,488 = 229 * 3,072`;
- determinism audits/matches `4 / 4`;
- independent reference audits/matches `4 / 4`.

## P3.3 rectangle closure remains exact

- rectangle builds `229`;
- rectangles `38,917`;
- covered faces `96,038`;
- faces saved `57,121`;
- reduction `594` permille = `59.4%`;
- directional covered faces exactly equal visibility directional faces;
- retained bytes `155,668 = 38,917 * 4`;
- primary mask coverage audits/matches `229 / 229`;
- rectangle determinism `4 / 4`;
- independent rectangle/reference audits `4 / 4`.

## P3.4 dev6 render-key closure

- render-key builds `229`, covering all `229` completed production jobs;
- visible canonical faces `96,038`, exactly equal P3.2 visibility faces;
- eligible faces `77,157`;
- unmapped faces `0`;
- ambiguous faces `18,881`;
- exact accounting: `77,157 + 0 + 18,881 = 96,038`;
- eligible rate `803` permille = `80.3%`;
- recognized canonical baked quads `114,919`;
- ignored noncanonical baked quads `73,977`;
- same-key adjacencies `10,328`;
- different-key adjacencies `70,564`;
- ineligible adjacencies `12,389`;
- retained bytes `11,255,808 = 229 * 49,152`;
- retained bytes/build `49,152`;
- scratch uses `233 >= 229` builds;
- max eligible faces `755`;
- determinism audits/matches `4 / 4`.

The same-key and different-key counts prove the comparator exercised both acceptance and rejection on real production terrain rather than trivially classifying everything one way.

## Scene/lifetime closure

- scene worker submitted/completed `229 / 229`;
- scene worker installs `225`;
- stale discards `4`, paired with preinstall invalidations `4` and no unsafe stale install;
- scene READY transitions `25`, rebuilds `24`;
- camera recenter events `2`;
- resource reload events `2`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed `25,244,616 / 25,244,616` bytes;
- arena used bytes `0` at shutdown;
- arena allocations/retired/reclaimed `450 / 450 / 450`;
- arena allocation failures `0`;
- arena stale-handle rejections `0`;
- retired/released resources `225 / 225`;
- pending upload batches, arena retirements and resource retirements all `0`.

## Human visual regression guard

The user explicitly reported the dev6 rendering looked fine. Because dev6 does not change GPU-emitted geometry, this is the intended regression guard rather than evidence that greedy rectangles are already rendered.

## Conclusion

A-0114 is superseded only with respect to the missing shutdown evidence; its historical partial observation remains immutable. A-0115 closes dev6 successfully. PR #38 is merge-eligible after the exact evidence-head CI is green. Promotion uses `[no-release]` under standing user authorization.

P3.4 remains ACTIVE after dev6. The next slice should build a render-key-aware merge-candidate sidecar before any greedy GPU emission, with explicit rectangle-level interpolation/UV safety still unresolved and therefore no claim of `renderCorrectMergeKeyComplete=true`.