# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev2, merge commit `3d2366a5c07f819a264f5f02dd2f3df9c5ec6fc0`
- Active development branch: `phase1/staging-upload`
- Active draft PR: #5, `Phase 1: bounded staging and upload infrastructure`
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
- Vulkan driver string observed: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`

## Completed milestones

### Phase 0 - COMPLETE

Validated Fabric bootstrap, Vulkan selection, Minecraft `GpuDevice` attachment, capability reporting, world entry, and clean shutdown on the real RX 6800 XT machine.

### Phase 1 dev1 - VALIDATED and merged through PR #3

Validated:

- `Minecraft.renderFrame(boolean)` lifecycle seam;
- fixed-allocation CPU frame timing ring;
- controlled Obsidian GPU command submission through Minecraft's Vulkan device;
- nonblocking timestamp result polling;
- world entry and clean shutdown.

### Phase 1 dev2 - VALIDATED and merged through PR #4

Validated:

- three-slot `FrameContextRing` with monotonically increasing serials;
- frame slots are bookkeeping only, never proof of GPU completion;
- real `GpuFence` completion tracking;
- zero-timeout steady-state fence polling;
- `DeferredReleaseQueue` for completion-gated GPU destruction;
- bounded shutdown cleanup;
- real-machine result `retiredResources=1`, `releasedResources=1`, `pending=0`, exit code 0.

## Phase 1 dev3 - ACTIVE; compile validated, runtime pending

Goal: establish bounded staging/upload ownership before terrain starts producing geometry.

### Exact Minecraft 26.2 findings

Exact Loom-resolved bytecode/API inspection confirmed:

- `GpuBuffer` supports `USAGE_MAP_WRITE`, `USAGE_MAP_READ`, `USAGE_COPY_SRC`, and `USAGE_COPY_DST`.
- `GpuBuffer.map(false, true)` provides persistent write mapping through `GpuBufferSlice.MappedView`.
- `CommandEncoder.copyToBuffer(sourceSlice, destinationSlice)` is source-first and requires matching slice lengths/usages.
- `CommandEncoder.createFence()` provides batch completion tracking.
- `DeviceFeatures.persistentMapping()` is exposed and is true on the reference machine.

Minecraft also provides `StagingBuffer.PersistentlyMapped`, but exact inspection found that its internal `MappableRingBuffer.currentBuffer()` can call `GpuFence.awaitCompletion(Long.MAX_VALUE)` when rotating back to a busy slot. Obsidian therefore does not use that helper as its hot-path staging policy; it uses the low-level public buffer/mapping/copy/fence abstractions and owns nonblocking admission/reclamation itself.

### Implemented dev3 staging foundation

`StagingUploadArena`:

- one fixed-capacity persistently mapped GPU staging buffer;
- validation capacity: 256 KiB;
- usage: `MAP_WRITE | COPY_SRC`;
- 16-byte aligned virtual monotonic write/reclaim cursors;
- wrap padding accounted as occupied capacity;
- fixed 64-entry in-flight batch table;
- no fallback staging allocation when safe space is unavailable;
- explicit backpressure counter;
- batches reclaimed only after their real fence completes;
- normal polling uses `awaitCompletion(0L)` only;
- bounded shutdown wait; unsafe in-flight staging memory is left for Minecraft device shutdown rather than destroyed.

Metrics currently exposed:

- capacity bytes;
- used/available bytes;
- staged bytes;
- submitted bytes;
- reclaimed bytes;
- high-water bytes;
- backpressure events;
- submitted/reclaimed batch counts;
- pending batches.

`GpuUploadProbe`:

- one-shot, non-visual development validation;
- creates one read-mappable COPY_DST destination;
- stages two deterministic 128-byte regions;
- records both buffer copies in one command encoder/submission;
- deliberately attempts a full-capacity extra allocation after the first two reservations; this must be rejected and counted as one backpressure event rather than allocating fallback memory;
- fences the batch;
- after completion/reclamation, maps the destination for read and verifies every copied byte against the deterministic patterns;
- closes the destination only after completion is known safe.

The completed dev2 `GpuResourceLifetimeProbe` and temporary staging API inspection workflow have been removed from the dev3 branch.

### Compile validation

The clean dev3 Java implementation compiled successfully in GitHub Actions on Java 25 / Gradle 9.5.1 against the exact Minecraft 26.2/Fabric dependency set. Build and artifact upload succeeded before the continuity-only commits.

A final exact-head CI run is still required after documentation changes before distributing the dev3 JAR.

## Dev3 real-machine success criteria

On the RX 6800 XT Vulkan test instance, confirm:

1. `obsidian 0.1.0-phase1-dev3` loads.
2. Staging initialization reports capacity `262144` bytes.
3. The upload probe submits exactly one batch containing two copies.
4. Log reports payload/submitted bytes `256`.
5. Exactly one deliberate backpressure event is reported.
6. The batch fence completes without a routine wait.
7. Staging reclaimed bytes reach `256` and pending batches reach `0`.
8. Deterministic readback verification succeeds.
9. Player can enter a world normally.
10. Shutdown reports no pending upload batches or GPU retirements and process exits with code 0.

Terrain replacement remains intentionally inactive.

## Proven architecture boundary

`Minecraft 26.2 Vulkan device -> Obsidian RendererBridge -> FrameCoordinator -> frame contexts -> controlled submissions -> fence-gated resource lifetime -> bounded persistently mapped staging + explicit backpressure`

Obsidian still does not:

- create a second Vulkan device/swapchain;
- own terrain rendering;
- infer GPU completion from frame count;
- perform routine device-wide waits;
- upload actual chunk meshes.

## Immediate next action

Run final CI on the documented dev3 head, download the CI-built `0.1.0-phase1-dev3` JAR, and test it on the reference Vulkan machine. If the staging copies/backpressure/readback/shutdown invariants pass, merge PR #5 with `[no-release]` and continue Phase 1 toward production-sized upload batching plus profiler snapshots/render-graph infrastructure before terrain ownership.
