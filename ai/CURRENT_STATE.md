# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev4, merge commit `7d2e92c9838192d7f85377fa022d7f5327345643`
- Active development branch: `phase1/frame-graph-profiler`
- Active draft PR: #7, `Phase 1: frame graph and integrated GPU profiler`
- Current development version: `0.1.0-phase1-dev5`

## Reference runtime

Validated reference machine:

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

Validated Fabric bootstrap, Vulkan selection, Minecraft `GpuDevice` attachment, capability reporting, world entry, and clean shutdown on the real RX 6800 XT machine.

### Phase 1 dev1 - VALIDATED and merged through PR #3

Validated the `Minecraft.renderFrame(boolean)` lifecycle seam, fixed-allocation CPU frame timing ring, controlled Obsidian GPU command submission through Minecraft's Vulkan device, nonblocking timestamp result polling, world entry, and clean shutdown.

### Phase 1 dev2 - VALIDATED and merged through PR #4

Validated three-slot frame contexts, real `GpuFence` completion tracking, zero-timeout steady-state polling, deferred GPU destruction, bounded shutdown cleanup, and clean real-machine accounting.

### Phase 1 dev3 - VALIDATED and merged through PR #5

Validated bounded persistent staging, batched buffer copies, explicit backpressure, deterministic readback, completion-gated ring reclamation, and clean shutdown.

### Phase 1 dev4 - VALIDATED and merged through PR #6

Validated reusable device-preferred GPU geometry storage/suballocation on the real RX 6800 XT machine: generation-safe handles, bounded allocation failure, multi-frame real-fence retirement, safe physical span reuse, stale-handle rejection, deterministic readback, full free-span coalescing, world entry, and clean shutdown.

Dev4 was squash-merged as `7d2e92c9838192d7f85377fa022d7f5327345643` with `[no-release]`. Runtime evidence is in `ai/attempts/A-0029-dev4-runtime-success.md`.

## Phase 1 dev5 - ACTIVE; CI validated, runtime pending

Goal: establish Obsidian-owned pass orchestration and GPU profiling before terrain rendering begins.

### Exact Minecraft 26.2 timestamp findings

Exact Loom-resolved bytecode/API inspection confirmed:

- `GpuDevice.createTimestampQueryPool(int)` returns `com.mojang.blaze3d.systems.GpuQueryPool`.
- `GpuQueryPool` exposes `size()`, `getValue(int)`, `getValues(int,int)`, and `close()`.
- `CommandEncoder.writeTimestamp(GpuQueryPool,int)` records timestamp work into a command encoder.
- `DeviceInfo.timestampPeriod()` provides the timestamp tick-to-nanosecond conversion.
- Minecraft's Vulkan query result path requests availability and does not use the Vulkan WAIT flag; unavailable results become empty optionals rather than blocking.
- `VulkanCommandEncoder.writeTimestamp` host-resets the exact query slot with `vkResetQueryPool` before recording `vkCmdWriteTimestamp2KHR`, so query-slot reuse does not require a separate reset submission on the current backend.
- the public `getValues` API allocates an `OptionalLong[]` and result wrappers. It is nonblocking but must not become an every-frame hot-path allocation source.
- Obsidian enforces timestamp query indices inside `[0, pool.size())` regardless of permissive-looking Java bytecode bounds.

The temporary API inspection workflow has been removed. Findings are preserved in `ai/attempts/A-0030-dev5-framegraph-api-inspection.md`.

### Implemented `FixedFrameGraph`

- maximum 16 passes;
- initialization-time pass definitions and dependency masks;
- primitive arrays for CPU begin/last/total timing and execution counts;
- no per-execution graph-node allocation;
- dependency/order validation and all-pass completion validation.

### Implemented `GpuTimestampProfiler`

- two timestamp slots per pass;
- start/end timestamps encoded around useful pass work;
- tick conversion through `timestampPeriod`;
- nonblocking result availability polling;
- poll/unavailable counters;
- dev5 validation is one-shot; production repeated result collection is bounded/sampled by D-0022.

