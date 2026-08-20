# Obsidian Master Roadmap and Product Plan

Last materially revised: 2026-08-21  
Roadmap schema: v1  
Canonical repository: `TrissTheBoss/Obsidian`

This file is the canonical description of **where Obsidian is intended to go**: product goals, planned renderer architecture, phase structure, feature inventory, validation gates, performance strategy, compatibility scope, experiments, release path, and the rules for changing that plan.

It is deliberately different from the other continuity files:

- `ai/CURRENT_STATE.md` answers **what is true right now**.
- `ai/MASTER_ROADMAP.md` answers **what we intend to build and in what order**.
- `ai/DECISIONS.md` answers **why durable choices were made**.
- `ai/attempts/` and `ai/ATTEMPT_LOG.md` answer **what was tried, what happened, and what evidence exists**.
- Source code and CI artifacts remain the authority for **what is actually implemented**.

A roadmap item is not evidence that a feature exists. Any item described below remains planned until `CURRENT_STATE.md`, code, CI, and required runtime evidence say otherwise.

---

## 1. Product mission

Obsidian is a client-side Minecraft Java Fabric renderer intended to become a clean, Vulkan-only, vendor-neutral replacement for the vanilla terrain/world rendering path and the usual stack of separate renderer/culling/immediate-mode optimization mods.

The renderer is being designed around a single central objective:

> **Make Minecraft feel consistently smooth under workloads that normally produce bad frame-time spikes, especially chunk streaming, camera rotation, world traversal, and large render distances.**

Average FPS matters, but it is not allowed to come at the expense of 1%/0.1% lows, uncontrolled memory growth, hidden quality reductions, or unstable synchronization.

### Primary priorities, in order

1. Exceptional 1% and 0.1% lows.
2. Smooth chunk loading, world traversal, and camera movement.
3. Treat 32 chunks as a normal baseline workload rather than an extreme case.
4. Scale cleanly toward 64/96/128+ render distances where hardware and world complexity permit.
5. High average FPS after tail latency is under control.
6. Reasonable, observable, bounded RAM and VRAM behavior.
7. Minimal visual differences from intended vanilla rendering.
8. Fix obvious rendering defects instead of reproducing them solely for pixel identity.
9. Vulkan-only renderer architecture.
10. Vendor-neutral baseline chosen by capabilities, not GPU brand.
11. Vanilla/Fabric-first compatibility.
12. Architecture suitable for eventual public release, debugging, profiling, and long-term maintenance.

### Explicit non-goals for the current roadmap

- OpenGL renderer fallback.
- Iris/shader-pack compatibility as an early blocker.
- Support for old/weak hardware at the expense of the modern renderer design.
- Separate NVIDIA/AMD/Intel renderer implementations.
- Giant-modpack compatibility before the renderer core is stable.
- Pixel-identical reproduction of inefficient vanilla behavior.
- Mandatory mesh shaders, work graphs, vendor extensions, or other bleeding-edge features.
- Mandatory LOD in the initial terrain renderer.

---

## 2. Reference workload and hardware

The primary development/runtime reference system is currently:

- Windows 11
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Minecraft 26.2
- Fabric Loader 0.19.3
- Java 25
- Vulkan backend

This machine is a **reference**, not a vendor-specific design target. Public-readiness validation must eventually include materially different GPU vendors and memory architectures.

### Reference scenarios to optimize for

The eventual benchmark suite should include at least:

- standing still in a loaded area;
- slow forward traversal;
- fast traversal through newly loaded chunks;
- rapid 180-degree camera turns;
- repeated camera sweeps at high render distance;
- teleport / abrupt scene replacement;
- dense terrain with caves and overdraw;
- forests / alpha-tested vegetation;
- villages / block entities;
- water-heavy scenes;
- particle-heavy/weather scenes;
- memory-pressure / long-session soak tests;
- chunk unload/reload churn;
- 32, 64, 96, and 128+ render-distance scaling where practical.

No single scene is allowed to become the only performance oracle.

---

## 3. Roadmap status vocabulary

Every major roadmap item should use one of these meanings consistently:

- **COMPLETE** — implementation, CI, and required runtime validation are complete and merged.
- **ACTIVE** — current milestone or actively being implemented.
- **PLANNED** — intended work, not yet started.
- **EXPERIMENTAL** — optional path that must prove stability/performance before becoming default.
- **DEFERRED** — still potentially wanted but intentionally moved later.
- **BLOCKED** — cannot proceed until a named dependency/evidence gap is resolved.
- **REJECTED** — considered and deliberately not planned; reason must exist in `DECISIONS.md` or an attempt record.
- **SUPERSEDED** — replaced by a newer roadmap item or strategy; historical wording should not simply disappear without a trace.

---

## 4. Architecture north star

Long-term data flow:

`Minecraft/Fabric -> game-to-render extraction -> immutable render snapshots -> world scene database -> asynchronous CPU mesh system + GPU scene system -> bounded uploads -> GPU visibility/compaction -> indirect rendering -> Vulkan render graph -> screen`

### 4.1 Game-to-render extraction

Responsibilities:

- observe Minecraft world/chunk/section changes;
- convert mutable game state into immutable renderer-owned snapshots;
- capture all neighbor data needed for meshing without worker-thread world reads;
- capture or derive material/model/light/tint information needed for supported render classes;
- provide stable generation/version identity so stale async results can be discarded;
- avoid retaining mutable world objects in long-lived worker jobs;
- keep extraction cost bounded on the render/client thread.

Desired end state:

