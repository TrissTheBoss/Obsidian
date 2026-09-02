# A-0203 - Phase 4 P4.1 persistent large-scene GPU visibility contract

**Date:** 2026-09-02  
**Status:** `SUCCESS` / **CONTRACT FROZEN BEFORE RENDERER-SOURCE CHANGE**  
**Branch:** `phase4/p4.1-persistent-scene-visibility`  
**Base:** synchronized Phase 3 merge `01547b55f68690a5d0aac8405fc0fe91cdf440f9`

## Objective

Begin Phase 4 — GPU-driven visibility at real-world scale — by scaling the already-proven Phase 1 GPU visibility/compaction producer architecture to a persistent, renderer-owned section metadata database without immediately changing P3.10 production draw ownership.

P4.1 is a correctness-first **shadow visibility** milestone. P3.10 remains the authoritative production opaque/cutout replacement/fallback path while P4.1 proves that a large persistent scene can be maintained incrementally and that GPU frustum visibility over that scene agrees with an independent CPU oracle at real camera positions and render-distance-scale candidate counts.

The milestone intentionally separates two risks:

1. **P4.1:** large persistent scene metadata + GPU frustum classification/compaction correctness and scale evidence;
2. **later Phase 4 slice:** connect proven GPU-visible production records to the same-OPAQUE-pass P3.10 draw path and remove CPU visible-list construction where safe.

No production terrain suppression/draw behavior may change in P4.1.

## Proven foundation to reuse, not redesign

A-0048/A-0049/A-0050/A-0052 already proved on the reference RX 6800 XT:

`GPU scene records -> native compute visibility -> atomic front compaction -> GPU visible count -> zero unused indirect tail -> public Blaze3D fixed-count indexed-indirect graphics -> deterministic readback`

Durable ownership rules remain:

- D-0025: native Vulkan stays narrow and uses Minecraft's existing Vulkan device/command stream; no second device/queue ownership architecture;
- D-0026: every compute-write consumer edge is explicit Synchronization2, never assumed from command order;
- D-0027: do not widen native graphics ownership merely to consume an indirect-count buffer; public Blaze3D fixed-count indirect + zero tail remains the graphics baseline until measured Phase 4 evidence separately justifies a different decision.

The existing `VulkanVisibilityCompactor` is a four-candidate validation implementation, not a scalable production object. Its exact proven synchronization and ownership shape should be generalized, not copied blindly with fixed constants.

## P4.1 scene model

Introduce a renderer-owned **persistent section metadata database** distinct from the existing P3.10 3x3x1 mesh canary.

Each live scene slot represents one loaded Minecraft section coordinate and at minimum carries:

- world section X/Y/Z as integer identity;
- slot generation/version;
- world/resource epoch identity needed to reject stale updates;
- loaded/live flag;
- implicit exact 16x16x16 section AABB derived from section coordinates;
- a stable validation command identity/template sufficient to prove GPU compaction correspondence without drawing it in P4.1.

The CPU database must use fixed/bounded primitive storage, stable slot handles/generations and a free-list or equivalent bounded reuse strategy. No object-per-face/object-per-frame scene representation is allowed.

### Event-driven population

Scene membership is maintained incrementally from lifecycle events, not reconstructed by scanning every loaded/rendered section each frame.

Required event classes:

- world replacement/teardown: invalidate all previous slots/generations;
- chunk load: discover/enqueue that chunk column's section records once;
- chunk unload: generation-safe removal of that column's slots;
- resource reload: invalidate any metadata whose correctness depends on resource epoch, while geometry-independent section bounds may be retained only if explicitly proven safe;
- section dirty events do **not** require global scene reconstruction; if P4.1 metadata tracks only load/bounds identity, dirtiness is diagnostic unless exact inspection shows membership can change and requires a bounded update.

Any render-thread chunk/section inspection triggered by load/update is bounded/admission-controlled. A camera turn alone must not cause an O(all loaded sections) Java rebuild.

### Capacity and memory

Capacity must be explicit and measurable. Implementation must:

