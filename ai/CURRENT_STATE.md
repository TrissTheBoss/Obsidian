# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev3, merge commit `de45e80f96c841372c8263deafd0506545f814cc`
- Active development branch: `phase1/device-arena`
- Active draft PR: #6, `Phase 1: device-local geometry arena and suballocator`
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

Validated bounded CPU-to-GPU staging and upload ownership:

- fixed 256 KiB persistently mapped `MAP_WRITE | COPY_SRC` staging arena;
- 16-byte aligned monotonic ring cursors;
- one controlled batch containing two real GPU copies;
- explicit backpressure instead of fallback allocation or waits;
- zero-timeout fence polling and completion-gated ring reclamation;
- deterministic destination readback verification;
- real-machine result `stagingSubmittedBytes=256`, `stagingReclaimedBytes=256`, `stagingHighWater=256`, `stagingBackpressureEvents=1`, `pendingUploadBatches=0`, exit code 0.

Dev3 was squash-merged as `de45e80f96c841372c8263deafd0506545f814cc` with `[no-release]`. Runtime evidence is in `ai/attempts/A-0026-dev3-runtime-success.md`.

## Phase 1 dev4 - ACTIVE; compile validated, runtime pending

Goal: establish a reusable device-preferred GPU geometry arena/suballocator before real chunk meshes use the upload path.

### Exact Minecraft 26.2 findings

Exact Loom-resolved bytecode/API inspection confirmed:

- `GpuBuffer` usage flags include `COPY_DST`, `COPY_SRC`, `VERTEX`, and `INDEX`.
- `GpuBufferSlice` supports offset/length sub-slicing for arena allocations.
- `CommandEncoder.copyToBuffer(sourceSlice, destinationSlice)` and `createFence()` provide the copy/completion primitives required for uploads, readback, and safe retirement.
- the Vulkan backend class is `VulkanGpuBuffer.Direct`.
- its VMA allocation setup starts on the automatic device-preferred path and only adds host-visible/coherent requirements when mapping usage is requested; host-mapped/client-storage paths select the host-preferred mode.

Portable terminology is therefore **device-preferred**, not a promise that every implementation physically uses discrete VRAM. On the RX 6800 XT this is the expected route to device-local memory; unified-memory GPUs may legitimately select shared memory.

The first backend inspection guessed an obsolete class name (`VulkanBuffer`). A jar class listing exposed the actual `VulkanGpuBuffer.Direct` name and a corrected inspection pass succeeded. The temporary inspection workflow has been removed.

### Implemented `DeviceGeometryArena`

Validation backing capacity: 512 KiB (`524288` bytes).

Backing buffer usage:

- `COPY_DST | COPY_SRC | VERTEX | INDEX`;
- no `MAP_READ` or `MAP_WRITE` flags;
- CPU writes therefore arrive through the already-validated `StagingUploadArena`.

Allocator/lifetime foundation:

- 4096 preallocated allocation metadata slots;
- packed 64-bit allocation handles containing `(slot, generation)`;
- stale or reused handles are rejected before a `GpuBufferSlice` is returned;
- best-fit aligned suballocation over sorted free spans;
- bounded allocation failure instead of fallback arena growth;
- adjacent free-span coalescing;
- fixed 64 retirement batches with up to 64 allocations per batch;
- one completion fence may guard multiple frees;
- an allocation becomes unavailable immediately when retired, but its span remains occupied until the real fence completes;
- zero-timeout steady-state retirement polling;
- retirement validation/state transition is allocation-free after removal of an early temporary `int[]` implementation;
- bounded shutdown behavior leaves unsafe live/in-flight arena resources for Minecraft device shutdown rather than closing them prematurely.

Metrics:

- capacity / used / free / high-water bytes;
- allocation count and bounded allocation failures;
- live and pending-free counts;
- successful/cancelled/retired/reclaimed allocation totals;
- retirement backpressure events;
- stale-handle rejection count;
- free-span count;
- largest free block;
- external-fragmentation estimate in permille;
- pending retirement batches.

### Implemented `DeviceArenaProbe`

One-shot non-visual validation sequence:

`allocate A/B/C -> deliberately fail oversized pressure allocation -> stage/upload A/B/C -> wait for upload completion -> copy/readback B while retiring B behind that fence -> reclaim B -> allocate D into B's freed span -> require same offset and slot with a newer generation -> deliberately reject stale B handle -> upload D -> copy/readback A/C/D while retiring them behind one fence -> reclaim -> verify bytes -> require one full coalesced free span`

