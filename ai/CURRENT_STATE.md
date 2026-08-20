# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Validated Phase 1 foundation merge: `5fffaae19f47b78393cc79aa7c11884ff1b54694`
- Active development branch: `phase1/resource-lifetime`
- Active draft PR: #4, `Phase 1: frame contexts and resource lifetime`
- Current development version: `0.1.0-phase1-dev2`

## Phase 0 - COMPLETE

Phase 0 is compile- and runtime-validated on Windows 11 / AMD Radeon RX 6800 XT with Minecraft 26.2 Vulkan, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, and Java 25.0.1.

## Phase 1 dev1 - VALIDATED AND MERGED

`0.1.0-phase1-dev1` proved the first controlled Obsidian GPU command path through Minecraft's real Vulkan device:

`Minecraft.renderFrame -> FrameCoordinator -> GpuDevice -> CommandEncoder -> Vulkan GPU execution -> nonblocking timestamp result`

Real RX 6800 XT validation succeeded: one probe submission on frame 1, timestamp completion observed without an explicit blocking wait, world entry succeeded, coordinator remained active for 2107 frames, and Minecraft exited with code 0.

PR #3 was squash-merged to `main` as `5fffaae19f47b78393cc79aa7c11884ff1b54694` using `[no-release]`; dev1 remains a development milestone rather than a public release.

Durable constraints established by dev1:

- keep Minecraft's existing Vulkan device/swapchain ownership until a concrete requirement proves deeper takeover is necessary;
- do not create routine profiler-only GPU submissions;
- frame-critical paths should remain allocation-free or explicitly justified.

## Phase 1 dev2 - COMPILE VALIDATED, RUNTIME TEST PENDING

Goal: prove safe GPU resource lifetime management before terrain owns any GPU memory.

### Exact Minecraft 26.2 API findings

A temporary GitHub Actions `javap` probe inspected the exact Loom-resolved client JAR and was removed afterward.

Confirmed relevant APIs:

- `GpuDevice.createBuffer(Supplier<String>, int usage, long size)`
- `GpuBuffer.USAGE_COPY_DST`
- `GpuBuffer.slice(...)`
- `GpuBuffer.isClosed()` / `GpuBuffer.close()`
- `CommandEncoder.writeToBuffer(...)`
- `CommandEncoder.createFence()`
- `CommandEncoder.submit()`
- `GpuFence.awaitCompletion(long timeoutNanos)` / `GpuFence.close()`

Routine retirement polling uses `GpuFence.awaitCompletion(0L)`, so Obsidian does not intentionally wait for the GPU during normal frames.

### Implemented dev2 infrastructure

- `FrameContext`
  - preallocated per-slot CPU frame metadata;
  - monotonically increasing frame serial;
  - explicitly not a GPU-completion signal.

- `FrameContextRing`
  - three preallocated rotating slots by default;
  - no per-frame allocation;
  - slot rotation never implies a resource is safe to reuse/destroy.

- `DeferredReleaseQueue`
  - fence-gated FIFO retirement queue for one ordered submission domain;
  - initial capacity 64, only grows when exhausted;
  - steady-state polling uses zero-timeout fence checks;
  - closes a resource and its fence only after completion;
  - bounded 2-second shutdown cleanup; if completion cannot be established, it refuses unsafe destruction and leaves the resource for Minecraft device shutdown.

- `GpuResourceLifetimeProbe`
  - one-shot, non-visual validation;
  - creates a 64-byte `USAGE_COPY_DST` GPU buffer;
  - writes a small payload through one command encoder;
  - creates a fence, submits once, and immediately retires the buffer to `DeferredReleaseQueue`;
  - expects the queue to close the buffer only after the fence signals;
  - keeps the upload `ByteBuffer` alive until release to avoid CPU-source-lifetime ambiguity.

- `FrameCoordinator`
  - now rotates frame contexts;
  - polls deferred retirement at frame end;
  - reports retired/released/pending resource counts at shutdown.

The validated dev1 timestamp probe was removed; dev2 replaces it with the resource-lifetime validation probe.

### Build evidence

Draft PR #4 is based on `main` merge `5fffaae19f47b78393cc79aa7c11884ff1b54694`.

Exact resource API inspection workflow run `32363250144` completed successfully. The temporary inspection workflow was removed from the development branch.

Dev2 code head `4487cdf67d49c0224673119cadbd8ac99078c613` passed GitHub Actions workflow run `32363597789` on Java 25 / Gradle 9.5.1, including artifact upload. The release job was intentionally skipped.

## Dev2 runtime validation still required

Before PR #4 may be merged, test `Obsidian-0.1.0-phase1-dev2` on the reference Vulkan machine and confirm:

1. correct dev2 version loads;
2. Minecraft uses Vulkan on the RX 6800 XT;
3. frame coordinator reports three context slots and fence-gated GPU safety;
4. resource lifetime probe submits/retires exactly once;
5. the deferred queue later releases the resource after fence completion;
6. a world can be entered normally;
7. shutdown reports `retiredResources=1`, `releasedResources=1`, `pending=0`;
8. process exits with code 0 and no rendering anomaly is observed.

Failure evidence to preserve includes probe failure, never-released retirement, nonzero pending count at normal shutdown, validation errors, or a crash.

## Architecture boundary

Current proven boundary:

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> controlled GPU submission`

Dev2 is testing the next boundary:

`controlled GPU submission -> explicit fence completion -> safe deferred resource destruction`

Obsidian still does not create a second Vulkan device/swapchain or replace terrain rendering.

## Next after dev2 validation

After real runtime validation and merge, continue Phase 1 with bounded staging/upload infrastructure and off-hot-path profiler snapshots/percentiles. Do not move terrain ownership onto Obsidian until resource lifetime and upload synchronization are proven.
