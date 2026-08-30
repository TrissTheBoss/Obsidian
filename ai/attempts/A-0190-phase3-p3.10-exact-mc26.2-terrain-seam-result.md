# A-0190 — Phase 3 P3.10 exact Minecraft 26.2 terrain seam result

Date: 2026-08-30
Status: **SUCCESS / A-0189 STAGE 0 CLOSED**
Parent contract: A-0189
Exact Minecraft version: 26.2

## Objective

Identify the exact Minecraft 26.2 opaque/cutout terrain submission seam from the resolved client bytecode before changing renderer source. The result must establish whether Obsidian can suppress exact vanilla terrain work and issue its already-proven custom full-section output at the same semantic stage while retaining Minecraft/Blaze3D graphics ownership.

## Probe history

The exact client jar resolved by Fabric Loom was:

`/home/runner/.gradle/caches/fabric-loom/26.2/minecraft-client-only.jar`

- Probe commit `279c2d792203d0e8484f9912b850b15ff0df232c`, run `33333711384`: failed only because the helper assumed the older class package/name `net.minecraft.client.renderer.SectionRenderDispatcher`. No seam conclusion was taken from this failed helper.
- Corrected discovery commit `995774e681fb7c60ad7040c43e6cd0245b77bbf6`, run `33333786992`: SUCCESS and identified the Minecraft 26.2 `renderer.chunk` classes.
- Targeted exact-container commit `12d2a20527ad8e0f6eb4f85e2beebbff9f1f00f7`, run `33333881919`, job `99317207833`: SUCCESS. Probe artifact `9738436543`, digest `sha256:4cc2ee0f115c88f9d15c9445f469f280d61456efe1466af27111e63076868799`.

## Exact class shape

Minecraft 26.2 terrain submission uses:

- `net.minecraft.client.renderer.chunk.ChunkSectionLayer`
- `ChunkSectionLayerGroup`
- `ChunkSectionsToRender`
- `SectionMesh`
- `SectionMesh$SectionDraw`
- `SectionRenderDispatcher`
- `SectionRenderDispatcher$RenderSection`.

`ChunkSectionLayer.SOLID` uses `RenderPipelines.SOLID_TERRAIN`.
`ChunkSectionLayer.CUTOUT` uses `RenderPipelines.CUTOUT_TERRAIN`.
`ChunkSectionLayer.TRANSLUCENT` uses `RenderPipelines.TRANSLUCENT_TERRAIN`.

`ChunkSectionLayerGroup.OPAQUE` is exactly `{SOLID, CUTOUT}`. `TRANSLUCENT` is separate.

## Exact `LevelRenderer.prepareChunkRenders(Matrix4fc)` seam

The method iterates `visibleSections` as `SectionRenderDispatcher$RenderSection` records. For each exact render section it:

1. obtains the `SectionMesh`;
2. obtains the section render origin;
3. loops `ChunkSectionLayer.values()`;
4. calls `SectionMesh.getSectionDraw(layer)`;
5. obtains the layer's buffer slice with `SectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer)`;
6. creates the section's dynamic `ChunkSectionInfo` using a camera/model matrix translated by the exact render origin;
7. builds the corresponding `RenderPass.Draw` and inserts it into the mutable per-layer grouped draw structure.

At the `getSectionDraw(layer)` point, exact `RenderSection`, exact `ChunkSectionLayer`, section node/origin, current section mesh and final vanilla draw identity are simultaneously available. Returning/observing no section draw at this point causes no vanilla draw to be inserted for that exact section/layer while leaving every other section/layer unchanged.

This is the narrow suppression seam for P3.10.

## Exact `ChunkSectionsToRender` shape

`ChunkSectionsToRender` is a public final record containing:

- `GpuTextureView textureView`;
- `EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroupsPerLayer`;
- `int maxIndicesRequired`;
- `GpuBufferSlice[] chunkSectionInfos`.

The public `drawGroupsPerLayer()` accessor returns that mutable per-layer map.

`renderGroup(group, sampler)`:

- creates a Blaze3D `RenderPass` on `group.outputTarget()`;
- binds default uniforms;
- binds `Sampler0` to the exact chunk/blocks atlas `textureView` and supplied sampler;
- binds `Sampler2` to the live Minecraft lightmap with the linear clamp sampler;
- loops each layer in the requested group;
- sets that layer's pipeline;
- calls `RenderPass.drawMultipleIndexed(...)` for each grouped vanilla draw list;
- closes the render pass.

The active `RenderPass` therefore remains available immediately after all surviving vanilla OPAQUE draws and before the pass closes. Obsidian's custom passthrough/repeat-aware pipelines can be encoded into this same public Blaze3D pass, avoiding any native Vulkan graphics takeover or separate ordering guess.

## Exact main-pass ordering

`LevelRenderer.lambda$addMainPass$0(...)` performs:

1. profiler push `solidTerrain`;
2. `chunkSectionsToRender.renderGroup(ChunkSectionLayerGroup.OPAQUE, chunkLayerSampler)`;
3. only afterward proceeds to solid features / later world consumers;
4. translucent features and `renderGroup(TRANSLUCENT, ...)` occur later.

Therefore P3.10 SOLID/CUTOUT replacements must be encoded inside the OPAQUE `renderGroup` semantic block, before its `RenderPass` closes. The existing Obsidian `GameRendererMixin` post-`LevelRenderer.render` comparison hook remains too late for production replacement.

## Obsidian compatibility result

The existing `WorkerBackedSectionLifecycleProbe` already owns:

- exact full-section passthrough + repeat-aware merged geometry;
- four proven draw classes: passthrough/merged x SOLID/CUTOUT;
- custom merged 60-byte vertex format and repeat-aware shaders;
- public Blaze3D pipelines derived from `SOLID_BLOCK` / `CUTOUT_BLOCK` semantics;
- exact live blocks-atlas + lightmap bindings;
- indexed-indirect GPU buffers in completion-gated arena ownership.

Because merged geometry uses Obsidian's custom repeat-aware pipeline, it must **not** be inserted as a vanilla `SOLID_TERRAIN` / `CUTOUT_TERRAIN` draw. Instead:

- suppress the exact vanilla section/layer draw during `prepareChunkRenders` only after Obsidian has reserved a valid replacement claim;
- immediately before the OPAQUE `RenderPass.close()`, encode the claimed Obsidian passthrough and merged commands into that same active render pass using Obsidian's proven pipelines;
- leave unclaimed/ambiguous/unsupported sections fully vanilla.

This preserves the correct opaque/cutout depth stage and removes duplicate terrain for claimed units.

## Stage 0 decision

**PUBLIC BLAZE3D SEAM AUTHORIZED.**

A-0189 Stage 0 is closed successfully. No native Vulkan graphics seam expansion is required. Dev24 source implementation is authorized with the exact suppression + same-OPAQUE-pass architecture above.

## Dev24 safety constraints derived from the seam

- A claim must be generation/resource-epoch/differential exact and LIVE before the vanilla draw is omitted.
- The claim plan must be fixed-capacity and render-thread owned.
- Suppression and replacement are one-for-one at `(sectionX, sectionY, sectionZ, SOLID|CUTOUT)` identity.
- Custom replacement commands execute in the same OPAQUE render pass before close.
- Existing post-world comparison draws must be disabled for records participating in production replacement, otherwise the frame would contain a duplicate copy.
- If a valid claim cannot be created, the vanilla draw must remain untouched.
