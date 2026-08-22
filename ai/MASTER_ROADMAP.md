# Obsidian Master Roadmap and Product Plan

Last materially revised: 2026-08-22  
Roadmap schema: v1  
Canonical repository: `TrissTheBoss/Obsidian`

This file is the canonical description of **where Obsidian is intended to go**: product goals, architecture direction, phase order, validation gates, feature scope, performance strategy, compatibility scope, experiments, and release path.

Repository roles:

- `ai/CURRENT_STATE.md` — what is true now and the immediate next milestone;
- `ai/MASTER_ROADMAP.md` — intended architecture and build order;
- `ai/DECISIONS.md` — durable engineering/product choices and their rationale;
- `ai/ATTEMPT_LOG.md` + `ai/attempts/` — immutable experiment/evidence history;
- source + exact CI + runtime evidence — authority for what is actually implemented.

A roadmap item is not evidence that it exists. Status must be synchronized from implementation/CI/runtime truth.

---

## 1. Product mission

Obsidian is a client-side Minecraft Java Fabric renderer intended to become a clean, Vulkan-only, vendor-neutral replacement for the vanilla terrain/world rendering path and the usual stack of separate renderer/culling/immediate-mode optimization mods.

Primary priorities, in order:

1. Exceptional 1% and 0.1% lows.
2. Smooth chunk loading, traversal, and camera movement.
3. Treat 32 chunks as a normal baseline workload.
4. Scale toward 64/96/128+ render distances where hardware permits.
5. High average FPS after tail latency is controlled.
6. Bounded, observable RAM/VRAM behavior.
7. Minimal visual differences from intended vanilla rendering.
8. Fix obvious rendering defects rather than reproduce them for pixel identity.
9. Vulkan-only architecture.
10. Vendor-neutral baseline selected by capabilities, not GPU brand.
11. Vanilla/Fabric-first compatibility.
12. Architecture fit for long-term debugging, profiling, and public release.

Current non-goals include OpenGL fallback, early shader-pack compatibility, vendor-specific renderer forks, giant-modpack compatibility before the core is stable, mandatory mesh shaders/work graphs, and mandatory LOD.

---

## 2. Reference workload and hardware

Primary development/runtime reference system:

- Windows 11;
- AMD Radeon RX 6800 XT, 16 GB VRAM;
- Ryzen 5 5600X;
- 16 GB DDR4-2666;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25;
- Vulkan backend.

The reference machine does not define a vendor-specific design. Public readiness eventually requires materially different GPU vendors and memory architectures.

Important benchmark scenarios include standing steady state, slow/fast traversal, chunk streaming, rapid camera turns, teleport/scene replacement, dense caves/overdraw, forests/cutout vegetation, villages/block entities, water/translucency, particles/weather, memory-pressure soak, unload/reload churn, and 32/64/96/128+ render-distance scaling.

---

## 3. Status vocabulary

- **COMPLETE** — implementation, required CI/runtime validation, and merge are complete.
- **ACTIVE** — current milestone / actively being implemented.
- **PLANNED** — intended but not started.
- **EXPERIMENTAL** — optional path requiring measured proof before default use.
- **DEFERRED** — intentionally moved later.
- **BLOCKED** — waiting on a named dependency/evidence gap.
- **REJECTED** — deliberately not planned; reason must be durable.
- **SUPERSEDED** — replaced by a newer strategy.

---

## 4. Architecture north star

Long-term flow:

`Minecraft/Fabric -> game-to-render extraction -> immutable render snapshots -> renderer scene database -> asynchronous CPU mesh system + GPU scene system -> bounded uploads -> GPU visibility/compaction -> indirect rendering -> Vulkan render graph -> screen`

### 4.1 Game-to-render extraction

- observe world/chunk/section changes;
- convert mutable game state to immutable renderer-owned snapshots;
- capture neighbor/halo data required for meshing;
- capture/derive material/model/light/tint truth for supported render classes;
- carry stable generation/version identity;
- avoid mutable world objects in long-lived worker jobs;
- keep extraction bounded on the render/client thread.

### 4.2 World scene database

Intended hierarchy:

`World -> Regions -> Chunk Columns -> Sections -> render batches/material classes`

