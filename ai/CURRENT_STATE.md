# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev7, merge commit `fa0379887a945e2bf2adc722270a064915477fce`
- Active development branch: `phase1/compute-indirect`
- Active draft PR: #10, `Phase 1: compute-generated indirect commands`
- Current development version: `0.1.0-phase1-dev8`
- Dev8 status: **implementation compile-clean; real RX 6800 XT runtime pending**

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

Runtime proved:

- public Blaze3D true `vkCmdDrawIndexedIndirect` path;
- shared `DeviceGeometryArena` vertex/index allocations;
- one indirect call containing two native 20-byte commands;
- two independently verified triangles;
- one useful submission and zero profiler-only submissions;
- staging 124/124 bytes, high-water 136;
- arena allocations=2, high-water=84, retired/reclaimed=2/2 and fully coalesced;
- world entry, clean shutdown and exit code 0.

Runtime evidence: `ai/attempts/A-0043-dev7-runtime-success.md`.
Merge commit: `fa0379887a945e2bf2adc722270a064915477fce`.

## Phase 1 dev8 - ACTIVE / draft PR #10

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

### Current dev8 implementation

- Mixins expose only the existing `GpuDeviceBackend` and `CommandEncoderBackend` objects for the isolated interop layer;
- `VulkanStorageIndirectBuffer`: device-preferred VMA buffer with native STORAGE_BUFFER | INDIRECT_BUFFER usages, non-mapped;
- `VulkanComputeIndirectGenerator`: shaderc-compiled compute shader, one storage descriptor, one 4-byte `firstIndex` push constant, one dispatch;
- compute writes exactly two native 20-byte indexed-indirect commands;
- explicit Sync2 barrier: COMPUTE_SHADER / SHADER_STORAGE_WRITE -> DRAW_INDIRECT / INDIRECT_COMMAND_READ;
- compute command buffer is inserted into Minecraft's existing `VulkanCommandEncoder` submission;
- graphics remains public Blaze3D `RenderPass.drawIndexedIndirect`;
- graph has four passes: geometry upload -> compute indirect generation -> indirect draw -> readback;
- CPU staging payload is now only 84 bytes (72 vertex + 12 index); indirect command bytes are generated entirely on GPU;
- expected staging high-water is 92 bytes from `[0,72)` + `[80,92)` aligned reservations;
- deterministic pixels remain left magenta, right magenta, corner black;
- expected graph executedMask=15;
- expected usefulSubmissions=1, profilerOnlySubmissions=0;
- expected computeDispatches=1, indirectCalls=1, indirectCommands=2, triangles=2;
- expected arena high-water=84, retired/reclaimed=2/2, used=0 and fragmentation=0 after completion.

First compile attempt found only an exact LWJGL handle-type mismatch (`vkAllocateDescriptorSets` requires `LongBuffer`, not `PointerBuffer`); corrected implementation then compiled successfully. Evidence: `ai/attempts/A-0045-dev8-compute-indirect-implementation.md`.

Completed dev7 probe, CPU-authored indirect buffer and temporary compute API inspection workflow have been removed from the clean branch.

## Proven architecture boundary

`Minecraft Vulkan device/queue/presentation -> FrameCoordinator -> bounded staging -> device geometry arena -> FixedFrameGraph -> one Minecraft-owned submission -> [narrow native Vulkan compute/storage seam] -> explicit compute-to-indirect Sync2 barrier -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

Native Vulkan access is now justified only for a capability public Blaze3D 26.2 cannot express. Obsidian still does not create a second device, queue, swapchain or native graphics renderer.

## Terrain meshing roadmap

Greedy meshing remains required under D-0024. Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

- **Phase 2:** one real section correctly; immutable snapshot + neighbor halo + simple reference mesher/differential oracle.
- **Phase 3:** production worker-local binary/bitmask greedy meshing with reusable scratch and render-correct merge keys covering material/layer/tint/light/AO/UV/special state.
- **Phase 4+:** GPU visibility/compaction consumes those reduced section meshes and generates/compacts indirect work.

## Immediate next action

1. Preserve dev8 native-compute decisions in the durable ledger.
2. Final-CI the exact cleaned/documented branch head.
3. Inspect the CI-built `0.1.0-phase1-dev8` artifact and checksums.
4. Keep PR #10 draft/unmerged.
5. Runtime-test dev8 on the reference RX 6800 XT: compute shader/pipeline creation, one dispatch, explicit barrier, two compute-generated indirect commands, both triangle pixels, timestamps, staging 84/84 high-water 92, arena 2/2 reclaim, world entry and clean exit.

## Relevant durable decisions

D-0014 through D-0024 remain active. Dev8 additionally establishes that native Vulkan interop is allowed only for the exact compute/storage capability absent from public Blaze3D, while public graphics and Minecraft submission ownership remain the default.