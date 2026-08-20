# A-0060 - Phase 2 dev2 exact Minecraft 26.2 rendering API inspection

**Date:** 2026-08-21  
**Result:** SUCCESS  
**Milestone:** Phase 2 dev2 / P2.2 first drawable real section  
**Production branch:** `phase2/drawable-real-section`  
**Temporary inspection branch:** `phase2/drawable-real-section-inspect`

## Objective

Inspect the exact Loom-resolved Minecraft 26.2 rendering bytecode/API surface needed to draw one real Obsidian section in the live world target with correct camera/world placement while preserving Minecraft's Vulkan device/presentation ownership and the existing public-Blaze3D graphics policy.

The inspection deliberately avoids relying on renderer APIs remembered from older Minecraft versions.

## Method

A temporary GitHub Actions workflow on a temporary non-production branch resolved the exact client compile classpath, ran `javap -public` and `javap -c -p` over the relevant Minecraft/Blaze3D classes, and uploaded the inspection outputs as workflow artifacts.

Successful inspection runs included:

- `32422844956` - initial world/camera/rendering API surface;
- `32423056159` - expanded camera state, render-target, model/material and depth/pipeline surface;
- `32423231162` - exact dynamic-uniform, bind-group, render-pipeline and model architecture inspection;
- `32423589981` - final `GpuBufferSlice` / index-binding contract inspection.

A later optional resource-extraction iteration (`32423439681`) failed only because a shell `grep` found no matching packaged shader resource under the compile classpath while `set -euo pipefail` was active. The `javap` portion had already completed; no production source was involved. The needed shader/uniform contracts were already available from exact class bytecode, so this did not block the milestone.

The temporary inspection workflow/PR is not production code and must not be merged.

## Camera and world-render lifecycle findings

### `CameraRenderState`

Minecraft 26.2 exposes the exact world-render camera state through `net.minecraft.client.renderer.state.level.CameraRenderState`, including public fields:

- `Vec3 pos`;
- `Quaternionf orientation`;
- `Matrix4f projectionMatrix`;
- `Matrix4f viewRotationMatrix`;
- x/y rotation and culling/fog state.

`GameRenderer.gameRenderState().levelRenderState.cameraRenderState` provides the current state used for world rendering.

### `GameRenderer.renderLevel`

Exact bytecode showed the important ordering:

1. build the camera render state;
2. compute the world projection, including the active hurt/bob/portal projection effects;
3. call `RenderSystem.setProjectionMatrix(..., PERSPECTIVE)`;
4. call `LevelRenderer.render(...)` with the camera state, view rotation and world resources;
5. only after `LevelRenderer.render(...)` returns does `GameRenderer` switch to the HUD projection and clear depth.

Therefore the narrow safe P2.2 injection point is **immediately after the invocation of `LevelRenderer.render(...)` inside `GameRenderer.renderLevel`**. At that point vanilla world rendering has completed, the live world color/depth targets still exist, and the active `RenderSystem` projection is still the exact world projection used by Minecraft.

### `LevelRenderer.render`

Exact public signature:

`render(GraphicsResourceAllocator, DeltaTracker, boolean, CameraRenderState, Matrix4fc, GpuBufferSlice, Vector4f, boolean)`.

The renderer uses the camera state for section/view-area positioning and applies the supplied view-rotation matrix. For Obsidian's section-local geometry, the explicit camera-relative model-view transform should therefore be:

`viewRotation * translate(sectionOrigin - cameraPosition)`.

This preserves small section-local vertex coordinates while placing the section correctly in Minecraft world space.

## Dynamic matrix/uniform findings

Minecraft 26.2 exposes:

- `RenderSystem.getDynamicUniforms()`;
- `DynamicUniforms.writeTransform(Matrix4f)` -> `GpuBufferSlice`;
- `RenderSystem.getProjectionMatrixBuffer()` -> the current projection UBO slice.

`DynamicUniforms.Transform` is a record containing:

- model-view matrix;
- color modulator;
- model offset;
- texture matrix.

`BindGroupLayouts` exposes:

- `DYNAMIC_TRANSFORMS`;
- `PROJECTION`;
- `MATRICES_PROJECTION`.

Exact bytecode showed `MATRICES_PROJECTION` combines the uniforms named `DynamicTransforms` and `Projection` in the same contract used by Minecraft's position/color debug pipelines.

`RenderSystem.bindDefaultUniforms(RenderPass)` does not itself supply `DynamicTransforms`, so a custom dev2 pipeline must bind both the dynamic-transform slice and the current projection slice explicitly.

## Live render-target / public graphics findings

`GameRenderer.mainRenderTarget()` exposes the current main `RenderTarget`.

`RenderTarget` exposes:

