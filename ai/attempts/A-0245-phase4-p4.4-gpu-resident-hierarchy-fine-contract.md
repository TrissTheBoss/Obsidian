# A-0245 - Phase 4 P4.4 GPU-resident hierarchy-fed fine visibility contract

**Date:** 2026-10-03  
**Status:** `SUCCESS / CONTRACT FROZEN BEFORE RENDERER-SOURCE CHANGE`  
**Milestone:** Phase 4 P4.4  
**Version target:** `0.4.0-phase4-dev4`  
**Branch:** `phase4/p4.4-gpu-resident-hierarchy-fine`  
**Base:** promoted P4.3 merge `4ba408becfcde42456b9d8468106a3e0d27de049`

## Objective

Remove P4.3's **intermediate CPU/readback dependency** between coarse column visibility and fine section visibility.

P4.4 must prove this sampled shadow data flow:

`persistent hierarchy snapshot -> coarse GPU visibility -> GPU-resident coarse-to-fine expansion/classification -> final validation readback`

No CPU-visible-column readback, Java candidate-list construction or Java candidate upload may be required **between** the coarse and fine GPU stages.

Final readback remains allowed for correctness validation.

## Why this is the next slice

P4.3 proved:

- P4.2 coarse visibility is conservative;
- hierarchy-fed fine candidates materially reduce the flat section set;
- P4.1/P4.3 fine plane classification is exact;
- safe-extra coarse lineage is correct and measurable;
- camera-only frames need not full-scan the Java section database.

The remaining sampled-path artifact is that P4.3 waits for the coarse result on CPU, walks the visible-column memberships in Java, uploads the fine candidate list, and only then dispatches fine compute.

P4.4 removes that dependency before any production command compaction or temporal visibility work.

## Frozen GPU snapshot representation

P4.4 may add a bounded GPU-resident section table aligned with the P4.2 column snapshot.

For every uploaded coarse snapshot candidate index, store a fixed vertical table covering the configured section range.

Each section-table entry contains at least:

- exact section X;
- exact section Y;
- exact section Z;
- exact stable P4.1 section identity, or identity 0 for no live section.

The table is rebuilt/uploaded **only when the persistent hierarchy/section serial changes**.

Camera-only frames must not rebuild or rewrite it.

Capacity:

`columnCapacity * configuredSectionCount`

must equal or be bounded by the existing P4.1 section-scene capacity.

No unbounded GPU/host allocation is permitted.

## Coarse output handoff

The P4.2 coarse compute output must expose a stable **snapshot candidate index** for every GPU-visible column.

P4.2 CPU correctness accounting must still resolve those indices back to the exact column identities and preserve all existing conservative-oracle semantics.

Required safeguards:

- every returned index must be within the captured snapshot candidate count;
- index -> identity resolution must match the captured snapshot;
- duplicate coarse identities remain forbidden;
- missing CPU-visible coarse identities remain forbidden;
- safe extra GPU columns remain allowed and separately counted.

P4.2 behavior is not weakened.

## GPU-resident fine stage

The P4.4 fine compute stage runs from the same sampled camera/frustum capture as the coarse stage.

Inputs:

- P4.2 coarse GPU output buffer;
- GPU-resident snapshot-aligned section table;
- captured camera section/local coordinates;
- captured six frustum planes;
- section count / snapshot candidate count bounds.

The fine stage must:

1. read the GPU-visible coarse candidate indices directly;
2. enumerate only their fixed section-table entries;
3. skip identity 0 entries;
4. count actual hierarchy-fed fine candidates;
5. apply the exact promoted P4.1 six-plane + epsilon fine section classifier;
6. compact visible section identities into a bounded output buffer.

The coarse and fine compute stages must be joined by an explicit compute-write -> compute-read synchronization edge.

No CPU readback may occur between them.

## Submission model

P4.4 should dispatch coarse and fine compute in one sampled command-encoder submission when the current Minecraft Vulkan backend allows that safely.

The final coarse and fine results may both be copied to host-visible readback buffers after the compute chain, with one completion fence for the sampled submission.

Steady-state polling remains zero-timeout.

