# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev4, merge commit `7d2e92c9838192d7f85377fa022d7f5327345643`
- Active development branch: `phase1/frame-graph-profiler`
- Active PR: #7, `Phase 1: frame graph and integrated GPU profiler`
- Current development version: `0.1.0-phase1-dev5`
- Dev5 status: **runtime validated; pending merge with `[no-release]`**

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

Validated reusable device-preferred GPU geometry storage/suballocation: packed `(slot,generation)` identities, bounded allocation failure, multi-frame real-fence retirement, exact span reuse only after completion, stale-handle rejection, deterministic readback, complete free-span coalescing, world entry, and clean shutdown.

Dev4 was squash-merged as `7d2e92c9838192d7f85377fa022d7f5327345643` with `[no-release]`. Runtime evidence is `ai/attempts/A-0029-dev4-runtime-success.md`.

### Phase 1 dev5 - VALIDATED; pending merge through PR #7

Dev5 establishes Obsidian-owned pass orchestration and integrated GPU timestamp profiling before terrain rendering.

Implemented:

- `FixedFrameGraph`: fixed 16-pass maximum; initialization-time pass definitions/dependency masks; primitive timing/execution arrays; no per-execution graph-node allocation; dependency/order validation.
- `GpuTimestampProfiler`: two timestamp slots/pass; timestamps encoded around useful work; `timestampPeriod` conversion; nonblocking availability polling; poll/unavailable metrics.
- `FrameGraphCommandStream`: one owned `CommandEncoder` for the validation graph; graph ordering, timestamps, staging upload, dependent copy, and completion fence all share the useful submission; profiler-only submissions remain zero.
- `FrameGraphProbe`: pass 0 stages deterministic 256 bytes; pass 1 depends on pass 0 and copies that range to a second destination in the same command stream; both ranges are verified.

Exact Minecraft 26.2 findings remain:

- timestamp query classes are under `com.mojang.blaze3d.systems`;
- Vulkan query retrieval is availability-based and does not use the WAIT flag;
- `VulkanCommandEncoder.writeTimestamp` resets the exact query slot before `vkCmdWriteTimestamp2KHR`;
- public `GpuQueryPool.getValues()` is nonblocking but allocates result wrappers, so continuous production collection remains bounded/sampled under D-0022.

#### Dev5 compile/package result

- exact pre-runtime-evidence source/docs head `33fc90941b4b728195f641060173d74328e2556f` passed GitHub Actions run `32371507539` on Java 25 / Gradle 9.5.1;
- artifact upload succeeded;
- CI JAR reported `0.1.0-phase1-dev5`;
- graph/profiler classes were present and completed `DeviceArenaProbe` was absent;
- test JAR SHA-256: `e62044f9556f97c90888ed2bcef36e784cb039126bd3c5cd10e358ed104bfe7e`.

#### Dev5 real-machine result - SUCCESS

The user tested the exact CI-built dev5 JAR on the reference Windows 11 / RX 6800 XT Vulkan machine.

Observed:

- correct dev5 build loaded and attached to Vulkan;
- context slots = 3;
- staging capacity = 262144 bytes;
- device arena capacity = 524288 bytes;
- graph passes = 2;
- graph submission on frame 1: passes=2, dependencies=1, usefulSubmissions=1, stagingPayloadBytes=256, profilerOnlySubmissions=0, timestampPeriod=10 ns/tick;
- graph verification on frame 1: executedMask=3, queryPolls=1, unavailablePolls=0, usefulSubmissions=1, profilerOnlySubmissions=0, copiedBytes=512;
- CPU and GPU pass timings resolved successfully. Logger output uses locale grouping (`315.500` means 315500 ns, etc.); these values validate timing plumbing and are not renderer benchmarks;
- staging submitted/reclaimed = 256/256 bytes, high-water=256, backpressure=0, pending=0;
- device arena remained intentionally unused/clean: all usage/allocation/retirement counters 0, freeSpans=1, largestFree=524288, fragmentation=0;
- user entered a single-player world normally;
- shutdown after 2649 frames reported `graphResult=VERIFIED`, one useful submission, zero profiler-only submissions, no pending GPU work;
- process exited with code 0.

Runtime evidence: `ai/attempts/A-0033-dev5-runtime-success.md`.

An observational vanilla behavior was also captured: Mojang's Chunk Sections UBO reactively grew through capacities 2 -> 4 -> 8 -> 16 -> 32 -> 64 -> 128 -> 256 -> 512 -> 1024 -> 2048 during world entry. This is future profiling evidence only and is not currently attributed as a performance defect.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame contexts -> completion-gated lifetime -> bounded persistent staging -> device-preferred arena -> generation-safe suballocation -> FixedFrameGraph -> owned command stream -> useful submission with embedded timestamp ranges`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own terrain rendering;
- infer GPU completion from CPU frame count;
- perform routine device-wide waits;
- poll allocating timestamp-result wrappers every frame;
- issue an Obsidian-owned graphics draw;
- upload/draw actual chunk meshes.

## Immediate action

1. Final-CI the runtime-evidence head of PR #7.
2. Promote and merge PR #7 with `[no-release]`.
3. Create Phase 1 dev6 branch from the resulting `main` merge commit.
4. Inspect exact Minecraft 26.2 graphics shader/pipeline/render-pass/texture/readback APIs and Vulkan backend behavior before implementation.
5. Implement the first Obsidian-owned graphics draw as an offscreen/nonvisual validation if the public abstraction supports a deterministic readback target cleanly.
6. Dev6 should prove shader resource/compilation path, graphics pipeline creation, vertex/index state, render-target ownership, an actual draw call, deterministic output verification, graph-integrated CPU/GPU timing, safe lifetime/completion handling, world entry, and clean shutdown.
7. Keep terrain replacement inactive until dev6 passes real-machine validation.

## Relevant durable decisions

- D-0014: profiling must not create routine extra GPU submissions.
- D-0015: preserve Minecraft Vulkan device/swapchain ownership until evidence demands deeper access.
- D-0016: GPU resource reclamation is completion-gated, never frame-count-gated.
- D-0017/D-0018: staging is bounded/backpressured and does not use Mojang's blocking ring policy.
- D-0019/D-0020: geometry is device-preferred and allocation identity is generation-safe.
- D-0021: profiling timestamps live inside owned useful command streams.
- D-0022: timestamp result collection is bounded/sampled because the public wrapper allocates.