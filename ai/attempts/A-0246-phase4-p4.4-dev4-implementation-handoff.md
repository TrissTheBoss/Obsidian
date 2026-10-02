# A-0246 - Phase 4 P4.4 dev4 implementation handoff

**Date:** 2026-10-03  
**Status:** `IMPLEMENTED / HOSTED CI REQUIRED`  
**Milestone:** Phase 4 P4.4 GPU-resident hierarchy-fed fine visibility  
**Version:** `0.4.0-phase4-dev4`  
**Branch:** `phase4/p4.4-gpu-resident-hierarchy-fine`

## Objective

Implement the frozen A-0245 GPU-resident coarse -> fine sampled validation path while retaining P4.3 as the independent CPU/readback control.

## Implementation

### Coarse output becomes snapshot indices

`VulkanLargeSceneColumnVisibilityProbe` still produces the same bounded visible-column count, but each compacted entry now contains the **coarse snapshot candidate index** rather than the column identity.

`LargeSceneColumnHierarchyProbe` resolves those indices back to the exact captured column identities before running the existing P4.2 conservative CPU/GPU comparison.

Required P4.2 semantics remain:

- invalid snapshot indices are fatal;
- duplicate resolved identities remain fatal;
- missing CPU-visible columns remain fatal;
- safe extra GPU columns remain allowed and counted.

### GPU section table

New validation path:

`GpuResidentHierarchicalFineVisibilityProbe`

A host-mapped upload table is aligned with the P4.2 column snapshot.

For every snapshot candidate index, the table stores one fixed record for each configured section Y:

- section X;
- section Y;
- section Z;
- exact stable P4.1 section identity, or 0 when no live section exists.

The table is rebuilt only while the P4.2 hierarchy snapshot is rebuilt and is uploaded only when that snapshot changes.

No camera-only section-table rebuild is introduced.

### Direct GPU coarse -> fine compute chain

New native compute class:

`VulkanGpuResidentHierarchicalFineProbe`

Bindings:

1. P4.2 coarse output buffer;
2. GPU-resident snapshot-aligned section table;
3. P4.4 fine output buffer.

The fine compute stage:

- reads the coarse GPU visible-count and snapshot-index list directly;
- maps each visible snapshot index into its fixed section-table range;
- skips identity-0 entries;
- atomically counts actual hierarchy-fed fine candidates;
- applies the exact P4.1/P4.3 six-plane + epsilon section classifier;
- compacts final visible section identities;
- records invalid coarse indices in the GPU output header.

There is no CPU readback or Java fine-candidate construction between coarse and fine compute.

### Synchronization

P4.4 inserts an explicit compute-write -> compute-read memory barrier at the fine stage before consuming the P4.2 coarse output.

The fine output is then synchronized compute-write -> transfer-read before final host readback.

P4.2 coarse and P4.4 fine run in the same sampled `CommandEncoder` submission and share the same completion fence.

No routine queue/device idle or blocking steady-state wait is introduced.

### Final CPU oracle

Only after final sampled completion, P4.4 independently enumerates the GPU-visible coarse columns and derives:

- CPU fine candidate count;
- candidate-set expected fine identities;
- CPU-coarse baseline fine identities;
- safe-extra coarse columns;
- safe-extra lineage fine identities.

The GPU-resident final result must match exactly.

P4.3 continues to run independently after P4.2 completion as the prior control path.

## Telemetry

P4.4 reports:

- samples started/completed/aborted stale/deferred;
- section-table snapshot builds/restarts/uploads;
- section-table bytes/upload bytes;
- snapshot lookup failures;
- invalid/duplicate coarse output indices;
- GPU vs CPU fine candidate counts;
- candidate-count mismatches;
- fine candidate reduction permille;
- baseline fine visible;
- safe-extra coarse columns;
- safe-extra lineage fine;
- missing/unexpected/duplicate final fine identities;
- `gpuResidentFineFalseCullCount`;
- final readback pending high-water;
- hard-failure/shutdown state;
- explicit `intermediateCpuReadback=false`;
- explicit `intermediateCpuCandidateBuild=false`;
- explicit `cameraOnlySectionTableRebuild=false`;
- unchanged production/native/command/temporal/Hi-Z flags.

## Native scope

The new native code remains compute/storage-only under D-0025.

It does not own:

- graphics passes;
- presentation;
- swapchain;
- VkDevice/queue lifecycle;
- terrain production draw submission.

## Version

`gradle.properties` now identifies:

`0.4.0-phase4-dev4`

## Next action

Open draft P4.4 PR and require hosted Java 25 / Gradle 9.5.1 Build + Context Governor. Fix only compile/frozen-contract defects. After an exact source head is green, synchronize continuity, freeze the final package head, publish an immutable dev4 Preview, and run A-0245 runtime validation.