No routine GPU wait, queue idle, or device idle is allowed.

## CPU validation oracle

After final sampled GPU completion, CPU validation may enumerate only the GPU-visible coarse columns represented by the completed sample.

It must independently derive:

- hierarchy-fed candidate count;
- candidate-set expected fine visible identities;
- CPU-coarse baseline fine identities;
- safe-extra coarse columns;
- safe-extra lineage fine identities.

P4.4 GPU final output must match the candidate-set expected fine identities exactly.

P4.3 remains the reference/control path during P4.4 development and may continue to run sampled validation independently.

## Required telemetry

At minimum:

- GPU-resident samples started/completed/aborted stale/deferred;
- section-table snapshot builds/restarts/uploads;
- section-table bytes and high-water;
- coarse output invalid indices;
- coarse output duplicate-index/identity failures;
- GPU-resident fine candidates;
- CPU independently counted fine candidates;
- candidate-count mismatches;
- candidate reduction permille vs flat live sections;
- baseline fine visible;
- safe-extra coarse columns;
- safe-extra lineage fine;
- GPU fine visible;
- missing/unexpected/duplicate fine identities;
- `gpuResidentFineFalseCullCount`;
- final readback pending high-water;
- `cameraOnlySectionTableRebuild=false`;
- `intermediateCpuReadback=false`;
- `intermediateCpuCandidateBuild=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- `commandCompactionEnabled=false`;
- `temporalVisibilityEnabled=false`;
- `hizEnabled=false`.

## Runtime exercise

Reference runtime remains Windows 11 / RX 6800 XT / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 / Vulkan.

Primary closure:

1. render distance **32 or higher**;
2. stable camera repeated samples;
3. rapid camera turns;
4. horizontal traversal/chunk churn;
5. vertical section traversal;
6. ordinary edits;
7. F3+T/recovery;
8. world leave/re-entry if practical;
9. normal exit;
10. explicit human Visual PASS.

## Promotion gates

P4.4 may promote only when:

- exact dev4 source passes hosted Java 25 / Gradle 9.5.1 CI;
- final P4.2 coarse oracle remains conservative and clean;
- P4.3 sampled CPU/readback control remains clean;
- P4.4 coarse output indices are always in-range/exactly resolvable;
- GPU-resident fine candidate count equals independent CPU count;
- zero missing/unexpected/duplicate P4.4 final fine identities;
- `gpuResidentFineFalseCullCount=0`;
- no intermediate CPU readback between coarse/fine GPU stages;
- no intermediate Java fine candidate construction/upload;
- camera-only frames do not rebuild the section table;
- compute-write -> compute-read synchronization is explicit;
- readback/lifetime remains asynchronous and completion-gated;
- inherited P4.1/P3.10/P3.7/worker/staging/arena/resource gates remain clean;
- production draw ownership unchanged;
- native graphics ownership unchanged;
- command compaction, temporal visibility and Hi-Z remain disabled;
- normal exit;
- explicit human Visual PASS.

No performance threshold is invented before measurement.

## Native scope

P4.4 stays within D-0025's existing native **compute/storage** interop seam.

Minecraft retains:

- VkDevice ownership;
- queue/submission lifecycle;
- swapchain/presentation;
- graphics/render-pass ownership.

P4.4 does not widen D-0023/D-0027 into native graphics.

## Explicit non-goals

P4.4 does **not** add:

- production visibility consumption;
- terrain draw command compaction;
- `vkCmdDrawIndexedIndirectCount`;
- native graphics/render-pass ownership;
- temporal visibility history;
- Hi-Z;
- region hierarchy;
- LOD;
- mesher changes;
- partial remeshing;
- transparency/entity/block-entity/particle ownership changes.

## Failure policy

If direct coarse->fine GPU chaining cannot be expressed safely through the current isolated compute seam, record the exact blocker before widening native scope.

Do not use CPU busy-wait/readback as a hidden fallback to claim P4.4 success.

## Immediate next action

Implement the sampled GPU-resident section table and direct coarse-output-index -> fine compute chain for `0.4.0-phase4-dev4`, preserving P4.3 as an independent control path.