Validation allocation sizes:

- A = 64 KiB (`65536` bytes)
- B = 96 KiB (`98304` bytes)
- C = 48 KiB (`49152` bytes)
- D = 80 KiB (`81920` bytes)
- alignment = 256 bytes
- deterministic validation payload per allocation = 256 bytes

Expected allocator values if runtime behaves as designed:

- initial A+B+C used/high-water = `212992` bytes;
- exactly one deliberate arena allocation failure;
- B begins at offset `65536`;
- D should reuse B's offset and metadata slot but advance that slot's generation;
- after D replaces B, used bytes = `196608`, free spans = 2, fragmentation estimate = `50` permille;
- stale old-B handle must be rejected exactly once;
- total successful allocations = 4 (A/B/C/D);
- total retired/reclaimed allocations = 4;
- after final reclamation: used = 0, free spans = 1, largest free block = `524288`, fragmentation = 0.

Expected staging totals:

- first upload batch: A/B/C patterns = 768 bytes;
- second upload batch: D pattern = 256 bytes;
- total staging submitted/reclaimed = 1024 bytes;
- expected staging high-water = 768 bytes;
- no deliberate staging pressure in dev4, so expected staging backpressure events = 0.

### Compile validation

- initial clean dev4 implementation compiled successfully in GitHub Actions run `32368743417`.
- review found one avoidable temporary `int[]` allocation inside arena retirement; it was removed.
- corrected implementation head `8c71b5eebed87ed66f98aaafb8af14b3b906cee6` passed GitHub Actions run `32368901978` on Java 25 / Gradle 9.5.1 with build and artifact upload successful.
- AI continuity and terminology-only commits followed; a final exact-head CI run is required before distributing the dev4 JAR.

Evidence is preserved in:

- `ai/attempts/A-0027-device-arena-api-inspection.md`
- `ai/attempts/A-0028-dev4-device-arena-implementation.md`
- decisions D-0019 and D-0020 in `ai/DECISIONS.md`.

## Dev4 real-machine success criteria

On the Windows 11 / RX 6800 XT Vulkan test instance, confirm:

1. `obsidian 0.1.0-phase1-dev4` loads and arms the device-preferred geometry arena foundation.
2. Frame coordinator reports staging capacity `262144` and arena capacity `524288`.
3. Initial arena upload reports 3 allocations, `usedBytes=212992`, `highWater=212992`, exactly one allocation failure, staging payload 768 bytes.
4. B readback/retirement creates one pending free/retirement batch.
5. After the B fence completes, D reuses B's exact offset `65536` and slot with an advanced generation.
6. Reuse log reports `usedBytes=196608`, `freeSpans=2`, `fragmentationPermille=50`, and `staleHandleRejections=1`.
7. Final readback/retirement batches A/C/D together and all deterministic contents verify correctly.
8. Final verification reports allocations=4, allocationFailures=1, retired=4, reclaimed=4, staleHandleRejections=1, usedBytes=0, freeSpans=1, largestFree=524288, fragmentationPermille=0.
9. User can enter a world and render/move normally.
10. Shutdown ideally reports stagingSubmittedBytes=1024, stagingReclaimedBytes=1024, stagingHighWater=768, stagingBackpressureEvents=0, pendingUploadBatches=0, arenaUsedBytes=0, arenaHighWater=212992, arenaAllocations=4, arenaAllocationFailures=1, arenaRetired=4, arenaReclaimed=4, arenaStaleHandleRejections=1, arenaFreeSpans=1, arenaLargestFree=524288, arenaFragmentationPermille=0, pendingArenaRetirementBatches=0, and no generic pending retirements; process exit code 0.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame contexts -> controlled submissions -> completion-gated lifetime -> bounded persistent staging -> batched copies/backpressure -> device-preferred geometry arena -> generation-safe suballocation -> completion-gated span reuse`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own terrain rendering;
- infer GPU completion from frame count;
- perform routine device-wide waits;
- upload actual chunk meshes;
- claim the current O(free-span-count) best-fit allocator is the final high-scale production allocator.

## Immediate next action

Run final CI on the exact documented dev4 head, distribute the CI-built `0.1.0-phase1-dev4` JAR, and runtime-test the full allocation/upload/retire/reuse/readback/coalescing sequence on the reference machine. Keep PR #6 draft until that validation passes. If dev4 succeeds, merge it with `[no-release]` and continue Phase 1 toward production-sized arena policy, profiler snapshots, render-graph ownership, and eventually the first Obsidian-owned terrain geometry.
