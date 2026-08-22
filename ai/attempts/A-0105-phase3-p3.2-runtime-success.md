# A-0105 - Phase 3 P3.2 binary visibility runtime success

**Date:** 2026-08-22  
**Branch:** `phase3/bitmask-visibility-masks`  
**Canonical PR:** #36 against `main`  
**Version:** `0.3.0-phase3-dev4`  
**Result:** `SUCCESS` — P3.2 runtime closure evidence satisfied.

## Runtime package

Canonical direct runtime package from A-0104:

- `Obsidian-0.3.0-phase3-dev4.jar`;
- package code head `ab394076853d2647340c8eb4f2983ec842823938`;
- exact package CI `32583676238` passed Java 25 / Gradle 9.5.1 build and artifact upload; release skipped;
- package SHA-256 `93211c45bae44f927fc3946c30ec336d3ad41ea6a015992f395ab669b9a8d14e`.

The later evidence/documentation branch head `03ff120fe4996c5d3d1ac85d2d355180f0fa204b` also passed exact PR CI `32583773383`, with build/artifact success and release skipped.

## Reference runtime result

The user ran dev4 on the established reference Vulkan system and exited normally.

Final coordinator gate:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- render-thread capture/GPU ownership preserved;
- `workerWorldReadsAfterCapture=0`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleEmission=false`;
- `synchronousSceneMeshBuilds=0`.

The historical `phase2ChunkLifecycleEvidenceReady=false` / `fixedAnchorReturnSceneReady=false` values are expected in this run because the already-closed long-distance fixed-anchor unload/return sequence was intentionally not repeated. A-0101 remains the canonical proof for that Phase 2 dependency; it is not a P3.2 gate.

## Worker and scheduler evidence

- worker submitted/started/completed `288/288/288`;
- cancelled jobs / cancellation requests `0/0`;
- stolen jobs `219`;
- queue-full rejections `0`;
- failed jobs `0`;
- shutdown join failures `0`;
- max queue depth `1`;
- HIGH/NORMAL/LOW submitted and completed `32/128/128`;
- worker output quads `216,654`;
- worker output vertex/index bytes `24,265,248 / 5,199,696`;
- worker scratch uses `295`, high-water `1,464` quads;
- legacy worker determinism audits/matches `7/7`;
- max simultaneous scene jobs `2`;
- max admission burst `2`;
- scheduler admission deferrals `0`.

## Binary visibility evidence

Production mask construction was exercised on every completed worker job:

- `visibilityBuilds=288`;
- `visibilityTotalFaces=102367`;
- direction totals:
  - WEST `7159`;
  - EAST `11145`;
  - DOWN `4424`;
  - UP `56663`;
  - NORTH `15272`;
  - SOUTH `7704`;
- exact direction sum: `102367`, matching `visibilityTotalFaces`;
- `visibilityRetainedBytes=884736`;
- `visibilityRetainedBytesPerBuild=3072`;
- exact retained-byte identity: `288 * 3072 = 884736`;
- total visibility build time `32,295,500 ns`;
- max visibility build time `676,500 ns`;
- max visible faces in one mask `910`;
- visibility scratch uses `295`, high-water supported rows `298`;
- visibility determinism audits/matches `7/7`;
- independent `ReferenceFaceMesh` audits/matches `7/7`.

This satisfies the P3.2 contract for deterministic compact machine-word face visibility with exact independent reference equivalence while retaining immutable worker inputs and zero live-world reads after capture.

## Scene/lifecycle evidence

- scene workers submitted/completed `288/288`;
- scene worker cancels / cancellation requests `0/0`;
- safe stale completed result discards `0`;
- scene worker installs / record installs `288/288`;
- scene worker queue rejections `0`;
- preinstall invalidations `0`;
- local scene ready `true`;
- scene READY transitions / rebuilds `32/31`;
- max live records / adjacent pairs `9/12`;
- camera recenter events `2`;
- invalidation batches `58`;
- dirty events `1809`;
- resource reload events `1`;
- dropped lifecycle events `0`;
- unsafe stale scene installs `0`;
- observed reasons include `section-dirty|world-change|resource-reload|scene-recenter`.

The runtime therefore exercised ordinary dirty rebuilds, resource reload rebuild, and normal recenter scheduling while preserving async scene correctness.

## Cleanup / exit

- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- staging submitted/reclaimed bytes `29,476,464 / 29,476,464`;
- pending upload batches `0`;
- arena used bytes `0`;
- arena allocations / retired / reclaimed `576/576/576`;
- arena allocation failures `0`;
- arena stale-handle rejections `0`;
- arena fragmentation `0`;
- pending arena retirement batches `0`;
- retired/released resources `288/288`;
- pending retirements `0`;
- process exit code `0`.

## Closure judgment

P3.2 binary/bitmask visibility masks is technically complete:

1. the permanent simple reference oracle remains independent;
2. all production workers derive masks only from immutable renderer-owned snapshots;
3. compact six-direction machine-word masks are bounded at exactly 3,072 retained bytes per section;
4. deterministic construction is proven by `7/7` audits;
5. exact conservative directional coverage is proven by independent reference audits `7/7` plus exact face/count accounting;
6. worker scratch/output/queue behavior remains bounded and observable;
7. greedy rectangle emission remains explicitly disabled, so P3.3 is not being claimed by this result.

With standing user merge authorization for the validated Phase 3 chain, PR #36 is eligible for promotion after its final evidence-only head passes exact PR CI. Promotion must use `[no-release]`.

## Next milestone

After P3.2 promotion and Class-A synchronization, activate **P3.3 — greedy rectangle extraction**. P3.3 must consume the proven binary visibility foundation while preserving exact visual merge-key truth and the independent reference oracle; it must not retroactively weaken P3.2 semantics.