- renderer jobs consume immutable compact data only;
- no random world traversal inside worker meshing;
- updates are versioned and can be cancelled/superseded safely.

### 4.2 World scene database

Intended hierarchy:

`World -> Regions -> Chunk Columns -> Sections -> render batches/material classes`

Responsibilities:

- authoritative renderer-side ownership of section state;
- section generation/version IDs;
- CPU mesh job state;
- GPU arena allocation handles;
- bounding boxes / visibility metadata;
- material/layer ranges;
- dirty/rebuild state;
- last-visible / temporal-visibility information;
- memory accounting;
- safe unload and replacement lifecycle.

The render thread must not rebuild a huge Java visible-section list every frame.

### 4.3 CPU mesh system

Target design:

- work-stealing worker pool;
- compact immutable input snapshots;
- worker-local reusable scratch memory;
- no allocation-heavy per-block/per-face object graph;
- bounded job queues;
- priority based on player/camera relevance and staleness;
- cancellation/version checks;
- frame-time-aware admission so meshing does not steal all CPU headroom;
- output suitable for large GPU arenas and indirect rendering.

The production mesher is planned to be **binary/bitmask greedy meshing**; the simple reference mesher remains permanently available as a correctness oracle.

### 4.4 GPU geometry and metadata storage

Baseline:

- large non-mapped device-preferred buffers;
- generation-safe allocation handles;
- custom suballocation;
- bounded persistent staging ring;
- batched transfer work;
- completion-gated reuse/destruction;
- no frame-count-based lifetime guesses;
- background fragmentation/compaction metrics;
- optional future defragmentation only when it can be made latency-safe.

Planned arena classes eventually include:

- opaque/cutout vertex data;
- index data;
- translucent geometry or sortable primitives;
- per-section metadata;
- GPU scene records;
- indirect command buffers;
- visibility/count/compaction buffers;
- optional material tables.

### 4.5 GPU visibility and draw generation

Target pipeline:

`scene records -> frustum/visibility compute -> optional temporal/Hi-Z tests -> compact surviving draws -> indirect command buffer -> indirect graphics`

Baseline goals:

- camera rotation updates constants, not giant CPU lists;
- section culling is GPU/hierarchical where practical;
- visible draw generation is GPU-owned;
- indirect commands are compacted;
- CPU submission count remains one/few deliberate submissions, not thousands of draws;
- explicit synchronization exists for compute-written graphics inputs.

### 4.6 Vulkan render graph

Long-term conceptual graph:

1. upload completion / transfer dependencies;
2. visibility compute;
3. depth prepass where beneficial;
4. Hi-Z construction when enabled;
5. optional occlusion pass;
6. opaque terrain;
7. cutout terrain;
8. entities;
9. block entities;
10. translucent terrain/entities;
11. particles;
12. weather;
13. UI/text;
14. presentation.

The graph is a scheduling/resource-lifetime layer, not an excuse to create excessive command submissions.

### 4.7 Adaptive scheduler

Planned inputs:

- CPU frame time;
- GPU frame time;
- mesh queue depth;
- snapshot/extraction queue depth;
- upload queue depth;
- staging pressure;
- GPU arena pressure;
- integrated-server/server-thread pressure when observable;
- Java allocation/GC pressure;
- memory pressure;
- camera/player motion;
- chunk priority/distance.

Planned outputs:

- number of mesh jobs admitted;
- upload budget;
- background maintenance/defrag budget;
- optional prefetch aggressiveness;
- expensive experimental feature throttling.

Principle: leave deliberate CPU/GPU headroom when doing so improves tail latency.

---

## 5. Non-negotiable engineering constraints

These constraints apply across all phases unless a later durable decision explicitly supersedes them.

### Render-thread constraints

The render thread may coordinate work but should not routinely:

- walk every loaded section;
- build large visible-section Java collections;
- allocate hot-path temporary objects;
- synchronously mesh chunks;
- issue thousands of draw calls from Java;
- perform many tiny buffer submissions;
- wait indefinitely for uploads or GPU completion;
- execute global device-idle waits.

### Synchronization constraints

- CPU frame serials do not prove GPU completion.
- Reuse/destruction is completion-gated.
- Normal polling is nonblocking.
- Shutdown waits are bounded.
- Compute producer/graphics consumer hazards are explicit.
- No routine `vkDeviceWaitIdle`.

### Memory constraints

- Staging is bounded and backpressured.
- Geometry storage is explicit and measurable.
- No unbounded fallback allocation when a ring/arena is full.
- Fragmentation must be observable.
- Compaction/defrag must never become an uncontrolled frame spike.

### Profiling constraints

- Profiling must not create routine profiler-only submissions.
- Timestamp-result polling is bounded/sampled until an allocation-free path is justified.
- Performance claims require measured evidence, not intuition.

---

## 6. Phase roadmap

The phase numbers describe architectural progression, not release numbers. Individual phases may contain multiple `devN` milestones.

### Phase 0 — Bootstrap and compatibility boundary — COMPLETE

Purpose: establish a loadable Fabric mod and Vulkan activation/compatibility behavior.

Completed outcomes include:

- Fabric/Minecraft 26.2 bootstrap;
- conflict detection;
- Vulkan-only activation model;
- non-Vulkan session remains nonfatal so the user can switch APIs;
- initial CI/release pipeline;
- repository AI continuity system.

Public checkpoint remains `v0.0.2-phase0` until a later release decision.

---

### Phase 1 — Vulkan/GPU infrastructure — COMPLETE

