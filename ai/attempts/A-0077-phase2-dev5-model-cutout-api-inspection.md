# A-0077 - Phase 2 dev5 exact Minecraft 26.2 model/cutout API inspection

Status: **SUCCESS / ARCHITECTURE GROUNDED / IMPLEMENTATION MAY PROCEED**

Date: 2026-08-21
Production branch: `phase2/broader-opaque-cutout-semantics`
Temporary inspection branch: `phase2/broader-opaque-cutout-semantics-inspect`
Temporary PR: #23, inspection-only; must close without merge.

Hosted exact-dependency evidence:

- broad run `32433264523` - SUCCESS - artifact `9429858820`;
- targeted run `32433356082` - SUCCESS - artifact `9429876185`.

The inspection used the exact Loom-resolved Minecraft 26.2 client compile classpath under Java 25 / Gradle 9.5.1 and `javap -public/-c -p` bytecode inspection. No older-version API assumptions are used below.

## 1. Vanilla section/model architecture

`SectionCompiler` constructs:

- `BlockModelLighter.enableCaching()`;
- `new ModelBlockRenderer(ambientOcclusion, true, blockColors)` - second flag enables directional face culling;
- one `BlockQuadOutput` that routes each emitted quad to `quad.materialInfo().layer()`;
- a second output used only by the leaves force-opaque option, which routes emitted leaf quads to SOLID regardless of the baked layer.

For each MODEL block, `SectionCompiler` calls `ModelBlockRenderer.tesselateBlock(...)` with section-relative block coordinates, the live `RenderSectionRegion`, world position, exact model from `BlockStateModelSet`, and `BlockState.getSeed(worldPos)`.

This is the strongest useful P2.5 seam: `ModelBlockRenderer.tesselateBlock` already performs exact model selection, block offset, directional/general quad iteration, face culling, light/AO preparation and tint, then emits a finished `BakedQuad + QuadInstance` through `BlockQuadOutput.put(float x,float y,float z,BakedQuad,QuadInstance)`.

## 2. Model selection and general quads

`BlockStateModel.collectParts(RandomSource,List<BlockStateModelPart>)` remains the exact selected-part entry point.

`BlockStateModelPart` exposes:

- `getQuads(Direction)`;
- `useAmbientOcclusion()`;
- particle/material flags.

There is no separate general-quad API: vanilla calls `part.getQuads(null)` after directional buckets.

Exact `ModelBlockRenderer` behavior for each selected part:

1. Iterate all six directions.
2. Obtain `part.getQuads(direction)`.
3. If nonempty, compute `Block.shouldRenderFace(sourceState, neighborState, direction)` once for that direction (cached across later selected parts).
4. If visible, process every quad in the directional list.
5. After directional buckets, iterate every quad from `part.getQuads(null)` with **no directional face-culling test**.

Therefore P2.5 must allow:

- multiple selected model parts;
- multiple quads per directional bucket;
- arbitrary numbers of general/unculled quads;
- per-quad material/layer identity.

The P2.3 rule “exactly one directional quad and no general quads” is intentionally too narrow for P2.5 and must not be extended by approximation.

## 3. Exact directional culling semantics

`ModelBlockRenderer.shouldRenderFace(...)` delegates to public `Block.shouldRenderFace(sourceState, neighborState, direction)`.

Minecraft 26.2 `Block.shouldRenderFace` does, in order:

- read the neighbor's opposite-face occlusion shape;
- reject immediately if the neighbor face shape is the full block shape;
- reject if `sourceState.skipRendering(neighborState,direction)`;
- accept immediately if neighbor face shape is empty;
- read source face occlusion shape;
- accept immediately if source face shape is empty;
- otherwise test `Shapes.joinIsNotEmpty(sourceFace, neighborFace, BooleanOp.ONLY_FIRST)` with a small thread-local shape-pair cache.

This is shape-based culling, not the P2.1 full-cube air test.

## 4. Exact baked-quad geometry/material contract

`BakedQuad` is a four-vertex record with:

- `position(int)` / position0..3;
- `packedUV(int)` / packedUV0..3;
- `direction()`;
- `materialInfo()`.

`BakedQuad.MaterialInfo` supplies:

- `sprite()`;
- `layer()`;
- `itemRenderType()`;
- `tintIndex()` / `isTinted()`;
- `shade()`;
- `lightEmission()`;
- `flags()`.

P2.5 must freeze exact four-vertex geometry and may not coerce it to canonical cube corners.

## 5. Exact arbitrary-quad light/AO behavior

`ModelBlockRenderer.tesselateBlock` first applies `BlockState.getOffset(worldPos)` to the emitted block-local base position.

AO selection remains exactly:

- renderer AO option enabled;
- `state.getLightEmission() == 0`;
- first selected model part `useAmbientOcclusion()`.

For AO, every directional and every general quad is passed to `BlockModelLighter.prepareQuadAmbientOcclusion(...)`.

For flat lighting:

- directional buckets obtain neighbor-face light once and pass it to `prepareQuadFlat(...)` for each quad;
- general quads pass `-1`, allowing `BlockModelLighter` to choose the correct per-quad flat source.

`BlockModelLighter.prepareQuadShape(...)` computes the exact min/max X/Y/Z bounds from all four `BakedQuad.position(i)` values. Those bounds determine `facePartial` and `faceCubic`; non-full quads therefore receive vanilla's non-cubic AO weighting rather than cube-corner lighting.

The lighter's neighbor reads remain source/face-adjacent/corner/diagonal positions with at most one block of displacement per axis. Directional culling reads only the direct neighbor. Thus the existing 18^3 one-block halo still contains every block-state sample required by this exact model/light/culling path for a section block, even though P2.5 no longer assumes cube geometry.