- `getColorTextureView()`;
- `getDepthTextureView()`;
- width/height and depth ownership.

The public `CommandEncoder.createRenderPass(...)` API can open a pass over an existing color target plus depth target with empty clear optionals, allowing the dev2 comparison draw to overlay the already-rendered vanilla world without clearing it.

Public `RenderPass` exposes everything P2.2 needs:

- `setPipeline(...)`;
- `setUniform(name, GpuBufferSlice)`;
- `setVertexBuffer(binding, GpuBufferSlice)`;
- `setIndexBuffer(GpuBuffer, IndexType)`;
- `drawIndexed(...)`;
- `drawIndexedIndirect(GpuBufferSlice, count)`.

No native Vulkan graphics takeover is required.

## Depth/pipeline findings

Minecraft 26.2 uses reversed depth for the relevant world/debug draw paths. `CompareOp.GREATER_THAN_OR_EQUAL` exists, and the built-in filled-debug pipeline uses:

- depth test `GREATER_THAN_OR_EQUAL`;
- no depth write;
- no culling.

That is the correct safe comparison policy for a P2.2 overlay drawn after vanilla world rendering: Obsidian geometry can be depth-tested against vanilla without destroying the existing depth buffer.

`RenderPipeline.Builder` publicly supports:

- custom vertex/fragment shader identifiers;
- `withBindGroupLayout(...)`;
- `withCull(...)`;
- explicit color target state;
- explicit depth/stencil state;
- explicit vertex binding;
- primitive topology.

Phase 1 A-0034 already proved that `GpuDevice.precompilePipeline(pipeline, ShaderSource)` safely precompiles a custom in-memory shader into the identity-keyed pipeline cache later consumed by `RenderPass.setPipeline`.

Therefore dev2 can remain on public Blaze3D graphics with a deliberately simple position-only debug shader using the exact `MATRICES_PROJECTION` UBO contract.

## Buffer/index findings

`DefaultVertexFormat.POSITION` uses `RGB32_FLOAT`, exactly matching dev2's three-float/12-byte vertex records.

`IndexType.INT` exists and is required for correctness because the worst-case reference stream can contain 24,576 faces / 98,304 drawable vertices, exceeding 16-bit index range.

`GpuBufferSlice` is a public record exposing:

- `buffer()`;
- `offset()`;
- `length()`.

Therefore a generation-safe index allocation returned by `DeviceGeometryArena.slice(handle)` can be bound through its real backing buffer with `IndexType.INT` without widening or weakening the arena abstraction. The indirect command can use `firstIndex = slice.offset() / 4`.

## Minecraft 26.2 block/model/material findings

The model architecture differs substantially from older remembered APIs.

Relevant exact APIs include:

- `ModelManager.getBlockStateModelSet()` / `getBlockModelSet()`;
- `BlockStateModelSet.get(BlockState)` -> `BlockStateModel`;
- `BlockStateModel.collectParts(RandomSource, List<BlockStateModelPart>)`;
- `BlockStateModelPart.getQuads(Direction)`;
- `BlockStateModelPart.useAmbientOcclusion()`;
- `BakedQuad` carrying four positions, packed UV data, face direction and material information;
- baked material/sprite objects exposing the actual sprite identity/UV domain.

These findings confirm the roadmap separation rather than collapsing P2.3/P2.4 into dev2:

- P2.2 may intentionally use an obvious debug material/color while proving geometry and transforms;
- P2.3 should implement correct sprite/material/UV/tint/layer identity using this exact model architecture;
- P2.4 should implement light/AO semantics independently.

Dev2 must not silently approximate unsupported complex model states as cubes.

## Implementation consequence

The narrow P2.2 implementation is now grounded:

1. capture immutable real section using the proven P2.1 path;
2. build/validate the permanent canonical reference oracle twice;
3. build a separate deterministic section-local position/index mesh twice;
4. upload vertex and index bytes through bounded staging to generation-safe device-arena allocations;
5. upload one indexed-indirect command;
6. inject a short-lived comparison overlay after `LevelRenderer.render(...)`;
7. compute model-view from the exact `CameraRenderState` and section origin;
8. bind `DynamicTransforms` + current `Projection` using `MATRICES_PROJECTION`;
9. draw bright debug-colored geometry through public Blaze3D with reversed-depth testing and no depth write;
10. keep vanilla terrain active and retire all dev2 allocations behind real GPU completion.

No new durable architecture decision is required: this follows D-0023, D-0024 and the existing Phase 2 roadmap.

## Next action

Implement the grounded dev2 runtime path on `phase2/drawable-real-section`, remove/close the temporary inspection workstream, run exact CI, then package `0.2.0-phase2-dev2` for reference RX 6800 XT runtime validation before P2.2 can be marked COMPLETE.
