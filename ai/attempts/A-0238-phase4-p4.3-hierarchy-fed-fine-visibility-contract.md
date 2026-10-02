# A-0238 - Phase 4 P4.3 hierarchy-fed fine section visibility contract

**Date:** 2026-10-03  
**Status:** `SUCCESS / CONTRACT FROZEN BEFORE RENDERER-SOURCE CHANGE`  
**Milestone:** Phase 4 P4.3  
**Version target:** `0.4.0-phase4-dev3`  
**Branch:** `phase4/p4.3-hierarchy-fed-fine-visibility`  
**Base:** promoted P4.2 merge `3b50f1e0fea11ae65c3e61689db20f03c8dd4766`

## Objective

Prove that the promoted P4.2 coarse GPU-visible column set can safely bound P4.1-style fine section visibility and materially reduce fine candidates while preserving the exact visible-section result.

P4.3 remains **shadow-only**. It does not yet feed production terrain submission.

## Why this is the next slice

P4.1 proved exact flat fine section GPU visibility.

P4.2 proved conservative coarse column visibility and measured a render-distance-32 sample of:

- 35,273 live flat fine candidates;
- 3,725 live columns;
- 1,252 final visible coarse columns;
- estimated coarse-bounded fine upper bound 10,523;
- zero coarse false culls/missing/duplicates.

The roadmap explicitly says later Phase 4 slices may feed the proven hierarchy into fine section visibility before later temporal visibility and real command compaction.

Therefore P4.3 converts the P4.2 reduction estimate into an actual independently validated fine classification path.

## Frozen design

### Asynchronous sampled hierarchy handoff

P4.3 may consume a **completed** P4.2 coarse sample only after:

- the hierarchy and section scene serials still match the captured sample;
- P4.2's CPU/GPU comparison is conservative;
- there are zero missing coarse identities and zero duplicate coarse identities.

The completed coarse GPU-visible column identities are a validation input to a new sampled fine pass.

This is intentionally not yet a same-frame GPU-to-GPU production dependency. P4.3 proves correctness and actual candidate reduction first.

### No flat Java scene scan on camera-only frames

P4.3 must not iterate the full section-slot capacity in response to camera movement.

Required handoff construction:

- the P4.2 column snapshot maintains an identity -> column-slot lookup while the hierarchy snapshot is built/changed;
- for each GPU-visible column, enumerate only that column's exact persistent occupancy;
- use exact section-coordinate lookup in `PersistentSectionScene` to obtain the corresponding section identity;
- build work incrementally under a fixed per-frame budget;
- stale hierarchy/section serials abort the sampled handoff and restart only from a later valid coarse sample.

Camera-only frames therefore walk only the bounded sampled visible-column membership, never the full Java section database.

### Fine GPU classifier

Reuse the already-proven `VulkanLargeSceneVisibilityProbe` semantics with a separate P4.3 validation instance/buffers.

Each hierarchy-fed candidate contains:

- exact section X/Y/Z;
- exact stable section identity.

Use the **same captured camera section/local coordinates and six frustum planes** as the coarse sample.

The output remains:

- atomic visible-section count;
- compacted visible-section identities.

No graphics pass consumes the P4.3 output.

### Independent hierarchical fine oracle

The CPU expected fine-visible set is derived only from CPU-coarse-visible columns from the same sample.

For each exact live section in those columns, run the promoted P4.1 fine CPU classification policy using the same captured camera and conservative epsilon.

A CPU-coarse-culled whole-column AABB contains all of its member sections, so sections in those columns are safely excluded from the expected fine set.

The GPU fine candidate set is built from the **GPU** coarse-visible columns. Conservative extra GPU columns are allowed as inputs, but the fine GPU stage must cull their non-visible sections so the final fine identity set is exact.

Required fine result:

- zero missing expected fine identities;
- zero unexpected fine identities;
- zero duplicate fine identities;
- GPU fine visible count equals CPU expected fine count.

