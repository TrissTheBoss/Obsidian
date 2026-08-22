# A-0110 - Phase 3 P3.3 greedy rectangle runtime success

**Date:** 2026-08-22  
**Branch:** `phase3/greedy-rectangle-sidecar`  
**Canonical PR:** #37 against `main`  
**Version:** `0.3.0-phase3-dev5`  
**Result:** `SUCCESS` — P3.3 dev5 runtime closure evidence satisfied.

## Human visual verdict

The user reported: **"evreything looks right."**

This is the required human visual regression verdict for the dev5 reference run. Dev5 still keeps the generalized `BakedSectionMesh` as the production drawable and does not yet emit greedy rectangles to the GPU.

## Runtime package

Canonical runtime package from A-0109:

- `Obsidian-0.3.0-phase3-dev5.jar`;
- canonical runtime source/package head `75a35de6b073ca0d9bce013c43f2043d37f9b79a`;
- exact package CI `32599625494` passed Java 25 / Gradle 9.5.1 build and artifact upload; release skipped;
- JAR size `295,404` bytes;
- SHA-256 `ae87c3b2b1dc0c01c04a61d1282452d653975626f58aeae02ca64fe5cd8b620d`.

The later evidence-record branch head `b1e768c34f940ab3ec2b97da4d9e2f21d9b45e65` passed exact PR CI `32599693103`, including build and artifact upload; release skipped.

## Final runtime gates

The user ran dev5 on the established Vulkan reference system and exited normally. Final coordinator state:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- render-thread capture/GPU ownership preserved;
- `workerWorldReadsAfterCapture=0`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleSidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `synchronousSceneMeshBuilds=0`.

The historical `phase2ChunkLifecycleEvidenceReady=false` / `fixedAnchorReturnSceneReady=false` values are expected because the already-closed fixed-anchor unload/return sequence was not repeated. A-0101 remains the canonical proof for that dependency.

## Worker and scheduler evidence

- submitted/started/completed `162/162/162`;
- cancelled jobs / cancellation requests `0/0`;
- stolen jobs `115`;
- queue-full rejections `0`;
- failed jobs `0`;
- shutdown join failures `0`;
- max queue depth `1`;
- HIGH/NORMAL/LOW submitted and completed `18/72/72`;
- worker output quads `113,116`;
- worker output vertex/index bytes `12,668,992 / 2,714,784`;
- worker scratch uses `166`, high-water `877` quads;
- baked-mesh determinism audits/matches `4/4`;
- max simultaneous scene jobs `2`;
- max admission burst `2`;
- scheduler admission deferrals `0`.

## P3.2 binary visibility remains valid

- visibility builds `162`;
- total visible faces `48,261`;
- WEST/EAST/DOWN/UP/NORTH/SOUTH face totals `3,446 / 3,802 / 486 / 31,722 / 7,619 / 1,186`;
- directional sum exactly `48,261`;
- retained visibility bytes `497,664 = 162 * 3,072`;
- visibility determinism audits/matches `4/4`;
- independent visibility reference audits/matches `4/4`.

## P3.3 greedy rectangle evidence

Every completed production worker job built the topology rectangle sidecar:

- `rectangleBuilds=162`;
- total rectangle records `21,286`;
- covered source faces `48,261`, exactly equal to P3.2 visibility faces;
- faces saved by topology merging `26,975`;
- reduction `558` permille = **55.8%**;
- rectangle records by direction WEST/EAST/DOWN/UP/NORTH/SOUTH `2,166 / 2,682 / 228 / 11,099 / 4,409 / 702`;
- covered faces by direction exactly `3,446 / 3,802 / 486 / 31,722 / 7,619 / 1,186`, matching the P3.2 directional visibility totals;
- retained rectangle bytes `85,144`;
- bytes per rectangle record `4`;
- exact retained-byte identity `21,286 * 4 = 85,144`;
- total rectangle build time `16,070,700 ns`;
- max rectangle build time `8,446,100 ns`;
- max rectangle records in one section `222`;
- rectangle scratch uses `166`, high-water `222` records;
- exact primary mask coverage audits/matches `162/162`;
- rectangle determinism audits/matches `4/4`;
- independent `ReferenceFaceMesh` rectangle audits/matches `4/4`.

This proves that the deterministic rectangle sidecar partitions the P3.2 conservative canonical face set with no overlaps, no missing faces and no extra faces, while achieving substantial real-world topology reduction.

## Scene/lifecycle evidence

- scene workers submitted/completed/installed `162/162/162`;
- scene worker cancels / cancellation requests `0/0`;
- safe stale completed-result discards `0`;
- scene worker queue rejections `0`;
- preinstall invalidations `0`;
- local scene ready `true`;
- scene READY transitions / rebuilds `18/17`;
- record installs `162`;
- max live records / adjacent pairs `9/12`;
- camera recenter events `1`;
- invalidation batches `26`;
- dirty events `858`;
- resource reload events `2`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- observed reasons include `section-dirty|world-change|resource-reload|scene-recenter`.

## Cleanup / exit

- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed bytes `15,390,256 / 15,390,256`;
- pending upload batches `0`;
- arena used bytes `0`;
- arena allocations/retired/reclaimed `324/324/324`;
- arena allocation failures `0`;
- arena stale-handle rejections `0`;
- arena fragmentation `0`;
- pending arena retirement batches `0`;
- retired/released resources `162/162`;
- pending retirements `0`;
- process exit code `0`.

## Closure judgment

P3.3 dev5 is technically complete and eligible for promotion because it proves:

1. deterministic worker-local greedy rectangle extraction from the proven P3.2 masks;
2. exact face-set preservation globally and per direction;
3. exact non-overlapping mask expansion on every primary production build;
4. independent `ReferenceFaceMesh` equivalence on audit cadence;
5. substantial real topology reduction (`55.8%` on this run);
6. bounded primitive scratch and compact 4-byte retained rectangle records;
7. scheduler/generation/event/resource/completion-gated lifetime behavior remains clean;
8. user visual regression verdict is positive;
9. greedy rectangles are still not GPU-emitted and the full render-correct merge key is explicitly not yet complete.

With standing user merge authorization, PR #37 may be promoted after this final evidence-only head passes exact PR CI. Promotion must use `[no-release]`.

## Next milestone

After P3.3 promotion, activate **P3.4 — render-correct merge key**. P3.4 must bind greedy eligibility to exact visual equivalence across the renderer-owned baked data needed for safe merging (including layer/material/sprite/UV/tint/light/AO/model-derived distinctions as applicable) while keeping arbitrary unproven generalized baked quads on an exact passthrough path.