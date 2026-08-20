# A-0047 - Phase 1 dev8 runtime validation success

- **Date:** 2026-08-20
- **Status:** SUCCESS
- **Build:** `0.1.0-phase1-dev8`
- **Branch:** `phase1/compute-indirect`
- **Draft PR:** #10
- **Reference machine:** Windows 11, AMD Radeon RX 6800 XT, Ryzen 5 5600X, Java 25.0.1, Minecraft 26.2, Fabric Loader 0.19.3

## Objective

Validate the first Obsidian compute-generated indirect path on real Vulkan hardware while preserving Minecraft ownership of device, queue submission, graphics render passes and presentation.

The required chain was:

`CPU geometry upload -> native Vulkan compute dispatch -> GPU-authored VkDrawIndexedIndirectCommand records -> explicit Sync2 compute-write/indirect-read barrier -> public Blaze3D drawIndexedIndirect -> deterministic readback -> completion-gated reclamation`.

## Intended result

- `graphPasses=4`, `executedMask=15`;
- one useful submission, zero profiler-only submissions;
- one native compute dispatch;
- two GPU-generated 20-byte indexed-indirect commands;
- one public indexed-indirect graphics call consuming both commands;
- left and right triangle pixels magenta, corner black;
- CPU staging payload only 84 bytes (72 vertex + 12 index), with no CPU indirect-command upload;
- staging high-water 92 bytes from aligned reservations;
- two arena allocations retired/reclaimed and fully coalesced;
- world entry and clean process exit.

## Actual result

All target invariants passed.

Runtime reported:

- correct `obsidian 0.1.0-phase1-dev8` loaded;
- Vulkan backend on `AMD Radeon RX 6800 XT`;
- `VK_KHR_synchronization2` available;
- `indirect=true`, `multiDrawIndirect=true`, `persistentMapping=true`;
- `graphPasses=4`;
- `usefulSubmissions=1`;
- `profilerOnlySubmissions=0`;
- `computeDispatches=1`;
- `indirectCalls=1`;
- `indirectCommands=2`;
- `triangles=2`;
- `nativeComputeSeam=true`;
- `nativeGraphicsSeam=false`;
- `pipelineValid=true`;
- `vertexBytes=72`;
- `indexBytes=12`;
- `gpuGeneratedIndirectBytes=40`;
- `stagingPayloadBytes=84`;
- `arenaUsedBytes=84` at submission.

Completion/verification on frame 1 reported:

- `executedMask=15`;
- left pixel `255/0/255/255`;
- right pixel `255/0/255/255`;
- corner pixel `0/0/0/255`;
- `pixelsVerified=3`;
- `arenaRetired=2`;
- `arenaReclaimed=2`;
- `arenaUsedBytes=0`;
- `arenaFreeSpans=1`;
- `arenaFragmentationPermille=0`.

Profiler ranges resolved nonblockingly in one poll. Logged GPU timings for this tiny validation workload were approximately 9.76 us upload, 12.28 us compute, 32.76 us draw, 6.40 us readback and 62.44 us total. These are validation timings, not renderer benchmarks.

Final shutdown accounting:

- `computeIndirectResult=VERIFIED`;
- `stagingSubmittedBytes=84`;
- `stagingReclaimedBytes=84`;
- `stagingHighWater=92`;
- `stagingBackpressureEvents=0`;
- `pendingUploadBatches=0`;
- `arenaHighWater=84`;
- `arenaAllocations=2`;
- `arenaAllocationFailures=0`;
- `arenaRetired=2`;
- `arenaReclaimed=2`;
- `arenaRetirementBackpressureEvents=0`;
- `arenaStaleHandleRejections=0`;
- `arenaUsedBytes=0`;
- `arenaFreeSpans=1`;
- `arenaLargestFree=524288`;
- `arenaFragmentationPermille=0`;
- `pendingArenaRetirementBatches=0`;
- `pendingRetirements=0`;
- process exit code `0`.

The log formatter rendered 1810 frames as `1.810`; this is locale grouping, not a fractional frame count.

## Evidence interpretation

This proves the 40-byte indirect command list was not authored by the CPU. Compared with dev7, CPU staging fell from 124 bytes to exactly 84 bytes while both independent triangle pixels still rendered correctly. Therefore the compute shader produced valid command records, the explicit Sync2 dependency made those writes visible to indirect-command fetch, and public Blaze3D successfully consumed the Obsidian-created storage+indirect buffer.

The same-frame completion (`after 0 frame(s)`) does not imply a CPU/GPU synchronization shortcut. The completion checks remained nonblocking; the tiny workload simply completed before the later poll in frame 1.

## Why it worked

- native interop stayed limited to the capability missing from public Blaze3D;
- the compute command buffer was inserted into Minecraft's existing Vulkan command encoder/submission;
- the storage buffer had both native STORAGE_BUFFER and INDIRECT_BUFFER usages;
- the compute shader wrote the native 20-byte indexed-indirect command layout;
- an explicit Synchronization2 memory dependency bridged shader storage writes to indirect-command reads;
- graphics remained on the already-validated public Blaze3D path;
- staging and geometry retirement remained tied to real submission completion.

## Lesson

D-0025 and D-0026 are validated on the reference AMD Vulkan stack. Obsidian can now generate indirect work entirely on the GPU without taking over Minecraft's graphics/presentation ownership.

## Next action

Promote and merge PR #10 with `[no-release]`, then begin Phase 1 dev9 from the resulting `main` commit.

Dev9 should establish the visibility/compaction bridge needed before Phase 2 terrain work: GPU scene/candidate records -> compute visibility decision -> compacted indexed-indirect records (and, if exact API/backend inspection supports it safely, a GPU-produced draw count) -> indirect graphics consumption -> deterministic offscreen verification. Keep terrain replacement inactive until this infrastructure milestone is runtime validated.