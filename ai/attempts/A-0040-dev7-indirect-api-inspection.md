# A-0040 - Phase 1 dev7 indexed-indirect and fence API inspection

**Date:** 2026-08-20  
**Status:** SUCCESS

## Objective

Determine whether Minecraft 26.2's public Blaze3D abstraction exposes true indexed-indirect drawing and whether arena lifetime can share the same useful submission completion point without adding another GPU submission or native synchronization object.

## Action

Used a temporary GitHub Actions workflow against the exact Fabric Loom-resolved Minecraft 26.2 client JAR. Inspected public signatures and bytecode for `RenderPass`, `GpuBuffer`, `DeviceFeatures`, `VulkanRenderPass`, `VulkanCommandEncoder`, its anonymous `GpuFence` implementation, and related classes. The temporary workflow was removed after the findings were captured.

## Result

SUCCESS.

### Indexed indirect drawing

Public `RenderPass` exposes `drawIndexedIndirect(GpuBufferSlice, int drawCount)`.

Exact validation in Minecraft 26.2 requires:

- `DeviceFeatures.drawIndirect()`;
- when `drawCount > 1`, `DeviceFeatures.multiDrawIndirect()`;
- command storage with `GpuBuffer.USAGE_INDIRECT_PARAMETERS` (`512`);
- command slice length of at least `drawCount * VkDrawIndexedIndirectCommand.SIZEOF`;
- a command-buffer offset aligned to 4 bytes.

The Vulkan backend forwards this to `vkCmdDrawIndexedIndirect` with the native `VkDrawIndexedIndirectCommand.SIZEOF` stride, which is 20 bytes. The command layout is the Vulkan five-field indexed-indirect structure: `indexCount`, `instanceCount`, `firstIndex`, signed `vertexOffset`, `firstInstance`.

The reference RX 6800 XT runtime has already reported both `drawIndirect=true` and `multiDrawIndirect=true`.

### Fence/timeline semantics

Exact `VulkanCommandEncoder$1` bytecode shows `createFence()` returns a Java object with:

- a captured `submitIndex = currentSubmitIndex`;
- a cached `completed` boolean;
- `awaitCompletion(timeout)` delegated to `VulkanCommandEncoder.awaitSubmitCompletion(submitIndex, timeout)`;
- `close()` only setting `completed=true`.

It does **not** allocate or destroy a native `VkFence`.

`VulkanCommandEncoder.submit()` signals its timeline submit semaphore at `currentSubmitIndex`, submits, then increments `currentSubmitIndex`.

Therefore multiple `GpuFence` handles created before one encoder submission are lightweight independent Java views of the same timeline completion point. Staging and geometry-arena retirement may each own a handle for one useful submission without creating another queue submission or a duplicate native synchronization object.

## Intended effect

Preserve D-0023's public-Blaze3D boundary while establishing the exact indirect-command and completion semantics needed for GPU-driven terrain foundations.

## Actual effect

No native Vulkan seam is needed for Phase 1 dev7. The public path provides the true Vulkan indexed-indirect command we need, and the existing timeline-based fence abstraction can safely support independent subsystem lifetime ownership for the same submission.

## Evidence

- temporary workflow `Phase 1 Indirect API Inspect`;
- workflow run `32375663499` and follow-up fence inspection run `32376322191`;
- artifacts `9408926278` and `9409174115`;
- exact Loom-resolved Minecraft 26.2 bytecode;
- temporary workflow removed from the clean branch.

## Next action

Implement a bounded device-preferred indirect-command buffer, allocate actual vertex/index spans from `DeviceGeometryArena`, render two triangles through one public `drawIndexedIndirect(..., 2)` call, use an independent timeline handle for arena retirement, and verify deterministic offscreen pixels.