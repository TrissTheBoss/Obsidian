# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Phase 1: COMPLETE — closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1: COMPLETE — `a714e19ce871bf73136d52f85a1780109aa851dd`.
- P2.2: COMPLETE — `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.
- P2.3: COMPLETE — `667230f51222746083efe89c72265d80ac9d3929`.
- P2.4: COMPLETE — `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`.
- P2.5: COMPLETE — `c17f7c6146678e18cacabc44d85c67413a040f73`.
- P2.5 Class-A sync: `306d74fdf2428af93feac2ce5e49296d508d9d2d`.
- `main` remains at the P2.5-complete state while P2.6's final fixed-target runtime coverage is open.

## Current Phase 2 integration branch

- Branch: `phase2/section-lifecycle-rebuild`.
- PR #25: open/draft against `main`.
- Current branch head contains corrected P2.6 plus validated P2.7.
- P2.7 PR #27 was explicitly merge-authorized and merged into this branch as merge commit `91eb704e769fff5d872c628a710cd8128a3314ee`.
- This stacked merge did **not** merge anything to `main` and did **not** waive P2.6's remaining fixed-target chunk-lifecycle gate.
- User has standing merge authorization for both dev6 and dev7 once the remaining P2.6 gate passes and final exact-head CI is green.

## P2.6 / dev6 — corrected behavior proven, final fixed-target chunk lifecycle coverage pending

Evidence:

- A-0080: section lifecycle/rebuild plan;
- A-0081: exact Minecraft 26.2 lifecycle/API bytecode inspection;
- A-0082: implementation/package;
- A-0083: initial event-ring runtime defect + target-scoped sticky/coalesced bridge correction;
- A-0084: corrected reference retest partial.

### Exact lifecycle seams

Observation-only hooks preserve vanilla ownership:

- private `LevelExtractor.setSectionDirty(IIIZ)` central dirty sink;
- `LevelExtractor.setLevel(ClientLevel)` world replacement/teardown;
- `ClientLevel.onChunkLoaded(ChunkPos)`;
- `ClientLevel.unload(LevelChunk)`;
- successful `ModelManager.reload(...)` completion.

Vanilla remains responsible for block/light/neighbor propagation. Obsidian consumes the resulting exact dirty-section coordinates.

The corrected `SectionLifecycleEvents` bridge has no global event ring. It uses target-scoped sticky/coalesced counters and advances renderer validity only for the tracked section, its relevant chunk neighborhood, world replacement or resource reload.

### Corrected standalone dev6 package

- Version: `0.2.0-phase2-dev6`.
- JAR SHA-256 `486db6d80490170961d5a98debcb691e440e775e433283ab50b30c894821feb8`.
- Sources SHA-256 `786d3c2f54bdc5f1e29b6ff10f5d4009eba7398dc215606fec3bbd4e86354f0e`.
- Exact final standalone run `32499796317`: Java 25 / Gradle 9.5.1 SUCCESS, Build SUCCESS, artifact upload SUCCESS, release SKIPPED.

### Corrected retest result

The user reported the corrected package looked good. Runtime proved:

- `hardFailure=false`;
- `installCount=18`, `rebuildInstalls=17`;
- `dirtyEvents=878`, `playerDirtyEvents=432`;
- `resourceReloadEvents=2`;
- `droppedLifecycleEvents=0`;
- no overflow/safety-invalidate reason;
- `staleInstallRejections=0`;
- deterministic Phase 2 capture/build invariants;
- full staging reclamation `1,588,928 / 1,588,928` bytes;
- arena retired/reclaimed `36 / 36`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `18 / 18`, pending `0`;
- process exit code `0`.

The run did **not** exercise the fixed-target unload/return class:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

This is a coverage gap, not evidence of another event-bridge defect.

### Remaining P2.6 closure

Run the corrected standalone dev6 artifact and require:

1. initial tracked section install;
2. multiple edits/rebuilds;
3. successful F3+T rebuild;
4. travel far enough for the tracked section's 3x3 halo chunk neighborhood to unload;
5. return so that neighborhood loads and the target rebuilds;
6. nonzero `chunkUnloadEvents` and `chunkLoadEvents`;
7. `droppedLifecycleEvents=0`, no overflow reason and zero stale install;
8. full completion-gated staging/arena/resource cleanup;
9. process exit 0 and shutdown `lifecycleGateReady=true`.

Until that result exists, PR #25 stays draft and must not merge to `main`.

## P2.7 / dev7 — runtime + human visual validated, authorized and integrated into the P2.6 branch

Evidence:

- A-0085: multi-section architecture;
- A-0086: implementation/package;
- A-0087: runtime-gate refinement;
- A-0088: refined package;
- A-0089: reference runtime success.

### Validated architecture

`RealMultiSectionSceneProbe` owns a fixed renderer-side 3x3 horizontal table:

- maximum 9 section records at one section Y;
- one shared scene generation/validity domain;
- exact dirty events from any rendered scene section are relevant;
- union halo chunk filtering radius 2, spanning a 5x5 chunk footprint;
- no event ring;
- correctness-first whole-window invalidation/rebuild;
- one-record-at-a-time bounded upload admission;
- fixed 4 MiB staging and 32 MiB geometry arena;
- camera-driven scene recenter;
- READY requires at least 3 simultaneous LIVE records and 2 adjacent pairs.

### Package/CI

Validated package:

- `Obsidian-0.2.0-phase2-dev7.jar` SHA-256 `59dde49b210b802fdd88e1bbc2da7a9eae9b7be045b9c760ae1adc3827599725`;
- sources SHA-256 `c1ebbe080fa5262cb77d4a4ff48c805712880100126f05611ed99fa163d76c57`.

Fresh evidence-synchronized dev7 head `143ab76df2b465274ee3075d45d34ddb9dd24a29` passed exact run `32512002699`: Java 25 / Gradle 9.5.1 SUCCESS, Build SUCCESS, artifact upload SUCCESS, release SKIPPED.

### Runtime + human validation

Reference Vulkan/RX 6800 XT runtime proved:

- `sceneGateReady=true`;
- `sceneReadyTransitions=16`;
- `sceneRebuilds=15`;
- `recordInstalls=144`;
- `maxLiveRecords=9`;
- `maxAdjacentPairs=12`;
- `cameraRecenterEvents=2`;
- `dirtyEvents=709`, `playerDirtyEvents=342`;
- `resourceReloadEvents=1`;
- `droppedLifecycleEvents=0`;
- `staleSceneRejections=0`;
- `probeStaleInstallRejections=0`;
- `uploadAdmissionDeferrals=0`;
- zero retirement backpressure/registration failures;
- staging submitted/reclaimed `11,087,992 / 11,087,992` bytes;
- arena allocations `288`, failures `0`, retired/reclaimed `288 / 288`, used bytes `0`, fragmentation `0`;
- deferred resources retired/released `144 / 144`, pending `0`;
- process exit code `0`.

Human oracle: the user reported the multi-section result looked completely fine; no persistent duplicate/missing shared borders or stale old-window geometry were reported.

### Stacked merge

The user explicitly authorized dev7 merge. After a fresh exact-head CI pass, PR #27 was merged into `phase2/section-lifecycle-rebuild` using merge commit `91eb704e769fff5d872c628a710cd8128a3314ee` so the validated dev7 head remains ancestry-visible.

PR #25 is now the sole Phase 2 integration PR to `main`. Once the remaining P2.6 fixed-target gate passes, the combined corrected P2.6 + validated P2.7 stack may be merged under the user's standing authorizations after final exact-head CI.

## Forward Phase 3 work

Phase 3 P3.1 worker/job architecture development has started on separate stacked branch `phase3/worker-job-architecture`, PR #29. It remains non-mergeable until this Phase 2 integration branch reaches `main`. Phase 3 does not change the outstanding P2.6 gate.

## Continuity model

Read in order before architecture/status changes:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts are immutable.

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

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + later worker-local binary/bitmask greedy meshing, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
