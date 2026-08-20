# A-0050 - Phase 1 dev9 GPU visibility/compaction implementation

- **Date:** 2026-08-20
- **Status:** COMPILE SUCCESS / RUNTIME PENDING
- **Base:** `68e9710ff964a44165122c5d85c0d559e4698b11` (validated dev8 merge)
- **Branch:** `phase1/visibility-compaction`
- **Draft PR:** #11
- **Version:** `0.1.0-phase1-dev9`

## Objective

Move from dev8's unconditional GPU-authored indirect commands to the first GPU scene visibility/compaction pipeline: upload scene candidates, decide visibility on GPU, compact only visible commands, retain a GPU-produced visible count, draw the compacted result through public Blaze3D and verify both graphics output and command-buffer contents.

## Exact API decision

A temporary exact Minecraft 26.2 inspection proved public Blaze3D has no count-buffer indirect draw. `RenderPass` exposes fixed-count indirect methods and `DeviceFeatures` has `drawIndirect`/`multiDrawIndirect` but no `drawIndirectCount`. The Vulkan backend records `vkCmdDrawIndexedIndirect` for the public method.

Vulkan itself has `vkCmdDrawIndexedIndirectCount`, but using it would require widening Obsidian into native graphics/render-pass command recording. Dev9 therefore keeps `nativeGraphicsSeam=false` and uses a fixed maximum of four indirect slots with GPU-zeroed unused tail commands. The GPU-produced visible count is still retained and read back for validation/future Phase 4 use.

Detailed inspection evidence: `ai/attempts/A-0049-dev9-indirect-count-api-inspection.md`.

## Implementation

### `VulkanInteropBuffer`

Generic non-mapped, device-preferred VMA buffer wrapper for the isolated Vulkan interop layer. Native Vulkan usage and logical public Blaze3D usage are specified independently so buffers can be compute storage while still participating in the validated public copy/draw operations.

### `VulkanVisibilityCompactor`

Owns two buffers:

- candidate scene buffer: 4 records x 16 bytes = 64 bytes; native STORAGE_BUFFER | TRANSFER_DST, public COPY_DST;
- compacted output: 4 x 20-byte `VkDrawIndexedIndirectCommand` + uint visibleCount = 84 bytes; native STORAGE_BUFFER | INDIRECT_BUFFER | TRANSFER_SRC, public INDIRECT_PARAMETERS | COPY_SRC.

The compute shader uses one four-invocation workgroup. Invocation 0 clears visibleCount and all output command slots, then a workgroup barrier makes the reset visible. Every invocation reads one candidate and evaluates the validation visibility predicate:

`abs(centerX) <= 0.50 && abs(centerY) <= 0.80`

Visible candidates atomically reserve a compacted front slot and write a native indexed-indirect command. Hidden candidates write nothing. Expected result is two valid commands in slots 0-1 and fully zero commands in slots 2-3.

### Synchronization

Two explicit Sync2 dependencies are recorded:

1. candidate upload -> visibility compute:
   - source: TRANSFER / TRANSFER_WRITE
   - destination: COMPUTE_SHADER / SHADER_STORAGE_READ

2. compacted output -> draw + readback:
   - source: COMPUTE_SHADER / SHADER_STORAGE_WRITE
   - destination stages: DRAW_INDIRECT | TRANSFER
   - destination accesses: INDIRECT_COMMAND_READ | TRANSFER_READ

This extends D-0026 to the real visibility producer/consumer chain rather than relying on command ordering alone.

### `VisibilityCompactionProbe`

Four physically on-screen triangles are placed at center X positions `-0.75, -0.25, +0.25, +0.75`. The compute validation frustum keeps only the inner pair.

Geometry:

- 12 vertices = 144 bytes;
- 12 uint16 indices = 24 bytes;
- total device arena geometry = 168 bytes.

Scene input:

- 4 x 16-byte candidate records = 64 bytes.

CPU staging payload:

- vertex 144 bytes;
- index 24 bytes;
- scene 64 bytes;
- total submitted payload = **232 bytes**.

With the 16-byte staging alignment policy, expected high-water is **240 bytes**:

- vertices `[0,144)`;
- indices `[144,168)`;
- scene `[176,240)`.

GPU output is 84 bytes and is never CPU-authored.

Public graphics issues one `drawIndexedIndirect(..., 4)` call. Only compacted slots 0-1 have `indexCount=3`; slots 2-3 must remain all-zero and therefore emit no geometry.

### Deterministic validation

Private 32x32 RGBA8 target:

- visible-left `(12,16)` = magenta;
- visible-right `(20,16)` = magenta;
- culled-left `(4,16)` = black;
- culled-right `(28,16)` = black;
- clear corner `(0,0)` = black.

Output-buffer readback requires:

- `visibleCount=2`;
- front two commands are the two expected inner-candidate `firstIndex` values in either atomic order;
- each visible command has indexCount=3, instanceCount=1, vertexOffset=0, firstInstance=0;
- tail two commands are fully zero.

## Compile evidence

The first complete source implementation compiled successfully against exact Minecraft 26.2 / LWJGL using Java 25 / Gradle 9.5.1.

After removing the completed dev8 probe/generator/buffer and the temporary API-inspection workflow, the clean source-only head `88c83a59ed83978134b13f0038038126bd5da2fc` passed GitHub Actions run `32415578830`:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release: SKIPPED.

The completed dev8 classes are intentionally absent from the clean branch.

## Intended runtime invariants

- graphPasses=4, executedMask=15;
- one useful submission, zero profiler-only submissions;
- computeDispatches=1;
- candidates=4, visibleCount=2, culledCount=2;
- indirectCalls=1, publicIndirectSlots=4;
- `nativeComputeSeam=true`, `nativeGraphicsSeam=false`, `indirectCountConsumed=false`;
- pixelsVerified=5;
- compactedCommandsVerified=4;
- staging submitted/reclaimed=232/232, high-water=240, backpressure=0;
- arena allocations=2, high-water=168, retired/reclaimed=2/2, used=0 after completion;
- one free arena span of 524288 bytes, fragmentation=0;
- no pending work at shutdown;
- world entry and process exit code 0.

## Next action

Synchronize `CURRENT_STATE` and the durable decision ledger, run final CI on the exact documented head, inspect/package that exact CI artifact, then runtime-test dev9 on the reference RX 6800 XT before any merge.