Purpose: prove the low-level renderer primitives before touching real terrain.

Runtime-validated capabilities include:

- frame lifecycle and CPU timings;
- completion-gated frame/resource lifetime;
- bounded persistent staging;
- device-preferred geometry arena;
- generation-safe suballocation;
- fixed frame graph;
- GPU timestamp ranges integrated into useful work;
- first real indexed graphics draw;
- arena-backed indexed indirect drawing;
- narrow native Vulkan compute/storage seam only where Blaze3D lacks compute;
- compute-generated indirect commands;
- explicit Synchronization2 hazards;
- GPU visibility selection;
- atomic indirect-command compaction;
- GPU visible-count generation;
- zeroed indirect tail for public fixed-count graphics;
- deterministic GPU readback validation.

Closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.

Phase 1 is the proven infrastructure foundation for real terrain, not the final production submission architecture.

---

### Phase 2 — Real-section correctness and renderer semantics — ACTIVE

Purpose: define what a **correct Minecraft section** means before optimizing it.

#### P2.1 — Immutable real-section snapshot + reference oracle — COMPLETE

Validated milestone: `0.2.0-phase2-dev1`.

Closing merge: `a714e19ce871bf73136d52f85a1780109aa851dd`.

Runtime evidence: `ai/attempts/A-0058-phase2-dev1-runtime-success.md`.

Implemented contract:

- capture one real loaded 16^3 section;
- one-block halo in every direction;
- primitive-only immutable snapshot;
- no world reads after capture;
- conservative first supported subset;
- canonical deterministic reference-face stream;
- duplicate-build determinism check;
- GPU upload/readback verification using Phase 1 memory infrastructure;
- vanilla terrain remains active.

This reference path must remain in the project even after the production greedy mesher exists.

#### P2.2 — First drawable real section — COMPLETE

Goal: render actual geometry derived from a real Minecraft section for a deliberately narrow supported subset.

Validated milestone: `0.2.0-phase2-dev2`.

