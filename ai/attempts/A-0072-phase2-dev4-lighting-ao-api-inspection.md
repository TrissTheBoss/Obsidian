# A-0072 - Phase 2 dev4 exact Minecraft 26.2 lighting/AO API inspection

Date: 2026-08-21
Status: SUCCESS
Milestone: Phase 2 P2.4 / 0.2.0-phase2-dev4
Production branch: `phase2/lighting-ao-correctness`
Temporary inspection PR: #20 (close without merge)

## Objective

Ground P2.4 block-light, sky-light, directional shade and ambient-occlusion work in the exact Loom-resolved Minecraft 26.2 client classes and bytecode rather than older LightTexture/ModelBlockRenderer assumptions.

## Hosted inspection evidence

Disposable workflow: `Inspect Phase 2 Dev4 APIs`.

Successful evidence runs/artifacts:

- broad lighting/AO inventory: run `32430354151`, artifact `9428878800`;
- targeted BlockModelLighter/LightCoords/terrain pipeline inspection: run `32430629966`, artifact `9428951869`;
- final BlockAndLightGetter/lightmap sampler inspection: run `32430826864`, artifact `9429008547`.

All inspection runs compile the dev4 baseline with Java 25 / Gradle 9.5.1 before `javap` over the exact Loom-resolved client classpath.

## Major 26.2 API corrections

The old class name `net.minecraft.client.renderer.LightTexture` is not the active 26.2 contract. Minecraft 26.2 uses `net.minecraft.client.renderer.Lightmap`.

`Lightmap` owns a 16x16 RGBA8 lightmap texture and exposes `getTextureView()`. `GameRenderer` publicly exposes both `lightmap()` and `levelLightmap()` texture views.

The exact block-lighting/AO helper is `net.minecraft.client.renderer.block.BlockModelLighter`, not an implementation detail that P2.4 needs to re-create from memory.

## Exact vanilla AO/flat selection

`ModelBlockRenderer.tesselateBlock` collects the selected baked model parts using the same deterministic state seed already established by P2.3.

It chooses the ambient-occlusion path only when all of these are true:

1. the renderer's ambient-occlusion option is enabled;
2. `BlockState.getLightEmission() == 0`;
3. the first selected `BlockStateModelPart.useAmbientOcclusion()` is true.

Otherwise it uses the flat-light path.

This is the exact selection rule dev4 should mirror for the supported materialized faces.

## Exact `BlockModelLighter` public contract

`BlockModelLighter` has a public constructor and exposes:

- `getLightCoords(BlockState, BlockAndTintGetter, BlockPos)`;
- `prepareQuadAmbientOcclusion(BlockAndTintGetter, BlockState, BlockPos, BakedQuad, QuadInstance)`;
- `prepareQuadFlat(BlockAndTintGetter, BlockState, BlockPos, int, BakedQuad, QuadInstance)`.

Minecraft 26.2 `ClientLevel` directly implements `net.minecraft.client.renderer.block.BlockAndTintGetter`. Therefore the render-thread dev4 capture can call the exact vanilla lighter directly against the live client level. There is no need for a fake light engine or a hand-reimplemented AO algorithm in the correctness oracle.

`BlockAndTintGetter` extends `BlockAndLightGetter` and adds `cardinalLighting()` and `getBlockTint(...)`. `BlockAndLightGetter` supplies the light-engine-backed brightness access used by `LightCoordsUtil`.

## Exact packed light coordinates

`LightCoordsUtil.getLightCoords(...)`:

- returns full-bright `15728880` for `BlockState.emissiveRendering()`;
- otherwise gets packed brightness from the active light engine;
- raises the packed block-light component to at least `BlockState.getLightEmission()`.

`QuadInstance.getLightCoordsWithEmission(vertex, materialLightEmission)` then applies the baked material's emission adjustment before vertex output.

P2.4 should capture this final per-vertex packed light value, because that is what `VertexConsumer.putBlockBakedQuad` actually writes to the vertex.

## Exact AO result carrier and tint order

`com.mojang.blaze3d.vertex.QuadInstance` initializes all four colors to white and holds four packed light coordinates.

`BlockModelLighter.prepareQuadAmbientOcclusion(...)` writes the four per-vertex AO/shade colors and four per-vertex packed light coordinates. It uses exact 26.2 neighbor BlockStates, `BlockState.getShadeBrightness`, view-blocking/light-dampening checks, `LightCoordsUtil.smoothBlend` / `smoothWeightedBlend`, and `BlockAndTintGetter.cardinalLighting()`.

Directional shade is exact:

- if `BakedQuad.MaterialInfo.shade()` is true, scale by `CardinalLighting.byFace(direction)`;
- otherwise scale by `CardinalLighting.up()`.

`ModelBlockRenderer.putQuadWithTint` applies world/biome tint *after* the lighter by calling `QuadInstance.multiplyColor(tint)`. Dev4 can reproduce the exact order by applying the already-captured P2.3 tint to the lighter's `QuadInstance` before freezing its four colors.

## Exact flat-light path

For a directional quad, vanilla first computes `BlockModelLighter.getLightCoords(state, level, adjacentFacePosition)` and passes that to `prepareQuadFlat(...)`.

