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
- Active milestone: **P2.6 - section lifecycle and rebuild correctness**
- Active branch: `phase2/section-lifecycle-rebuild`
- Active draft PR: #25, `Phase 2 dev6: section lifecycle and rebuild correctness`
- Current development version: `0.2.0-phase2-dev6`
- P2.6 status: **human visual behavior passed on first runtime, but machine gate exposed lossy event-bridge overflow; target-scoped correction implemented + exact CI/package/bytecode verified; corrected reference runtime retest pending**
- Last fully runtime-validated development version: `0.2.0-phase2-dev5`

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

## P2.6 / dev6 - CORRECTED IMPLEMENTATION / CI + PACKAGE VERIFIED / RETEST PENDING

Evidence:

- plan: `ai/attempts/A-0080-phase2-dev6-section-lifecycle-plan.md`;
- exact Minecraft 26.2 lifecycle inspection: `ai/attempts/A-0081-phase2-dev6-lifecycle-api-inspection.md`;
- initial implementation/package: `ai/attempts/A-0082-phase2-dev6-implementation-and-package.md`;
- first runtime machine-gate defect + correction: `ai/attempts/A-0083-phase2-dev6-runtime-event-bridge-correction.md`;
- temporary inspection PR #26 closed without merge.

### Exact lifecycle observation seams

Dev6 observes vanilla behavior without cancelling or replacing it:

- private `LevelExtractor.setSectionDirty(IIIZ)` central sink after vanilla has already calculated block/light/border propagation;
- `LevelExtractor.setLevel(ClientLevel)` for world replacement/teardown;
- `ClientLevel.onChunkLoaded(ChunkPos)` and `ClientLevel.unload(LevelChunk)` for client chunk lifecycle;
- successful `ModelManager.reload(...)` future completion for model/resource invalidation.

Vanilla's own dirty propagation already expands block changes by one block before section conversion and light updates with neighbor sections, so Obsidian consumes exact resulting section coordinates rather than maintaining a parallel heuristic.

### Generation-safe implementation

`SectionGenerationGate` owns monotonic renderer generation and permanently self-tests stale-token rejection. `SectionSnapshot.tryCaptureSection(...)` recaptures the exact tracked section plus halo without loading/generating chunks.

`RealSectionLifecycleProbe` owns one generation at a time, preserves P2.5 deterministic cube/generalized/layered build correctness, carries generation + lifecycle validity sequence through capture/build/upload/install, rejects intervening relevant-event work before installation, draws only while LIVE, suppresses stale drawing immediately on invalidation and completion-retires old geometry/indirect ownership before replacement admission.

`FrameCoordinator` coalesces relevant observed events into one generation advance per frame-drain batch, owns tracked-section identity, rejects stale installs, records rebuild installs/latency/reasons and requires runtime evidence across section edits, resource reload and chunk unload/load.

### Corrected lifecycle bridge

The first runtime proved the visual/update path but exposed that the initial 256-entry global event ring was wrong for the exact `LevelExtractor` dirty sink: startup/chunk streaming produced hundreds of thousands of unrelated section-dirty events, overwrote rare lifecycle events and advanced the stale token for unrelated sections.

`SectionLifecycleEvents` is now an allocation-free target-scoped coalescing bridge with no per-event ring:

- before a tracked section exists, unrelated section/chunk churn does not advance renderer validity;
- once bound, only exact dirty events for the tracked section advance validity;
- only chunk load/unload events in the tracked section's 3x3 X/Z halo neighborhood advance validity;
- world replacement and successful resource reload always advance validity;
- world replacement immediately unbinds the old target;
- sticky counters drain by delta and cannot overwrite rare chunk lifecycle events;
- `latestSequence()` now represents only events relevant to the tracked renderer validity domain;
- the bridge has no normal overflow/drop path. `REASON_OVERFLOW` remains only as an explicit internal safety-invalidation reason.

