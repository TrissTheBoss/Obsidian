# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Phase 1: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Phase 2 P2.1: **COMPLETE / runtime validated and merged** — closing merge `a714e19ce871bf73136d52f85a1780109aa851dd`
- Phase 2 P2.2: **COMPLETE / runtime + human visual validated and merged** — closing merge `f9c64267c5becb3bd80897efdb09ed65a6ce8697`
- Phase 2 P2.3: **COMPLETE / runtime + human visual validated and merged** — closing merge `667230f51222746083efe89c72265d80ac9d3929`
- Phase 2 P2.4: **COMPLETE / runtime + human visual validated and merged** — closing merge `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`
- P2.4 Class-A sync merge: `a6d7d2ff96948910e22b2ab4e3e5212408ef97c2`
- Phase 2 P2.5: **COMPLETE / runtime + human visual validated and merged** — closing merge `c17f7c6146678e18cacabc44d85c67413a040f73`
- P2.5 Class-A sync merge: `306d74fdf2428af93feac2ce5e49296d508d9d2d`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Formal active milestone: **P2.6 - section lifecycle and rebuild correctness**
- P2.6 branch: `phase2/section-lifecycle-rebuild`
- P2.6 draft PR: #25
- P2.6 version: `0.2.0-phase2-dev6`
- P2.6 status: **corrected event bridge runtime-proven for edits/resource reload with zero drops; required tracked-neighborhood chunk unload/load coverage still not exercised; unmerged**
- Forward stacked work: **P2.7 - multi-section integration implementation started, not merge-eligible before P2.6 closure**
- P2.7 stacked branch: `phase2/multi-section-scene`
- P2.7 stacked draft PR: #27 (base `phase2/section-lifecycle-rebuild`)
- P2.7 temporary exact-CI PR: #28 (base `main`, never merge)
- Current development version on stacked branch: `0.2.0-phase2-dev7`
- Last fully runtime-validated and merged development version: `0.2.0-phase2-dev5`

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts remain immutable.

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan backend

## Proven Phase 2 foundation

P2.1 established immutable 16^3 section + one-block-halo capture and the permanent deterministic cube-face `ReferenceFaceMesh` oracle.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect drawing.

P2.3 established exact baked sprite/UV/material/tint identity for the conservative SOLID full-cube subset.

P2.4 established exact Minecraft 26.2 block/sky light, directional shade and ambient-occlusion semantics through immutable capture + exact BLOCK/lightmap drawing.

P2.5 established exact generalized vanilla MODEL semantics for the accepted SOLID/CUTOUT domain through `ModelBlockRenderer.tesselateBlock(...) -> BlockQuadOutput`, immutable arbitrary quad capture and deterministic layered BLOCK-format public indexed-indirect drawing. Runtime evidence: `ai/attempts/A-0079-phase2-dev5-runtime-success.md`.

## P2.6 / dev6 - corrected behavior proven, final coverage pending

Evidence:

- plan: `ai/attempts/A-0080-phase2-dev6-section-lifecycle-plan.md`;
- exact Minecraft 26.2 lifecycle inspection: `ai/attempts/A-0081-phase2-dev6-lifecycle-api-inspection.md`;
- initial implementation/package: `ai/attempts/A-0082-phase2-dev6-implementation-and-package.md`;
- first runtime machine-gate defect + event-bridge correction: `ai/attempts/A-0083-phase2-dev6-runtime-event-bridge-correction.md`;
- corrected reference retest: `ai/attempts/A-0084-phase2-dev6-corrected-retest-partial.md`.

Exact lifecycle observation seams remain:

- private `LevelExtractor.setSectionDirty(IIIZ)` central dirty sink;
- `LevelExtractor.setLevel(ClientLevel)` world replacement/teardown;
- `ClientLevel.onChunkLoaded(ChunkPos)` and `ClientLevel.unload(LevelChunk)`;
- successful `ModelManager.reload(...)` completion.

The A-0083 correction removed the lossy 256-entry global event ring. Dev6 uses a target-scoped sticky/coalesced bridge whose validity sequence advances only for the tracked section, its halo-neighborhood chunk lifecycle, world replacement or resource reload.

### Corrected retest result

The user reported the corrected package looked good. Shutdown/runtime evidence proved:

- `hardFailure=false`;
- `installCount=18`, `rebuildInstalls=17`;
- `dirtyEvents=878`, `playerDirtyEvents=432`;
- `resourceReloadEvents=2`;
- `droppedLifecycleEvents=0`;
- no overflow/safety-invalidate reason;
- `staleInstallRejections=0`;
- deterministic P2.5 capture/build semantics retained;
- staging fully reclaimed `1,588,928 / 1,588,928` bytes;
- arena retirement/reclamation `36 / 36`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `18 / 18`, pending `0`;
- process exit `0`.

