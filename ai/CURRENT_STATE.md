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
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.4 - block/sky lighting and ambient occlusion correctness**
- Active branch: `phase2/lighting-ao-correctness`
- Active draft PR: #19, `Phase 2 dev4: lighting and ambient occlusion correctness`
- Current development version: `0.2.0-phase2-dev4`
- P2.4 status: **exact Minecraft 26.2 API grounded + implementation complete + CI/package verified; reference RX 6800 XT runtime/human visual validation pending**

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

P2.1 established the immutable 16^3 section + one-block halo snapshot and permanent deterministic `ReferenceFaceMesh` geometry oracle.

P2.2 established deterministic indexed real geometry, exact section/camera placement and public depth-tested indexed-indirect world drawing.

P2.3 established exact Minecraft 26.2 baked sprite/UV/tint/material identity for the conservative supported SOLID full-cube path. Runtime/human evidence is `ai/attempts/A-0070-phase2-dev3-runtime-success.md`.

The permanent P2.1 reference oracle remains independent of all later materialized/optimized representations.

## P2.4 / dev4 - ACTIVE

Evidence:

- plan: `ai/attempts/A-0071-phase2-dev4-lighting-ao-plan.md`;
- exact 26.2 API/bytecode inspection: `ai/attempts/A-0072-phase2-dev4-lighting-ao-api-inspection.md`;
- implementation/package: `ai/attempts/A-0073-phase2-dev4-implementation-and-package.md`.

Temporary inspection PR #20 was closed without merge.

### Exact 26.2 lighting contract now used

Minecraft 26.2 uses public `BlockModelLighter` + `QuadInstance` for block light/AO results and `Lightmap` rather than the older `LightTexture` assumption.

Dev4 mirrors exact `ModelBlockRenderer` AO selection:

- client AO enabled;
- `BlockState.getLightEmission() == 0`;
- first selected `BlockStateModelPart.useAmbientOcclusion()`.

For supported faces, render-thread capture calls exact vanilla `BlockModelLighter.prepareQuadAmbientOcclusion(...)` or the directional flat path against `ClientLevel`, applies exact P2.3 tint after AO/shade, applies baked material emission to packed light, maps the result to Obsidian canonical corners and freezes only primitive values.

Exact directional shade comes from `CardinalLighting.byFace(direction)` when baked `shade=true`, otherwise `CardinalLighting.up()`.

### Halo proof

Exact `BlockModelLighter` bytecode for the P2.3 canonical unit-face subset samples only coordinates at most one block away from the source block on each axis. The existing local `-1..16` / 18^3 section snapshot therefore contains every block-state neighbor needed for supported-face AO at section borders.

This proof does not automatically extend to future P2.5 non-full/general/custom geometry.

### Immutable lighting capture

New `SectionLightingSnapshot`:

- render-thread only;
- validates section/reference/material/resource identity;
- deterministically reselects the exact P2.3 baked directional quad;
- uses vanilla `BlockModelLighter` as the correctness oracle;
- freezes exact four-corner packed light and unmodulated ARGB AO/shade/tint color;
- records AO-vs-flat identity and light ranges;
- retains no live world/model/light-engine objects.

Two captures must be content-identical in the validation probe.

### Pure BLOCK-format mesh

New `LitSectionMesh` consumes only immutable Obsidian captures and emits exact `DefaultVertexFormat.BLOCK` layout:

- float3 Position;
- RGBA8 Color;
- float2 exact baked UV0;
- signed-short2 packed UV2/light;
- 28 bytes/vertex;
- 4 vertices / 6 int32 indices per accepted face.

Pure mesh construction performs zero world/model/light-engine/resource reads. Exact unmodulated color/light remains retained for validation. Only emitted RGB is uniformly multiplied by 3/4 to distinguish the comparison overlay; packed light is unchanged.

Worst-case reference geometry + indices remains `3,342,336` bytes, below the existing 4 MiB dev-validation capacity.

### Live lit comparison

New `RealSectionLightingProbe` is the active `FrameCoordinator` path. It preserves P2.2/P2.3 upload/placement/lifetime behavior and adds:

