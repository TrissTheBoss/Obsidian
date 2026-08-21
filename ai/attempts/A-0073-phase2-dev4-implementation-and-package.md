# A-0073 - Phase 2 dev4 implementation and package

Date: 2026-08-21
Status: IMPLEMENTED / CI PACKAGE SUCCESS / RUNTIME PENDING
Milestone: Phase 2 P2.4 / 0.2.0-phase2-dev4
Branch: `phase2/lighting-ao-correctness`
Draft PR: #19

## Code head

First complete dev4 renderer behavior head: `e973e6fd80d9bd009383385e3a0713a2abf78932`.

This head contains no temporary inspection workflow. The disposable API-inspection work stayed on PR #20 and was closed without merge after A-0072 recorded its evidence.

## Implemented path

### `SectionLightingSnapshot`

New render-thread-only immutable P2.4 capture.

For every P2.3-supported reference face it:

- validates the P2.1 section/reference and P2.3 material identities;
- refuses capture if the P2.3 model/atlas resource epoch already changed;
- reconstructs the immutable BlockState ID;
- deterministically reselects the exact baked model parts with `BlockState.getSeed(worldPos)`;
- requires the same single directional BakedQuad/material identity that P2.3 accepted;
- mirrors exact Minecraft 26.2 AO selection: client AO enabled, state light emission zero, first selected part `useAmbientOcclusion()`;
- calls the public vanilla `BlockModelLighter.prepareQuadAmbientOcclusion(...)` or exact directional flat path against live `ClientLevel`;
- applies the already-captured P2.3 world/biome tint *after* vanilla AO/shade using `QuadInstance.multiplyColor`, matching `ModelBlockRenderer` order;
- applies baked `MaterialInfo.lightEmission()` via `QuadInstance.getLightCoordsWithEmission` before freezing each vertex light value;
- maps the baked-vertex results back into Obsidian's permanent canonical four-corner order;
- retains only primitive exact ARGB colors, packed light coordinates and AO/flat mode after capture;
- records deterministic fingerprints and block/sky light ranges.

Two lighting captures must be content-identical in the validation probe.

### `LitSectionMesh`

New pure drawable stage. It consumes only:

`SectionSnapshot + ReferenceFaceMesh + SectionMaterialSnapshot + SectionLightingSnapshot`.

It performs no live world/model/light-engine/resource reads and emits the exact `DefaultVertexFormat.BLOCK` layout:

- float3 section-local Position;
- RGBA8 Color;
- float2 exact baked UV0;
- signed-short2 packed UV2/light;
- 28 bytes per vertex;
- 4 vertices / 6 int32 indices per accepted face.

The mesh retains the exact unmodulated vanilla AO/shade/tint ARGB color and packed light per corner for deterministic validation. Only the emitted comparison RGB is uniformly multiplied by 3/4 so the overlay remains distinguishable from vanilla. Lightmap values themselves are not altered.

The permanent maximum remains within the validation arena:

- vertex bytes: `24576 * 4 * 28 = 2,752,512`;
- index bytes: `24576 * 6 * 4 = 589,824`;
- total: `3,342,336` bytes, below 4 MiB.

### `RealSectionLightingProbe`

New active dev4 probe. The completed dev3 `RealSectionMaterialProbe` remains in source as historical/proven diagnostic code but is no longer the active coordinator path.

The dev4 probe preserves P2.2/P2.3 placement/upload/lifetime semantics and adds:

- duplicate deterministic lighting captures;
- duplicate deterministic lit-mesh builds;
- public `RenderPipelines.SOLID_BLOCK` shader/bind-layout contract;
- `DefaultVertexFormat.BLOCK` with explicit TRIANGLES for Obsidian's indexed geometry;
- proven `DEBUG_QUADS` comparison depth state and no culling;
- exact blocks atlas as `Sampler0`;
- exact live `GameRenderer.levelLightmap()` as `Sampler2`;
- exact `RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)` lightmap sampler;
- public indexed-indirect draw;
- model/atlas resource-epoch check before every live draw;
- six sustained, individually reclaimed comparison passes;
- completion-gated vertex/index/indirect retirement;
- `nativeGraphicsSeam=false`;
- `profilerOnlySubmissions=0`.

### Active coordinator/bootstrap

`FrameCoordinator` now constructs `RealSectionLightingProbe`, retains the proven 4 MiB staging + 4 MiB geometry validation capacities, five-second world-entry delay and six-pass human validation window, and logs final material/light/AO/drawable/lifetime state.

Bootstrap log now identifies the active Phase 2 dev4 lighting/AO probe.

## Hosted CI/package evidence

GitHub Actions run `32431204825` on behavior head `e973e6fd80d9bd009383385e3a0713a2abf78932`:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- public release: SKIPPED.

Artifact ID: `9429131700`.

Built package:

- `Obsidian-0.2.0-phase2-dev4.jar`;
- SHA-256: `0982465d0f917c7a65c21c5e3331a4a25f85299181321a91915d03839a3db5e3`;
- sources SHA-256: `f8505544fa6e07c8421ba458510499ba41ca10907965b427188f99226d22a3e8`;
- metadata: exactly `obsidian 0.2.0-phase2-dev4`.

Package inspection confirms these active classes are present:

- `SectionLightingSnapshot`;
- `LitSectionMesh`;
- `RealSectionLightingProbe`;
- active `FrameCoordinator`.

`javap` inspection of the packaged bytecode confirms:

- `FrameCoordinator` constructs `RealSectionLightingProbe`, not the dev3 probe;
- `RealSectionLightingProbe` references `RenderPipelines.SOLID_BLOCK`;
- it references `DefaultVertexFormat.BLOCK`;
- it binds `Sampler2`;
- it calls `GameRenderer.levelLightmap()`;
- it calls `SamplerCache.getClampToEdge(FilterMode.LINEAR)`;
- it calls public `RenderPass.drawIndexedIndirect`.

## Runtime success gate

Reference RX 6800 XT runtime validation is still required. A successful run must establish:

- exact dev4 version and Vulkan backend;
- duplicate deterministic reference/material/lighting/drawable results;
- supported face/light accounting;
- at least one lit materialized face;
- meaningful AO/flat distribution according to the sampled world/settings;
- finite block/sky packed light ranges;
- `worldReadsAfterLightingCapture=0` for pure lit mesh construction;
- `oneBlockHaloSufficient=true` under the exact supported-subset proof;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- `indexedIndirect=true`;
- blocks atlas and lightmap bound;
- stable material resource epoch through each comparison pass;
- visible geometry/UV alignment retained;
- light level, face shade and AO corner-darkening pattern visually agree with vanilla aside from the deliberate uniform 3/4 RGB overlay modulation;
- all 6 passes complete;
- zero profiler-only submissions;
- full staging/arena/indirect reclamation;
- process exit 0.

World-dependent face counts, light values, AO counts and fingerprints are evidence only and must not be hard-coded.

## Deliberate boundary

P2.4 still does not include P2.5 broad model/cutout semantics, P2.6 event-driven light/block invalidation, production greedy meshing, global vanilla terrain replacement, or production-scale visibility/performance tuning.
