# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Phase 1: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Phase 2 P2.1: **COMPLETE / runtime validated and merged**
- P2.1 closing merge: `a714e19ce871bf73136d52f85a1780109aa851dd`
- Phase 2 P2.2: **COMPLETE / runtime + human visual validated and merged**
- P2.2 closing merge: `f9c64267c5becb3bd80897efdb09ed65a6ce8697`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.3 - correct textures/material identity**
- Active branch: `phase2/material-texture-identity`
- Active draft PR: #16, `Phase 2 dev3: texture and material identity`
- Current development version: `0.2.0-phase2-dev3`
- P2.3 status: **ACTIVE - exact Minecraft 26.2 material/model/sprite/UV/tint/layer API inspection in progress before implementation**

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Roles:

- `CURRENT_STATE.md` = current truth and immediate next action;
- `MASTER_ROADMAP.md` = canonical long-range product/phase/feature plan and roadmap governance;
- `OPERATING_MANUAL.md` = engineering and handoff procedure;
- `DECISIONS.md` = durable architectural policy;
- attempts = immutable evidence/history;
- source, CI and runtime logs = actual implementation/proof.

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

## Proven Phase 1 foundation

Phase 1 established:

`bounded persistent staging -> generation-safe device arena -> fixed frame graph -> narrow native compute/storage seam -> GPU scene visibility -> atomic indirect compaction + visible count -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

This remains the infrastructure foundation for real terrain.

## P2.1 closing truth

Evidence: `ai/attempts/A-0058-phase2-dev1-runtime-success.md`.

P2.1 permanently established:

- immutable real 16^3 section snapshot;
- one-block halo / 18^3 = 5832 sampled cells;
- primitive-only post-capture data;
- conservative supported-full-cube subset;
- deterministic canonical `ReferenceFaceMesh`;
- no post-snapshot live-world reads during meshing;
- bounded staging / generation-safe arena / completion-gated lifetime validation;
- vanilla terrain remains active.

`ReferenceFaceMesh` is permanent and must remain independent of the future optimized/greedy mesher.

## P2.2 closing truth - COMPLETE

Runtime evidence: `ai/attempts/A-0066-phase2-dev2-runtime-success.md`.

Merge: PR #14 -> `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.

P2.2 established the first real section-derived drawable geometry path while retaining the P2.1 oracle:

- deterministic `DrawableSectionMesh` from `SectionSnapshot` + `ReferenceFaceMesh` only;
- one reference face -> one drawable quad / 4 vertices / 6 indices;
- section-local positions and 32-bit indices;
- exact Minecraft 26.2 world-render hook and camera-relative transform;
- public Blaze3D live-world indexed-indirect graphics;
- `nativeGraphicsSeam=false`;
- live main color/depth target with reversed-depth comparison semantics;
- bounded validation staging/device-arena ownership;
- completion-gated geometry/indirect retirement;
- no post-snapshot world reads during mesh construction;
- no profiler-only submissions.

The reference RX 6800 XT validation completed all six sustained comparison passes. The tester explicitly reported the orientation-colored Obsidian geometry was **perfectly aligned** with the corresponding vanilla terrain.

Final logged shutdown invariants included:

- `completedVisualPasses=6`;
- final sample 5832 cells; 4014 air + 82 supported + 0 unsupported = 4096 interior cells;
- reference/drawable faces=139/139;
- drawable vertices=556;
- drawable indices=834;
- deterministic reference builds=2;
- deterministic drawable builds=2;
- `worldReadsAfterSnapshot=0`;
- `pipelineValid=true`;
- staging submitted/reclaimed=59256/59256 bytes;
- arena allocations/retired/reclaimed=12/12/12;
- final arena used=0, one 4194304-byte free span, fragmentation=0;
- indirect resources retired/released=6/6;
- all pending upload/arena/resource retirements=0;
- process exit code 0.

Terrain-dependent coordinates, fingerprints and counts are not hard-coded success values.

## P2.3 / dev3 - ACTIVE

Planning evidence: `ai/attempts/A-0067-phase2-dev3-material-identity-plan.md`.

Goal: replace P2.2 orientation/debug colors with exact Minecraft texture/material identity for the deliberately conservative supported full-cube path while preserving the permanent reference oracle and proven placement/lifetime architecture.

Dev3 begins with exact Loom-resolved Minecraft 26.2 inspection for:

- `BlockStateModelSet` / `BlockStateModel` / `BlockStateModelPart` selection and quad ownership;
- `BakedQuad` positions, packed UVs, direction, tint and material data;
- `Material.Baked` / `TextureAtlasSprite` identity and UV domain;
- block tint/color resolution and required world/position context;
- material/render-layer classification;
- public textured Blaze3D pipeline/bind-group/texture-sampler contracts;
- model/texture resource reload lifetime and invalidation.

The expected architecture is a render-thread capture/materialization bridge that converts mutable Minecraft model/resource state into immutable renderer-owned primitive/material identity. Future async mesh work must not retain or query live `ModelManager`, baked-model, sprite or world objects.

## Deliberate P2.3 boundary

Dev3 must not claim or pull forward:

- block/sky lighting or ambient occlusion - P2.4;
- broad cutout/non-full/custom-model semantics - P2.5+ unless a tiny case is required to prove material classification;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale visibility/performance - Phase 4+.

Unsupported resource-pack/custom-model cases must be explicit rather than silently approximated.

## Immediate next action

1. Complete exact Minecraft 26.2 API/bytecode inspection on the disposable dev3 inspection workstream.
2. Record findings immutably on the production dev3 branch and close the inspection PR without merge.
3. Implement renderer-owned material/sprite/UV identity for the conservative supported subset while keeping post-snapshot meshing world/model-manager free.
4. Preserve the validated P2.2 section-local transform, public indexed-indirect graphics and completion-gated lifetime path.
5. Run exact Java 25 / Gradle 9.5.1 CI and package `0.2.0-phase2-dev3`.
6. Keep PR #16 draft until reference RX 6800 XT runtime/visual validation proves textured materialized geometry aligns with vanilla and all lifetime invariants remain clean.

## Relevant durable decisions

D-0014 through D-0027 remain active. Especially relevant:

- D-0016 completion-gated lifetime;
- D-0017 bounded/backpressured staging;
- D-0020 generation-safe arena identity;
- D-0023 public Blaze3D graphics first;
- D-0024 permanent reference oracle before production binary/bitmask greedy meshing;
- D-0025 narrow native Vulkan seam only for missing compute/storage capability;
- D-0027 public indexed-indirect graphics baseline unless profiling later justifies native indirect-count integration.
