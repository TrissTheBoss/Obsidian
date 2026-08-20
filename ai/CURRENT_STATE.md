# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Active development branch: `phase1/resource-lifetime`
- Active draft PR: #4, `Phase 1: frame contexts and resource lifetime`
- Current Phase 1 development version: `0.1.0-phase1-dev2`

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

## Phase 1 - ACTIVE

### Milestone 1: frame/GPU foundation - VALIDATED and merged

Validated in `0.1.0-phase1-dev1` on the real Windows 11 / RX 6800 XT machine:

- `Minecraft.renderFrame(boolean)` lifecycle hook works at runtime.
- fixed-allocation CPU frame timing ring runs continuously.
- Obsidian can create a `CommandEncoder` through Minecraft's active Vulkan `GpuDevice`.
- a one-shot timestamp command submission completed successfully without an explicit blocking wait.
- no competing Vulkan device or swapchain is created.
- world entry and clean shutdown succeeded.

This milestone was merged through PR #3.

### Milestone 2: frame contexts and GPU resource lifetime - VALIDATED

Development version: `0.1.0-phase1-dev2`.

Implemented:

- `FrameContextRing`
  - three preallocated frame slots;
  - monotonically increasing serials;
  - zero per-frame allocation from the ring itself;
  - frame rotation is bookkeeping only and never treated as proof of GPU completion.

- `DeferredReleaseQueue`
  - owns resources waiting for GPU-safe destruction;
  - normal-frame polling uses `GpuFence.awaitCompletion(0L)` only;
  - resources remain alive while the fence is incomplete;
  - retirement/release counters are retained for diagnostics;
  - shutdown uses a bounded completion budget and does not intentionally destroy still-in-flight resources.

- `GpuResourceLifetimeProbe`
  - one-shot validation only;
  - allocates a 64-byte Obsidian-owned GPU buffer;
  - writes data and records a real fence through Minecraft 26.2's command encoder;
  - submits once;
  - immediately retires the buffer into `DeferredReleaseQueue`;
  - destroys it only after the fence reports completion.

Exact Minecraft 26.2 API inspection confirmed:

- `CommandEncoder.createFence()`.
- `GpuFence.awaitCompletion(long timeoutNanos)` and `close()`.
- `GpuBuffer.close()` / `isClosed()`.
- normal steady-state retirement can therefore poll fences with timeout `0L` rather than waiting.

### dev2 real-machine runtime result

Real test on 2026-08-20 succeeded using Windows 11 / RX 6800 XT / Minecraft 26.2 Vulkan.

Observed sequence:

1. Fabric loaded `obsidian 0.1.0-phase1-dev2`.
2. Minecraft selected Vulkan on the RX 6800 XT.
3. Obsidian attached successfully and armed the resource-lifetime foundation.
4. `FrameCoordinator` reported `contextSlots=3` and explicitly logged that GPU safety is fence-gated.
5. The 64-byte resource-lifetime probe submitted and retired its buffer on frame 1 with `pendingRetirements=1`.
6. A later zero-timeout fence poll in the same frame iteration reported completion; the buffer was released safely.
7. The player entered a single-player world and normal chunk/resource loading continued.
8. Shutdown after 1647 frames reported `retiredResources=1, releasedResources=1, pending=0`.
9. Process exit code was 0.

Important interpretation: `released on frame 1 after 0 frame(s)` does not mean Obsidian blocked. The release path uses a zero-timeout fence poll; the submitted work simply completed before the later poll during the same frame iteration.

This proves the resource-lifetime boundary:

`Obsidian resource -> Minecraft GpuDevice submission -> real GpuFence -> DeferredReleaseQueue -> destruction only after completion`

## Architecture boundary now proven

Current proven boundary:

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame-context bookkeeping -> controlled GPU submission -> fence-gated resource retirement`

Obsidian still does not:

- create a second Vulkan device or swapchain;
- own terrain rendering;
- infer GPU completion from frame count;
- perform routine device-wide waits;
- allocate or upload terrain geometry.

## Next Phase 1 milestone

Build bounded staging/upload ownership before terrain uses the system.

Required pieces:

1. Inspect the exact Minecraft 26.2 buffer mapping/copy interfaces used for host-visible upload and device-local copy destinations.
2. Implement a fixed-capacity staging ring or equivalent bounded staging arena.
3. Suballocate aligned upload slices without per-upload heap churn on the hot path.
4. Batch copy commands into owned submissions rather than issuing many tiny submissions.
5. Reclaim staging space only when the submission fence/completion primitive is safe.
6. Apply backpressure when the ring is full instead of allocating unbounded temporary buffers.
7. Keep shutdown bounded and safe.
8. Add counters for bytes staged, bytes submitted, high-water usage, stalls/backpressure events, and reclaimed bytes.
9. Validate with a small non-visual upload/copy workload on the RX 6800 XT before terrain data enters the path.

Target success criterion:

- write deterministic bytes into bounded host-visible staging storage;
- copy them into an Obsidian-owned GPU destination through Minecraft's active Vulkan device;
- submit in a controlled batch;
- fence the submission;
- reclaim the staging region only after completion;
- retire the destination safely;
- enter a world and shut down with no pending resources or staging allocations.

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

PR #4 is runtime validated and can be merged after the successful dev2 test is recorded in the append-only attempt history. Continue Phase 1 on a fresh branch with bounded staging/upload infrastructure. Do not begin terrain replacement until staging ownership, copy batching, and fence-gated reclamation have passed the same real-machine validation loop.