The renderer-side correctness capture may still call vanilla against the live render-thread world; after `BlockQuadOutput` results are frozen, pure mesh construction must perform zero live world/model/light/resource reads.

## 6. Tint/output seam

After the lighter prepares `QuadInstance`, `ModelBlockRenderer.putQuadWithTint(...)` resolves the baked quad's tint index through `BlockColors`, calls `QuadInstance.multiplyColor(tint)`, and only then invokes `BlockQuadOutput.put(...)`.

Therefore a custom P2.5 `BlockQuadOutput` sees the exact final vanilla per-vertex AO/shade/tint color state and can freeze it without reproducing tint-cache behavior.

`QuadInstance` methods already used successfully by P2.4 remain the required primitive extraction seam:

- `getColor(vertex)`;
- `getLightCoordsWithEmission(vertex, materialInfo.lightEmission())`.

## 7. Exact render-layer contract

`ChunkSectionLayer` in 26.2 has exactly:

- SOLID;
- CUTOUT;
- TRANSLUCENT.

`ChunkSectionLayer.byTransparency` maps:

- translucent -> TRANSLUCENT;
- transparent/cutout -> CUTOUT;
- otherwise -> SOLID.

Vanilla `SectionCompiler` creates one `BufferBuilder` per layer actually encountered and routes each emitted baked quad by `quad.materialInfo().layer()` (except the optional leaves force-opaque path).

SOLID and CUTOUT are both non-translucent and both use a 4 MiB section-layer buffer size in vanilla.

## 8. Exact public CUTOUT graphics path

`RenderPipelines.SOLID_BLOCK` = the public BLOCK shader/bind-layout snippet with no alpha cutout define.

`RenderPipelines.CUTOUT_BLOCK` = the same BLOCK snippet plus exact shader define:

`ALPHA_CUTOUT = 0.5f`.

Likewise terrain uses `SOLID_TERRAIN` and `CUTOUT_TERRAIN`; `ChunkSectionLayer.CUTOUT.pipeline()` is `CUTOUT_TERRAIN`.

The shared generic block snippet uses:

- `DefaultVertexFormat.BLOCK`;
- `Sampler0 + Sampler2` bind group;
- Globals/fog/matrix bindings;
- depth testing;
- QUADS before mesh index generation.

CUTOUT adds alpha discard; it does **not** add translucent blending. This allows the P2.4 public `BLOCK`/blocks-atlas/lightmap proof to extend cleanly to a second public cutout pipeline by preserving the exact `ALPHA_CUTOUT=0.5` define.

For the validation overlay, P2.5 may continue using the already human-validated comparison depth state while copying CUTOUT_BLOCK shaders/bind layouts/color state and the exact cutout define.

## 9. Leaves special case

`SectionCompiler` can force leaves into SOLID depending its `cutoutLeaves` configuration via `ModelBlockRenderer.forceOpaque(...)`. This layer override occurs outside `ModelBlockRenderer.tesselateBlock` in the chosen `BlockQuadOutput` callback.

The first generalized P2.5 proof should therefore **exclude/count leaves explicitly** rather than guess the live leaves-mode option. Leaves can be added once that option is grounded and carried explicitly.

## 10. Chosen P2.5 implementation boundary

Proceed with a new immutable generalized baked-quad capture driven by exact vanilla `ModelBlockRenderer.tesselateBlock(...)`.

For each interior snapshot state, first-proof eligibility is:

- `RenderShape.MODEL`;
- no fluid (translucent/fluid remains Phase 6);
- no block entity;
- not leaves in the first proof;
- exact model exists;
- every emitted quad for the accepted block is blocks-atlas SOLID or CUTOUT;
- any block emitting TRANSLUCENT or otherwise unsupported quad material is rejected as a whole rather than partially rendered.

The custom `BlockQuadOutput` freezes, per emitted quad:

- exact four section-local positions including vanilla block offset;
- exact four UVs;
- exact four final AO/shade/tint colors;
- exact four packed lights including baked emission;
- `BakedQuad.direction()`;
- exact layer/material/sprite/tint/shade/emission/flags identity;
- source block/state identity for diagnostics.

A pure mesh then groups accepted quads deterministically into contiguous SOLID and CUTOUT ranges using `DefaultVertexFormat.BLOCK`, with separate public indexed-indirect draws/pipelines per layer.

This architecture:

- preserves `ReferenceFaceMesh` unchanged as the permanent cube oracle;
- naturally supports general quads, multiple quads, non-full shapes, block offsets and crossed-model geometry when their emitted materials are SOLID/CUTOUT;
- reuses vanilla itself as the P2.5 correctness oracle during render-thread capture;
- leaves later Phase 3 free to optimize against a deterministic immutable generalized-quad reference rather than against Minecraft objects.

## 11. Boundaries retained

- TRANSLUCENT/fluid geometry: Phase 6.
- Leaves force-opaque option: explicitly unsupported in first proof.
- Block entities: later renderer domain.
- P2.6 owns event-driven invalidation/rebuild lifecycle.
- P2.7 owns persistent multi-section scene ownership.
- Phase 3 owns production binary/bitmask greedy meshing.
- No broader native graphics seam is justified; `nativeGraphicsSeam=false` remains required.

## Result

The exact 26.2 inspection supports a generalized dev5 proof without reimplementing culling/AO/tint logic and without weakening prior oracles. Implementation may proceed on the production dev5 branch.

This attempt is immutable once committed.