Responsibilities include section generations, job state, GPU allocation handles, bounds/visibility metadata, material ranges, dirty/rebuild state, visibility history, memory accounting, and safe replacement/unload.

The render thread must not rebuild a huge Java visible-section list every frame.

### 4.3 CPU mesh system

Target design:

- work-stealing workers;
- compact immutable inputs;
- worker-local primitive scratch;
- bounded relevance-priority queues;
- cancellation/version checks;
- frame-time-aware admission;
- no allocation-heavy per-face object graph;
- output suited to large GPU arenas and indirect rendering.

The production strategy is **binary/bitmask greedy meshing**. The simple independent reference oracle remains permanently available.

### 4.4 GPU geometry and metadata storage

Baseline:

- non-mapped device-preferred buffers;
- generation-safe allocation handles;
- explicit suballocation;
- bounded persistent staging;
- completion-gated reuse/destruction;
- measurable capacity/high-water/fragmentation;
- no frame-count lifetime guesses;
- no unbounded fallback allocation.

### 4.5 GPU visibility and draw generation

Target pipeline:

`scene records -> frustum/visibility compute -> optional temporal/Hi-Z tests -> compact surviving draws -> indirect command buffer -> graphics`

Camera rotation should update constants rather than rebuild giant CPU lists. Compute-written indirect data must have explicit synchronization.

### 4.6 Vulkan render graph

Long-term conceptual order:

1. upload/transfer dependencies;
2. visibility compute;
3. optional depth prepass;
4. optional Hi-Z build/occlusion;
5. opaque terrain;
6. cutout terrain;
7. entities;
8. block entities;
9. translucent terrain/entities;
10. particles/weather;
11. UI/text;
12. presentation.

The graph is a scheduling/lifetime layer, not permission to create excessive submissions.

### 4.7 Adaptive scheduler

Future inputs include CPU/GPU time, mesh/extraction/upload queues, staging/arena pressure, server pressure where observable, GC/allocation pressure, memory pressure, camera/player motion, and chunk relevance.

Outputs include mesh admission, upload budget, maintenance/defrag budget, and optional prefetch/experiment throttling. The scheduler should deliberately leave headroom when that improves tail latency.

---

## 5. Non-negotiable engineering constraints

### Render thread

Do not routinely synchronously mesh chunks, walk every loaded section, allocate large hot-path collections, issue thousands of Java draw calls, perform many tiny submissions, wait indefinitely for uploads/completion, or use global device-idle waits.

### Synchronization

CPU frame serials do not prove GPU completion. Reuse/destruction is completion-gated. Normal polling is nonblocking. Shutdown waits are bounded. Producer/consumer hazards are explicit. No routine `vkDeviceWaitIdle`.

### Memory

Staging and queues are bounded/backpressured. Geometry storage is explicit and measurable. Fragmentation is observable. Compaction/defrag must be latency-safe and budgeted.

### Profiling

Normal profiling must not create profiler-only submissions. Timestamp collection remains bounded/sampled until deeper allocation-free access is justified by evidence. Performance claims require measurements.

---

## 6. Phase roadmap

### Phase 0 — Bootstrap and compatibility boundary — COMPLETE

Completed Fabric/Minecraft 26.2 bootstrap, renderer conflict handling, Vulkan-only activation model, nonfatal non-Vulkan session behavior, initial CI/release pipeline, and repository continuity system.

Public checkpoint: `v0.0.2-phase0` unless a later release decision supersedes it.

### Phase 1 — Vulkan/GPU infrastructure — COMPLETE

Proven infrastructure includes frame lifecycle/timings, completion-gated resources, bounded persistent staging, device-preferred geometry arenas, generation-safe suballocation, frame graph, integrated GPU timestamps, indexed graphics, indexed indirect drawing, narrow native compute/storage seam where public Blaze3D is insufficient, compute-generated indirect commands, Synchronization2 barriers, GPU visibility selection/compaction, GPU visible counts, zeroed indirect tail, and deterministic GPU readback validation.

Closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.

### Phase 2 — Real-section correctness and renderer semantics — COMPLETE

Purpose: establish what a correct real Minecraft section means before optimizing it.

