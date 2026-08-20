# A-0045 - Phase 1 dev8 compute-generated indirect implementation

**Date:** 2026-08-20  
**Status:** SUCCESS - hosted compile validation; runtime pending  
**Version:** `0.1.0-phase1-dev8`

## Objective

Prove the implementation path for GPU-generated indexed-indirect commands while preserving one useful Minecraft-owned submission and public Blaze3D graphics.

## Action

Implemented:

- `GpuDeviceAccessor` and `CommandEncoderAccessor` Mixins exposing only the already-existing backend objects;
- `VulkanStorageIndirectBuffer`, a non-mapped VMA device-preferred buffer with native `STORAGE_BUFFER | INDIRECT_BUFFER` usages, wrapped as public `VulkanGpuBuffer` with logical `USAGE_INDIRECT_PARAMETERS` so public `RenderPass.drawIndexedIndirect` can consume it;
- `VulkanComputeIndirectGenerator` using LWJGL shaderc for one tiny compute shader, one storage descriptor, a four-byte `firstIndex` push constant and one dispatch;
- compute writes two native 20-byte `VkDrawIndexedIndirectCommand` records;
- explicit Synchronization2 barrier from COMPUTE_SHADER / SHADER_STORAGE_WRITE to DRAW_INDIRECT / INDIRECT_COMMAND_READ;
- compute command buffer inserted through Minecraft's existing `VulkanCommandEncoder.execute`, keeping the same submission builder;
- `FrameGraphCommandStream.backendInteropEncoder()` as a tightly documented interop seam;
- `ComputeIndirectDrawProbe` with four passes: geometry upload -> compute generation -> public indexed-indirect draw -> readback;
- CPU stages only 72 vertex + 12 index bytes; no CPU indirect-command upload remains;
- two independent triangle pixels plus clear corner remain the deterministic graphics oracle;
- same useful submission timeline continues to gate staging and arena reclamation;
- version bumped to `0.1.0-phase1-dev8`.

Completed dev7 `ArenaIndirectDrawProbe`, CPU-authored `IndexedIndirectCommandBuffer` and temporary compute-inspection workflow were removed from the cleaned branch.

## Compile result

First compile failed only because LWJGL's `vkAllocateDescriptorSets` overload expects a `LongBuffer`, not a `PointerBuffer`. This exact signature mismatch was corrected without changing the architecture.

The corrected implementation compiled successfully in GitHub Actions with Java 25 / Gradle 9.5.1, including shaderc/Vulkan bindings, Mixins and packaging.

## Intended effect

Create the smallest possible Vulkan compute seam while proving GPU-written indirect commands can feed the already-validated public graphics path in one owned submission.

## Actual effect

Hosted compilation confirms the implementation is valid against the exact 26.2/LWJGL types. Real RX 6800 XT runtime validation is still required for shader compilation, descriptor/pipeline creation, compute dispatch, barrier correctness, public indirect consumption, pixels and shutdown lifetime.

## Expected runtime accounting

- graphPasses=4, executedMask=15;
- usefulSubmissions=1, profilerOnlySubmissions=0;
- computeDispatches=1;
- indirectCalls=1, indirectCommands=2, triangles=2;
- nativeComputeSeam=true, nativeGraphicsSeam=false;
- gpuGeneratedIndirectBytes=40;
- staging submitted/reclaimed=84/84 bytes;
- staging high-water=92 from aligned reservations `[0,72)` and `[80,92)`;
- arena allocations/high-water=2/84, retired/reclaimed=2/2, used=0 after completion;
- left/right magenta, corner black, pixelsVerified=3;
- no pending upload/arena/resource retirements at normal shutdown.

## Next action

Finish decisions/current-state documentation, run CI on the exact cleaned documented head, inspect the CI JAR/checksums and distribute dev8 for the reference Windows 11 / RX 6800 XT Vulkan test. Keep PR #10 draft/unmerged until runtime validation passes.