# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev7, merge commit `fa0379887a945e2bf2adc722270a064915477fce`
- Active development branch: `phase1/compute-indirect`
- Active draft PR: #10, `Phase 1: compute-generated indexed-indirect commands`
- Current development version: `0.1.0-phase1-dev8`
- Dev8 status: **runtime validated on the reference RX 6800 XT; exact runtime-evidence head `f761ee70f7e8c5e5ce4ccf661357058d71cd6266` pending final CI + merge with `[no-release]`**

## Reference runtime

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

Phase 0 and Phase 1 dev1-dev7 are runtime validated and merged with development `[no-release]` semantics.

### Phase 1 dev7 - VALIDATED / merged PR #9

Runtime proved public Blaze3D true indexed-indirect drawing from shared device-arena geometry, including two independent commands/triangles, completion-gated arena reclamation and deterministic readback.

Runtime evidence: `ai/attempts/A-0043-dev7-runtime-success.md`.
Merge commit: `fa0379887a945e2bf2adc722270a064915477fce`.

## Phase 1 dev8 - RUNTIME VALIDATED / pending merge through PR #10

Goal: prove GPU compute can generate indexed-indirect command data that a following graphics pass consumes, while retaining Minecraft ownership of device, graphics queue/submission and presentation.

### Exact Minecraft 26.2 capability finding

Public Blaze3D has no compute/storage-buffer surface:

- no ComputePass;
- no ComputePipeline / CompiledComputePipeline;
- ShaderType exposes only VERTEX and FRAGMENT;
- public GpuBuffer has no STORAGE_BUFFER usage;
- public CommandEncoder has no dispatch method.

The Vulkan backend exposes a narrow interop surface sufficient to fill only that gap:

- `VulkanDevice.vkDevice()` and `vma()`;
- `VulkanCommandEncoder.allocateAndBeginTransientCommandBuffer()` and `execute(...)`;
- public `VulkanGpuBuffer` can wrap an Obsidian-created storage+indirect VkBuffer and still be consumed by public `RenderPass.drawIndexedIndirect`.

Evidence: `ai/attempts/A-0044-dev8-compute-api-inspection.md`.

### Dev8 implementation and compile result

- Mixins expose only the existing `GpuDeviceBackend` and `CommandEncoderBackend` objects for the isolated interop layer;
- `VulkanStorageIndirectBuffer`: device-preferred VMA buffer with native STORAGE_BUFFER | INDIRECT_BUFFER usages, non-mapped;
- `VulkanComputeIndirectGenerator`: shaderc-compiled compute shader, one storage descriptor, one 4-byte `firstIndex` push constant, one dispatch;
- compute writes exactly two native 20-byte indexed-indirect commands;
- explicit Sync2 barrier: COMPUTE_SHADER / SHADER_STORAGE_WRITE -> DRAW_INDIRECT / INDIRECT_COMMAND_READ;
- compute command buffer is inserted into Minecraft's existing `VulkanCommandEncoder` submission;
- graphics remains public Blaze3D `RenderPass.drawIndexedIndirect`;
- graph has four passes: geometry upload -> compute indirect generation -> indirect draw -> readback;
- CPU staging payload is only 84 bytes (72 vertex + 12 index); indirect command bytes are generated entirely on GPU;
- expected staging high-water is 92 bytes from `[0,72)` + `[80,92)` aligned reservations.

First compile attempt found one exact LWJGL handle-type mismatch (`vkAllocateDescriptorSets` requires `LongBuffer`, not `PointerBuffer`); corrected implementation then compiled successfully. Evidence: `ai/attempts/A-0045-dev8-compute-indirect-implementation.md`.

Final package evidence before runtime: `ai/attempts/A-0046-dev8-final-package-verification.md`.

### Real RX 6800 XT runtime result - SUCCESS

- correct `0.1.0-phase1-dev8` loaded on Vulkan;
- `VK_KHR_synchronization2` available;
- `indirect=true`, `multiDrawIndirect=true`, `persistentMapping=true`;
- graphPasses=4, executedMask=15;
- usefulSubmissions=1, profilerOnlySubmissions=0;
- computeDispatches=1;
- indirectCalls=1, indirectCommands=2, triangles=2;
- nativeComputeSeam=true, nativeGraphicsSeam=false;
- pipelineValid=true;
- GPU generated 40 bytes of native indexed-indirect commands;
- CPU staging submitted/reclaimed=84/84 bytes, high-water=92, backpressure=0;
- left and right triangle pixels both verified magenta, clear corner verified black, pixelsVerified=3;
- arena allocations=2, high-water=84, retired/reclaimed=2/2, used=0 after completion;
- freeSpans=1, largestFree=524288, fragmentation=0, no pending arena retirements;
- all four GPU timestamp ranges resolved nonblockingly;
- world entry succeeded;
- shutdown after 1810 frames had no pending work;
- process exited with code 0.

Runtime evidence: `ai/attempts/A-0047-dev8-runtime-success.md`.

## Proven architecture boundary

`Minecraft Vulkan device/queue/presentation -> FrameCoordinator -> bounded staging -> generation-safe device geometry arena -> FixedFrameGraph -> one Minecraft-owned submission -> [narrow native Vulkan compute/storage seam] -> explicit compute-to-indirect Sync2 barrier -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

Native Vulkan access is justified only for the capability public Blaze3D 26.2 cannot express. Obsidian still does not create a second device, queue, swapchain or native graphics renderer.

## Terrain meshing roadmap

Greedy meshing remains required under D-0024. Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

- **Phase 2:** one real section correctly; immutable snapshot + neighbor halo + simple reference mesher/differential oracle.
- **Phase 3:** production worker-local binary/bitmask greedy meshing with reusable scratch and render-correct merge keys covering material/layer/tint/light/AO/UV/special state.
- **Phase 4+:** GPU visibility/compaction consumes those reduced section meshes and generates/compacts indirect work.

Block ID alone is never a valid greedy merge key. Keep the simple reference mesher after the optimized mesher lands so greedy output can be differential-tested and fuzzed.

## Immediate next action

1. Final-CI the exact dev8 runtime-evidence head of PR #10.
2. Promote and squash-merge PR #10 with `[no-release]`.
3. Start Phase 1 dev9 from the resulting `main` merge commit.
4. Establish the GPU visibility/compaction bridge before real terrain: GPU scene/candidate records -> compute visibility decision -> compacted indexed-indirect records -> graphics consumption -> deterministic offscreen verification.
5. Inspect exact Vulkan 1.3 / Minecraft backend support for `vkCmdDrawIndexedIndirectCount` and draw-count storage. Prefer a GPU-produced draw count if this can be integrated without taking over normal graphics ownership; otherwise validate fixed-count compaction first and record the exact limitation.
6. Preserve one useful submission, zero profiler-only submissions, explicit synchronization and completion-gated reclamation.
7. Terrain replacement remains inactive until the visibility/compaction infrastructure is runtime validated.

## Relevant durable decisions

D-0014 through D-0026 remain active. D-0024 keeps binary/bitmask greedy meshing as the Phase 3 production CPU mesher target; D-0025/D-0026 constrain the native compute seam and synchronization requirements.