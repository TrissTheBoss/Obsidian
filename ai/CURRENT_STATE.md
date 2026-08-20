# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev5, merge commit `d3c0a465cb10a648f5f1c241890b3a6eacf52b36`
- Active development branch: `phase1/first-draw`
- Active PR: #8, `Phase 1: first Obsidian-owned graphics draw`
- Current development version: `0.1.0-phase1-dev6`
- Dev6 status: **runtime validated; pending merge with `[no-release]`**

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

Validated Fabric bootstrap, Vulkan selection, Minecraft `GpuDevice` attachment, capability reporting, world entry, and clean shutdown.

### Phase 1 dev1 - VALIDATED / merged PR #3

Validated the `Minecraft.renderFrame(boolean)` lifecycle seam, fixed CPU frame timing ring, controlled GPU submission, timestamp result retrieval, world entry, and shutdown.

### Phase 1 dev2 - VALIDATED / merged PR #4

Validated three-slot frame contexts, real `GpuFence` completion tracking, zero-timeout steady-state polling, deferred destruction, and clean shutdown accounting.

### Phase 1 dev3 - VALIDATED / merged PR #5

Validated bounded persistent staging, batched copies, explicit backpressure, deterministic readback, and completion-gated ring reclamation.

### Phase 1 dev4 - VALIDATED / merged PR #6

Validated a device-preferred geometry arena with generation-safe handles, bounded allocation failure, multi-frame fence-gated reuse, stale-handle rejection, deterministic readback, and complete free-span coalescing.

### Phase 1 dev5 - VALIDATED / merged PR #7

Validated fixed pass orchestration and integrated GPU timestamp profiling: one useful owned submission, zero profiler-only submissions, nonblocking timestamp retrieval, deterministic dependent GPU copy/readback, world entry, and clean shutdown. Dev5 was squash-merged as `d3c0a465cb10a648f5f1c241890b3a6eacf52b36` with `[no-release]`. Runtime evidence: `ai/attempts/A-0033-dev5-runtime-success.md`.

### Phase 1 dev6 - VALIDATED; pending merge through PR #8

Goal achieved: first actual Obsidian-owned graphics draw without touching the presented Minecraft framebuffer.

Validated implementation:

- exact Minecraft 26.2 public graphics/API and Vulkan cache inspection supported staying inside Blaze3D;
- private 16x16 `RGBA8_UNORM` target, never presented;
- custom in-memory vertex/fragment shaders;
- pipeline precompiled through `GpuDevice.precompilePipeline` and reused through the backend pipeline cache;
- three POSITION vertices, three 16-bit indices;
- graph passes: geometry upload -> offscreen indexed draw -> texture readback;
- all uploads, timestamps, render pass, indexed draw, readback and completion fence in one useful submission;
- profiler-only submissions = 0;
- deterministic pixel verification.

Real-machine result on the reference RX 6800 XT:

- correct `0.1.0-phase1-dev6` loaded on Vulkan;
- graphPasses=3;
- usefulSubmissions=1;
- pipelineValid=true;
- drawCalls=1, triangles=1;
- vertex/index payload=36/6 bytes, staging payload=42 bytes;
- graph verified with executedMask=7;
- center `(8,8)` = `255/0/255/255` magenta;
- corner `(0,0)` = `0/0/0/255` black;
- pixelsVerified=2;
- timestamp queries resolved nonblockingly;
- staging submitted/reclaimed=42/42 bytes;
- staging high-water=54 bytes due to 16-byte alignment before the index reservation;
- backpressure=0, pending upload batches=0;
- device geometry arena remained completely unused/clean with one full 524288-byte free span;
- user entered a single-player world normally;
- shutdown after 2543 frames had no pending work;
- process exited with code 0.

Runtime evidence: `ai/attempts/A-0037-dev6-runtime-success.md`.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> completion-gated lifetime -> bounded staging -> device-preferred arena -> FixedFrameGraph -> owned command stream -> embedded timestamps -> public Blaze3D graphics pipeline/render pass -> verified indexed draw/readback`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own or modify the presented framebuffer;
- own terrain rendering;
- upload/draw actual chunk meshes;
- perform routine device-wide waits;
- poll allocating timestamp wrappers every frame;
- require native Vulkan access for its current graphics path.

## Immediate next action

1. Final-CI the dev6 runtime-evidence head of PR #8.
2. Promote and merge PR #8 with `[no-release]`.
3. Start Phase 1 dev7 from the resulting `main` merge commit.
4. Inspect exact Minecraft 26.2 indexed-indirect drawing support and backend semantics before implementation.
5. Connect real `DeviceGeometryArena` allocations to a validated offscreen graphics draw rather than private one-off vertex/index buffers.
6. Add bounded indirect-command storage, one/few indirect draws, embedded timestamps, deterministic pixel readback, completion-gated retirement and clean accounting.
7. Keep terrain replacement inactive until dev7 passes real-machine validation.

## Terrain meshing roadmap note

Greedy meshing is now a required final-product direction. Detailed research/design is being added during dev7 planning. It belongs in the CPU terrain mesher after the one-chunk correctness path is established and before large-scale terrain throughput work. The preferred performance target is a bitmask/binary greedy mesher with merge keys that preserve visual/material correctness, rather than a naive per-face rectangle scan.

## Relevant durable decisions

- D-0014: profiling must not create routine extra GPU submissions.
- D-0015: preserve Minecraft Vulkan device/swapchain ownership until evidence demands deeper access.
- D-0016: reclamation is real-completion-gated, never frame-count-gated.
- D-0017/D-0018: staging is bounded/backpressured and avoids Mojang's blocking ring policy.
- D-0019/D-0020: geometry is device-preferred and allocation identity is generation-safe.
- D-0021: timestamps live inside useful owned command streams.
- D-0022: timestamp results are bounded/sampled because the public wrapper allocates.
- D-0023: the initial graphics path remains on public Blaze3D until evidence justifies native Vulkan access.