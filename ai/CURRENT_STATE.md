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

Phase 0 is no longer pending runtime validation. The user tested `v0.0.2-phase0` on the reference Windows 11 / Radeon RX 6800 XT machine with Minecraft configured to Vulkan.

Validated real runtime path:

- Prism Launcher 10.0.5.
- Minecraft 26.2.
- Fabric Loader 0.19.3.
- Fabric API 0.158.0+26.2.
- Java 25.0.1.
- AMD Radeon RX 6800 XT detected as a discrete GPU.
- Minecraft selected the Vulkan backend.
- Driver string: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`.
- Obsidian attached to the Vulkan `GpuDevice` successfully.
- Obsidian capability reporting completed.
- The client loaded resources, started an integrated server, created/entered a world, rendered/ran normally, saved, shut down, and exited with code 0.

Observed Vulkan extensions exposed by Minecraft in that run included:

- `VK_KHR_synchronization2`
- `VK_KHR_swapchain`
- `VK_KHR_surface`
- `VK_KHR_win32_surface`
- `VK_KHR_dynamic_rendering`
- `VK_KHR_push_descriptor`
- `VK_EXT_debug_utils`
- `VK_EXT_vertex_attribute_divisor`
- `VK_AMD_buffer_marker`

Minecraft/DeviceInfo also reported indirect drawing, multi-draw indirect, and persistent mapping support.

Phase 0 therefore proved the real boundary:

`Fabric -> Obsidian bootstrap -> Minecraft 26.2 GpuDevice -> Vulkan backend -> RX 6800 XT`

## Phase 1 - ACTIVE

Phase 1 has started on `phase1/frame-foundation`.

The first Phase 1 objective is intentionally narrow: establish a real render-frame lifecycle root and prove that Obsidian can submit non-visual GPU commands through Minecraft's active Vulkan device without yet replacing terrain or changing the rendered image.

### Exact 26.2 API findings

A temporary GitHub Actions API-inspection workflow interrogated the exact Loom-resolved Minecraft 26.2 client JAR with `javap`. The temporary workflow was removed after the interfaces were established.

Relevant confirmed APIs:

- `Minecraft.renderFrame(boolean)` is available as a whole-frame lifecycle seam.
- `GpuDevice.createCommandEncoder()`.
- `GpuDevice.createTimestampQueryPool(int)`.
- `CommandEncoder.writeTimestamp(GpuQueryPool, int)`.
- `CommandEncoder.submit()`.
- `GpuQueryPool.getValue(int)` returns `OptionalLong` so results can be polled without an explicit blocking wait.
- `GpuQueryPool.close()`.

Important constraint discovered: timestamp writes live on a command encoder and submission is explicit. Creating start/end timestamp encoders every frame would add extra GPU submissions and could itself damage frame pacing. Do not implement GPU frame timing that way.

### Implemented Phase 1 frame foundation

Current branch code adds:

- `render/frame/FrameCoordinator`
  - one render-thread lifecycle root for future frame contexts, deferred resource retirement, uploads, render-graph work, and profiling;
  - begin/end hooks around `Minecraft.renderFrame(boolean)`;
  - fixed-allocation CPU frame timing collection.

- `render/frame/FrameTimings`
  - primitive `long[]` ring;
  - 2048 samples by default;
  - no per-frame allocations from the ring itself;
  - records whole-frame CPU wall time for later percentile analysis.

- `render/frame/GpuSubmissionProbe`
  - one-shot only;
  - creates a two-entry timestamp query pool;
  - writes two timestamp commands to one command encoder;
  - performs exactly one extra `submit()` for the entire probe;
  - polls query results asynchronously on subsequent frames;
  - logs completion/failure without crashing Minecraft;
  - releases the query pool after completion or shutdown.

- `MinecraftFrameMixin`
  - injects at `Minecraft.renderFrame` HEAD and RETURN;
  - calls the Phase 1 frame coordinator;
  - invokes Obsidian shutdown from `Minecraft.close`.

- Bootstrap lifecycle now creates the frame coordinator only after Vulkan has been confirmed active and closes Phase 1 resources during shutdown.

### Compile validation

The implementation compiled successfully against the exact Minecraft 26.2/Fabric dependency set in GitHub Actions.

Clean branch head before continuity-doc updates:

- commit: `10a6e979a2cbfd5b8531ac98fb6cf3f00907d7aa`
- workflow run: `32315268985`
- result: `success`
- build artifact upload: successful

The earlier implementation head `5add40c578325642b2c15eb66540b50e74725b15` also passed the build before the temporary API-inspection workflow was removed.

## What Phase 1 has NOT proven yet

The new Phase 1 code has not yet been run on the reference Windows/Vulkan machine.

Before merging or releasing this milestone, validate `0.1.0-phase1-dev1` on the real machine and confirm:

1. Minecraft selects Vulkan.
2. Obsidian logs that the Phase 1 frame coordinator is active.
3. The one-shot GPU probe logs `submitted` once.
4. The timestamp query later logs `completed` without blocking/crashing.
5. The player can reach the title screen and enter a world.
6. Shutdown logs that the frame coordinator closed cleanly.
7. No visible rendering difference is introduced.
8. No repeated GPU probe submissions occur.

## Architecture boundary after this milestone

Obsidian still does not own terrain rendering and does not create a second Vulkan device.

Current boundary:

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator`

Obsidian can now observe the render-frame lifecycle and has a minimal path for controlled GPU command submission through Minecraft's device abstraction.

This is the intended foundation for the next pieces:

- frame-context rotation;
- explicit resource retirement/deferred destruction;
- upload/staging ownership;
- profiler snapshots and percentiles;
- render-graph scheduling;
- eventually Obsidian-owned terrain commands.

## Major technical lesson from the first Phase 1 probe

Do not implement per-frame GPU profiling by creating additional command submissions at both frame boundaries. That would contaminate the thing being measured.

Future GPU profiling should either:

- integrate timestamps into command streams Obsidian already owns, or
- hook an existing Minecraft submission/encoder path after exact backend ownership has been understood.

## Reference hardware and priorities

Primary reference system:

- Windows 11
- AMD Radeon RX 6800 XT, 16 GB VRAM
- AMD Ryzen 5 5600X
- 16 GB DDR4-2666

Priority order remains:

1. 1% / 0.1% lows and frame pacing.
2. Smooth chunk loading/streaming.
3. Very large render-distance scaling.
4. Average FPS.
5. Sensible RAM/VRAM use.

## Immediate next action

Build/download the clean `0.1.0-phase1-dev1` PR artifact and run it on the same Vulkan test instance used for Phase 0. Record the log result in `ai/ATTEMPT_LOG.md` before deciding whether this frame-foundation milestone is ready to merge.