- **P2.1 COMPLETE** — immutable 16^3 section snapshot + one-block halo + permanent simple reference face oracle. Merge `a714e19ce871bf73136d52f85a1780109aa851dd`.
- **P2.2 COMPLETE** — first drawable real section, world/camera alignment and boundary occlusion validated. Merge `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.
- **P2.3 COMPLETE** — material/sprite/UV/tint identity for conservative supported terrain. Merge `667230f51222746083efe89c72265d80ac9d3929`.
- **P2.4 COMPLETE** — exact supported block/sky light, shade and AO semantics. Merge `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`.
- **P2.5 COMPLETE** — generalized accepted SOLID/CUTOUT vanilla-emitted model quads while keeping P2.1 oracle independent. Merge `c17f7c6146678e18cacabc44d85c67413a040f73`.
- **P2.6 COMPLETE** — dirty/world/resource/chunk lifecycle, generation-safe rebuild/install, completion-gated replacement. Final fixed-target unload/return event-class closure is A-0101. Integration merge `794483f955c861cbf9e24ade2463ba51ab9ab284`.
- **P2.7 COMPLETE** — persistent 3x3 neighboring section scene, multi-section validity domain, recentering, border/camera validation and full reclamation. Integration merge `794483f955c861cbf9e24ade2463ba51ab9ab284`.

Phase 2 exit criteria are satisfied: immutable real snapshots, permanent oracle, validated render semantics, safe rebuild/unload lifecycle, multiple live neighboring sections, and explicit unsupported cases.

### Phase 3 — Production asynchronous CPU mesher / greedy meshing — ACTIVE

Purpose: make section mesh production fast enough for large-distance streaming without sacrificing Phase 2 correctness.

#### P3.1 — Worker/job architecture — COMPLETE

Validated `0.3.0-phase3-dev1` through `dev3`.

Merges:

- dev1 PR #29 `c39cf17b4864e7f7081007238117aea5be3c26e3`;
- dev2 PR #32 `58b2b8b8b1962f2809029e32d147a4a96a93b486`;
- dev3 PR #34 `1b6615eac2494a197cea86d314cf5b099d2418e8`.

Proven foundation:

- dedicated bounded workers and HIGH/NORMAL/LOW queues;
- global priority selection and work stealing;
- immutable inputs only;
- render-thread-only live capture and GPU ownership;
- cancellation/stale-result rejection;
- production async 3x3 scene install;
- reusable worker scratch;
- periodic determinism audits;
- bounded two-record-per-frame admission;
- queue/execution/output/scratch metrics;
- clean bounded shutdown integrated with completion-gated staging/arena/resources.

A-0101 final P3.1 runtime passed `phase3GateReady=true` and `schedulerEvidenceReady=true` with 208/208 jobs completed, 159 steals, all relevance tiers exercised, zero queue-full/failure/join failure and clean shutdown.

#### P3.2 — Binary/bitmask visibility masks — COMPLETE

Validated milestone: `0.3.0-phase3-dev4`.

Closing merge: PR #36 `54ca3cb2d64eda958579407728e757eb0c98b948`.

Evidence: A-0103 through A-0106.

Completed contract:

- pure `BinarySectionVisibility` built only from immutable `SectionSnapshot`;
- six directional masks in permanent oracle order WEST/EAST/DOWN/UP/NORTH/SOUTH;
- 4,096 bits / 64 machine words per direction;
- exactly 3,072 retained bytes for all six direction masks per section;
- reusable 18x18 supported/air halo-row scratch;
- machine-word directional visibility derivation;
- exact conservative semantics `SUPPORTED_FULL_CUBE && neighbor == AIR`;
- unsupported neighbors suppress faces exactly as the independent oracle;
- scalar snapshot self-validation in the correctness-first dev4 path;
- periodic duplicate-mask determinism checks;
- independent `ReferenceFaceMesh` set-equivalence audits;
- production worker integration as a sidecar while existing `BakedSectionMesh` remains drawable authority;
- bounded metrics for builds, directional faces, bytes, build time, scratch and audits.

Reference runtime closure:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleEmission=false`;
- 288/288 production worker jobs completed;
- zero queue-full rejection/failure/shutdown-join failure;
- visibility builds `288`;
- total faces `102,367`;
- WEST/EAST/DOWN/UP/NORTH/SOUTH `7,159 / 11,145 / 4,424 / 56,663 / 15,272 / 7,704`, exact sum `102,367`;
- retained bytes `884,736 = 288 * 3,072`;
- visibility determinism audits/matches `7/7`;
- independent reference audits/matches `7/7`;
- zero dropped lifecycle events / unsafe stale installs;
- workers/staging/arena/resources clean;
- process exit `0`.

