# A-0035 - Phase 1 dev6 first offscreen indexed draw implementation

**Date:** 2026-08-20  
**Status:** PARTIAL - compile validated, runtime pending  
**Version:** `0.1.0-phase1-dev6`

## Objective

Implement the first actual Obsidian-owned graphics draw inside Minecraft 26.2's Vulkan-backed Blaze3D lifecycle while keeping normal Minecraft output unchanged.

## Action

Implemented `FirstDrawProbe` and extended the existing owned graph command stream.

Validation resources:

- private 16x16 `RGBA8_UNORM` texture with `RENDER_ATTACHMENT | COPY_SRC`;
- private texture view;
- 36-byte `COPY_DST | VERTEX` position buffer for three float3 vertices;
- 6-byte `COPY_DST | INDEX` buffer for three unsigned-short indices;
- 1024-byte `MAP_READ | COPY_DST` readback buffer;
- one precompiled graphics pipeline using `DefaultVertexFormat.POSITION`, `PrimitiveTopology.TRIANGLES`, culling disabled, explicit RGBA8 color target state, and tiny in-memory vertex/fragment shader strings.

Graph:

1. `first-draw-geometry-upload` - stage 36 vertex bytes and 6 index bytes through the validated bounded staging ring;
2. `first-draw-offscreen-render` - clear the private target black, bind the pipeline/VB/IB, issue `drawIndexed(3,1,0,0,0)` for one triangle;
3. `first-draw-readback` - copy the complete 16x16 target to the readback buffer.

All three passes, all timestamp writes, both staging copies, the graphics draw, texture readback, and the staging completion fence share one `CommandEncoder` / useful submission. Profiler-only submissions remain zero.

Deterministic output:

- triangle coordinates: (-0.75,-0.75), (0.75,-0.75), (0,0.75);
- fragment color: magenta RGBA = 255/0/255/255;
- clear color: black RGBA = 0/0/0/255;
- verify center pixel `(8,8)` is magenta;
- verify corner pixel `(0,0)` remains black.

The checks intentionally use pixels far from triangle edges to avoid rasterization edge-rule ambiguity.

Staging accounting expectation:

- payload bytes = 42 (36 vertex + 6 index);
- the staging arena aligns each allocation to 16 bytes, so after the first 36-byte reservation the 6-byte index reservation begins at virtual offset 48;
- expected high-water = 54 bytes;
- expected submitted/reclaimed payload = 42/42;
- expected backpressure = 0.

Lifecycle review also changed `FrameGraphCommandStream.submit()` so software profiler bookkeeping happens before the actual staging-owned queue submission. This removes a theoretical post-submit exception path that could make caller cleanup incorrectly assume no GPU work was in flight.

Completed dev5 `FrameGraphProbe` was removed. The temporary dev6 API inspection workflow was removed. Bootstrap/coordinator logs now describe the offscreen first-draw milestone, and the development version was bumped to `0.1.0-phase1-dev6`.

## Intended effect

Prove shader compilation, pipeline caching/binding, render-target creation, vertex/index uploads, indexed drawing, target transitions/readback, graph-integrated CPU/GPU timing, useful-submission ownership, deterministic pixel correctness, resource cleanup, and coexistence with vanilla rendering before terrain replacement begins.

## Actual effect

Java/API compile validation is successful. The clean first-draw implementation passed GitHub Actions build run `32373756653`. A follow-up lifecycle-safety change also passed build run `32373926561` at head `4f1c47fca49c5d60ed722031f614fa21710448fa`.

Graphical runtime behavior is not yet validated because the hosted environment cannot launch a real graphical Minecraft/Vulkan session.

## Evidence

- branch `phase1/first-draw`;
- draft PR #8;
- clean implementation compile run `32373756653`;
- lifecycle-reviewed compile run `32373926561`;
- lifecycle-reviewed head `4f1c47fca49c5d60ed722031f614fa21710448fa`;
- exact API evidence in `A-0034-dev6-first-draw-api-inspection.md`.

## Why compile validation succeeded

The implementation uses only exact Minecraft 26.2 symbols proven through Loom-resolved inspection: public texture/buffer creation, custom pipeline precompile, render-pass creation, vertex/index binding, indexed drawing, texture-to-buffer copy, and existing graph/timestamp/staging APIs.

## Risks remaining for runtime test

- driver-side custom shader compilation must succeed on the real AMD Vulkan backend;
- automatic resource/layout transitions across staging upload -> render pass -> texture readback must behave as Blaze3D's public abstraction promises;
- RGBA8 texture readback channel order and deterministic center/corner pixels must match the verified public format contract;
- the private target must remain entirely non-presented and must not disturb vanilla rendering.

## Next action

Record the durable public-Blaze3D first-draw boundary, update `CURRENT_STATE.md`, run final exact-head CI after documentation-only commits, inspect/download the canonical build artifact, and distribute the dev6 JAR for the real RX 6800 XT runtime test. Keep PR #8 draft and unmerged until that test passes.