### Exact lookup additions

Small non-mutating lookup helpers are allowed:

- `PersistentSectionScene.sectionSlot(x,y,z)`;
- snapshot-scoped column identity -> persistent column slot lookup maintained only when the P4.2 snapshot changes.

These are lookup/indexing additions only and must not change lifecycle truth.

## Bounded work and memory

- fine candidate build budget: **8,192 section probes per frame**;
- candidate capacity must remain bounded by the existing exact P4.1 section-scene capacity;
- allocation sizes and high-water candidate counts must be logged;
- no unbounded per-frame allocation;
- no synchronous GPU wait in normal operation;
- normal readback polling remains zero-timeout;
- shutdown remains completion-gated with the existing bounded policy.

## Required telemetry

At minimum report:

- hierarchical fine samples started/completed/aborted stale/deferred;
- coarse GPU-visible columns;
- CPU coarse-visible columns;
- flat live section candidates;
- hierarchy-fed fine candidate count;
- candidate reduction permille;
- CPU expected fine visible count;
- GPU fine visible count;
- missing/unexpected/duplicate fine identities;
- `gpuHierarchicalFineFalseCullCount`;
- candidate-build probes and build frames;
- snapshot lookup failures;
- readback pending high-water;
- candidate/upload/readback bytes;
- hard failure / shutdown-abandon state;
- `cameraOnlyFullSectionScan=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- `commandCompactionEnabled=false`;
- `temporalVisibilityEnabled=false`;
- `hizEnabled=false`.

## Runtime exercise

Use the reference Windows 11 / RX 6800 XT / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 Vulkan setup.

Primary sample:

1. render distance **32 or higher**;
2. let P4.1/P4.2/P4.3 settle;
3. hold stable camera long enough for repeated hierarchy-fed fine samples;
4. rapid turns;
5. horizontal movement/chunk churn;
6. vertical section traversal;
7. ordinary edits;
8. F3+T and recovery;
9. leave/re-entry if practical;
10. normal exit.

Explicit human visual parity remains required because production rendering must remain unchanged.

## Promotion gates

P4.3 may promote only when:

- exact dev3 source passes hosted Java 25 / Gradle 9.5.1 CI;
- P4.2 structural/coarse correctness remains clean;
- P4.1 flat fine oracle remains clean;
- hierarchy-fed fine path has zero missing/unexpected/duplicate identities;
- `gpuHierarchicalFineFalseCullCount=0`;
- actual hierarchy-fed candidate count is measured against flat candidates;
- camera-only frames never full-scan the Java section database;
- sampled build work remains bounded;
- readback/lifetime remains asynchronous and completion-gated;
- inherited P3.10/P3.7/worker/staging/arena/resource gates remain clean;
- production draw ownership unchanged;
- native graphics expansion unchanged;
- command compaction/temporal/Hi-Z remain disabled;
- normal exit;
- explicit human visual PASS.

No performance threshold is invented before measurement.

## Explicit non-goals

P4.3 does **not** add:

- production use of P4.2/P4.3 visibility output;
- same-frame graphics draw command compaction;
- `vkCmdDrawIndexedIndirectCount`;
- native Vulkan graphics/render-pass ownership;
- temporal visibility history;
- Hi-Z;
- async compute ownership;
- region hierarchy;
- LOD;
- mesher semantic changes;
- partial remeshing/GPU patching;
- transparency/entity/block-entity/particle rendering changes.

## Failure policy

Do not weaken P4.2 coarse conservatism, P4.1 fine exactness, candidate bounds, lifecycle correctness or inherited production/lifetime gates after measurement.

If sampled CPU-readback handoff proves too expensive or semantically insufficient, record that result and freeze a new strategy rather than silently turning P4.3 into production GPU-to-GPU compaction.

## Immediate next action

Implement the bounded sampled hierarchy-fed fine validator for dev3, then package an immutable Preview only after hosted exact-head CI succeeds.
