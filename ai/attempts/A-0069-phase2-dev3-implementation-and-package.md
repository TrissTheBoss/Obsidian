# A-0069 - Phase 2 dev3 implementation and package

Date: 2026-08-21
Status: IMPLEMENTED / CI PACKAGE SUCCESS / RUNTIME PENDING
Milestone: Phase 2 P2.3 / 0.2.0-phase2-dev3
Branch: `phase2/material-texture-identity`
PR: #16

## Objective

Implement the first real Minecraft texture/material identity path on top of the runtime-validated P2.1/P2.2 section oracle and placement architecture, while keeping P2.4 light/AO and Phase 3 greedy meshing out of scope.

## Exact grounding

A-0068 records the Loom-resolved Minecraft 26.2 model/material/UV/tint/render binding contracts used by this implementation. No older BakedQuad/model package assumptions were used.

## Implemented extraction path

New `SectionMaterialSnapshot` runs only on the render thread and converts live Minecraft model/resource state into immutable renderer-owned values.

For every permanent reference face it:

- reconstructs the captured BlockState from the immutable state ID;
- seeds model variant selection with the vanilla `BlockState.getSeed(worldPos)` rule;
- obtains `BlockStateModel` / selected `BlockStateModelPart` values;
- conservatively requires no general/null-direction quads and exactly one compatible directional quad;
- requires `ChunkSectionLayer.SOLID` for this first P2.3 proof;
- explicitly counts CUTOUT, TRANSLUCENT, missing, multi-quad, wrong-atlas, geometry and tint rejections rather than silently approximating them;
- validates the baked quad occupies exactly the same unit face as the P2.1 oracle;
- maps baked vertex UVs into the canonical Obsidian face-corner order using exact `UVPair.unpackU/V`;
- captures block-atlas + sprite identity, layer, flags, tint index, shade, light emission and animation identity;
- resolves tinted faces through the exact Minecraft `BlockTintSource.colorInWorld(...)` path while live world context is still legal;
- assigns deterministic renderer material IDs;
- retains no live BlockStateModel, BlockStateModelPart, BakedQuad, TextureAtlasSprite, ModelManager, world, or BlockPos reference after capture;
- records a model-set/blocks-atlas resource epoch so stale materialized geometry is rejected if resource identity changes before draw.

## Pure materialized mesh

New `MaterializedSectionMesh` consumes only:

- `SectionSnapshot`;
- `ReferenceFaceMesh`;
- `SectionMaterialSnapshot`.

It performs no Minecraft world/model/resource reads.

Vertex format is public `DefaultVertexFormat.POSITION_TEX_COLOR`:

- float3 section-local position;
- float2 exact baked UV;
- RGBA8 tint/comparison color;
- 4 vertices and 6 32-bit indices per supported materialized face.

The exact captured tint is retained in `SectionMaterialSnapshot`. For human visual comparison only, emitted RGB is multiplied by 3/4 with alpha 1 so a correct textured overlay remains visibly distinguishable from the already-rendered vanilla surface. This comparison modulation is not claimed as Minecraft lighting. P2.4 light/AO remains absent by design.

Worst-case P2.3 validation geometry remains within the existing explicit 4 MiB staging and 4 MiB device-arena capacities: POSITION_TEX_COLOR vertices plus 32-bit indices are below 3 MiB for the permanent reference maximum.

## Live graphics path

New `RealSectionMaterialProbe` preserves the P2.2 world/camera/lifetime path while replacing diagnostic colors with exact textured material data:

- duplicate deterministic reference builds;
- duplicate deterministic material captures;
- duplicate deterministic pure mesh builds;
- bounded persistent staging;
- generation-safe vertex/index arena allocations;
- public indexed-indirect graphics;
- no native graphics expansion (`nativeGraphicsSeam=false`);
- custom triangle-list pipeline using Minecraft's public `GUI_TEXTURED` position/tex/color shaders + bind layouts, with the already-proven DEBUG_QUADS reversed-depth state;
- live blocks atlas bound as `Sampler0` through public `RenderPass.bindTexture`;
- current world Globals, DynamicTransforms and Projection UBOs;
- resource-epoch validation before every live comparison draw;
- completion-gated arena and indirect-resource retirement;
- no profiler-only submissions.

`FrameCoordinator` now runs the dev3 probe after the same five-second world-entry delay and repeats six fully reclaimed comparison passes for sustained human observation.

The old `RealSectionDrawableProbe` remains in source as the proven P2.2 diagnostic implementation but is no longer the active coordinator path.

## Hosted compile/package evidence

Exact code head: `a7ae05ba8da391c052804d08b5a9ed4546c3311c`.

GitHub Actions run `32428077000`:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- public release: SKIPPED.

Artifact ID: `9428132378`.

Package inspection:

- `Obsidian-0.2.0-phase2-dev3.jar`;
- SHA-256: `7eb4cc26ad48ba10e986cd2069720a56f6f4079ec724dec6223ee6dd25376e58`;
- sources SHA-256: `03d6ab63ce81fe96d88148d5f3b6b078d87f0bef252957a5d1a42ecca9d4e818`;
- metadata reports exactly `obsidian 0.2.0-phase2-dev3`;
- required classes present: `SectionMaterialSnapshot`, `MaterializedSectionMesh`, `RealSectionMaterialProbe`, `FrameCoordinator`, `ObsidianBootstrap`;
- bytecode inspection confirms active `FrameCoordinator` constructs `RealSectionMaterialProbe`;
- bytecode inspection confirms dev3 references `RenderPipelines.GUI_TEXTURED`, `DefaultVertexFormat.POSITION_TEX_COLOR`, blocks atlas identity and `Sampler0`.

## Runtime gate

The reference RX 6800 XT run must still prove:

- exact dev3 version loads on Vulkan and the existing render hook applies;
- P2.1 snapshot/reference invariants remain deterministic;
- two material captures are deterministic;
- at least one conservative SOLID reference face materializes;
- all unsupported faces are explicitly accounted for by rejection counters;
- pure materialized mesh is deterministic and has `4 * faces` vertices / `6 * faces` indices;
- `worldReadsAfterMaterialCapture=0` for mesh construction;
- textured public pipeline compiles at runtime;
- live blocks atlas binding remains valid and resource epoch is stable during each pass;
- visible Obsidian comparison geometry uses recognizable Minecraft block textures rather than orientation colors;
- UV orientation is correct: no mirror/rotation/stretch relative to vanilla for the supported faces;
- geometry remains perfectly aligned while the camera moves;
- tint appearance is sensible where tinted supported faces are encountered;
- brightness/AO differences are not treated as P2.3 failure because P2.4 intentionally remains absent;
- six sustained comparison passes complete;
- `profilerOnlySubmissions=0`;
- staging, arena allocations and indirect resources fully reclaim behind completion;
- all pending retirement counts are zero at clean shutdown;
- process exits code 0.

PR #16 remains draft and must not merge until this runtime/human visual gate passes.
