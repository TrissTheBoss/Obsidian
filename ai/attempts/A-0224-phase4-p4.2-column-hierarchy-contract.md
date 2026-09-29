# A-0224 - Phase 4 P4.2 persistent column hierarchy and coarse GPU visibility contract

**Date:** 2026-09-29  
**Status:** `SUCCESS` / **CONTRACT FROZEN BEFORE RENDERER-SOURCE CHANGE**  
**Milestone:** Phase 4 P4.2  
**Version target:** `0.4.0-phase4-dev2`  
**Branch:** `phase4/p4.2-column-hierarchy`  
**Base:** promoted P4.1 merge `59d130e17000b07e6ed8bbe226cb2f50eb795c74`

## Objective

Add the first real large-distance hierarchy above P4.1's proven persistent section database: a bounded persistent **chunk-column hierarchy** with conservative GPU coarse frustum visibility.

P4.2 remains shadow-only. It does not change P3.10 production terrain suppression/draw ownership and does not feed GPU coarse results into production draw submission.

## Why this is the next slice

P4.1 proved exact flat section-level GPU frustum classification at real runtime scale, including a measured high-water of 10,642 live non-empty sections at render distance 16. The section-capacity formula scales with horizontal area times vertical section count; at the roadmap's 64/96/128+ targets, a flat section candidate path grows toward hundreds of thousands to more than a million slots.

The Phase 4 roadmap explicitly requires a persistent region/chunk-column/section hierarchy before later temporal visibility, command compaction and any evidence-gated indirect-count work.

Therefore P4.2 addresses scale before production ownership:

`persistent sections -> persistent columns -> conservative coarse GPU column visibility -> measured candidate reduction`

The already-promoted P4.1 section classifier remains the fine-grained control/oracle beside it.

## Frozen scope

### Persistent column hierarchy

Maintain a primitive bounded column database keyed by exact chunk X/Z.

Each live column record carries at minimum:

- chunk X/Z;
- stable validation identity;
- live non-empty section count;
- exact minimum live section Y;
- exact maximum live section Y;
- bounded occupancy metadata sufficient to recompute min/max exactly after section removal.

Membership is updated from the **same exact lifecycle/resync operations** that mutate the P4.1 section scene. There is no camera-frame world polling.

Required invariants:

- every live P4.1 section belongs to exactly one live column;
- column live-section count equals occupancy count;
- every live section Y is inside the column min/max;
- zero-count columns are removed;
- slot reuse receives a new identity;
- overflow/capacity failure is explicit and disables only the P4.2 shadow path rather than dropping live columns silently.

### Capacity and memory

Column capacity is derived from the exact active cache diameter:

`(2 * exactCacheRadius + 1)^2`

Hard column-slot ceiling: **131,072**.

This covers the current roadmap's render-distance 128 target with the exact Minecraft 26.2 cache-radius rule while remaining an explicit safety bound.

Vertical occupancy storage is sized from the exact active section range with overflow-safe arithmetic. No assumption that the world is permanently 24 sections tall may be baked into correctness.

Runtime logs must report:

- column capacity/live/high-water;
- column metadata bytes;
- occupancy bytes/words per column;
- installs/removals/slot reuses;
- capacity failures;
- hierarchy audit failures.

### GPU coarse classifier

Add a second narrow Vulkan compute probe dedicated to columns.

Candidate record must contain enough data to derive a conservative chunk-column AABB:

- chunk X/Z;
- min live section Y;
- max live section Y;
- stable column identity;
- optional count/debug fields.

The GPU computes the AABB in camera-relative coordinates and rejects a column only when the whole aggregate AABB is outside a frustum plane using the same conservative epsilon policy as P4.1.

Output:

- atomic visible-column count;
- compacted visible-column identities.

No graphics command consumes this result in P4.2.

### Independent CPU oracle and hierarchy audit

P4.2 must validate both structural and visibility correctness.

Structural audit:

- section -> column membership exact;
- live counts exact;
- min/max exact;
- no duplicate live column coordinates;
- no stale identity after slot reuse.

CPU visibility oracle independently classifies the same column AABBs using Minecraft's authoritative culling frustum.

Required sampled counters:

- CPU visible;
- CPU boundary-ambiguous;
- CPU culled;
- GPU visible;
- missing visible column identities;
- unexpected visible column identities;
- duplicate GPU identities;
- `gpuColumnFalseCullCount`.

All unsafe false-cull/missing/duplicate counters must remain zero.

### Scale evidence

Record:

- live sections;
- live columns;
- sections-per-live-column;
- visible columns;
- coarse visible ratio;
- conservative estimated fine-section candidate upper bound after coarse rejection;
- P4.1 flat section candidate count for comparison where available;
- hierarchy snapshot/upload bytes;
- CPU hierarchy maintenance time;
- camera-only hierarchy maintenance time.

No performance pass threshold is invented before measurement.

The architectural gate remains strict: camera-only frames may reuse a stable column snapshot and must not rebuild or Java-walk the full column or section hierarchy.

## Required runtime exercise

Use the reference Windows 11 / RX 6800 XT / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 Vulkan setup.

Exercise:

1. enter a world and let P4.1 + P4.2 resync/populate;
2. use **render distance 32 or higher** for the main scale sample when practical;
3. hold a stable camera for repeated P4.1 and P4.2 samples;
4. rapid 360-degree turns;
5. horizontal traversal causing real column load/unload churn;
6. vertical traversal across section boundaries;
7. ordinary block edits;
8. F3+T and recovery;
9. world leave/re-entry if practical;
10. normal exit.

Because P4.2 is shadow-only, explicit human visual parity with the promoted P3.10 baseline remains required.

## Promotion gates

P4.2 may promote only when:

- exact dev2 source head passes hosted Java 25 / Gradle 9.5.1 CI;
- hierarchy capacity/memory is explicit and bounded;
- structural hierarchy audits are exact with zero failures;
- GPU/CPU coarse oracle has zero unsafe false culls, zero missing visible identities and zero duplicates;
- P4.1 section-level oracle remains clean;
- camera-only frames do not full-scan/rebuild the Java hierarchy;
- readback/lifetime remains asynchronous/completion-gated;
- inherited P3.10/P3.7/worker/staging/arena/resource gates remain clean;
- no production draw ownership change;
- no native graphics expansion;
- normal exit;
- explicit human visual PASS.

## Explicit non-goals

P4.2 does **not** add:

- production GPU-driven terrain submission;
- real indirect command compaction from live terrain meshes;
- `vkCmdDrawIndexedIndirectCount`;
- native graphics/render-pass ownership;
- temporal occlusion heuristics;
- Hi-Z;
- async compute ownership;
- LOD;
- translucency/fluids/entities/block entities/particles;
- mesher semantic changes;
- partial remeshing/GPU patching.

Real command compaction is a later Phase 4 slice after hierarchy scale/correctness evidence.

## Failure policy

Do not weaken coarse-AABB conservatism, P4.1's fine oracle, capacity safety, inherited production gates or lifecycle requirements after observing data.

If the first hierarchy layout fails correctness or scale evidence, record the failure immutably and change strategy under a new attempt.

## Immediate next action

Implement the bounded persistent column hierarchy and shadow coarse GPU/CPU visibility path for dev2 without changing production draw ownership.