The historical Phase 2 fixed-anchor unload/return proof was intentionally not repeated; A-0101 remains authoritative for that already-closed dependency.

No new human visual verdict is recorded for dev4 because P3.2 did not change GPU-emitted geometry. Existing P2/P3.1 visual validation remains the visual baseline while P3.2 closure is differential/topology/runtime based.

#### P3.3 — Greedy rectangle extraction — ACTIVE

Current milestone. Activation does **not** mean greedy rectangles already exist.

Contract:

- consume the proven P3.2 directional masks;
- operate only on immutable worker-owned data;
- use machine-word/primitive worker-local rectangle extraction;
- find deterministic maximal mergeable rectangles for mask-eligible canonical faces;
- preserve orientation/winding;
- measure source-face -> rectangle/quad reduction;
- keep the permanent reference oracle independent;
- preserve every visual merge attribute required for correctness;
- keep non-mergeable/arbitrary generalized baked-model geometry on a safe exact passthrough path;
- prove exact coverage against P3.2/reference semantics before production geometry replacement;
- keep scratch/output bounded and observable;
- preserve scheduler, cancellation, generation/event/resource checks and completion-gated lifetime behavior.

A correctness-first P3.3 dev milestone should initially produce rectangle extraction as a sidecar/differential product. Any milestone that changes GPU-emitted geometry requires renewed runtime and human visual validation.

#### P3.4 — Render-correct merge key — PLANNED

Faces may merge only when every output-affecting property agrees, including face direction, material/sprite identity, layer, tint/color, sky/block light, four-corner AO pattern, AO diagonal choice when relevant, UV behavior, fluid/special state where supported, and model-specific attributes required by the supported block class. Never merge merely because state/block IDs match.

P3.3 may introduce only the portion of this key necessary for faces it actually proves mergeable; P3.4 remains the roadmap checkpoint for the full production merge-key contract.

#### P3.5 — Border/halo correctness — PLANNED

Validate face visibility, light/AO and rebuild invalidation across section boundaries with no worker-thread live-world reads.

#### P3.6 — T-junction policy — PLANNED

Default to greedy topology unless real Vulkan hardware shows cracks. Prefer stable local/eye-relative positions and targeted mitigation/splitting over globally abandoning greedy meshing.

#### P3.7 — Differential correctness framework — PLANNED

Run reference and optimized meshers on representative snapshots; expand greedy rectangles conceptually to covered faces; compare coverage/material/light/AO truth; preserve useful failing fixtures. The optimized path never becomes its own oracle.

#### P3.8 — Meshing benchmarks — PLANNED

Track snapshot-to-job latency, P50/P95/P99/max mesh CPU time, input cells, exposed reference faces, greedy rectangles/quads, reduction ratio, vertex/index bytes, scratch high-water, allocations/GC, cancellations/stale jobs, and worker utilization.

#### P3.9 — Partial remeshing — EXPERIMENTAL

Only after full-section greedy meshing is stable and measured. Partial subregion/slice rebuilds must prove enough CPU benefit to justify metadata/fragmentation complexity.

Phase 3 exit criteria:

- greedy mesher is default for supported terrain;
- reference differential tests pass;
- worker system remains bounded/cancellation-safe;
- hot paths avoid routine allocation;
- throughput supports later large-scale scene testing.

### Phase 4 — GPU-driven visibility at real-world scale — PLANNED

Scale Phase 1 visibility primitives to thousands of real section records:

- persistent region/chunk-column/section hierarchy;
- GPU frustum culling;
- conservative temporal visibility;
- Hi-Z occlusion EXPERIMENTAL initially;
- real command compaction;
- native indirect-count consumption only if profiling justifies it;
- region-level hierarchy for very large render distances.

