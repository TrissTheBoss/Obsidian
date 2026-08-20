# A-0068 - Phase 2 dev3 exact Minecraft 26.2 material API inspection

Date: 2026-08-21
Status: SUCCESS
Milestone: Phase 2 P2.3 / 0.2.0-phase2-dev3
Production branch: `phase2/material-texture-identity`
Temporary inspection PR: #17 (close without merge)

## Objective

Ground P2.3 texture/material work in the exact Loom-resolved Minecraft 26.2 classes instead of older BakedQuad/model assumptions.

## Hosted inspection evidence

Disposable workflow: `Inspect Phase 2 Dev3 APIs`

Successful runs:

- initial API inventory: run `32426945680`, artifact `9427777218`;
- targeted UV/render binding inspection: run `32427282733`, artifact `9427880522`.

Both runs compiled the production dev3 source with Java 25 / Gradle 9.5.1 before using `javap` on the exact resolved client classpath.

## Exact model/material architecture

Minecraft 26.2 uses:

`BlockStateModelSet -> BlockStateModel -> BlockStateModelPart -> net.minecraft.client.resources.model.geometry.BakedQuad`.

Important exact public contracts:

- `BlockStateModelSet.get(BlockState)` returns the baked state model.
- `BlockStateModel.collectParts(RandomSource, List<BlockStateModelPart>)` performs deterministic variant/part selection when seeded correctly.
- Vanilla `SectionCompiler` passes `BlockState.getSeed(BlockPos)` into `ModelBlockRenderer.tesselateBlock`, which sets that seed before `collectParts`.
- `BlockStateModelPart.getQuads(Direction)` returns the directional baked quads.
- Vanilla also processes `getQuads(null)` for unculled/general quads; P2.3 may explicitly reject model cases requiring those while the conservative cube path is established.

This supersedes older-package assumptions such as `net.minecraft.client.renderer.block.model.BakedQuad`.

## Exact BakedQuad/material contract

`BakedQuad` is a four-position/four-packed-UV record with:

- `position(int)` / `position0..3()`;
- `packedUV(int)` / `packedUV0..3()`;
- `direction()`;
- `materialInfo()`.

`BakedQuad.MaterialInfo` exposes:

- `sprite()`;
- `layer()` as `ChunkSectionLayer`;
- `itemRenderType()`;
- `tintIndex()` / `isTinted()`;
- `shade()`;
- `lightEmission()`;
- material flags.

`ChunkSectionLayer` is exactly `SOLID`, `CUTOUT`, or `TRANSLUCENT` and exposes its pipeline/vertex format. P2.3 can therefore classify layer identity without guessing.

`Material.Baked` contains the baked `TextureAtlasSprite` and `forceTranslucent` state.

## Exact UV semantics

Vanilla `VertexConsumer.putBlockBakedQuad` reads each `BakedQuad.packedUV(i)` and decodes it through:

- `net.minecraft.client.model.geom.builders.UVPair.unpackU(long)`;
- `UVPair.unpackV(long)`.

P2.3 should call those exact helpers rather than reimplement the packing convention.

`TextureAtlasSprite` exposes `contents().name()` for sprite identity, `atlasLocation()`, U/V bounds, transparency and animation state. Ordinary supported block materials are expected to resolve against `TextureAtlas.LOCATION_BLOCKS`; anything else should be explicit in validation.

## Exact tint semantics

Vanilla `ModelBlockRenderer` resolves a quad's `tintIndex`; when tinted it calls the corresponding `BlockTintSource.colorInWorld(BlockState, BlockAndTintGetter, BlockPos)` and multiplies the quad color.

Architectural consequence: biome/world-context tint must be captured during the render-thread extraction/materialization stage. Future async mesh work must consume the captured color and must not call the live world.

## Exact public graphics seam for the dev3 proof

`RenderPass` exposes public `bindTexture(String, GpuTextureView, GpuSampler)` in addition to the already proven public vertex/index/indirect methods.

`RenderSystem` exposes the current global settings UBO, projection UBO and dynamic-transform writer.

Minecraft's built-in `core/position_tex_color` pipeline contract uses:

- `DefaultVertexFormat.POSITION_TEX_COLOR`;
- global settings;
- `MATRICES_PROJECTION`;
- one texture binding named `Sampler0`.

This gives dev3 a deliberately narrow way to prove exact sprite UV + tint identity on the live depth-tested world without introducing the BLOCK lightmap contract early. Correct light/AO remains P2.4.

The existing reversed-depth/no-depth-write dev2 comparison state can be retained while replacing its position/color shader with the public textured shader contract and binding the live blocks atlas texture/sampler.

## Resource reload/lifetime implication

`ModelManager` owns a replaceable `BlockStateModelSet` field and rebuilds it during resource reload. P2.3 material captures should carry a resource-generation/epoch identity and be invalidated if the active model set changes before use. Renderer-owned mesh/material records must retain only immutable/captured identity/UV/color data, not live `BlockStateModel`, `BakedQuad`, `TextureAtlasSprite`, `ModelManager`, or world references.

## Implementation decision for dev3

Implement a new immutable materialized section mesh for the conservative P2.1/P2.2 reference faces:

1. reconstruct the immutable face's BlockState from its captured state ID;
2. seed model selection with the exact vanilla `state.getSeed(worldPos)` rule;
3. require a single compatible directional quad for the face and explicitly count rejected cases;
4. validate that the quad covers the same canonical unit face;
5. capture sprite/atlas identity, decoded UVs, layer, tint color and relevant material flags;
6. emit `POSITION_TEX_COLOR` vertices plus the already proven 32-bit index layout;
7. render only the supported SOLID material subset in the first dev3 visual proof, while logging CUTOUT/TRANSLUCENT/complex cases as explicit unsupported counts;
8. bind the live blocks atlas as `Sampler0` through public Blaze3D;
9. keep lighting/AO out of the vertex contract until P2.4.

The permanent P2.1 reference oracle is not modified and does not depend on the model manager.
