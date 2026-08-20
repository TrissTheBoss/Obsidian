# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Active development branch: `phase1/frame-foundation`
- Active draft PR: #3, `Phase 1: frame and GPU foundation`
- Current Phase 1 development version: `0.1.0-phase1-dev1`

## Phase status

### Phase 0 - COMPLETE and runtime validated

Phase 0 is complete on the reference Windows 11 / Radeon RX 6800 XT machine.

Validated runtime stack:

- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, discrete GPU
- Minecraft Vulkan backend
- AMD driver string `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`

Obsidian attached to Minecraft's Vulkan `GpuDevice`, captured device/capability metadata, reached the title screen and a single-player world, and shut down with exit code 0.

Observed Vulkan extensions included `VK_KHR_synchronization2`, `VK_KHR_dynamic_rendering`, `VK_KHR_swapchain`, `VK_KHR_surface`, `VK_KHR_win32_surface`, `VK_KHR_push_descriptor`, `VK_EXT_debug_utils`, `VK_EXT_vertex_attribute_divisor`, and `VK_AMD_buffer_marker`. Minecraft/DeviceInfo also reported indirect drawing, multi-draw indirect, and persistent mapping support.

Validated Phase 0 boundary:

`Fabric -> Obsidian bootstrap -> Minecraft 26.2 GpuDevice -> Vulkan backend -> RX 6800 XT`

## Phase 1 - ACTIVE; frame/GPU foundation runtime validated

The first Phase 1 milestone has now passed a real Windows/Vulkan runtime test.

### Exact 26.2 API findings

A temporary GitHub Actions inspection workflow interrogated the exact Loom-resolved Minecraft 26.2 client JAR with `javap`; the workflow was removed after use.

Confirmed APIs used by the first milestone:

- `Minecraft.renderFrame(boolean)` as the whole-frame lifecycle seam
- `GpuDevice.createCommandEncoder()`
- `GpuDevice.createTimestampQueryPool(int)`
- `CommandEncoder.writeTimestamp(GpuQueryPool, int)`
- `CommandEncoder.submit()`
- `GpuQueryPool.getValue(int)` returning `OptionalLong` for nonblocking polling
- `GpuQueryPool.close()`

Important constraint: timestamp writes become GPU work through explicit command submission. Routine profiler-only submissions at frame boundaries would contaminate frame pacing, so normal profiling must eventually integrate into command streams Obsidian already owns or an existing verified submission path.

### Implemented Phase 1 frame foundation

Current branch code adds:

- `render/frame/FrameCoordinator`
  - render-thread lifecycle root for future frame contexts, deferred destruction, uploads, render-graph work, and profiling
  - begin/end hooks around `Minecraft.renderFrame(boolean)`
  - fixed-allocation CPU whole-frame timing

- `render/frame/FrameTimings`
  - primitive `long[]` ring
  - 2048 samples
  - no per-frame allocations from the ring itself

- `render/frame/GpuSubmissionProbe`
  - development validation probe only
  - creates a two-entry timestamp query pool
  - records two timestamp commands in one encoder
  - performs exactly one additional `submit()` for the entire process lifetime
  - polls results without an explicit blocking wait
  - releases its query pool after completion/shutdown

- `MinecraftFrameMixin`
  - injects at `Minecraft.renderFrame` HEAD and RETURN
  - invokes the frame coordinator
  - invokes Obsidian resource cleanup from `Minecraft.close`

The coordinator is created only after Vulkan has been confirmed active.

## Phase 1 dev1 runtime validation

Real test of `Obsidian-0.1.0-phase1-dev1` on 2026-08-20 succeeded.

Observed sequence:

1. Fabric loaded `obsidian 0.1.0-phase1-dev1`.
2. Minecraft selected Vulkan on the RX 6800 XT.
3. Obsidian attached to the Vulkan backend and armed the Phase 1 frame foundation.
4. `FrameCoordinator` became active with a 2048-sample CPU timing ring.
5. `GpuSubmissionProbe` submitted exactly once on frame 1.
6. A later nonblocking poll in the same frame iteration found both timestamp values ready: timestamp0 `20938905848`, timestamp1 `20938905908`, delta `60` ticks.
7. Resource loading completed and the player entered a single-player world.
8. The coordinator remained active for 2107 frames.
9. Minecraft shut down normally and the process exited with code 0.

Important interpretation: the log message `after 0 frame(s)` does not imply an explicit GPU wait. The implementation polls `GpuQueryPool.getValue(...)`; the result happened to be ready later in frame 1. No blocking query wait or device-wide idle was introduced by Obsidian.

This proves the first controlled Obsidian GPU command path on the real reference machine:

`Minecraft render frame -> Obsidian FrameCoordinator -> Minecraft GpuDevice -> CommandEncoder -> Vulkan GPU execution -> nonblocking timestamp result`

Terrain replacement remains intentionally inactive.

## Compile/build validation

The Phase 1 implementation has repeatedly passed GitHub Actions against the exact Minecraft 26.2/Fabric dependency set. The clean branch builds include workflow run `32315268985` and subsequent documentation-clean build run `32315487369`, both successful.

## Architecture boundary now proven

Current proven boundary:

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> controlled GPU submission`

Obsidian still does not:

- create a second Vulkan device or swapchain
- own terrain rendering
- submit per-frame profiler-only GPU command buffers
- perform device-wide waits

## Next Phase 1 milestone

The next work should turn the validated frame root into real resource-lifetime infrastructure, without changing terrain rendering yet:

1. Define rotating frame contexts with monotonically increasing frame serials.
2. Add deferred resource retirement/destruction queues keyed to safe GPU completion.
3. Inspect the exact 26.2 fence/submission semantics needed to know when an Obsidian-owned resource is safe to reclaim.
4. Add bounded staging/upload ownership scaffolding without issuing terrain uploads yet.
5. Add profiler snapshot/percentile calculations off the hot path.
6. Keep all routine frame-path allocations at zero or explicitly justified.
7. Avoid routine `vkDeviceWaitIdle`/equivalent waits.

The next success criterion should be: create/retire a small Obsidian-owned GPU resource through the frame-context/deferred-destruction system, prove it is reclaimed only after GPU completion, enter a world, and shut down cleanly.

## Reference hardware and priorities

Primary reference system:

- Windows 11
- AMD Radeon RX 6800 XT, 16 GB VRAM
- AMD Ryzen 5 5600X
- 16 GB DDR4-2666

Priority order remains:

1. 1% / 0.1% lows and frame pacing
2. smooth chunk loading/streaming
3. very large render-distance scaling
4. average FPS
5. sensible RAM/VRAM use

## Immediate handoff instruction

PR #3 is ready to be finalized after recording the successful dev1 runtime test. Merge the validated frame/GPU foundation, then continue Phase 1 with frame contexts and deferred GPU resource lifetime management. Do not expand into terrain replacement until resource ownership/synchronization is proven on the real Vulkan backend.