Exit: persistent real scene data, bounded CPU submission, no giant Java visibility traversal, and measured 32/64/96/128+ scaling.

### Phase 5 — Frame pacing, streaming, and adaptive scheduling — PLANNED

- frame budget controller;
- relevance/age/motion-aware mesh priority;
- upload budgeting from staging/completion pressure;
- memory-pressure response;
- frame-budgeted maintenance/defrag.

Exit: Obsidian background work does not create avoidable streaming spikes and tail-latency data drives tuning.

### Phase 6 — Transparency and fluids — PLANNED

Add translucent classification, water/fluid geometry semantics, section-local translucent representation, camera-relative sorting with minimal rebuilds, and deterministic fallback. GPU transparency sorting remains EXPERIMENTAL until proven.

### Phase 7 — Entities — PLANNED

Conservative entity culling, batching/instancing where compatible, reduced state setup, static geometry caching where legal/useful, animation/update separation, and entity profiling.

### Phase 8 — Block entities — PLANNED

Classify static-ish vs dynamic block entities, cull conservatively, cache stable state/geometry, batch compatible renderers, and profile separately from terrain.

### Phase 9 — Particles and weather — PLANNED

Batch particle geometry/state, offscreen culling, bounded particle buffers, weather batching, and separate simulation/render optimization. GPU particle paths remain experimental.

### Phase 10 — UI, text, and immediate rendering — PLANNED

Batch common UI primitives, cache reusable glyph/text geometry, reduce state changes, manage atlas/glyph lifetime and preserve input/UI responsiveness.

### Phase 11 — Experimental renderer features — PLANNED / OPTIONAL

Candidate experiments include Hi-Z, async compute culling, GPU transparency sorting, mesh shaders, aggressive compression, partial remeshing, render-graph aliasing, device-address geometry, async arena defrag, native indirect-count graphics, alternate visibility hierarchies, and optional LOD.

Experiments require capability checks, explicit settings, conservative defaults, diagnostics, and safe auto-disable/fallback where feasible.

### Phase 12 — Stabilization, compatibility expansion, public-release readiness — PLANNED

Configuration/UI polish, presets, migration, crash diagnostics, benchmark export, multi-vendor testing, broader resource/model compatibility decisions, selected Fabric compatibility, docs/debug bundles, reproducible releases, licensing decision if unresolved, regression gates, soak/reload/world-change stress and upgrade planning.

---

## 7. Feature inventory

### Terrain core

- [COMPLETE foundation] Immutable section snapshots + neighbor halo.
- [COMPLETE foundation] Permanent reference face/mesh oracle.
- [COMPLETE foundation] Production binary/bitmask visibility masks.
- [ACTIVE] Greedy rectangle extraction foundation.
- [PLANNED] Production greedy mesher / generalized merge-key integration.
- [PLANNED] Full production opaque/cutout terrain replacement.
- [COMPLETE foundation] Supported lighting/AO/tint/material/UV truth.
- [PLANNED] Fluids/translucent terrain.
- [COMPLETE foundation] Section rebuild/update/unload lifecycle.
- [COMPLETE foundation] Multi-section persistent scene ownership.
- [PLANNED] Large-distance streaming.
- [EXPERIMENTAL] Partial remeshing.
- [EXPERIMENTAL] Optional LOD after full-detail terrain is stable.

### GPU scene/draw system

- [COMPLETE foundation] Device-preferred arena/suballocator.
- [COMPLETE foundation] Bounded staging ring.
- [COMPLETE foundation] Integrated frame graph and GPU timestamps.
- [COMPLETE foundation] Indexed indirect rendering.
- [COMPLETE foundation] Compute-generated commands + visibility/compaction primitive.
- [COMPLETE foundation] Persistent multi-section real-scene validation path.
- [PLANNED] Large-scale persistent scene database / culling hierarchy.
- [PLANNED] Temporal visibility.
- [EXPERIMENTAL] Hi-Z occlusion.
- [EXPERIMENTAL] Native indirect-count consumption.

### Scheduling/streaming