Dev6 remains a one-section lifecycle correctness proof. P2.7 still owns persistent multi-section scene ownership; Phase 3 still owns production asynchronous worker meshing/greedy meshing.

## First dev6 runtime result - visual pass, machine gate fail

The user reported everything worked visually. The log also proved repeated edit-driven rebuilds, resource reload observation, deterministic P2.5 capture/build semantics, immediate stale draw suppression, completion-gated replacement, full staging/arena/resource reclamation and process exit 0.

However shutdown was not a P2.6 success:

- `lifecycleGateReady=false`;
- `installCount=33`, `rebuildInstalls=32`;
- `dirtyEvents=1184`, `resourceReloadEvents=1`;
- `chunkLoadEvents=0`, `chunkUnloadEvents=0`;
- `droppedLifecycleEvents=391374`;
- observed reasons included `event-overflow`;
- process exit code `0`.

That tested package is not canonical closure evidence.

## Corrected dev6 CI/package evidence

Corrected implementation head: `6e01a8c9a5a9018eb427b42c0c1ad15fc92769d4`.

GitHub Actions run `32499586294`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- Upload build artifacts: SUCCESS;
- Publish versioned release: SKIPPED.

Artifact ID: `9452971102`.

Corrected package:

- `Obsidian-0.2.0-phase2-dev6.jar`;
- SHA-256 `486db6d80490170961d5a98debcb691e440e775e433283ab50b30c894821feb8`;
- sources SHA-256 `786d3c2f54bdc5f1e29b6ff10f5d4009eba7398dc215606fec3bbd4e86354f0e`;
- metadata exactly `obsidian 0.2.0-phase2-dev6`.

Packaged bytecode confirms the active lifecycle coordinator/probe and exact lifecycle mixins, plus the corrected target identity, relevant-only validity sequence, sticky dirty/load/unload/world/resource counters and target-neighborhood chunk filtering. The old 256-entry event ring is absent. Public SOLID/CUTOUT BLOCK pipelines, `ALPHA_CUTOUT`, atlas/lightmap bindings and `drawIndexedIndirect` remain unchanged. No native graphics seam was introduced.

## Required corrected runtime gate

The reference RX 6800 XT retest must exercise multiple edits inside the logged tracked-section bounds, successful F3+T resource reload, tracked 3x3-halo chunk unload then reload/return, and normal shutdown.

Required machine closure:

- at least three rebuild installs;
- nonzero section-dirty events;
- nonzero resource-reload events;
- nonzero tracked-neighborhood chunk-unload and chunk-load events;
- `droppedLifecycleEvents=0`;
- no event-bridge overflow reason;
- no stale generation installation;
- deterministic P2.5 capture/build semantics;
- full completion-gated reclamation;
- process exit `0`;
- shutdown `lifecycleGateReady=true`.

Human oracle: no stale block geometry should persist after edits/reload/unload-return. The prior visual pass remains positive evidence because the correction only changes lifecycle event transport/validity scoping, but the corrected package still needs one complete lifecycle run for closure.

## Merge authorization

The user explicitly authorized merging dev6 on 2026-08-21. Treat this as standing authorization conditional on the corrected reference runtime lifecycle gate passing and final exact-head CI remaining green. Do not ask for merge authorization again unless dev6 behavior/scope changes materially after validation.

## Immediate next action

1. Run exact-head CI containing A-0083 and this corrected runtime-pending continuity update.
2. Confirm the exact-head artifact is byte-identical to the corrected implementation package.
3. Deliver the corrected `0.2.0-phase2-dev6` lifecycle-test JAR.
4. Validate edits + F3+T + tracked-neighborhood unload/return and require `droppedLifecycleEvents=0`, nonzero chunk load/unload counters and `lifecycleGateReady=true`.
5. If the corrected gate passes, record immutable success evidence and merge PR #25 under the user's standing authorization, then Class-A synchronize `main` before starting P2.7/dev7.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