For the canonical unit-face geometry accepted by P2.3, `BlockModelLighter` marks the face cubic, so the directional face uses the outward adjacent cell's light value. All four vertices receive the flat light coordinate, followed by exact cardinal shade color.

General/null-direction quads are still outside the conservative P2.3/P2.4 supported subset.

## Exact vertex format

`DefaultVertexFormat.BLOCK` is the active block/terrain vertex contract:

- Position: `RGB32_FLOAT` = 12 bytes;
- Color: `RGBA8_UNORM` = 4 bytes;
- UV0: `RG32_FLOAT` = 8 bytes;
- UV2/light: `RG16_SINT` = 4 bytes.

Total: **28 bytes per vertex**.

`VertexConsumer.setLight(int)` writes the packed light low/high 16-bit halves into UV2. `VertexConsumer.putBlockBakedQuad` consumes each baked vertex's position/UV, `QuadInstance.getColor(i)`, and `QuadInstance.getLightCoordsWithEmission(i, materialInfo.lightEmission())`.

For the permanent maximum of 24,576 reference faces, 4 vertices/face at 28 bytes plus 6 int32 indices/face is 3,342,336 bytes, still below the existing 4 MiB dev-validation geometry/staging capacities.

## Exact public graphics/lightmap seam

`RenderPipelines.SOLID_BLOCK` is a public block pipeline using `DefaultVertexFormat.BLOCK` and the block shader path. Its generic block bind layouts include globals, fog, `Sampler0` + `Sampler2`, and the block snippet adds dynamic matrices/projection.

The live chunk renderer binds:

- `Sampler0` to the block atlas with its chunk-layer sampler;
- `Sampler2` to `Minecraft.gameRenderer.lightmap()`;
- the lightmap sampler is exactly `RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)`.

This gives dev4 a public Blaze3D comparison path: use the `SOLID_BLOCK` shaders/bind layouts and `DefaultVertexFormat.BLOCK`, retain the already-proven reversed-depth/no-write comparison state, bind the live blocks atlas as `Sampler0`, bind `GameRenderer.levelLightmap()` as `Sampler2` with the exact clamp-to-edge linear sampler, and keep indexed-indirect drawing.

`SOLID_TERRAIN` uses the terrain/ChunkSection UBO path and is not needed merely to prove correct lighting semantics for the existing camera-relative validation drawable.

## Halo proof for the supported unit-cube subset

The exact `BlockModelLighter.prepareQuadAmbientOcclusion` bytecode samples the face-adjacent plane, the four side-adjacent neighbors, the corresponding outward-side states used by view-blocking tests, and corner combinations formed from those directions. For the P2.3 accepted canonical unit face, every sampled block coordinate is at most one block away from the source block on each axis.

Therefore the existing `SectionSnapshot` local domain `-1..16` on each axis (16^3 interior + one-block halo) is sufficient to contain every block-state neighbor needed by the supported full-cube AO geometry, including section-border faces.

The correctness oracle may still call the live `BlockModelLighter` during render-thread capture so it sees the exact active light engine. The resulting per-face/per-corner light/AO values are then frozen. No mesh-stage world/light-engine access is necessary.

If P2.5 later admits non-full/general/custom geometry, this halo proof must be revisited rather than assumed to generalize.

## Topology / diagonal result

The exact supported 26.2 block path does not choose a different quad triangle diagonal from AO corner values. AO/light varies per vertex while the quad topology remains the standard fixed quad/index topology. Dev4 therefore keeps the proven canonical 0-1-2 / 0-2-3 triangle split and does not invent an AO-diagonal merge key.

## Implementation decision

Implement dev4 as three new active pieces while leaving dev3 proof code intact as historical diagnostics:

1. `SectionLightingSnapshot`
   - render-thread-only exact vanilla light/AO capture;
   - deterministically reselect the same baked model/quad used by P2.3;
   - call exact `BlockModelLighter` against `ClientLevel`;
   - apply exact P2.3 tint after AO/shade;
   - map baked-vertex results to Obsidian canonical corners;
   - store final packed light (including material emission), exact per-corner ARGB color, AO-vs-flat identity and diagnostics;
   - retain only immutable primitive data.

2. `LitSectionMesh`
   - pure build from snapshot/reference/material/lighting captures only;
   - emit `DefaultVertexFormat.BLOCK` (28 bytes/vertex) plus existing 32-bit indices;
   - no live world/model/light-engine/resource reads;
   - preserve exact UV and exact captured light/AO/tint identity;
   - optionally apply only a documented uniform 3/4 comparison RGB modulation to make the overlay distinguishable, while retaining the exact unmodulated colors separately.

3. `RealSectionLightingProbe`
   - preserve P2.3 upload/placement/lifetime behavior;
   - duplicate deterministic lighting captures + lit-mesh builds;
   - public `SOLID_BLOCK` shader/bind-layout contract with proven comparison depth state;
   - blocks atlas `Sampler0` + exact live level lightmap `Sampler2`;
   - public indexed-indirect draw;
   - completion-gated reclamation and six sustained visual passes.

The permanent P2.1 geometry oracle and P2.3 material capture remain independent and unchanged.
