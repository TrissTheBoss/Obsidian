# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev6, merge commit `355e0ed6108468c019bced9b3b229e4e494f9bab`
- Active development branch: `phase1/arena-indirect-draw`
- Active draft PR: #9, `Phase 1: arena-backed indexed-indirect draw`
- Current development version: `0.1.0-phase1-dev7`
- Dev7 status: **runtime validated; pending final exact-head CI + merge with `[no-release]`**

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
Validated the first actual Obsidian graphics draw through public Blaze3D on the RX 6800 XT: custom shaders/pipeline, vertex/index state, one indexed triangle, private RGBA8 render target, texture readback, deterministic pixel verification, integrated timestamps, world entry and clean shutdown. Merge commit: `355e0ed6108468c019bced9b3b229e4e494f9bab`.

### Phase 1 dev7 - VALIDATED; pending merge through PR #9

Runtime-validated the shared geometry arena feeding the true indexed-indirect graphics path through public Blaze3D.

Exact/API foundation:

- public `RenderPass.drawIndexedIndirect(GpuBufferSlice, int)`;
- Vulkan backend calls `vkCmdDrawIndexedIndirect`;
- indirect command storage uses `USAGE_INDIRECT_PARAMETERS`;
- command records use native 20-byte `VkDrawIndexedIndirectCommand` layout;
- drawCount > 1 uses `multiDrawIndirect` capability;
- lightweight `GpuFence` handles observe the encoder submission timeline, enabling independent staging and arena lifetime owners for the same useful submission without another queue submission.

Real RX 6800 XT result:

- correct `0.1.0-phase1-dev7` loaded on Vulkan;
- indirect=true, multiDrawIndirect=true, persistentMapping=true;
- graphPasses=3, executedMask=7;
- usefulSubmissions=1, profilerOnlySubmissions=0;
- indirectCalls=1, indirectCommands=2, triangles=2;
- pipelineValid=true;
- arena-backed geometry: 72 vertex bytes + 12 index bytes;
- indirect command payload: 40 bytes;
- staging submitted/reclaimed=124/124 bytes, high-water=136, backpressure=0;
- left and right triangle pixels both verified magenta, clear corner verified black, pixelsVerified=3;
- arena allocations=2, high-water=84, retired/reclaimed=2/2, used=0 after completion;
- freeSpans=1, largestFree=524288, fragmentation=0, no pending arena retirements;
- world entry succeeded;
- shutdown after 2276 frames had no pending work;
- process exited with code 0.

Runtime evidence: `ai/attempts/A-0043-dev7-runtime-success.md`.

## Proven architecture boundary

`Minecraft Vulkan device -> FrameCoordinator -> real completion timeline -> bounded staging -> generation-safe device geometry arena -> FixedFrameGraph -> owned command stream -> embedded timestamps -> public Blaze3D graphics pipeline -> arena-backed multi-command indexed-indirect draw -> deterministic readback -> completion-gated arena reclamation`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own the presented framebuffer;
- render actual Minecraft terrain;
- perform routine device-wide waits;
- rely on CPU frame count for GPU completion;
- require native Vulkan access for the current graphics/indirect path.

## Terrain meshing roadmap

Greedy meshing is required under D-0024. Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

- **Phase 2 - one chunk correctly:** define immutable section snapshots, neighbor halo, rendered-face/material/light/AO semantics and keep a deliberately simple reference mesher for differential correctness.
- **Phase 3 - production CPU terrain mesher:** implement worker-local binary/bitmask greedy meshing, reusable scratch, material/layer/tint/light/AO/UV-aware merge keys, AO diagonal selection, border handling, T-junction validation, build-time/quad/byte metrics and scheduler integration.
- **Phase 4+ - GPU visibility:** consume the reduced section meshes and generate/compact indirect draw work.

Block ID alone is never a valid greedy merge key. Keep the simple reference mesher after the optimized mesher lands so greedy output can be differential-tested and fuzzed.

## Immediate next action

1. Final-CI the dev7 runtime-evidence head of PR #9.
2. Promote and squash-merge PR #9 with `[no-release]`.
3. Start Phase 1 dev8 from the resulting `main` merge commit.
4. Inspect exact Minecraft 26.2 compute pipeline, storage-buffer/read-write binding, dispatch and compute-to-indirect synchronization semantics.
5. If public Blaze3D supports the needed path, validate compute-generated indexed-indirect command records consumed by a following graphics pass in the same owned graph/submission.
6. Keep the validation private/offscreen and deterministic; terrain replacement remains inactive until the compute/indirect bridge is proven.

## Relevant durable decisions

D-0014 through D-0024 remain active. Public Blaze3D remains the preferred boundary until exact evidence proves a missing capability or measured bottleneck.