Closing merge: `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.

Runtime evidence: `ai/attempts/A-0066-phase2-dev2-runtime-success.md`.

Completed work:

- inspect exact 26.2 block/model/material/sprite/light APIs;
- decide initial terrain vertex format;
- generate vertex/index geometry from the P2.1 snapshot;
- upload to generation-safe arenas;
- create a real section GPU scene record / drawable ownership path sufficient for the correctness probe;
- render through the proven indexed-indirect graphics infrastructure;
- compare against vanilla while vanilla remains active;
- validate world-space positioning and camera transforms;
- validate section boundaries and neighbor occlusion for the conservative supported subset.

Human visual validation confirmed the orientation-colored Obsidian geometry was visible and perfectly aligned with the corresponding vanilla terrain. Machine validation also confirmed deterministic reference/drawable builds, zero post-snapshot world reads during meshing, public Blaze3D indexed-indirect drawing, zero profiler-only submissions, bounded staging, and completion-gated full reclamation.

P2.2 deliberately does not claim P2.3 material/texture/UV/tint semantics or P2.4 light/AO correctness.

#### P2.3 — Correct textures/material identity — PLANNED

Goals:

- atlas/sprite identity for supported model cubes;
- UV mapping semantics;
- tint/color path;
- material/render-layer classification;
- resource reload invalidation;
- stable material IDs or tables suitable for async meshes;
- texture/resource lifetime across reloads.

Resource-pack/custom-model support may remain incomplete initially, but failures must be explicit rather than silently rendering incorrect geometry.

#### P2.4 — Lighting and ambient occlusion correctness — PLANNED

Goals:

- block light;
- sky light;
- per-corner light as required by the chosen format;
- vanilla-intent AO behavior for supported block classes;
- AO diagonal selection based on corner values;
- halo data sufficient for border lighting/AO without live worker world reads;
- deterministic reference representation that Phase 3 can compare against.

#### P2.5 — Broader opaque/cutout block semantics — PLANNED

Expand support deliberately:

- ordinary full cubes;
- axis-aligned simple model cases;
- cutout vegetation/model classes where architecture allows;
- tinted blocks;
- biome-dependent color inputs;
- selected non-full model cases only after exact semantics are understood.

Unsupported cases must remain observable in metrics.

#### P2.6 — Section lifecycle and rebuild correctness — PLANNED

Goals:

- section load;
- section unload;
- block update invalidation;
- neighbor-border invalidation;
- resource reload invalidation;
- stale async result rejection;
- safe replacement of live GPU allocations;
- multiple rebuilds of the same section;
- generation/version identity carried end to end.

#### P2.7 — Multi-section integration — PLANNED

Goals:

- several neighboring real sections simultaneously;
- scene database records rather than one-shot probe ownership;
- stable camera movement;
- no visible duplicate/missing borders;
- bounded upload behavior under rebuild bursts;
- begin retiring synthetic probe-only assumptions.

#### Phase 2 exit criteria

Phase 2 is complete when:

- real section snapshots are immutable and lifecycle-safe;
- a permanent reference oracle exists;
- supported terrain can be rendered with correct position/material/UV/light/AO semantics;
- section updates/unloads/rebuilds are safe;
- multiple sections can be managed through persistent scene ownership;
- unsupported cases are explicit and measurable;
- the renderer has enough semantic truth that an optimized mesher can be judged against it.

Global vanilla terrain replacement is **not required** merely to declare the correctness semantics ready for Phase 3.

---

### Phase 3 — Production asynchronous CPU mesher / greedy meshing — PLANNED

Purpose: make section mesh production fast enough for large-distance streaming without sacrificing the Phase 2 correctness contract.

#### P3.1 — Worker/job architecture

- dedicated meshing worker pool;
- work stealing;
- bounded priority queues;
- immutable snapshot jobs;
- generation/version checks;
- cancellation of stale work;
- worker-local reusable scratch;
- no per-face Java object allocation;
- metrics for queue wait, execution time, cancellation, and output size.

#### P3.2 — Binary/bitmask visibility masks

Production target:

- represent occupancy/face visibility using machine-word bitsets where practical;
- build directional face masks efficiently;
- keep the reference implementation independent for differential tests;
- use compact primitive structures and reuse scratch storage.

#### P3.3 — Greedy rectangle extraction

For each compatible face plane:

- find maximal mergeable rectangles;
- emit one quad per rectangle;
- preserve orientation/winding;
- maintain deterministic output where useful for debugging;
- measure rectangle/quad reduction ratio.

#### P3.4 — Render-correct merge key

Faces may merge only when every property that affects output agrees. The production key must account for all relevant supported semantics, including:

- face direction;
- material/sprite/texture identity;
- render layer;
- tint/color state;
- sky/block light;
- four-corner AO pattern;
- AO diagonal choice when relevant;
- UV behavior;
- fluid/special state where supported;
- model-specific attributes required by the supported block class.

Never merge merely because block IDs match.

#### P3.5 — Border/halo correctness

- face visibility across section boundaries;
- AO/light across boundaries;
- neighboring-section versioning;
- rebuild invalidation when border data changes;
- no worker-thread live-world reads.

#### P3.6 — T-junction policy

Default:

- accept greedy topology unless real hardware demonstrates visible cracks;
- use stable local/eye-relative positions;
- validate on multiple GPUs;
- if needed, prefer targeted expansion/splitting/raster-safe mitigation over globally abandoning greedy meshing.

#### P3.7 — Differential correctness framework

For representative snapshots:

- run reference oracle;
- run greedy mesher;
- expand greedy quads conceptually into covered reference faces;
- compare coverage/material/light/AO semantics;
- preserve failing snapshot fixtures where legally/practically possible;
- never let the optimized mesher become its own oracle.

#### P3.8 — Meshing benchmarks

Track at minimum:

- snapshot-to-job latency;
- mesh CPU time;
- P50/P95/P99/max mesh time;
- input cells;
- exposed reference faces;
- greedy quads;
- reduction ratio;
- vertex bytes;
- index bytes;
- scratch high-water;
- allocations/GC attributable to meshing;
- cancelled/stale jobs;
- worker utilization.

#### P3.9 — Partial remeshing — EXPERIMENTAL

Only after full-section greedy meshing is stable and measured.

Possible direction:

- track dirty subregions/face slices;
- rebuild only affected mesh parts;
- prove reduced CPU work is worth metadata/fragmentation complexity;
- auto-disable if instability or memory fragmentation appears.

Phase 3 exit criteria:

- greedy mesher is default for supported terrain;
- reference differential tests pass;
- worker system is bounded and cancellation-safe;
- hot paths avoid routine allocation;
- throughput supports subsequent large-scale scene testing.

---

### Phase 4 — GPU-driven visibility at real-world scale — PLANNED

Purpose: scale the proven Phase 1 synthetic visibility pipeline to thousands of real section records.

#### P4.1 — Persistent scene hierarchy

- regions;
- chunk columns;
- sections;
- section bounding data;
- geometry allocation identity;
- per-layer draw metadata;
- visibility history;
- compact GPU layout suitable for bulk compute.

#### P4.2 — GPU frustum culling

- camera constants uploaded once per frame/update;
- compute tests section bounds;
- no giant Java visible list;
- compact surviving draw records;
- measure candidates vs visible vs culled.

#### P4.3 — Temporal visibility

Planned conservative strategy:

- remember recently visible sections;
- avoid pathological flicker/overaggressive culling;
- bias toward visibility when uncertain;
- keep correctness ahead of tiny overdraw wins.

#### P4.4 — Hi-Z occlusion — EXPERIMENTAL initially

- depth hierarchy generation;
- conservative section occlusion tests;
- camera-cut handling;
- temporal hysteresis;
- false-positive prevention;
- performance gate before default enablement.

#### P4.5 — Real command compaction

- compact opaque/cutout section draws;
- persistent capacity planning;
- zero-tail fallback remains valid;
- GPU-visible count remains first-class.

#### P4.6 — Indirect-count consumption — EXPERIMENTAL / evidence-gated

`vkCmdDrawIndexedIndirectCount` may be introduced only if profiling shows fixed-capacity public indirect consumption materially matters.

Requirements before enabling:

- feature/capability gate;
- exact graphics render-pass ownership design;
- fallback to public fixed-count path;
- no accidental broad native graphics takeover;
- measured CPU/GPU benefit.

#### P4.7 — Region-level hierarchy

At very large distances, avoid flat tests of every possible section when a coarser hierarchy can reject entire regions/columns cheaply.

Phase 4 exit criteria:

- real section scene data is persistent;
- camera movement does not require massive Java traversal;
- visibility/compaction costs scale acceptably with high render distance;
- indirect draw submission remains bounded;
- 32/64/96/128+ scaling data exists.

---

### Phase 5 — Frame pacing, streaming, and adaptive scheduling — PLANNED

Purpose: convert high raw throughput into consistently good frame times.

#### P5.1 — Frame budget controller

Monitor CPU/GPU frame time and reserve headroom before admitting background work.

#### P5.2 — Mesh scheduling priority

Potential inputs:

- distance;
- view direction;
- whether currently visible;
- whether missing geometry vs stale geometry;
- age of job;
- camera velocity;
- player velocity;
- section importance.

#### P5.3 — Upload budgeting

- bytes per frame/time window;
- staging availability;
- pending transfer count;
- completion latency;
- coalesce adjacent work where practical;
- defer instead of blocking.

#### P5.4 — Memory-pressure response

- arena pressure metrics;
- staged upload pressure;
- optional eviction/rebuild strategy;
- no panic allocation spikes;
- warn/report when desired render distance exceeds practical memory capacity.

#### P5.5 — Background maintenance budgets

Maintenance such as defragmentation, cache cleanup, profiler exports, or resource rebuilds must be frame-budget-aware.

Phase 5 exit criteria:

- chunk streaming stress no longer creates avoidable long spikes from Obsidian work;
- queue/backpressure behavior is observable;
- scheduler reacts predictably under CPU/GPU/memory pressure;
- tail-latency benchmarks drive tuning.

---

### Phase 6 — Transparency and fluids — PLANNED

Purpose: support visually correct translucent terrain without turning sorting into a frame-time disaster.

Planned work:

- translucent material classification;
- water/fluid geometry semantics;
- section-local translucent representation;
- camera-relative sorting strategy;
- minimize full geometry rebuilds caused only by camera movement;
- evaluate bucketed/primitive sorting;
- evaluate GPU sorting only after a stable CPU/reference path exists;
- preserve deterministic fallback.

#### GPU transparency sorting — EXPERIMENTAL

May be exposed in the experimental menu once implemented. It must prove correctness, stable frame pacing, and acceptable memory cost before becoming default.

---

### Phase 7 — Entities — PLANNED

Purpose: make separate entity-culling/per-entity renderer optimization mods unnecessary for the core supported game.

Planned directions:

- conservative entity visibility culling;
- distance/frustum tests;
- batching/instancing where model/material compatibility permits;
- avoid repeated state setup;
- cache static geometry where legal/useful;
- animation/update separation from draw submission;
- profiler counts for entities considered/culled/drawn;
- preserve gameplay-visible semantics.

Do not apply aggressive occlusion that causes popping unless explicitly experimental.

---

### Phase 8 — Block entities — PLANNED

Planned directions:

- classify static-ish vs highly dynamic block entities;
- conservative culling;
- cache stable geometry/state where possible;
- batch compatible renderers;
- avoid rebuilding static content every frame;
- retain correctness for animated/special cases;
- expose counts/timing separately from terrain.

---

### Phase 9 — Particles and weather — PLANNED

Planned directions:

- batch particle geometry/state;
- conservative offscreen culling;
- reduce per-particle submission overhead;
- bounded particle buffer growth;
- weather batching;
- preserve particle simulation semantics separately from rendering optimization;
- profile fill-rate vs CPU bottlenecks.

Possible GPU particle paths remain experimental until proven worthwhile.

---

### Phase 10 — UI, text, and immediate rendering — PLANNED

Goal: remove the remaining reasons to depend on separate immediate-render acceleration mods.

Planned directions:

- batch common UI primitives;
- cache reusable text/glyph geometry;
- reduce redundant state changes;
- atlas/glyph lifetime management;
- keep latency/input responsiveness high;
- profile debug-screen and chat-heavy cases;
- maintain compatibility with Fabric/vanilla UI expectations where practical.

---

### Phase 11 — Experimental renderer features — PLANNED / OPTIONAL

Experimental features should live behind explicit settings and stability tracking. Failure must not silently corrupt normal rendering.

Planned experiment candidates:

- Hi-Z occlusion;
- asynchronous compute culling;
- GPU transparency sorting;
- mesh shaders;
- aggressive mesh compression;
- partial section remeshing;
- render-graph resource aliasing;
- device-address geometry access;
- asynchronous GPU arena defragmentation;
- native indirect-count graphics;
- alternative visibility hierarchies;
- optional experimental LOD.

### Experimental-feature safety model

Desired behavior:

- clearly labeled experimental menu;
- capability checks;
- defaults remain conservative;
- crash/validation failure can auto-disable the offending experiment on next launch;
- preserve last known stable configuration;
- diagnostic reason visible to the user/log;
- experiments do not silently become baseline without a roadmap + decision update.

---

### Phase 12 — Stabilization, compatibility expansion, and public-release readiness — PLANNED

Purpose: turn the renderer from a validated development system into something fit for broad use.

Work includes:

- configuration UI polish;
- sensible presets;
- migration/versioned config;
- crash diagnostics;
- benchmark export tooling;
- multiple-vendor hardware testing;
- clean fallback/disable behavior;
- broader resource-pack/model compatibility decisions;
- selected Fabric compatibility work;
- documentation;
- issue templates/debug bundles;
- reproducible public releases;
- licensing decision before broad public release if still unresolved;
- performance regression gates;
- long-session soak tests;
- world-change/resource-reload stress;
- upgrade path across Minecraft/Fabric versions.

Public release criteria should be written explicitly when the renderer is close enough that real compatibility scope is known.

---

## 7. Feature inventory

This section is a product-level checklist. Phase sections above describe sequencing; this section describes the intended final capability set.

### Terrain core

- [COMPLETE foundation] Real immutable section snapshots.
- [COMPLETE foundation] Neighbor halo/padding.
- [COMPLETE foundation] Permanent reference face/mesh oracle.
- [PLANNED] Production binary/bitmask greedy mesher.
- [PLANNED] Opaque terrain.
- [PLANNED] Cutout terrain.
- [PLANNED] Lighting.
- [PLANNED] Ambient occlusion.
- [PLANNED] Tint/biome color.
- [PLANNED] Resource/material identity.
- [PLANNED] Fluids/translucent terrain.
- [PLANNED] Section rebuild/update/unload lifecycle.
- [PLANNED] Multi-section persistent scene ownership.
- [PLANNED] Large-distance streaming.
- [EXPERIMENTAL] Partial remeshing.
- [EXPERIMENTAL] Optional LOD after full-detail terrain is stable.

### GPU scene / draw system

- [COMPLETE foundation] Device-preferred arena/suballocator.
- [COMPLETE foundation] Bounded staging ring.
- [COMPLETE foundation] Integrated frame graph.
- [COMPLETE foundation] GPU timestamps.
- [COMPLETE foundation] Indexed indirect rendering.
- [COMPLETE foundation] Compute-generated commands.
- [COMPLETE foundation] GPU visibility/compaction primitive.
- [PLANNED] Persistent real-section scene database.
- [PLANNED] Large-scale frustum culling.
- [PLANNED] Hierarchical region/column culling.
- [PLANNED] Temporal visibility.
- [EXPERIMENTAL] Hi-Z occlusion.
- [EXPERIMENTAL] Native indirect-count consumption.

### Scheduling / streaming

- [PLANNED] Work-stealing meshing workers.
- [PLANNED] Priority mesh queue.
- [PLANNED] Stale-job cancellation.
- [PLANNED] Adaptive frame-budget controller.
- [PLANNED] Upload budgeting.
- [PLANNED] Memory-pressure handling.
- [PLANNED] Bounded maintenance/defrag work.

### Other rendering domains

- [PLANNED] Entities.
- [PLANNED] Entity culling/batching.
- [PLANNED] Block entities.
- [PLANNED] Particles.
- [PLANNED] Weather.
- [PLANNED] UI batching.
- [PLANNED] Text/glyph caching.

### Profiling / diagnostics

- [FOUNDATION COMPLETE] CPU frame timing ring.
- [FOUNDATION COMPLETE] Integrated GPU timestamp ranges.
- [PLANNED] P50/P95/P99/P99.9/max reporting.
- [PLANNED] CPU/GPU per-stage timing.
- [PLANNED] Mesh queue depth/latency.
- [PLANNED] Upload queue depth/latency.
- [PLANNED] RAM/VRAM telemetry.
- [PLANNED] allocation/GC telemetry.
- [PLANNED] visible/culled section counts.
- [PLANNED] draws/triangles/quads.
- [PLANNED] arena fragmentation/high-water.
- [PLANNED] benchmark JSON/CSV export.
- [PLANNED] `/render benchmark start` / stop workflow or equivalent final command surface.

### Configuration / UX

- [PLANNED] Vulkan requirement messaging.
- [PLANNED] Performance presets.
- [PLANNED] Detailed advanced settings.
- [PLANNED] Experimental settings page.
- [PLANNED] Auto-disable unstable experiments.
- [PLANNED] Diagnostic status page / log summary.
- [PLANNED] Clear renderer conflict reporting.

---

## 8. Performance measurement plan

Performance work is not accepted solely from subjective feel.

### Required frame metrics

Eventually capture:

- average FPS / average frame time;
- P50;
- P95;
- P99;
- P99.9;
- maximum frame time;
- CPU render-thread time;
- GPU frame time;
- GC events/pauses where observable.

### Required renderer metrics

- sections loaded;
- sections dirty;
- snapshots pending;
- mesh jobs queued/running/completed/cancelled;
- mesh P50/P95/P99/max;
- faces before greedy;
- quads after greedy;
- vertex/index bytes;
- staging bytes/high-water/backpressure;
- arena bytes/high-water/free/fragmentation;
- visible/culled candidates;
- compacted commands;
- indirect draw count;
- triangles/quads submitted;
- resource retirements pending;
- CPU/GPU stage timings.

### Benchmark validity rules

- warm-up must be distinguished from steady state;
- identical world/scenario when comparing revisions;
- record render distance and major graphics settings;
- record JVM/memory settings;
- record hardware/driver;
- distinguish vanilla comparison, prior Obsidian revision, and experimental settings;
- never hide visual differences that materially reduce work;
- investigate regressions in lows even when average FPS rises.

---

## 9. Memory strategy

No fixed production arena size is declared yet; validation capacities used during Phase 1/2 are not assumed to be final budgets.

Production policy:

- capacity is explicit and observable;
- allocation failure is bounded/recoverable;
- no surprise unbounded growth;
- memory pressure feeds the scheduler;
- VRAM/RAM use must be reportable;
- high render distance may consume more memory, but the relationship should be understandable and tunable;
- geometry compression is allowed only if visual correctness and decode cost remain acceptable;
- aggressive compression remains experimental until measured.

Potential future strategies:

- multiple size-class arenas;
- region-oriented locality;
- background compaction;
- cold-section eviction/rebuild;
- compressed vertex formats;
- metadata compaction;
- optional LOD memory tradeoff.

Any strategy that can create large relocation bursts must be frame-budgeted.

---

## 10. Compatibility strategy

### Initial compatibility promise

- Minecraft 26.2;
- Fabric;
- vanilla-first rendering semantics;
- Vulkan backend;
- no simultaneous complete renderer replacement.

### Renderer conflicts

Obsidian intends to make Sodium-class terrain replacement and separate immediate/culling optimization mods unnecessary. Complete renderer conflicts should be detected and rejected clearly rather than allowed to produce undefined ownership.

### Resource packs / custom models

Compatibility may be incomplete during early terrain phases.

Rules:

- unsupported behavior should be measurable/loggable;
- do not silently render a complex case as a wrong cube merely for coverage numbers;
- expand compatibility after core renderer semantics are stable;
- architectural shortcuts that permanently prevent ordinary resource-pack support require a durable decision.

### Shader packs

Iris/shader-pack compatibility is not an early roadmap requirement. It may be revisited after the base renderer and public architecture are stable.

---

## 11. Testing ladder

Every milestone should use the strongest relevant rung, not stop at compilation when runtime behavior is the real risk.

1. **Static/code review** — invariants, lifetime, allocation, error paths.
2. **Exact API inspection** — when Minecraft/LWJGL/Vulkan contracts are uncertain.
3. **CI compilation** — exact declared dependencies.
4. **Synthetic GPU validation** — deterministic data/pixel/readback when appropriate.
5. **Real-world section validation** — actual Minecraft state.
6. **Reference-hardware runtime** — RX 6800 XT machine.
7. **Gameplay stress** — movement, world entry/exit, updates.
8. **Benchmark comparison** — averages and tail latency.
9. **Soak/stability** — long sessions/reloads/world changes.
10. **Cross-vendor validation** — required before broad public readiness for GPU-sensitive features.

A feature should not be promoted merely because a lower rung passed when a higher rung is necessary to prove its actual contract.

---

## 12. Release strategy

### Development milestones

- development versions may be distributed as CI artifacts;
- milestone merges normally use `[no-release]`;
- PRs remain draft until required runtime evidence exists;
- a compile-clean JAR is not automatically a runtime-validated JAR.

### Public releases

Public releases should represent coherent validated checkpoints, not every internal dev milestone.

Before a future major public renderer release, define explicit gates for:

- supported Minecraft/Fabric versions;
- compatible/incompatible renderer mods;
- supported block/material classes;
- known visual limitations;
- minimum Vulkan/device features;
- tested GPU vendors;
- config migration;
- crash recovery;
- benchmark/regression status;
- license state.

---

## 13. Experimental menu plan

The eventual experimental menu is intended to contain high-risk/high-variance features separately from baseline settings.

Candidate toggles:

- Hi-Z occlusion;
- async compute culling;
- GPU transparency sorting;
- mesh shaders;
- aggressive mesh compression;
- partial section remeshing;
- render-graph aliasing;
- device-address geometry;
- async GPU defragmentation;
- native indirect-count draw;
- optional LOD.

Each experiment should declare:

- capability requirements;
- stability level;
- expected benefit;
- known failure modes;
- memory impact;
- whether restart/reload is required;
- fallback behavior.

Desired auto-disable behavior:

- detect a validation/crash marker attributable to an experiment where feasible;
- disable that experiment for the next launch;
- retain the rest of the user's configuration;
- log the reason;
- allow deliberate re-enable.

---

## 14. Roadmap dependency rules

Some ordering is intentional and should not be casually bypassed.

### Correctness before greedy optimization

Phase 2 reference semantics must exist before Phase 3 greedy meshing becomes production. Greedy output must have an independent oracle.

### Real terrain before large-scale visibility tuning

Phase 1 proves GPU primitives, but Phase 4 tuning must use real section distributions/geometry rather than synthetic triangles alone.

### Stable opaque path before complex transparency

Transparency sorting should not distort the architecture before ordinary opaque/cutout terrain lifecycle is stable.

### Profiling before deep native expansion

Broader native Vulkan ownership is justified only by a concrete missing public capability or measured bottleneck.

### Full-detail baseline before LOD

LOD remains optional/experimental until the full-detail renderer works well enough to measure what LOD would actually solve.

---

## 15. Roadmap governance: how this file may be changed

This section is mandatory procedure for AI agents and maintainers.

### 15.1 Treat this file as canonical plan state, not an append-only log

Unlike attempt records, the roadmap **is allowed to change** as the product plan changes. However, meaningful changes must remain explainable through decisions/attempts/PR history.

Do not preserve stale wording merely for history; preserve the **reason/history elsewhere**, then keep this file readable as the best current plan.

### 15.2 Classify every roadmap edit

Before editing, classify the change:

#### Class A — Status synchronization

Examples:

- PLANNED -> ACTIVE;
- ACTIVE -> COMPLETE after merge/runtime validation;
- updating the active dev milestone;
- adding the merge SHA for a completed phase.

Required records:

- update this roadmap;
- update `CURRENT_STATE.md` if current truth changed;
- attempt/runtime evidence must already exist for claims of validation.

A new architecture decision is not required if the plan itself did not change.

#### Class B — Detail refinement

Examples:

- expanding validation criteria;
- breaking a planned phase into smaller milestones;
- clarifying metrics;
- adding implementation notes consistent with existing decisions.

Required records:

- update this roadmap;
- add an attempt if the refinement came from substantive research/API inspection;
- update `CURRENT_STATE.md` only if immediate next work changed.

#### Class C — Roadmap restructuring

Examples:

- moving greedy meshing to a different phase;
- reordering transparency/entities;
- splitting or merging phases;
- changing a major dependency/gate.

Required records:

- update this roadmap;
- create a new immutable attempt explaining the proposed/researched change;
- add or supersede a `DECISIONS.md` entry when the ordering reflects a durable engineering/product choice;
- update `CURRENT_STATE.md` if the active or next milestone changed;
- summarize the change in the active PR/issue when relevant.

#### Class D — Product priority/scope change

Examples:

- adding OpenGL support;
- changing Vulkan-only policy;
- making shaders a core requirement;
- abandoning vendor neutrality;
- changing tail latency from the top priority;
- making LOD mandatory;
- broadening/narrowing compatibility promises substantially;
- dropping a major promised renderer domain.

Required records:

- explicit durable decision in `DECISIONS.md` that supersedes prior policy;
- roadmap update;
- `CURRENT_STATE.md` update;
- attempt/research evidence where technical reasoning is involved;
- active PR/issue summary;
- do not silently overwrite the old goal.

#### Class E — Removal/rejection of a major feature

Do not simply delete it.

Required procedure:

1. mark the roadmap item `REJECTED`, `DEFERRED`, or `SUPERSEDED` in the revision that makes the decision;
2. record why in `DECISIONS.md` or an immutable attempt;
3. identify the replacement if superseded;
4. update dependent phases;
5. after the historical reason is durable, later cleanup may remove excessive stale detail from the canonical roadmap while retaining a concise reference to the superseding decision.

### 15.3 Evidence rules

Do not mark an item COMPLETE based on intention, compilation alone, or chat discussion when runtime behavior is part of the feature contract.

Evidence hierarchy should include, as applicable:

- exact source/commit SHA;
- CI run;
- artifact/checksum;
- real-machine runtime log;
- deterministic validation/readback;
- benchmark data;
- cross-vendor test;
- attempt record.

### 15.4 Keep CURRENT_STATE and roadmap roles separate

`CURRENT_STATE.md` should remain concise enough to answer:

- what branch/PR/version is active;
- what just passed;
- what is currently being tested;
- what the next concrete action is.

Do **not** copy the entire roadmap into `CURRENT_STATE.md`.

The roadmap should contain long-range plan detail and should link conceptually to the current phase without becoming a daily log.

### 15.5 Decisions override stale roadmap text

If `DECISIONS.md` contains a newer active decision that conflicts with this roadmap, the decision is the stronger architectural record until the roadmap is synchronized.

The agent that notices the mismatch should fix the roadmap as part of the same coherent work when authorized.

### 15.6 Attempts remain immutable

Never edit old attempt files merely because the roadmap changed.

Create a new attempt that says what changed and what prior assumption/plan it supersedes.

### 15.7 Roadmap edits and Git workflow

For roadmap changes made during an active feature branch:

- update the roadmap on that branch;
- include the roadmap change in the existing coherent PR rather than opening a duplicate PR solely for the same milestone;
- mention material roadmap changes in the PR body;
- allow normal CI to run even for docs-heavy branches when the repository workflow does so;
- do not claim source behavior changed when only docs changed.

For roadmap-only work outside an active feature branch, use a dedicated documentation/planning branch and draft PR unless repository policy later changes.

### 15.8 Required review checklist after roadmap edits

Before handoff, verify:

- priorities still match `OPERATING_MANUAL.md`;
- current status matches `CURRENT_STATE.md`;
- durable architecture choices match `DECISIONS.md`;
- newly completed items have evidence;
- no planned feature was silently deleted;
- phase dependencies still make sense;
- experimental items remain clearly marked;
- greedy meshing remains governed by D-0024 unless superseded explicitly;
- native Vulkan scope remains governed by D-0023/D-0025/D-0027 unless superseded explicitly;
- next milestone is clear;
- the active PR describes any material plan change.

---

## 16. Roadmap revision log

This is a concise index, **not** the evidence log. Detailed reasoning belongs in attempts/decisions.

### 2026-08-21 — P2.2 Class-A status synchronization

- synchronized P2.1 to COMPLETE with its runtime evidence and merge SHA;
- synchronized P2.2 to COMPLETE after exact CI, reference RX 6800 XT runtime validation, human-visible alignment validation, and merge `f9c64267c5becb3bd80897efdb09ed65a6ce8697`;
- marked the immutable snapshot, neighbor halo, and permanent reference oracle terrain foundations complete;
- left P2.3 as the next planned milestone without changing later phase ordering, product scope, or durable architecture.

### 2026-08-20 — v1

- created the canonical master roadmap;
- consolidated product priorities, architecture direction, all major phases, feature inventory, experimental plan, profiling/benchmark strategy, compatibility/release plan, and roadmap governance;
- preserved Phase 2 reference-oracle -> Phase 3 binary/bitmask greedy-meshing ordering;
- established formal change classes and synchronization procedure with `CURRENT_STATE.md`, `DECISIONS.md`, and immutable attempt records.

---

## 17. Immediate roadmap position

Current position at the time of this revision:

- Phase 0: COMPLETE.
- Phase 1: COMPLETE through GPU visibility/indirect compaction.
- Phase 2: ACTIVE.
- P2.1: COMPLETE / `0.2.0-phase2-dev1`.
- P2.2: COMPLETE / `0.2.0-phase2-dev2`, first drawable real section with human-validated world/camera alignment.
- Next planned milestone: P2.3, correct texture/material identity for the supported terrain subset.
- Phase 3 production binary/bitmask greedy meshing remains planned after Phase 2 establishes correct render semantics.

Always verify the live details in `ai/CURRENT_STATE.md` before acting on this final section because active milestone state changes more frequently than the long-range plan.
