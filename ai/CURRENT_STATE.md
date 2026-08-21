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
- Phase 2 P2.3: **COMPLETE / runtime + human visual validated and merged**
- P2.3 closing merge: `667230f51222746083efe89c72265d80ac9d3929`
- Phase 2 P2.4: **COMPLETE / runtime + human visual validated and merged**
- P2.4 closing merge: `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`
- P2.4 Class-A sync merge: `a6d7d2ff96948910e22b2ab4e3e5212408ef97c2`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.5 - broader opaque/cutout block semantics**
- Active branch: `phase2/broader-opaque-cutout-semantics`
- Current development version: `0.2.0-phase2-dev5`
- P2.5 status: **ACTIVE / exact Minecraft 26.2 model, cutout, arbitrary-quad and culling API grounding first**

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

P2.1 established immutable section + one-block-halo capture and the permanent deterministic cube-face `ReferenceFaceMesh` oracle.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect drawing.

P2.3 established exact baked sprite/UV/material/tint identity for the conservative SOLID full-cube subset.

P2.4 established exact Minecraft 26.2 block/sky light, directional shade and ambient-occlusion corner semantics for that subset through `SectionLightingSnapshot` + `LitSectionMesh` and the public BLOCK/lightmap pipeline. Final evidence: `ai/attempts/A-0075-phase2-dev4-runtime-success.md`.

The P2.1 cube oracle remains permanent and independent. P2.5 must not redefine it to mean arbitrary baked geometry.

## P2.5 / dev5 - ACTIVE

Plan: `ai/attempts/A-0076-phase2-dev5-broader-block-semantics-plan.md`.

Canonical scope:

- ordinary full cubes remain supported;
- expand axis-aligned simple model cases;
- add cutout vegetation/model classes where architecture allows;
- retain tinted and biome-colored cases;
- add selected non-full cases only after exact semantics are understood;
- unsupported cases remain explicit and measurable.

### Exact Minecraft 26.2 grounding required before implementation

Inspect exact Loom-resolved APIs/bytecode for:

- `BlockStateModelPart.getQuads(Direction)` versus `getQuads(null)` general/unculled geometry;
- multi-part and multi-quad models;
- arbitrary `BakedQuad` vertex positions/winding/UV/material fields;
- vanilla directional/general-quad compilation and face-culling/shape rules;
- exact SOLID versus CUTOUT render-layer selection and public pipeline state;
- alpha discard, cull, depth/write and BLOCK/lightmap requirements for CUTOUT;
- arbitrary-quad tint and biome-color behavior;
- `ModelBlockRenderer` / `BlockModelLighter` handling of non-full quad positions, AO shape flags and per-vertex light mapping;
- whether the one-block halo remains sufficient for any selected broader subset;
- common crossed vegetation/general-quad representation;
- correct per-layer draw grouping and bounded indexed-indirect submission.

Do not rely on older-version API memory.

### Architecture rule

If arbitrary/general/cutout baked geometry cannot be expressed faithfully by the cube-face oracle, add a companion immutable renderer-owned reference-quad capture rather than mutating `ReferenceFaceMesh` semantics.

After live capture, pure mesh construction must perform zero world/model/light/resource reads. Accepted and rejected geometry must be deterministic and counted by reason/layer.

### Deliberate boundary

P2.5 does **not** include:

- translucent/fluid terrain - Phase 6;
- event-driven section/block/light/resource update lifecycle - P2.6;
- persistent multi-section scene ownership - P2.7;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale performance claims.

## Immediate next action

1. Open the dev5 draft PR.
2. Run hosted exact Minecraft 26.2 API/bytecode inspection for broader geometry/cutout semantics.
3. Record inspection findings immutably before selecting the first exact broader subset.
4. Implement only the subset proven by that inspection.
5. Keep the PR draft/unmerged until exact CI and reference RX 6800 XT runtime + human visual validation pass.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016, D-0017, D-0020, D-0023, D-0024, D-0025 and D-0027.
