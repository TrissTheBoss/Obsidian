# A-0044 - Phase 1 dev8 compute API inspection

**Date:** 2026-08-20  
**Status:** SUCCESS - exact capability boundary identified

## Objective

Determine whether Minecraft 26.2 public Blaze3D can generate indexed-indirect command data on the GPU with compute while preserving the existing Minecraft-owned Vulkan device/queue/submission model.

## Action

Resolved the exact Minecraft 26.2 client through Loom in GitHub Actions and inspected public signatures plus Vulkan backend bytecode. Temporary workflow/artifacts were used only for inspection and removed before the testable dev8 branch.

Inspection runs:

- `32407719004`, artifact `9420931376`;
- extended run `32408066760`, artifact `9421056164`.

## Result

SUCCESS: the missing public capability is concrete and narrow.

Public Blaze3D 26.2 has no compute pass/pipeline/storage-buffer API:

- no `ComputePass`;
- no `ComputePipeline` / `CompiledComputePipeline`;
- `ShaderType` contains only VERTEX and FRAGMENT;
- public `GpuBuffer` usage flags contain no STORAGE_BUFFER bit;
- `CommandEncoder` exposes no dispatch method.

The Vulkan backend exposes enough targeted interop to avoid a second device/queue/swapchain:

- `VulkanDevice.vkDevice()` and `vma()` are public;
- `VulkanCommandEncoder.allocateAndBeginTransientCommandBuffer()` and `execute(VkCommandBuffer)` are public;
- `VulkanGpuBuffer` is public, has a public constructor around an existing VkBuffer and can still be consumed by public `RenderPass.drawIndexedIndirect`;
- `VulkanGpuBuffer.Direct` confirms device-preferred VMA allocation behavior;
- public graphics/indirect rendering can therefore remain Blaze3D-owned.

## Intended effect

Establish whether D-0023's evidence-driven Vulkan escape hatch is actually needed for compute and, if so, constrain it to the smallest possible surface.

## Actual effect

Compute is the first proven capability that public Blaze3D 26.2 cannot express. A narrow Vulkan seam is justified for storage-buffer allocation, compute pipeline/dispatch and the compute-to-indirect memory barrier only. Device, queues, submission, graphics passes and presentation remain Minecraft-owned.

## Why it matters

This enables the future GPU visibility/compaction architecture without prematurely taking over the Vulkan backend or duplicating Minecraft's graphics infrastructure.

## Next action

Implement dev8 with a raw device-preferred STORAGE_BUFFER|INDIRECT buffer wrapped as `VulkanGpuBuffer`, compile the compute shader with LWJGL shaderc, insert one transient compute command buffer into the existing `VulkanCommandEncoder` submission, add an explicit Sync2 compute-write -> draw-indirect-read barrier, then consume the result with public `RenderPass.drawIndexedIndirect`.