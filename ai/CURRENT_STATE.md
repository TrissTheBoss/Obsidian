# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev6, merge commit `355e0ed6108468c019bced9b3b229e4e494f9bab`
- Active development branch: `phase1/arena-indirect-draw`
- Active draft PR: #9, `Phase 1: arena-backed indirect draw`
- Current development version: `0.1.0-phase1-dev7`
- Dev7 status: **compile validated; runtime pending**

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

### Phase 0 - COMPLETE
Validated Fabric bootstrap, Vulkan selection, Minecraft `GpuDevice` attachment, capability reporting, world entry and clean shutdown.

### Phase 1 dev1 - VALIDATED / merged PR #3
Validated frame lifecycle, fixed CPU timing storage, controlled GPU submission and timestamp retrieval.

### Phase 1 dev2 - VALIDATED / merged PR #4
Validated three-slot frame contexts, real completion fences, zero-timeout polling and deferred GPU destruction.

### Phase 1 dev3 - VALIDATED / merged PR #5
Validated bounded persistent staging, batched copies, explicit backpressure and deterministic readback.

### Phase 1 dev4 - VALIDATED / merged PR #6
Validated device-preferred arena suballocation, generation-safe handles, completion-gated span reuse, stale-handle rejection and coalescing.

### Phase 1 dev5 - VALIDATED / merged PR #7
Validated fixed frame-graph pass ordering and GPU timestamps embedded inside one useful owned submission with zero profiler-only submissions.

### Phase 1 dev6 - VALIDATED / merged PR #8
Validated the first actual Obsidian graphics draw through public Blaze3D on the RX 6800 XT: custom shaders/pipeline, vertex/index state, one indexed triangle, private RGBA8 render target, texture readback, deterministic pixel verification, integrated timestamps, world entry and clean shutdown.

Dev6 runtime evidence: `ai/attempts/A-0037-dev6-runtime-success.md`.
Dev6 was squash-merged as `355e0ed6108468c019bced9b3b229e4e494f9bab` with `[no-release]`.

## Phase 1 dev7 - ACTIVE; compile validated, runtime pending

Goal: connect the validated shared geometry arena to the real indexed-indirect draw path that future GPU visibility/compaction will target.

### Exact Minecraft 26.2 findings

Exact Loom-resolved API/backend inspection proved the public path is sufficient:

- `RenderPass.drawIndexedIndirect(GpuBufferSlice, int)` is public;
- it requires `DeviceFeatures.drawIndirect()`;
- draw counts greater than one also require `DeviceFeatures.multiDrawIndirect()`;
- command storage requires `GpuBuffer.USAGE_INDIRECT_PARAMETERS`;
- command offsets are 4-byte aligned;
- native `VkDrawIndexedIndirectCommand` stride is 20 bytes;
- the Vulkan backend calls `vkCmdDrawIndexedIndirect` directly;
- the reference RX 6800 XT already reports indirect and multi-draw-indirect support.

Fence inspection also proved `VulkanCommandEncoder.createFence()` creates only a Java timeline-submit-index handle. It does not create/destroy a native `VkFence`. Multiple handles created before one submit observe the same timeline completion point and can be independently owned by staging and arena lifetime tracking without another GPU submission.

Evidence: `ai/attempts/A-0040-dev7-indirect-api-inspection.md`.

### Implemented dev7 path

`IndexedIndirectCommandBuffer`:

- fixed capacity = 64 commands for validation;
- 20 bytes per command;
- device-preferred/non-mapped;
- usages `COPY_DST | INDIRECT_PARAMETERS`.

`ArenaIndirectDrawProbe`:

- allocates 72 vertex bytes and 12 index bytes from the real `DeviceGeometryArena`;
- uploads those spans through the validated bounded staging ring;
- creates two 20-byte indexed-indirect commands;
- uses one `drawIndexedIndirect(..., 2)` call to render two separated triangles;
- uses a private 16x16 RGBA8 target, never presented;
- reads the target back and verifies left triangle, right triangle and clear-corner pixels independently;
- creates an additional lightweight timeline handle before the same useful submit and transfers it to arena retirement;
- arena-retirement registration is nonblocking and post-submit failure-safe;
- timestamps remain embedded in the useful upload/draw/readback command stream;
- profiler-only submissions remain zero.

Three graph passes:

1. `arena-indirect-upload`;
2. `arena-indexed-indirect-draw`;
3. `arena-indirect-readback`.

### Expected runtime invariants

Graph/draw:

- graphPasses=3;
- executedMask=7;
- usefulSubmissions=1;
- profilerOnlySubmissions=0;
- indirectCalls=1;
- indirectCommands=2;
- triangles=2;
- pipelineValid=true;
- left `(4,8)` = magenta `255/0/255/255`;
- right `(11,8)` = magenta `255/0/255/255`;
- corner `(0,0)` = black `0/0/0/255`;
- pixelsVerified=3.

Staging:

- vertex=72 bytes;
- index=12 bytes;
- indirect=40 bytes;
- submitted/reclaimed payload=124/124 bytes;
- expected high-water=136 bytes because reservations begin at virtual offsets 0, 80 and 96 under 16-byte alignment;
- backpressure=0;
- pendingUploadBatches=0.

Device arena:

- allocations=2;
- payload/high-water expected=84 bytes;
- retired=2;
- reclaimed=2;
- retirementBackpressure=0;
- used=0 after completion;
- freeSpans=1;
- largestFree=524288;
- fragmentation=0;
- pendingArenaRetirementBatches=0.

CPU/GPU pass timings are run-dependent; successful timestamp resolution matters, not exact values.

### Compile status

The cleaned/hardened dev7 implementation compiles against exact Minecraft 26.2 in GitHub Actions. Completed dev6 probe and temporary indirect API inspection workflow are absent from the clean branch. Evidence: `ai/attempts/A-0041-dev7-arena-indirect-implementation.md`.

## Proven architecture boundary

`Minecraft Vulkan device -> FrameCoordinator -> real completion timeline -> bounded staging -> generation-safe device geometry arena -> FixedFrameGraph -> owned command stream -> embedded timestamps -> public Blaze3D graphics pipeline -> arena-backed indexed-indirect draw`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own the presented framebuffer;
- render actual Minecraft terrain;
- perform routine device-wide waits;
- rely on CPU frame count for GPU completion;
- require native Vulkan access for the current draw path.

## Terrain meshing roadmap

Greedy meshing is a required final-product direction under D-0024. Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

- **Phase 2 - one chunk correctly:** define immutable section snapshots, neighbor halo, rendered-face/material/light/AO semantics and keep a deliberately simple reference mesher for differential correctness.
- **Phase 3 - production CPU terrain mesher:** implement worker-local binary/bitmask greedy meshing, reusable scratch, material/layer/tint/light/AO/UV-aware merge keys, AO diagonal selection, border handling, T-junction validation, build-time/quad/byte metrics and scheduler integration.
- **Phase 4+ - GPU visibility:** consume the reduced section meshes and generate/compact indirect draw work.

Block ID alone is never a valid greedy merge key. Keep the simple reference mesher after the optimized mesher lands so greedy output can be differential-tested and fuzzed.

## Immediate next action

1. Update PR #9 with the exact API/implementation findings.
2. Run final CI on the exact documented dev7 head.
3. Download and inspect the CI artifact.
4. Distribute `0.1.0-phase1-dev7` for the reference RX 6800 XT Vulkan/world test.
5. Keep PR #9 draft/unmerged until the runtime test passes.

## Relevant durable decisions

D-0014 through D-0023 remain active. D-0024 makes binary/bitmask greedy meshing the production Phase 3 terrain-meshing target.