The run did **not** exercise the required tracked-neighborhood unload/return path: `chunkUnloadEvents=0`, `chunkLoadEvents=0`. Therefore shutdown correctly remained `lifecycleGateReady=false`. This is currently a coverage gap, not evidence of a new event-bridge defect.

P2.6 remains unmerged. The user's standing dev6 merge authorization remains conditional on a corrected-package run that actually produces nonzero tracked-neighborhood chunk unload/load counters and `lifecycleGateReady=true`.

## P2.7 / dev7 - stacked multi-section implementation candidate

Evidence/plan: `ai/attempts/A-0085-phase2-dev7-multi-section-scene-plan.md`.

P2.7 is intentionally developed on top of the corrected dev6 head while P2.6 awaits its final runtime coverage. It must not merge ahead of P2.6.

### Candidate scene architecture

`RealMultiSectionSceneProbe` introduces a fixed renderer-owned 3x3 horizontal scene table:

- maximum 9 section records at one selected section Y;
- center selected from the existing useful near-player snapshot path;
- neighboring records use X/Z offsets `-1..+1`;
- each eligible record reuses the proven P2.6 `RealSectionLifecycleProbe` for exact immutable capture, generalized SOLID/CUTOUT mesh semantics, bounded upload, public indexed-indirect draw and completion-gated retirement;
- at least 3 simultaneous LIVE records and 2 horizontally adjacent pairs are required for the validation scene to become READY;
- scene recenters when the player exits the current +/-1-section X/Z window.

### Multi-section validity domain

The lossless lifecycle bridge is widened without adding a queue:

- exact dirty events from any rendered section in the 3x3 window at the scene Y are relevant;
- chunk load/unload uses radius 2 around the scene center because the union of all one-block halos spans a 5x5 chunk footprint;
- world/resource changes remain globally relevant;
- renderer scene recenter is an explicit internal invalidation reason;
- unrelated world dirtiness remains irrelevant to the scene validity sequence.

All current records share one scene generation/event-sequence domain. P2.7 deliberately invalidates/rebuilds the whole validation window after a relevant change.

### Bounded ownership/admission

- staging remains fixed at 4 MiB;
- validation geometry arena is fixed at 32 MiB;
- no fallback growth allocation;
- at most one new record is admitted while the staging ring has no pending batch;
- normal completion polling remains nonblocking;
- old scene records completion-retire before replacement ownership becomes reusable.

Whole-window invalidation is a correctness-first P2.7 boundary, not the intended production scheduling policy. Phase 3 still owns per-section async scheduling, worker pools, cancellation/versioning, reusable worker-local scratch and binary/bitmask greedy meshing.

### Dev7 validation gate

After exact compile/package validation, the reference runtime must show:

- `sceneReadyTransitions >= 2`;
- `sceneRebuilds >= 1`;
- `maxLiveRecords >= 3`;
- `maxAdjacentPairs >= 2`;
- `cameraRecenterEvents >= 1`;
- nonzero dirty/resource/chunk-unload/chunk-load events;
- `droppedLifecycleEvents=0`;
- zero stale scene/probe installs;
- bounded staging/arena behavior and complete reclamation;
- normal exit code `0` and shutdown `sceneGateReady=true`.

Human oracle: simultaneous neighboring overlay sections must not show persistent duplicate/missing shared borders during camera motion; no stale old-window geometry may persist after edit/reload/recenter. A temporary whole-window blank interval while rebuilding is acceptable for this correctness milestone.

## Merge authorization

- Dev6: user has standing merge authorization only after its required runtime machine gate passes.
- Dev7: no merge authorization has yet been granted. Keep PR #27 draft even after compile/package success until its runtime/human gate and an explicit merge authorization are both present.

## Immediate next action

1. Run exact Java 25 / Gradle 9.5.1 CI on the current dev7 head through temporary main-targeting PR #28.
2. Fix any exact Minecraft 26.2 compile/API failures without changing the stated P2.7 boundary unless evidence requires it.
3. On successful behavior CI, inspect the packaged JAR bytecode/metadata and record immutable implementation/package evidence.
4. Close temporary PR #28 without merge.
5. Deliver a dev7 runtime-test JAR while keeping canonical PR #27 stacked/draft.
6. Independently, P2.6 still needs one corrected run that exercises far travel + tracked-neighborhood unload/return before PR #25 can merge under its standing authorization.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + later binary greedy meshing, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
