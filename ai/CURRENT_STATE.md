# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev3, merge commit `de45e80f96c841372c8263deafd0506545f814cc`
- Active development branch: `phase1/device-arena`
- Active PR: #6, `Phase 1: device-preferred geometry arena and suballocator`
- Current development version: `0.1.0-phase1-dev4`

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

Validated three-slot frame contexts, real `GpuFence` completion tracking, zero-timeout steady-state polling, deferred GPU destruction, bounded shutdown cleanup, and clean real-machine accounting (`retiredResources=1`, `releasedResources=1`, `pending=0`).

### Phase 1 dev3 - VALIDATED and merged through PR #5

Validated bounded persistent staging, batched buffer copies, explicit backpressure, deterministic readback, completion-gated ring reclamation, and clean shutdown. Real-machine result included `stagingSubmittedBytes=256`, `stagingReclaimedBytes=256`, `stagingBackpressureEvents=1`, `pendingUploadBatches=0`, exit code 0.

### Phase 1 dev4 - VALIDATED; pending merge through PR #6

Goal: establish reusable device-preferred GPU storage/suballocation before real chunk meshes use the upload path.

Implemented and now runtime validated:

- one fixed 512 KiB non-mapped `COPY_DST | COPY_SRC | VERTEX | INDEX` backing buffer on Minecraft 26.2's device-preferred Vulkan/VMA allocation path;
- 4096 preallocated allocation metadata slots;
- packed `(slot,generation)` 64-bit allocation handles;
- stale-handle rejection after a freed slot/span is reused;
- best-fit aligned allocation over sorted/coalescing free spans;
- bounded allocation failure instead of fallback arena growth;
- fixed completion-gated retirement metadata with zero-timeout steady-state fence polling;
- allocation-free retirement validation/state transition;
- used/free/high-water, allocation, retirement, stale-handle, largest-free-block and fragmentation metrics;
- uploads reuse the validated `StagingUploadArena` rather than introducing a second upload mechanism.

Exact Minecraft 26.2 inspection established that `VulkanGpuBuffer.Direct` starts from VMA's automatic device-preferred allocation policy and only adds host-visible/coherent requirements when map usages are requested. Portable documentation therefore says **device-preferred** rather than promising literal discrete VRAM on every architecture.

### Dev4 real-machine result - SUCCESS

The user tested `0.1.0-phase1-dev4` on the reference Windows 11 / RX 6800 XT Vulkan machine.

Observed sequence:

1. Obsidian loaded the correct dev4 build and armed the device-preferred arena foundation.
2. Frame coordinator reported staging capacity `262144` and device arena capacity `524288`.
3. A/B/C allocated successfully; initial used/high-water was `212992` bytes.
4. One deliberately impossible arena allocation failed without arena growth.
5. Initial A/B/C staging payload was 768 bytes.
6. B readback/retirement was submitted on frame 1 with one pending free/retirement batch.
7. On frame 2, after real fence completion, D reused B's exact offset `65536` and slot `1`; generation advanced `1 -> 2`.
8. The old B handle was deliberately used and correctly rejected (`staleHandleRejections=1`).
9. After D reuse: used bytes `196608`, free spans 2, fragmentation `50` permille.
10. Final A/C/D readback/retirement was submitted on frame 3.
11. Final verification completed on frame 4, with all deterministic bytes correct.
12. Final allocator state: allocations=4, failures=1, retired=4, reclaimed=4, staleHandleRejections=1, usedBytes=0, freeSpans=1, largestFree=`524288`, fragmentation=0.
13. Staging totals were submitted/reclaimed `1024/1024`, high-water 768, backpressure 0, pending batches 0.
14. The user entered a single-player world normally.
15. Shutdown after 2251 frames had zero pending upload/arena/generic retirement work and process exit code 0.

This is stronger than earlier same-frame probes because B retirement/reuse crossed a real frame boundary. It confirms the arena is completion-gated rather than frame-count-gated.

Evidence:

- `ai/attempts/A-0027-device-arena-api-inspection.md`
- `ai/attempts/A-0028-dev4-device-arena-implementation.md`
- `ai/attempts/A-0029-dev4-runtime-success.md`
- decisions D-0019 and D-0020 in `ai/DECISIONS.md`

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame contexts -> controlled submissions -> completion-gated lifetime -> bounded persistent staging -> batched copies/backpressure -> device-preferred geometry arena -> generation-safe suballocation -> completion-gated span reuse/coalescing`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own terrain rendering;
- infer GPU completion from frame count;
- perform routine device-wide waits;
- upload actual chunk meshes;
- claim the current O(free-span-count) best-fit allocator is the final high-scale production allocator.

## Next Phase 1 milestone: dev5 frame graph / command-stream profiler

After merging PR #6 with `[no-release]`, create a fresh branch from merged `main` and establish Obsidian-owned orchestration before terrain rendering.

Required dev5 pieces:

1. Inspect exact Minecraft 26.2 timestamp query/result and command encoder semantics again on the current dependency set.
2. Create a small fixed-capacity render/frame graph representation with declared pass order/dependencies and no per-frame graph-node allocation on the hot path.
3. Introduce an Obsidian-owned command-stream/submission object so upload/validation work and later rendering passes can share deliberate submissions.
4. Integrate GPU timestamp writes into those owned command streams; do not add profiler-only per-frame submissions.
5. Track CPU pass timing, GPU timestamp ranges, submission count, pass count, and query availability/missed samples.
6. Keep normal query polling nonblocking.
7. Add a nonvisual graph validation workload such as `upload -> copy/validation -> completion`, with timestamps surrounding owned work and deterministic verification.
8. Prove graph execution ordering, one/few deliberate submissions, timestamp result retrieval, world entry, and clean shutdown on the reference machine.
9. Keep terrain replacement inactive until this orchestration/profiling layer is validated.

The core rule from D-0014 remains active: profiling must measure Obsidian work from inside command streams it already owns, not create routine extra submissions solely to measure them.