- compute required candidate capacity with overflow-safe arithmetic from the active supported scene policy;
- enforce a documented hard upper bound rather than allocate unboundedly;
- report candidate capacity, live/high-water slots, metadata bytes, GPU input/output bytes, allocation failures and fallback/disabled state;
- fail/fallback conservatively if the scene cannot be represented within the configured bound;
- never silently drop live candidates because capacity was exceeded.

The first implementation may choose a hard bound after exact vertical-range/render-distance API inspection. The bound is a memory/safety constant, **not** a performance pass threshold and must be recorded before runtime measurement.

## GPU input and frustum contract

P4.1 uses the real Minecraft world camera/frustum for classification.

Before source implementation, exact Minecraft 26.2 API inspection must identify:

- the authoritative camera position/view/projection data available at the relevant frame point;
- the exact active render-distance/view-distance value or a safe renderer-scene radius source;
- loaded chunk/section enumeration APIs suitable for event-triggered population;
- world min/max section Y APIs;
- any coordinate-space conventions needed to derive six frustum planes without guessing mappings from another version.

GPU candidate bounds use section-coordinate-derived AABBs. Camera-relative arithmetic must avoid catastrophic world-coordinate precision loss; the design should transport integer section identity and camera origin/constants rather than upload huge absolute float positions if exact inspection supports that form.

### Conservative plane/AABB rule

The GPU may cull a section only when the entire section AABB is definitely outside at least one frustum plane. Borderline/ambiguous cases are visible, never culled.

The CPU oracle is independently implemented from the same immutable scene snapshot and camera constants. It must not call the GPU implementation or use GPU output as its reference.

To avoid turning float boundary noise into false safety claims:

- CPU oracle classifies `VISIBLE`, `CULLED`, or `BOUNDARY_AMBIGUOUS` using a frozen conservative epsilon derived from the exact float transport representation;
- GPU must never classify CPU `VISIBLE` or `BOUNDARY_AMBIGUOUS` candidates as culled;
- away from the ambiguity band, CPU/GPU visibility must match exactly;
- ambiguity count is reported and must not be hidden.

If exact API/float inspection permits a stronger exact bit-for-bit CPU/GPU rule, a later attempt may tighten this contract before measurement, never after observing a failure.

## GPU compaction contract

Generalize the Phase 1 producer path to N candidates:

1. reset output visible count and validation command/output storage;
2. explicit reset/write -> compute dependency where required;
3. dispatch enough workgroups to classify all live candidate slots;
4. visible candidates reserve compacted front slots and write a stable per-candidate identity/template;
5. write the GPU visible count;
6. explicit compute-write -> transfer/readback dependency for sampled validation;
7. if/when an indirect command buffer is emitted in P4.1 shadow mode, unused command tail entries must be fully zero, preserving D-0027 semantics even though P4.1 does not submit those commands to production graphics.

No assumption of a single workgroup/global barrier is allowed. The four-invocation dev9 reset pattern must be replaced with a scalable reset/dispatch ordering mechanism.

Atomic compaction order is not required to be numerically sorted. Correctness is defined by:

- exact visible count for non-ambiguous candidates plus conservative inclusion of ambiguous candidates;
- no missing visible candidate identity;
- no duplicate candidate identity;
- no culled candidate identity in compacted output away from the ambiguity rule;
- every compacted identity maps to a currently live generation/slot;
- deterministic visibility bit/classification result for identical inputs;
- command-tail zero correctness whenever command-format shadow output is enabled.

## Validation/readback policy

GPU readback is validation instrumentation, not a steady-state production dependency.

- Never block the render thread waiting for readback.
- Use completion-gated asynchronous readback through the existing frame/resource lifetime model.
- Sample at a bounded cadence and/or after meaningful scene/camera transitions rather than every frame once basic correctness is established.
- Retain enough sampled evidence to cover steady camera, rapid turn, horizontal traversal, vertical traversal, chunk load/unload churn, world replacement and F3+T.

The authoritative runtime correctness counters include at least:

- scene live slots / high-water / capacity;
- chunk columns added/removed;
- scene slot installs/removals/reuses;
- stale generation update rejections;
- capacity/fallback events;
- GPU visibility dispatches and candidates tested;
- CPU visible / culled / ambiguous counts;
- GPU visible count;
- missing visible identities;
- duplicate visible identities;
- unexpected culled identities in GPU output;
- GPU false-cull count (must be zero);
- sampled comparison records and exact/conservative matches;
- readback pending/high-water and completion state;
- input/output GPU bytes;
- native compute seam true only where actually used;
- native graphics expansion **false**.

## Performance evidence policy

P4.1 captures a baseline; it does **not** invent a numeric pass threshold before measurement.

Measure at minimum:

- render-thread CPU maintenance cost for scene updates separately from camera-only frames;
- candidate count versus configured/rendered distance;
- GPU visibility dispatch timing through the existing timestamp framework where it can be measured without profiler-only submissions;
- scene metadata upload bytes and update frequency;
- compacted visible ratio/count;
- allocation/GC behavior attributable to P4.1;
- memory high-water.

The key architectural gate is qualitative but strict: a camera-only frame must not rebuild or Java-walk the full persistent section database. Any measured frame-critical O(all loaded sections) CPU path introduced by P4.1 is a design failure even if the reference machine's average FPS appears acceptable.

## Inherited P3.10 / Phase 3 gates

P4.1 is shadow-only and must not weaken the now-canonical terrain renderer:

- P3.10 same-OPAQUE-pass production replacement behavior unchanged;
- complete-capture conservative vanilla fallback unchanged;
- leaves/kelp unsupported content remains correct through vanilla fallback;
- same-column Y recenter behavior remains correct for the existing P3.10 canary until superseded by a later production large-scene design;
- P3.7 differential correctness remains authoritative for P3.10 mesh records;
- worker world reads after capture remain zero;
- synchronous scene mesh builds remain zero;
- unsafe stale installs remain zero;
- staging/arena/deferred-resource lifetime remains completion-gated and clean;
- no P3.9 partial-remeshing revival.

## Explicit P4.1 non-goals

Do **not** add in this milestone:

- production GPU-driven terrain draw ownership;
- native Vulkan graphics/render-pass takeover;
- `vkCmdDrawIndexedIndirectCount` consumption;
- Hi-Z/occlusion culling;
- temporal occlusion heuristics;
- async-compute queue ownership;
- mesh shaders/work graphs;
- LOD;
- translucency/fluids;
- entity/block-entity/particle visibility;
- mesher semantic changes;
- partial remeshing/GPU patching.

## Required runtime exercise

Reference runtime must include:

1. world entry and persistent scene population;
2. stable camera with scene at useful scale;
3. rapid 360-degree camera turns without CPU scene rebuild;
4. horizontal traversal causing chunk-column add/remove churn;
5. vertical movement across section boundaries;
6. ordinary block edit while P3.10 remains active;
7. F3+T reload and recovery;
8. at least one world leave/re-entry or replacement if practical;
9. normal shutdown.

Visual behavior must remain indistinguishable from the promoted P3.10 baseline because P4.1 is shadow-only. Any visual change is therefore a regression.

## Promotion gates

P4.1 may promote only when:

- exact source head passes hosted Java 25 / Gradle 9.5.1 CI;
- persistent scene capacity/memory is explicit and bounded;
- real camera/section API seams were established from exact Minecraft 26.2 evidence, not guessed;
- real-world-scale candidate counts are observed;
- CPU/GPU oracle comparison has zero unsafe false culls, zero missing visible identities and zero duplicates across the required exercise;
- sampled readback/lifetime completes without render-thread blocking;
- camera-only frames do not perform full Java scene reconstruction/scan;
- all inherited P3.10/P3.7/worker/lifetime gates remain clean;
- process exits normally;
- human visual verdict is PASS.

## Failure policy

Do not weaken the oracle, ambiguity rule, capacity safety, inherited P3.10 gates or lifecycle requirements after observing data. If the first metadata layout/dispatch strategy fails correctness or scale evidence, record the failure immutably and change the strategy under a new attempt.

## Immediate next action

Perform exact Minecraft 26.2 API/source inspection for large-scene membership, vertical section range, view distance and authoritative camera/frustum data, then record that result before implementing the first P4.1 source classes. No renderer-source change is authorized before that inspection is complete.
