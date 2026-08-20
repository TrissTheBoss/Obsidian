# A-0034 - Phase 1 dev6 first-draw API inspection

**Date:** 2026-08-20  
**Status:** SUCCESS

## Objective

Determine whether Minecraft 26.2's public Blaze3D API is sufficient to create, execute, and deterministically verify the first Obsidian-owned graphics draw without taking over Minecraft's Vulkan device/swapchain or introducing a backend-specific native seam.

## Action

Added a temporary GitHub Actions inspection workflow on `phase1/first-draw`, resolved Minecraft 26.2 through Loom, and used `javap`/jar inspection on the exact client classes. The workflow was iterated to inspect graphics formats/topology, pipeline builder/compiler interfaces, textures/views, render passes, texture-to-buffer copy, vertex formats, Vulkan pipeline caching, and Vulkan render-pass pipeline binding. The temporary workflow was removed after the findings were captured.

Inspection runs included:

- `32372674296` - initial public graphics surface;
- `32372909781` - exact format/topology/shader interfaces;
- `32373119217` - Vulkan pipeline-cache and render-pass binding semantics.

## Intended effect

Find a public, ownership-safe path for an offscreen graphics validation consisting of custom shader compilation, graphics pipeline creation, vertex/index binding, one indexed draw, texture readback, and deterministic pixel verification.

## Actual effect

SUCCESS. The required path is available through public Blaze3D APIs; dev6 does not need native Vulkan access.

Exact relevant public APIs include:

- `GpuDevice.createTexture(...)`;
- `GpuDevice.createTextureView(...)`;
- `GpuDevice.createBuffer(...)`;
- `GpuDevice.precompilePipeline(RenderPipeline, ShaderSource)`;
- `CommandEncoder.createRenderPass(...)`;
- `CommandEncoder.copyTextureToBuffer(...)`;
- `RenderPass.setPipeline(...)`;
- `RenderPass.setVertexBuffer(...)`;
- `RenderPass.setIndexBuffer(...)`;
- `RenderPass.drawIndexed(...)`.

Exact type/usage findings:

- `GpuFormat.RGBA8_UNORM` exists and is suitable for a deterministic four-byte color target;
- `PrimitiveTopology.TRIANGLES` exists;
- `IndexType.SHORT` exists;
- texture usage flags include `RENDER_ATTACHMENT=8` and `COPY_SRC=2`;
- buffer usage flags include `COPY_DST=8`, `VERTEX=32`, `INDEX=64`, and `MAP_READ=1`;
- `ColorTargetState(Optional<BlendFunction>, GpuFormat, int)` supports an explicit RGBA8 target and `WRITE_ALL=15`;
- `DefaultVertexFormat.POSITION` is available and uses semantic name `Position`;
- `copyTextureToBuffer` validates COPY_SRC/COPY_DST usage and buffer capacity before delegating to the backend.

Pipeline-cache finding:

- `VulkanDevice` owns `Map<RenderPipeline,VulkanRenderPipeline> pipelineCache`;
- `VulkanDevice.precompilePipeline(pipeline, shaderSource)` uses `pipelineCache.computeIfAbsent(...)` with the supplied `ShaderSource`;
- `VulkanRenderPass.setPipeline(pipeline)` calls `VulkanDevice.getOrCompilePipeline(pipeline)`, which reads the same identity-keyed cache;
- therefore a pipeline precompiled with an Obsidian in-memory `ShaderSource` is the exact compiled pipeline later reused by `RenderPass.setPipeline`.

This permits dev6 to use tiny standalone shader strings with no uniforms/resource-reload dependency:

- vertex shader: `in vec3 Position; gl_Position = vec4(Position, 1.0);`
- fragment shader: constant RGBA output.

The indexed-draw argument order was confirmed through the Vulkan backend: `drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)` forwards directly to `vkCmdDrawIndexed`.

## Evidence

- branch `phase1/first-draw`;
- draft PR #8;
- temporary inspection workflow commits through `33ef8054f2d1a1de8bb270a6f26b7ff8505826e4`;
- API artifacts from runs `32372674296`, `32372909781`, and `32373119217`;
- temporary workflow removed before the clean dev6 compile head.

## Why it worked

Minecraft 26.2's Blaze3D abstraction now exposes enough explicit GPU concepts to own a private render target, graphics pipeline, render pass, vertex/index buffers, and readback copy while leaving device/swapchain ownership with Minecraft. The Vulkan backend's identity-keyed pipeline cache also makes direct custom `ShaderSource` precompilation safe for a one-shot validation before resource reload finishes.

## Lesson

Do not pierce into Vulkan merely because the renderer is Vulkan-only. Keep using the public abstraction until a concrete missing capability is proven. Backend bytecode inspection remains useful to validate performance/lifetime semantics such as cache reuse without making backend classes part of Obsidian's production API surface.

## Next action

Implement `0.1.0-phase1-dev6` as a one-shot three-pass graph: geometry upload -> offscreen indexed draw -> texture readback, all in one useful submission with embedded timestamps. Verify a center magenta pixel and an outside black pixel before distributing the runtime-test JAR.