- [COMPLETE foundation] Work-stealing workers.
- [COMPLETE foundation] Bounded relevance-priority queues.
- [COMPLETE foundation] Stale-job cancellation/safe discard.
- [COMPLETE foundation] Production queue/execution/output/scratch metrics.
- [PLANNED] Adaptive frame-budget controller.
- [PLANNED] Upload budgeting.
- [PLANNED] Memory-pressure handling.
- [PLANNED] Bounded maintenance/defrag work.

### Other rendering domains

Entities, block entities, particles, weather, UI batching and text/glyph caching remain PLANNED in their phase order.

### Profiling/diagnostics

CPU timing, integrated GPU timestamps, worker queue/execution/output metrics, current scene draw/quad/byte accounting and arena fragmentation/high-water are foundation-complete. Percentile reporting, per-stage CPU/GPU timing, upload latency, RAM/VRAM, GC/allocation, large-scale visible/culled counts and benchmark export remain planned.

---

## 8. Performance measurement plan

Required long-term frame metrics: average frame time/FPS, P50/P95/P99/P99.9/max, CPU render-thread time, GPU frame time and GC events/pauses where observable.

Renderer metrics include sections loaded/dirty, jobs queued/running/completed/cancelled, mesh percentiles, faces before greedy, quads after greedy, reduction ratio, vertex/index bytes, staging high-water/backpressure, arena use/fragmentation, visibility counts, compacted commands, draw counts and stage timings.

Benchmark rules:

- distinguish warm-up from steady state;
- use identical worlds/settings when comparing revisions;
- record render distance/JVM/hardware/driver;
- distinguish vanilla, prior Obsidian and experimental configurations;
- do not hide visual quality reductions;
- investigate tail-latency regressions even when average FPS improves.

---

## 9. Memory strategy

No validation arena size is automatically a production budget.

Production policy:

- explicit observable capacity;
- bounded/recoverable allocation failure;
- no surprise unbounded growth;
- memory pressure feeds scheduling;
- reportable RAM/VRAM relationship to render distance;
- compression only when correctness/decode cost are acceptable;
- relocation/defrag work must be frame-budgeted.

Potential later strategies: size-class arenas, region locality, background compaction, cold-section eviction/rebuild, compressed vertex formats and metadata compaction.

---

## 10. Compatibility strategy

Initial promise: Minecraft 26.2 + Fabric + vanilla-first semantics + Vulkan, with no simultaneous full renderer replacement.

Unsupported resource/model behavior must be explicit/measurable; never silently render a complex unsupported case as an incorrect cube merely to increase coverage. Broader ordinary resource-pack compatibility comes after the core renderer is stable. Shader-pack compatibility remains later work.

---

## 11. Testing ladder

Use the strongest relevant rung:

1. static/invariant review;
2. exact API inspection where uncertain;
3. exact CI compilation;
4. synthetic GPU validation/readback;
5. real-section validation;
6. reference-hardware runtime;
7. gameplay stress;
8. benchmark comparison;
9. soak/stability;
10. cross-vendor validation before broad GPU-sensitive public readiness.

Compilation alone is never enough when runtime behavior is part of the contract.

---

## 12. Release strategy

Development versions may be distributed as CI artifacts/direct test JARs. Internal milestone merges normally use `[no-release]`. Draft PRs remain unmerged until required runtime evidence exists.

Public releases are coherent validated checkpoints, not every dev milestone. Future public gates must explicitly define supported Minecraft/Fabric versions, conflicts, block/material scope, visual limitations, Vulkan requirements, tested vendors, config migration, recovery, benchmark/regression state and licensing.

---

## 13. Experimental menu plan

Experiments should declare capability requirements, stability, expected benefit, failure modes, memory impact, restart/reload requirements and fallback behavior. Where feasible, validation/crash markers should auto-disable only the offending experiment on next launch while preserving the rest of the configuration.

---

## 14. Dependency rules

- Correctness before greedy optimization: Phase 3 must keep the independent Phase 2 oracle.
- Real terrain before large-scale visibility tuning: Phase 4 uses real section distributions.
- Stable opaque/cutout path before complex transparency.
- Profiling/evidence before broader native Vulkan expansion.
- Full-detail baseline before optional LOD.

