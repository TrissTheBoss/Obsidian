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
- P2.6 status: **exact Minecraft 26.2 lifecycle seams grounded + generation-safe one-section implementation assembled / exact CI pending**
- Last runtime-validated development version: `0.2.0-phase2-dev5`

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

## P2.6 / dev6 - IMPLEMENTATION ASSEMBLED / CI PENDING

Evidence:

- plan: `ai/attempts/A-0080-phase2-dev6-section-lifecycle-plan.md`;
- exact Minecraft 26.2 lifecycle inspection: `ai/attempts/A-0081-phase2-dev6-lifecycle-api-inspection.md`;
- temporary inspection PR #26 closed without merge.

Exact inspection runs:

- lifecycle run `32487025079`, artifact `9448266980`;
- targeted `LevelExtractor`/chunk-storage run `32487514059`, artifact `9448449721`.

### Exact lifecycle observation seams

Dev6 observes vanilla behavior without cancelling or replacing it:

- private `LevelExtractor.setSectionDirty(IIIZ)` central sink after vanilla has already calculated block/light/border propagation;
- `LevelExtractor.setLevel(ClientLevel)` for world replacement/teardown;
- `ClientLevel.onChunkLoaded(ChunkPos)` and `ClientLevel.unload(LevelChunk)` for client chunk lifecycle;
- successful `ModelManager.reload(...)` future completion for model/resource invalidation.

Vanilla's own dirty propagation already expands block changes by one block before section conversion and light updates with neighbor sections, so Obsidian consumes exact resulting section coordinates rather than maintaining a parallel heuristic.

### Generation-safe implementation

`SectionLifecycleEvents` is a bounded primitive-only lifecycle bridge. It coalesces observed section-dirty, nearby chunk load/unload, world-change and resource-reload events without retaining Minecraft world objects.

`SectionGenerationGate` owns the monotonically increasing renderer generation and includes a deterministic stale-token rejection self-test.

`SectionSnapshot.tryCaptureSection(...)` recaptures the exact tracked section identity and its one-block halo without loading/generating missing chunks.

`RealSectionLifecycleProbe` owns one generation at a time. It preserves the P2.5 deterministic cube-reference/generalized-capture/layered-mesh path; carries the generation and lifecycle-event sequence through capture/build/upload/install; checks for intervening lifecycle events before GPU admission/submission; draws only while LIVE; stops drawing immediately on invalidation; and completion-gates geometry/indirect retirement before replacement generation admission.

`FrameCoordinator` owns the tracked section and generation lifecycle. Relevant events coalesce into one generation advance per drain batch. A stale or invalidated probe cannot become/remain live. Resource epoch mismatches force invalidation. Repeated section rebuild latency and lifecycle reason counts are logged.

Dev6 remains a one-section correctness proof. P2.7 still owns persistent multi-section scene ownership; Phase 3 still owns production asynchronous worker meshing/greedy meshing.

## Merge authorization

The user explicitly authorized merging dev6 on 2026-08-21. Treat this as standing authorization conditional on the required reference runtime + human lifecycle gate passing and final exact-head CI remaining green. Do not ask for merge authorization again unless dev6 behavior/scope changes materially after validation.

## Immediate next action

1. Run exact Java 25 / Gradle 9.5.1 CI on the implementation head and fix any exact Minecraft/Mixin signature issue.
2. Download and inspect the exact successful artifact: version metadata, active coordinator/probe, lifecycle mixins, generation gate and public SOLID/CUTOUT indexed-indirect path.
3. Record implementation/package evidence immutably and synchronize this file to runtime-pending status.
4. Produce the exact-head `0.2.0-phase2-dev6` lifecycle-test JAR for the reference RX 6800 XT session.
5. Keep PR #25 draft/unmerged until runtime/human lifecycle validation passes; merge is already authorized if that gate passes.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
