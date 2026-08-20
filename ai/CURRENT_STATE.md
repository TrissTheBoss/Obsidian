# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev5, merge commit `d3c0a465cb10a648f5f1c241890b3a6eacf52b36`
- Active development branch: `phase1/first-draw`
- Active draft PR: #8, `Phase 1: first Obsidian-owned graphics draw`
- Current development version: `0.1.0-phase1-dev6`
- Dev6 status: **compile validated; runtime pending**

## Reference runtime

Validated reference machine:

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan driver observed: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`

## Completed milestones

### Phase 0 - COMPLETE

Validated Fabric bootstrap, Vulkan selection, Minecraft `GpuDevice` attachment, capability reporting, world entry, and clean shutdown.

### Phase 1 dev1 - VALIDATED / merged PR #3

Validated the `Minecraft.renderFrame(boolean)` lifecycle seam, fixed CPU frame timing ring, controlled GPU submission, timestamp result retrieval, world entry, and shutdown.

### Phase 1 dev2 - VALIDATED / merged PR #4

Validated three-slot frame contexts, real `GpuFence` completion tracking, zero-timeout steady-state polling, deferred destruction, and clean shutdown accounting.

### Phase 1 dev3 - VALIDATED / merged PR #5

Validated bounded persistent staging, batched copies, explicit backpressure, deterministic readback, and completion-gated ring reclamation.

### Phase 1 dev4 - VALIDATED / merged PR #6

Validated a device-preferred geometry arena with generation-safe handles, bounded allocation failure, multi-frame fence-gated reuse, stale-handle rejection, deterministic readback, and complete free-span coalescing.

### Phase 1 dev5 - VALIDATED / merged PR #7

Validated fixed pass orchestration and integrated GPU timestamp profiling:

- two-pass dependency graph;
- one useful owned submission;
- zero profiler-only submissions;
- nonblocking timestamp result retrieval;
- deterministic dependent GPU copy/readback;
- world entry and clean shutdown;
- exact runtime evidence in `ai/attempts/A-0033-dev5-runtime-success.md`.

Dev5 was squash-merged as `d3c0a465cb10a648f5f1c241890b3a6eacf52b36` with `[no-release]`.

## Phase 1 dev6 - ACTIVE; compile validated, runtime pending

Goal: prove the first actual Obsidian-owned graphics draw without changing the presented Minecraft image.

### Exact Minecraft 26.2 graphics findings

Exact Loom-resolved public/API and Vulkan bytecode inspection confirmed that dev6 can stay inside Blaze3D; no native Vulkan seam is justified.

Public path:

- `GpuDevice.createTexture(...)` and `createTextureView(...)`;
- `GpuDevice.createBuffer(...)`;
- `GpuDevice.precompilePipeline(RenderPipeline, ShaderSource)`;
- `CommandEncoder.createRenderPass(...)`;
- `CommandEncoder.copyTextureToBuffer(...)`;
- `RenderPass.setPipeline(...)`;
- `RenderPass.setVertexBuffer(...)`;
- `RenderPass.setIndexBuffer(...)`;
- `RenderPass.drawIndexed(...)`.

Relevant exact constants/types:

- `GpuFormat.RGBA8_UNORM`;
- `PrimitiveTopology.TRIANGLES`;
- `IndexType.SHORT`;
- texture `RENDER_ATTACHMENT | COPY_SRC`;
- vertex/index buffers `COPY_DST | VERTEX` / `COPY_DST | INDEX`;
- readback buffer `MAP_READ | COPY_DST`;
- `ColorTargetState(..., RGBA8_UNORM, WRITE_ALL)`;
- `DefaultVertexFormat.POSITION` with shader semantic `Position`.

Vulkan cache semantics were also inspected:

- `VulkanDevice.precompilePipeline(pipeline, customShaderSource)` stores the compiled pipeline in the same identity-keyed `pipelineCache` used by `VulkanRenderPass.setPipeline` through `getOrCompilePipeline`;
- therefore dev6 can precompile tiny Obsidian-owned shader strings during initialization and bind that exact cached pipeline later without waiting for resource reload;
- indexed draw parameters forward as Vulkan `indexCount, instanceCount, firstIndex, vertexOffset, firstInstance`.

Temporary API inspection workflow has been removed. Evidence: `ai/attempts/A-0034-dev6-first-draw-api-inspection.md`.

### Implemented first draw

`FirstDrawProbe` owns a one-shot private graphics validation:

- 16x16 `RGBA8_UNORM` offscreen target, never presented;
- black clear color `(0,0,0,1)`;
- three POSITION vertices forming a large centered triangle;
- three 16-bit indices;
- tiny custom vertex shader with no uniforms;
- tiny custom fragment shader outputting magenta `(1,0,1,1)`;
- explicitly precompiled/cached graphics pipeline;
- 1024-byte MAP_READ readback buffer.

Three graph passes execute in one command stream:

1. `first-draw-geometry-upload`;
2. `first-draw-offscreen-render`;
3. `first-draw-readback`.

The same useful submission contains:

- 36 vertex upload bytes;
- 6 index upload bytes;
- all pass timestamp writes;
- render-target clear;
- pipeline/VB/IB binding;
- one `drawIndexed(3,1,0,0,0)` call;
- texture-to-buffer readback;
- the staging completion fence.

Profiler-only submissions remain zero.

Deterministic validation checks pixels far from triangle edges:

- center pixel `(8,8)` must be RGBA `255/0/255/255`;
- corner `(0,0)` must remain clear RGBA `0/0/0/255`.

### Expected dev6 accounting

Graph/draw:

- graph passes = 3;
- executed mask = `7`;
- useful submissions = 1;
- profiler-only submissions = 0;
- draw calls = 1;
- triangles = 1;
- pipeline valid = true;
- pixels verified = 2.

Staging:

- payload submitted/reclaimed = `42/42` bytes;
- vertex reservation = 36 bytes at offset 0;
- index reservation is aligned to virtual offset 48 and occupies 6 bytes;
- expected staging high-water = 54 bytes;
- expected backpressure = 0;
- pending upload batches = 0 after completion.

Device geometry arena remains initialized but unused by this one-shot draw probe:

- used/high-water/alloc/failure/retire/reclaim/stale counters = 0;
- free spans = 1;
- largest free span = 524288;
- fragmentation = 0.

CPU/GPU per-pass timing values are run-dependent; successful resolution matters, not exact numbers. `unavailableQueryPolls` may be zero or positive.

### Compile validation

- clean first-draw implementation passed GitHub Actions run `32373756653`;
- lifecycle review identified and fixed a theoretical post-submit error boundary in `FrameGraphCommandStream.submit()`: fallible profiler software bookkeeping now occurs before actual queue submission, preventing a post-submit bookkeeping exception from sending the caller through cleanup that assumes nothing is in flight;
- lifecycle-reviewed head `4f1c47fca49c5d60ed722031f614fa21710448fa` passed GitHub Actions run `32373926561` with build and artifact upload successful;
- later continuity-only commits require one final exact-head CI before distribution.

Implementation evidence: `ai/attempts/A-0035-dev6-first-draw-implementation.md`.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> completion-gated lifetime -> bounded staging -> device-preferred arena -> FixedFrameGraph -> owned command stream -> embedded timestamps -> public Blaze3D graphics pipeline/render pass -> private offscreen draw/readback`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own or modify the presented framebuffer;
- own terrain rendering;
- upload/draw actual chunk meshes;
- perform routine device-wide waits;
- poll allocating timestamp wrappers every frame;
- require native Vulkan access for its current graphics path.

## Immediate next action

1. Final-CI the exact documented dev6 head.
2. Download and inspect the exact GitHub Actions artifact.
3. Distribute `0.1.0-phase1-dev6` for the reference RX 6800 XT Vulkan runtime test.
4. Runtime success requires pipeline compilation, one indexed offscreen draw, center/corner pixel verification, graph/timestamp completion, staging 42/42 with high-water 54, world entry, clean shutdown, and exit code 0.
5. Keep PR #8 draft/unmerged until the runtime test passes.

## Relevant durable decisions

- D-0014: profiling must not create routine extra GPU submissions.
- D-0015: preserve Minecraft Vulkan device/swapchain ownership until evidence demands deeper access.
- D-0016: reclamation is real-completion-gated, never frame-count-gated.
- D-0017/D-0018: staging is bounded/backpressured and avoids Mojang's blocking ring policy.
- D-0019/D-0020: geometry is device-preferred and allocation identity is generation-safe.
- D-0021: timestamps live inside useful owned command streams.
- D-0022: timestamp results are bounded/sampled because the public wrapper allocates.
- Dev6 evidence currently supports a follow-up durable decision that the first graphics path remains on public Blaze3D rather than adding native Vulkan access.