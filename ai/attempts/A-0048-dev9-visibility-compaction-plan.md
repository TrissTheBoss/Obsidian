# A-0048 - Phase 1 dev9 GPU visibility/compaction plan

- **Date:** 2026-08-20
- **Status:** PLANNED / ACTIVE
- **Base:** `68e9710ff964a44165122c5d85c0d559e4698b11` (validated dev8 merge)
- **Branch:** `phase1/visibility-compaction`

## Objective

Bridge the validated dev8 compute-generated indirect path toward the future GPU-driven terrain architecture by adding a small GPU scene/candidate database, visibility decisions and indirect-command compaction.

## Intended validation chain

`CPU uploads candidate scene records + arena geometry -> GPU compute visibility test -> GPU compacts visible indexed-indirect commands -> GPU writes visible count -> explicit synchronization -> public Blaze3D fixed-capacity indirect draw -> read back target + compacted command/count data -> deterministic verification -> completion-gated geometry reclamation`.

## Planned test shape

- four triangle candidates stored in the real `DeviceGeometryArena`;
- four small GPU scene records containing draw metadata and visibility bounds/center data;
- compute workgroup resets the output command slots/count, evaluates all four candidates, atomically compacts only visible commands to the front, and leaves unused tail commands with `indexCount=0`;
- expected visible count: 2;
- public Blaze3D issues one fixed-capacity indexed-indirect call over four command slots;
- visible candidate center pixels must be magenta;
- culled candidate center pixels must remain black;
- clear corner remains black;
- output readback must report count=2, two valid front commands and two zero-index-count tail commands.

## Indirect-count decision checkpoint

Before widening the graphics backend seam, inspect exact Minecraft 26.2 and Vulkan support for `vkCmdDrawIndexedIndirectCount` / public equivalents.

Preferred initial policy:

- keep normal graphics on public Blaze3D if a fixed maximum draw count with GPU-zeroed tail commands provides a correct validation path;
- preserve the GPU-produced count for future optimization and metrics;
- do not duplicate the whole graphics render-pass backend merely to consume the count unless exact evidence shows the benefit/requirement justifies that wider seam.

If exact inspection reveals a narrow safe way to consume GPU draw count without taking over normal graphics state, record and evaluate it separately.

## Synchronization requirements

Compute output will be consumed both as indirect-command data and by validation readback. The barrier must therefore cover shader storage writes to every downstream access actually used (draw-indirect read and transfer/readback where applicable). Command ordering alone is not the memory-visibility contract.

## Performance/architecture constraints

- one useful Minecraft-owned submission;
- zero profiler-only submissions;
- bounded/fixed validation buffers;
- no per-frame production allocation introduced by the probe;
- arena lifetimes remain completion-gated;
- no device-wide waits;
- no real Minecraft terrain replacement yet.

## Relationship to greedy meshing

D-0024 remains unchanged. Phase 2 establishes one-section correctness and the reference mesher; Phase 3 implements the production worker-local binary/bitmask greedy mesher. Dev9 only proves the GPU consumer/visibility shape those reduced meshes will eventually feed.