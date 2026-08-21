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
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Next milestone: **P2.6 - section lifecycle and rebuild correctness**
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

P2.5 established exact broader Minecraft MODEL semantics without weakening the permanent cube oracle. `SectionBakedQuadSnapshot` captures vanilla's finished `ModelBlockRenderer.tesselateBlock(...) -> BlockQuadOutput` results for accepted blocks, including arbitrary/general/multi-quad geometry, block offsets, shape-culling outcome, UVs, final AO/shade/tint colors, packed light, direction and SOLID/CUTOUT material identity. Unsupported/translucent/wrong-atlas blocks are rejected atomically rather than partially rendered. `BakedSectionMesh` deterministically groups immutable captured quads into public SOLID and CUTOUT BLOCK-format indexed-indirect passes.

P2.5 runtime evidence: `ai/attempts/A-0079-phase2-dev5-runtime-success.md`.

## P2.5 / dev5 - COMPLETE

Closing merge: `c17f7c6146678e18cacabc44d85c67413a040f73`.

Evidence chain:

- A-0076: P2.5 plan;
- A-0077: exact Minecraft 26.2 model/cutout API inspection;
- A-0078: implementation + package/CI evidence;
- A-0079: reference RX 6800 XT runtime + human visual success.

Final validated package: `Obsidian-0.2.0-phase2-dev5.jar`, SHA-256 `68e393636e0ca216c99b3253033f701ac38aad6ba373538430a910cce238d42e`.

Reference validation completed six sustained passes with stable section `(64,4,8)`: 626 generalized quads = 321 SOLID + 305 CUTOUT, 2504 vertices, 3756 indices, deterministic duplicate capture/builds, `worldReadsAfterGeneralizedCapture=0`, `cubeOraclePreserved=true`, one-block-halo sufficiency for captured culling/light samples, valid public SOLID/CUTOUT pipelines, exact `ALPHA_CUTOUT=0.5`, public indexed-indirect drawing, bound blocks atlas/lightmap, `nativeGraphicsSeam=false`, zero profiler-only submissions and full completion-gated staging/arena/resource reclamation. Process exited 0. The user reported everything looked fine.

P2.5 deliberately does not claim leaves force-opaque support, translucent/fluid terrain, production-scale global terrain replacement, event-driven update lifecycle, persistent multi-section ownership or production greedy meshing.

## P2.6 - NEXT

Canonical goals from `MASTER_ROADMAP.md`:

- section load;
- section unload;
- block update invalidation;
- neighbor-border invalidation;
- resource reload invalidation;
- stale async result rejection;
- safe replacement of live GPU allocations;
- multiple rebuilds of the same section;
- generation/version identity carried end to end.

P2.3 exposed the concrete lifecycle defect this milestone must eliminate: a validation overlay could briefly retain stale geometry after a block edit until the next bounded recapture. P2.6 must replace that one-shot/pass-based behavior with event-driven dirtying, versioned rebuild scheduling and safe replacement/unload behavior while retaining the immutable snapshot/capture boundary and completion-gated GPU lifetime rules.

## Immediate next action

1. Merge this Class-A status synchronization with `[no-release]` after exact-head CI.
2. Branch P2.6/dev6 from the synchronized `main`.
3. Record an immutable P2.6 plan before implementation.
4. Inspect exact Minecraft 26.2 client hooks/APIs for section load/unload, block/light updates, neighbor-border implications and resource reload lifecycle before choosing injection/event seams.
5. Keep dev6 draft/unmerged until its exact CI and required runtime validation pass; dev6 requires fresh explicit merge authorization when its gate is satisfied.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