### Implemented `FrameGraphCommandStream`

- uses the validated bounded staging batch admission path;
- owns one `CommandEncoder` per graph execution;
- combines graph ordering, CPU pass timing, GPU timestamp writes, upload copies, and dependent copies in the same command stream;
- submits exactly once through `StagingUploadArena.submitBatch`, so useful work, timestamps, and completion fence are in the same submission;
- profiler-only submissions are zero;
- timestamp results are polled only after that same useful submission is known complete.

### Implemented `FrameGraphProbe`

Two-pass nonvisual graph:

1. `validation-upload`: stage deterministic 256-byte data into destination `[0,256)`.
2. `validation-dependent-copy`: depends on pass 0 and GPU-copies `[0,256)` to `[512,768)` in the same command stream.

Expected runtime invariants:

- graph passes = 2;
- executed mask = `3`;
- one declared dependency;
- useful submissions = 1;
- profiler-only submissions = 0;
- staging submitted/reclaimed = `256/256` bytes;
- staging high-water = 256;
- staging backpressure = 0;
- deterministic verification covers 512 bytes total;
- CPU/GPU pass times and total GPU time are reported but exact values are hardware/run dependent;
- unavailable query polls may be zero or positive and are not themselves a failure;
- device arena remains initialized but unused by this probe: usage/allocation/retirement counters remain zero, free spans=1, largest free=`524288`, fragmentation=0.

### CI/package status

- initial dev5 implementation compiled successfully against Java 25 / Gradle 9.5.1 / exact Minecraft 26.2;
- completed dev4 probe and temporary API workflow were removed;
- shutdown diagnostics preserve the graph result before probe close;
- cleaned/documented head `c2cead6b41e010f9a00e151ad8635acf80662f4e` passed GitHub Actions run `32371303571` with build and artifact upload successful;
- the resulting CI JAR was inspected: version is `0.1.0-phase1-dev5`, all four graph/profiler classes and `FrameCoordinator` are present, and `DeviceArenaProbe` is absent;
- evidence is recorded in `ai/attempts/A-0032-dev5-final-ci-package.md`.

This file and A-0032 are continuity-only changes after that verified build. Run one final CI on this exact documentation head before distributing the canonical dev5 test artifact.

Evidence:

- `ai/attempts/A-0030-dev5-framegraph-api-inspection.md`
- `ai/attempts/A-0031-dev5-framegraph-profiler-implementation.md`
- `ai/attempts/A-0032-dev5-final-ci-package.md`
- decisions D-0021 and D-0022 in `ai/DECISIONS.md`.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame contexts -> completion-gated lifetime -> bounded persistent staging -> device-preferred arena -> FixedFrameGraph -> owned command stream -> useful submission with embedded timestamp ranges`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own terrain rendering;
- infer GPU completion from CPU frame count;
- perform routine device-wide waits;
- poll allocating timestamp-result wrappers every frame;
- upload or draw actual chunk meshes.

## Dev5 real-machine success criteria

On the Windows 11 / RX 6800 XT Vulkan instance:

1. `obsidian 0.1.0-phase1-dev5` loads.
2. Frame coordinator reports context slots=3, staging capacity=262144, device arena capacity=524288, graph passes=2.
3. Graph submission reports passes=2, dependencies=1, usefulSubmissions=1, stagingPayloadBytes=256, profilerOnlySubmissions=0.
4. The useful submission completes without an explicit routine wait.
5. Timestamp results become available; pass/total GPU nanoseconds are reported.
6. Graph verification reports executedMask=3 and copiedBytes=512.
7. Both destination ranges verify byte-for-byte.
8. Staging finishes with submitted/reclaimed=256/256, high-water=256, backpressure=0, pending=0.
9. Device-arena counters remain unused/clean and the full 524288-byte free span remains intact.
10. User enters a world normally and shutdown reports `graphResult=VERIFIED`, usefulSubmissions=1, profilerOnlySubmissions=0, no pending GPU work, and process exit code 0.

Terrain replacement remains intentionally inactive until this orchestration/profiling layer passes the same real-machine validation loop.