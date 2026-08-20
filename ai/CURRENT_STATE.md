# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev2, merge commit `3d2366a5c07f819a264f5f02dd2f3df9c5ec6fc0`
- Active development branch: `phase1/staging-upload`
- Active PR: #5, `Phase 1: bounded staging and upload infrastructure`
- Current development version: `0.1.0-phase1-dev3`

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

### Phase 1 dev3 - VALIDATED; pending merge through PR #5

Goal: establish bounded staging/upload ownership before terrain starts producing geometry.

Exact Minecraft 26.2 inspection confirmed:

- `GpuBuffer` supports map-read/map-write and copy source/destination usages.
- persistent write mapping is available through `GpuBufferSlice.MappedView`.
- `CommandEncoder.copyToBuffer(sourceSlice, destinationSlice)` is source-first.
- `CommandEncoder.createFence()` supplies batch completion tracking.
- the reference RX 6800 XT reports persistent mapping support.
- Mojang's `StagingBuffer.PersistentlyMapped` ultimately uses `MappableRingBuffer`, whose wrap path can call `GpuFence.awaitCompletion(Long.MAX_VALUE)` when cycling to a busy buffer. Obsidian therefore owns its own nonblocking staging admission/reclamation policy above Minecraft's lower-level GPU abstractions.

Implemented `StagingUploadArena`:

- one fixed-capacity persistently mapped staging buffer;
- validation capacity 256 KiB;
- `MAP_WRITE | COPY_SRC` usage;
- 16-byte aligned monotonic virtual write/reclaim cursors;
- wrap padding counted as occupied space;
- fixed 64-entry in-flight batch table;
- no fallback allocation when safe space is unavailable;
- explicit backpressure metrics;
- zero-timeout steady-state fence polling;
- completion-gated ring-space reclamation;
- bounded shutdown behavior.

Implemented `GpuUploadProbe`:

- stages two deterministic 128-byte payloads;
- encodes both copies into one submission;
- deliberately requests an impossible full-capacity allocation while 256 bytes are occupied to prove explicit backpressure;
- fences the batch;
- reclaims staging only after completion;
- maps the destination for read and verifies every copied byte.

Compile validation:

- exact documented dev3 head `9a4fa580b8fdaed0030b2242b491789aa7c37a11` passed GitHub Actions run `32367096172` on Java 25 / Gradle 9.5.1;
- build and artifact upload succeeded.

Real-machine runtime validation on 2026-08-20: `SUCCESS`.

Observed:

1. `obsidian 0.1.0-phase1-dev3` loaded on Minecraft 26.2 Vulkan / RX 6800 XT.
2. Staging capacity reported `262144` bytes.
3. One batch contained exactly two copies totaling 256 payload bytes.
4. High-water use was 256 bytes.
5. Exactly one deliberate backpressure event occurred.
6. The batch completed through nonblocking fence polling.
7. Reclaimed bytes reached 256 and pending batches reached 0.
8. Deterministic destination readback verified successfully.
9. The user entered a single-player world normally.
10. Shutdown after 3037 frames reported `stagingSubmittedBytes=256`, `stagingReclaimedBytes=256`, `stagingHighWater=256`, `stagingBackpressureEvents=1`, `pendingUploadBatches=0`, `retiredResources=0`, `releasedResources=0`, `pendingRetirements=0`; process exit code was 0.

Evidence is preserved in `ai/attempts/A-0026-dev3-runtime-success.md`.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame contexts -> controlled submissions -> fence-gated resource lifetime -> bounded persistently mapped staging -> batched copies -> explicit backpressure -> completion-gated reclamation`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own terrain rendering;
- infer GPU completion from frame count;
- perform routine device-wide waits;
- upload actual chunk meshes;
- own a reusable device-local geometry arena.

## Next Phase 1 milestone: dev4 device-local arena/suballocator

After merging PR #5 with `[no-release]`, create a fresh branch from merged `main` and implement a reusable Obsidian-owned device-local geometry arena before terrain uses it.

Required dev4 pieces:

1. Inspect exact Minecraft 26.2 buffer usage/slice/copy behavior needed for large geometry buffers and readback validation.
2. Create one fixed-size GPU arena suitable for future vertex/index/metadata allocations without host mapping in the normal path.
3. Implement aligned suballocation with stable allocation handles carrying slot/generation identity so stale handles cannot silently reference reused memory.
4. Support freeing allocations only after their last-use completion signal is safe; never reuse freed spans merely because CPU frames advanced.
5. Coalesce adjacent free spans and expose used/free/high-water/largest-free-block/fragmentation metrics.
6. Define bounded failure behavior when no suitable span exists; do not grow or allocate fallback arena buffers during the validation milestone.
7. Use the validated staging arena to upload deterministic data into multiple arena allocations in one or few controlled batches.
8. Free allocations in a nontrivial order after completion, allocate replacement data, and prove safe span reuse/generation changes.
9. Copy selected arena ranges to a readback buffer and verify deterministic contents after reuse.
10. Enter a world and shut down with zero pending upload batches, zero unsafe frees, and clean arena accounting.

Suggested validation sequence:

`allocate A/B/C -> stage/upload A/B/C -> fence -> verify completion -> free B -> allocate D into reusable space -> upload D -> fence -> read back A/C/D -> verify -> free all -> coalesce to one full free span`

Terrain replacement remains intentionally inactive until this arena/lifetime layer is proven.