---

## 15. Roadmap governance

This file is editable canonical plan state, unlike immutable attempts.

### Class A — status synchronization

Examples: PLANNED -> ACTIVE, ACTIVE -> COMPLETE, merge SHA/status updates. Requires roadmap + `CURRENT_STATE` synchronization and existing evidence for completion claims.

### Class B — detail refinement

Examples: validation criteria, smaller milestones, metrics, implementation notes consistent with durable decisions. Add an attempt when substantive research produced the refinement.

### Class C — restructuring

Reordering/splitting/merging phases requires a new attempt and durable decision when the ordering itself is architectural.

### Class D — product priority/scope change

Changing Vulkan-only policy, vendor neutrality, tail-latency priority, mandatory LOD, shader/core scope or major compatibility promises requires an explicit durable superseding decision.

### Class E — major removal/rejection

Mark REJECTED/DEFERRED/SUPERSEDED first, record why durably, update dependencies, then later clean stale detail only after history is preserved.

Evidence for COMPLETE may include source SHA, exact CI, artifact/checksum, real-machine runtime, deterministic validation, benchmarks and cross-vendor testing as applicable.

Attempts remain immutable. `CURRENT_STATE.md` should stay concise/current; this roadmap carries long-range detail. Newer durable decisions override stale roadmap text until synchronized.

After roadmap edits verify priorities, current state, decisions, evidence, phase dependencies, experiment labels, D-0024 greedy/oracle rules and D-0023/D-0025/D-0027 native-scope rules.

---

## 16. Roadmap revision log

### 2026-08-22 — P3.2 Class-A completion / P3.3 activation

- completed and merged P3.2 binary/bitmask visibility masks via PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`;
- recorded dev4 reference runtime `phase3GateReady=true`, `schedulerEvidenceReady=true`, `binaryVisibilityEvidenceReady=true`;
- recorded 288 production visibility builds, 102,367 total faces, exact six-direction accounting, exact 3,072 bytes/build, determinism `7/7` and independent reference audits `7/7`;
- retained `BakedSectionMesh` as drawable authority during P3.2, so greedy emission remains unclaimed;
- activated P3.3 greedy rectangle extraction as the next milestone;
- preserved the permanent independent reference oracle, immutable worker input rule, bounded scheduling/lifetime architecture and future renderer-domain ordering;
- P3.2 promotion used `[no-release]`.

### 2026-08-22 — Phase 2 + P3.1 Class-A synchronization

- synchronized Phase 2 through P2.7 to COMPLETE;
- recorded A-0101 fixed-anchor unload/return proof closing the final P2.6 event-class observation;
- synchronized P3.1 dev1/dev2/dev3 to COMPLETE;
- recorded exact retarget CI and validated merges;
- activated P3.2 while preserving D-0024 ordering and native-scope decisions.

### 2026-08-21 — P2.2 through P2.5 Class-A synchronization

Successive status synchronizations recorded validated drawable alignment, texture/material/UV/tint semantics, lighting/AO semantics and generalized SOLID/CUTOUT model-quad support while preserving later lifecycle/multi-section/greedy ordering.

### 2026-08-20 — v1

Created the canonical master roadmap, consolidated product priorities/architecture/phases/features/experiments/profiling/compatibility/release plans, preserved Phase 2 reference-oracle -> Phase 3 binary/bitmask greedy ordering, and established formal change governance.

---

## 17. Immediate roadmap position

- Phase 0: COMPLETE.
- Phase 1: COMPLETE.
- Phase 2: COMPLETE through P2.7.
- Phase 3: ACTIVE.
- P3.1: COMPLETE through `0.3.0-phase3-dev3`.
- P3.2: COMPLETE through `0.3.0-phase3-dev4`, PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3: ACTIVE — greedy rectangle extraction is the next implementation milestone; no greedy production geometry is claimed yet.
- P3.4 and later Phase 3 work remain PLANNED/EXPERIMENTAL according to their sections.
- Phases 4-12 retain their planned order and scope.

Always verify live details in `ai/CURRENT_STATE.md` before acting because active milestone state changes more frequently than the long-range plan.