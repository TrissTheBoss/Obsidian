# A-0049 - Phase 1 dev9 indirect-count / visibility API inspection

- **Date:** 2026-08-20
- **Status:** SUCCESS
- **Branch:** `phase1/visibility-compaction`
- **Temporary workflow:** `Phase 1 Visibility API Inspect` (removed after inspection)
- **Inspection artifact:** GitHub Actions artifact `phase1-visibility-api-inspect`

## Objective

Determine whether dev9 should widen the native graphics seam in order to consume a GPU-produced visible draw count, or whether visibility/compaction can be validated while keeping graphics on public Blaze3D.

## Exact Minecraft 26.2 findings

`com.mojang.blaze3d.systems.RenderPass` exposes:

- `drawIndexedIndirect(GpuBufferSlice, int)`;
- `drawIndirect(GpuBufferSlice, int)`;

It does **not** expose a draw-count-buffer variant.

`DeviceFeatures` contains exactly these relevant booleans:

- `multiDrawIndirect`;
- `drawIndirect`;

and has no `drawIndirectCount` feature field.

`VulkanRenderPass.drawIndexedIndirect` ultimately records `VK12.vkCmdDrawIndexedIndirect(...)` with native `VkDrawIndexedIndirectCommand.SIZEOF` stride. No `vkCmdDrawIndexedIndirectCount` reference appeared in the inspected RenderPass/VulkanRenderPass/VulkanCommandEncoder/VulkanDevice bytecode.

## Vulkan specification finding

Vulkan provides `vkCmdDrawIndexedIndirectCount` as a core Vulkan 1.2 command. It reads an unsigned draw count from a buffer and executes `min(count, maxDrawCount)` commands. Both command and count buffers require indirect-buffer usage, and the command requires the `drawIndirectCount` feature. Critically, it is a graphics draw command and must execute inside a render-pass instance/dynamic rendering scope with the required graphics pipeline/index state bound.

## Decision for dev9

Do **not** widen native graphics ownership merely to consume the count during Phase 1 dev9.

Instead:

1. GPU compute writes a compacted command list and a visible count.
2. Compute zeros all unused tail command slots (`indexCount=0`, other fields zero).
3. Public Blaze3D issues one fixed-capacity `drawIndexedIndirect(..., maxCandidates)` call.
4. Zero-tail commands perform no geometry work while preserving the already-validated public graphics boundary.
5. Dev9 reads back and verifies the GPU visible count and compacted command contents, so the count remains a real GPU artifact ready for future use.

A later Phase 4 optimization may justify native `vkCmdDrawIndexedIndirectCount` if profiling shows fixed-capacity zero-tail command fetch/dispatch overhead is material. That decision must include an exact plan for integrating the native command inside Minecraft's graphics render-pass ownership without duplicating broad graphics state.

## Why this is the safer Phase 1 choice

The purpose of dev9 is to validate scene visibility and GPU command compaction, not to expand graphics backend ownership. The fixed-capacity/zero-tail path proves the hard producer side while preserving `nativeGraphicsSeam=false` and avoiding a premature native render-pass implementation.

## Next action

Compile/runtime validate four candidate scene records -> two visible compacted commands -> visible count 2 -> two zero tail commands -> public four-slot indexed-indirect draw -> deterministic visible/culled pixel oracle and output-buffer readback.