- duplicate deterministic lighting captures;
- duplicate deterministic lit drawable builds;
- public `RenderPipelines.SOLID_BLOCK` shaders/bind layouts;
- `DefaultVertexFormat.BLOCK`;
- proven reversed-depth comparison state;
- blocks atlas as `Sampler0`;
- live `GameRenderer.levelLightmap()` as `Sampler2`;
- exact clamp-to-edge LINEAR lightmap sampler;
- public indexed-indirect draw;
- model/atlas resource-epoch check before each draw;
- five-second entry delay + six fully reclaimed visual passes;
- completion-gated geometry/indirect lifetime;
- `nativeGraphicsSeam=false`;
- zero profiler-only submissions.

Completed older probes remain in source as historical diagnostics and are inactive.

## Dev4 CI/package evidence

Behavior head before documentation-only synchronization: `e973e6fd80d9bd009383385e3a0713a2abf78932`.

GitHub Actions run `32431204825`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- artifact upload: SUCCESS;
- public release: SKIPPED.

Artifact ID: `9429131700`.

Package:

- `Obsidian-0.2.0-phase2-dev4.jar`;
- SHA-256 `0982465d0f917c7a65c21c5e3331a4a25f85299181321a91915d03839a3db5e3`;
- sources SHA-256 `f8505544fa6e07c8421ba458510499ba41ca10907965b427188f99226d22a3e8`;
- metadata exactly `obsidian 0.2.0-phase2-dev4`.

Packaged bytecode confirms active `FrameCoordinator -> RealSectionLightingProbe`, `SOLID_BLOCK`, `DefaultVertexFormat.BLOCK`, `Sampler2`, `GameRenderer.levelLightmap()`, clamp-to-edge LINEAR sampler and public `drawIndexedIndirect`.

## Dev4 runtime success criteria

The reference RX 6800 XT run must prove:

- exact `obsidian 0.2.0-phase2-dev4` loads on Vulkan;
- deterministic reference/material/lighting/drawable duplicates;
- at least one supported lit face;
- exact materialized/light/drawable face accounting;
- vertices = faces * 4 and indices = faces * 6;
- block and sky light ranges are logged from real packed values;
- AO/flat counts match the sampled world/settings without hard-coded expectations;
- `worldReadsAfterLightingCapture=0` for pure mesh construction;
- `oneBlockHaloSufficient=true` under the exact supported-subset bytecode proof;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- `indexedIndirect=true`;
- blocks atlas and level lightmap are bound;
- resource epoch stays stable during each pass;
- texture/geometry/UV alignment remains correct;
- relative block/sky lighting, directional face shade and AO corner darkening agree visually with vanilla, ignoring the deliberate uniform 3/4 RGB comparison modulation;
- all six visual passes complete;
- `profilerOnlySubmissions=0`;
- staging fully reclaims;
- all arena allocations retire/reclaim behind completion;
- indirect resources retire/release;
- final arena used=0, one full 4 MiB free span, fragmentation=0;
- no pending upload/arena/resource retirements;
- process exits 0.

World-dependent counts, light values and fingerprints are runtime evidence, not hard-coded success values.

## P2.3 block-edit observation retained for P2.6

The dev3 stale overlay after a block edit was a bounded probe recapture delay. Event-driven block/light update invalidation, neighbor invalidation and rebuild scheduling remain P2.6 scope. Do not add ad-hoc P2.4 lifecycle behavior merely to shorten the validation overlay interval.

## Deliberate P2.4 boundary

Dev4 does **not** claim:

- broad CUTOUT/non-full/custom-model support - P2.5+;
- event-driven section/block/light lifecycle - P2.6;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale visibility/performance - Phase 4+.

## Immediate next action

1. Run final exact-head Java 25 / Gradle 9.5.1 CI after continuity/PR synchronization.
2. Runtime-test the exact dev4 artifact on the reference RX 6800 XT.
3. After the five-second arm delay, inspect all six passes in mixed lighting while moving/turning.
4. Judge relative light level, directional face shading, AO corner darkening, texture/UV identity and world/camera alignment. Ignore the uniform 3/4 comparison darkening.
5. Save the complete Prism log and record the result immutably.
6. Keep PR #19 draft/unmerged until this gate passes.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016, D-0017, D-0020, D-0023, D-0024, D-0025 and D-0027.
