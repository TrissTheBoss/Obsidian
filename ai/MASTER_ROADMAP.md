# Obsidian Master Roadmap and Product Plan

Last materially revised: 2026-08-23  
Roadmap schema: v1  
Canonical repository: `TrissTheBoss/Obsidian`

This file is the canonical long-range plan for Obsidian. It describes product goals, architecture direction, phase order, validation gates, feature scope, performance strategy, compatibility scope, experiments and release path. It is planning state, not implementation evidence.

Repository roles:

- `ai/CURRENT_STATE.md` — current truth and immediate milestone;
- `ai/MASTER_ROADMAP.md` — intended architecture and build order;
- `ai/DECISIONS.md` — durable engineering/product decisions;
- `ai/ATTEMPT_LOG.md` + `ai/attempts/` — immutable experiment/evidence history;
- source + exact CI + runtime evidence — authority for what actually exists.

---

## 1. Product mission

Obsidian is a client-side Minecraft Java Fabric renderer intended to become a clean Vulkan-only, vendor-neutral replacement for the vanilla terrain/world rendering path and the usual stack of separate renderer/culling/immediate-mode optimization mods.

Priority order:

1. Exceptional 1% and 0.1% lows.
2. Smooth chunk loading, traversal and camera motion.
3. Treat 32 chunks as a normal baseline workload.
4. Scale toward 64/96/128+ render distance where hardware permits.
5. High average FPS after tail latency is controlled.
6. Bounded, observable RAM/VRAM behavior.
7. Minimal visual differences from intended vanilla rendering.
8. Vulkan-only architecture.
9. Vendor-neutral capability-driven baseline.
10. Vanilla/Fabric-first compatibility.
11. Architecture suitable for long-term profiling, debugging and public release.

Current non-goals include OpenGL fallback, early shader-pack compatibility, vendor-specific renderer forks, giant-modpack compatibility before the core is stable, mandatory mesh shaders/work graphs and mandatory LOD.

---

## 2. Reference workload and hardware

Primary reference runtime:

- Windows 11;
- AMD Radeon RX 6800 XT, 16 GB VRAM;
- Ryzen 5 5600X;
- 16 GB DDR4-2666;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25;
- Vulkan backend.

The reference machine does not define a vendor-specific design. Public readiness eventually requires materially different GPU vendors and memory architectures.

Benchmark scenarios include steady state, traversal, chunk streaming, rapid camera turns, teleport/scene replacement, dense caves/overdraw, forests/cutout vegetation, villages/block entities, water/translucency, particles/weather, memory-pressure soak, unload/reload churn and 32/64/96/128+ render-distance scaling.

---

## 3. Status vocabulary

- **COMPLETE** — implementation, required CI/runtime validation and merge are complete.
- **ACTIVE** — current milestone.
- **PLANNED** — intended but not started.
- **EXPERIMENTAL** — optional path requiring measured proof before default use.
- **DEFERRED** — intentionally moved later.
- **BLOCKED** — waiting on named evidence/dependency.
- **REJECTED** — deliberately not planned; rationale must be durable.
- **SUPERSEDED** — replaced by a newer strategy.

---

## 4. Architecture north star

Long-term flow:

`Minecraft/Fabric -> immutable render extraction -> renderer scene database -> async CPU mesh system + GPU scene system -> bounded uploads -> GPU visibility/compaction -> indirect rendering -> Vulkan render graph -> screen`

### 4.1 Game-to-render extraction

- observe world/chunk/section changes;
- convert mutable game state to immutable renderer-owned snapshots;
- capture neighbor/halo data needed for meshing;
- capture exact model/material/light/tint truth for supported render classes;
- carry stable generation/version identity;
- never retain mutable world objects in worker jobs;
- keep extraction bounded on the render/client thread.

### 4.2 Scene database

Target hierarchy:

`World -> Regions -> Chunk Columns -> Sections -> render batches/material classes`

Responsibilities include generations, job state, GPU allocation handles, bounds/visibility metadata, material ranges, dirty/rebuild state, visibility history, memory accounting and safe replacement/unload.

### 4.3 CPU mesh system

Target design:

- work-stealing workers;
- compact immutable inputs;
- worker-local primitive scratch;
- bounded relevance-priority queues;
- cancellation/version checks;
- frame-time-aware admission;
- no allocation-heavy per-face object graph;
- output suited to large GPU arenas/indirect rendering.

Production strategy: **binary/bitmask greedy meshing**, while permanently retaining an independent simple reference oracle.

### 4.4 GPU geometry/metadata

- device-preferred buffers;
- generation-safe allocation handles;
- explicit suballocation;
- bounded persistent staging;
- completion-gated reuse/destruction;
- measurable capacity/high-water/fragmentation;
- no frame-count lifetime guesses;
- no unbounded fallback allocation.

### 4.5 GPU visibility/draw generation

Target flow:

`scene records -> frustum/visibility compute -> optional temporal/Hi-Z tests -> compact surviving draws -> indirect command buffer -> graphics`

Camera rotation should update constants rather than rebuild giant CPU lists. Compute-written indirect data requires explicit synchronization.

### 4.6 Vulkan render graph

Long-term order: upload dependencies, visibility compute, optional depth/Hi-Z, opaque terrain, cutout terrain, entities, block entities, translucent rendering, particles/weather, UI/text, presentation.

### 4.7 Adaptive scheduler

Future inputs: CPU/GPU time, mesh/extraction/upload queues, staging/arena pressure, memory pressure, allocation/GC pressure, motion and chunk relevance. Outputs include mesh admission, upload budget and maintenance/defrag budget.

---

## 5. Non-negotiable constraints

### Render thread

Do not routinely synchronously mesh chunks, walk every loaded section, allocate large hot-path collections, issue thousands of Java draws, perform many tiny submissions, wait indefinitely for uploads/completion or use global device-idle waits.

### Synchronization

CPU frame serials do not prove GPU completion. Reuse/destruction is completion-gated. Polling is nonblocking. Shutdown waits are bounded. Producer/consumer hazards are explicit. No routine `vkDeviceWaitIdle`.

### Memory

Staging and queues are bounded/backpressured. Geometry storage is explicit/measurable. Fragmentation is observable. Compaction/defrag must be latency-safe and budgeted.

### Profiling

Profiling must not create profiler-only submissions. Timestamp collection remains bounded/sampled until deeper access is justified. Performance claims require measurements.

---

## 6. Phase roadmap

### Phase 0 — Bootstrap and compatibility boundary — COMPLETE

Minecraft 26.2/Fabric bootstrap, Vulkan-only activation, renderer conflict handling, nonfatal non-Vulkan behavior, CI/release pipeline and continuity system.

### Phase 1 — Vulkan/GPU infrastructure — COMPLETE

Completion-gated resources, bounded staging, generation-safe device arena, frame graph/timestamps, indexed indirect drawing, narrow native compute/storage seam, compute-generated indirect commands, Synchronization2, GPU visibility/compaction, visible-count handling and deterministic GPU readback validation.

Closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.

### Phase 2 — Real-section correctness and renderer semantics — COMPLETE

- **P2.1 COMPLETE** — immutable section + halo + permanent oracle.
- **P2.2 COMPLETE** — first real drawable/alignment/boundary validation.
- **P2.3 COMPLETE** — material/sprite/UV/tint identity.
- **P2.4 COMPLETE** — exact supported light/shade/AO semantics.
- **P2.5 COMPLETE** — generalized accepted SOLID/CUTOUT vanilla-baked quads.
- **P2.6 COMPLETE** — lifecycle/generation-safe rebuild/install; A-0101 closes fixed-target unload/return proof.
- **P2.7 COMPLETE** — persistent neighboring 3x3 section scene and recentering.

Phase 2 exit criteria are satisfied.

### Phase 3 — Production asynchronous CPU mesher / greedy meshing — ACTIVE

Purpose: make section mesh production fast enough for large-distance streaming without weakening Phase 2 correctness.

#### P3.1 — Worker/job architecture — COMPLETE

Merges:

- dev1 PR #29 `c39cf17b4864e7f7081007238117aea5be3c26e3`;
- dev2 PR #32 `58b2b8b8b1962f2809029e32d147a4a96a93b486`;
- dev3 PR #34 `1b6615eac2494a197cea86d314cf5b099d2418e8`.

Proven: bounded dedicated workers, HIGH/NORMAL/LOW lanes, global priority + stealing, immutable inputs, render-thread capture/GPU ownership, stale-result rejection, async 3x3 install, reusable scratch, determinism audits, bounded admission, metrics and clean shutdown.

#### P3.2 — Binary/bitmask visibility masks — COMPLETE

Validated as `0.3.0-phase3-dev4`; PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.

`BinarySectionVisibility` provides deterministic six-direction conservative topology at exactly 3,072 retained bytes/section, with reusable machine-word scratch, scalar correctness validation and permanent independent `ReferenceFaceMesh` differential proof.

Reference closure: 288/288 jobs, 102,367 visible faces, exact directional/byte accounting, determinism `7/7`, reference audits `7/7`, clean lifetime and exit 0.

#### P3.3 — Greedy rectangle extraction — COMPLETE

Validated as `0.3.0-phase3-dev5`; PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.

Evidence: A-0108, A-0110, A-0111.

Completed contract:

- deterministic primitive topology rectangles consuming only P3.2 masks;
- fixed direction/plane coordinate semantics;
- exact no-missing/no-extra/no-overlap expansion to P3.2 visibility;
- direct independent `ReferenceFaceMesh` equivalence on audit cadence;
- packed 4-byte retained rectangle records;
- bounded reusable scratch;
- production worker sidecar integration while `BakedSectionMesh` remains drawable;
- scheduler/generation/event/resource/completion-gated lifetime behavior preserved.

Reference dev5 runtime:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- 162/162 worker jobs completed;
- 48,261 visible canonical faces -> 21,286 topology rectangles;
- 26,975 faces saved = **55.8% topology reduction**;
- retained rectangle bytes `85,144 = 21,286 * 4`;
- primary mask audits `162/162`;
- rectangle determinism `4/4`;
- independent rectangle/reference audits `4/4`;
- zero queue rejection/failure/join failure;
- clean lifetime and exit 0;
- positive human visual regression verdict.

P3.3 did not emit greedy rectangles to the GPU.

#### P3.4 — Render-correct merge and emission semantics — ACTIVE

Goal: move from topology-only greedy rectangles to render-correct merge candidates and eventually GPU-emitted greedy geometry without weakening exact material/UV/color/light/model semantics.

**dev6 — canonical render-key sidecar — COMPLETE.** Validated as `0.3.0-phase3-dev6`; PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.

Dev6 maps a canonical face only when exactly one baked SOLID/CUTOUT quad from the same source block is proven to be the exact full unit-cube face for that direction. Exact render equivalence includes direction, layer, full material/sprite/tint/shade/emission/animation identity, corner/winding signature, raw per-corner UV bits, exact ARGB and packed light. Arbitrary generalized geometry remains passthrough. Runtime closure proved `renderMergeKeyEvidenceReady=true`, exact accounting/determinism, clean lifetime and exit 0.

**dev7 — render-key-aware merge candidates — COMPLETE.** Validated as `0.3.0-phase3-dev7`; PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`.

`RenderMergeCandidates` partitions the complete dev6-eligible canonical face set directly into deterministic same-render-key rectangles; P3.3 topology boundaries are not mandatory candidate boundaries. Retained payload is 6 logical bytes/candidate. Reference runtime proved 85,880 candidates covering exactly 95,805 eligible faces with 23,617 canonical passthrough faces, 7,318 multi-face candidates and 9,925 saved faces (10.3%), exact `263/263` coverage audits, `6/6` determinism, clean lifetime and exit 0.

Dev7 deliberately did not change GPU geometry. Same per-face payload equality is necessary but not sufficient proof that one four-vertex large quad reproduces repeated unit-face interpolation or atlas-UV resets.

**dev8 — ordinary four-vertex emission-safety classifier — ACTIVE.** Version `0.3.0-phase3-dev8`, branch `phase3/rectangle-emission-safety`, draft PR #40.

A-0122 freezes the exact dev8 contract. For a repeated four-corner payload `P[0..3]`, a merged width requires `P0==P1 && P2==P3`; a merged height requires `P0==P2 && P1==P3`. Apply independently to exact ARGB, packed light and raw atlas `(u,v)` bits. Both-axis merges therefore require a constant four-corner field for each attribute.

`OrdinaryQuadEmissionSafety` retains one flag byte/candidate and classifies color-safe, light-safe, UV-safe and combined ordinary-attribute-safe state. Workers retain the sidecar and report exact safe/unsafe distributions, per-direction accounting, one-byte retained accounting, primary classification audits and deterministic duplicate audits. `ordinaryQuadEmissionSafetyEvidenceReady=true` is the dev8 runtime gate in addition to all prior gates.

A zero ordinary-safe multi-face result is valid evidence; the gate must not manufacture a useful ordinary-quad subset. If atlas UV reset dominates, the next P3.4 slice should design/prove a sprite-local repeat-aware representation rather than stretching atlas UVs or weakening correctness.

Through dev8:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` remains the authoritative GPU drawable.

Any later P3.4 slice that changes emitted GPU geometry requires renewed explicit human visual validation. A future emission slice must also preserve later P3.6 T-junction/rasterization policy obligations rather than treating attribute-safety alone as final proof.

#### P3.5 — Border/halo correctness — PLANNED

Validate face visibility, light/AO and rebuild invalidation across section boundaries with no worker-thread live-world reads.

#### P3.6 — T-junction policy — PLANNED

Default to greedy topology unless real Vulkan hardware shows cracks. Prefer stable positions and targeted mitigation/splitting over globally abandoning greedy meshing.

#### P3.7 — Differential correctness framework — PLANNED

Run reference and optimized meshers on representative snapshots; expand greedy rectangles conceptually to covered faces; compare coverage/material/light/AO truth; preserve failing fixtures. Optimized output never becomes its own oracle.

#### P3.8 — Meshing benchmarks — PLANNED

Track snapshot-to-job latency, P50/P95/P99/max mesh CPU time, input cells, exposed reference faces, greedy rectangles/quads, reduction ratio, vertex/index bytes, scratch high-water, allocations/GC, cancellations/stale jobs and worker utilization.

#### P3.9 — Partial remeshing — EXPERIMENTAL

Only after full-section greedy meshing is stable and measured. Partial slice/subregion rebuilds must prove enough benefit to justify metadata/fragmentation complexity.

Phase 3 exits only when the greedy mesher is default for supported terrain, permanent differential correctness passes, the worker system remains bounded/cancellation-safe, hot paths avoid routine allocation and throughput supports large-scale scene testing.

### Phase 4 — GPU-driven visibility at real-world scale — PLANNED

Persistent region/chunk-column/section hierarchy, GPU frustum culling, conservative temporal visibility, optional Hi-Z, real command compaction, capability/evidence-gated indirect-count consumption and hierarchy suited to 32/64/96/128+ distances.

### Phase 5 — Frame pacing, streaming and adaptive scheduling — PLANNED

Frame budget controller, relevance/age/motion-aware mesh priority, upload budgeting, memory-pressure response and frame-budgeted maintenance/defrag.

### Phase 6 — Transparency and fluids — PLANNED

Translucent classification, water/fluid geometry semantics, section-local translucent representation, camera-relative sorting and deterministic fallback. GPU sorting remains EXPERIMENTAL until proven.

### Phase 7 — Entities — PLANNED

Conservative culling, batching/instancing where compatible, reduced state setup, legal static-geometry caching, update/render separation and profiling.

### Phase 8 — Block entities — PLANNED

Classify static-ish/dynamic block entities, cull conservatively, cache stable state/geometry, batch compatible renderers and profile separately.

### Phase 9 — Particles and weather — PLANNED

Batch particle geometry/state, offscreen culling, bounded particle buffers, weather batching and separate simulation/render optimization.

### Phase 10 — UI, text and immediate rendering — PLANNED

Batch common UI primitives, cache reusable glyph/text geometry, reduce state changes, manage atlas/glyph lifetime and preserve responsiveness.

### Phase 11 — Experimental renderer features — PLANNED / OPTIONAL

Hi-Z, async compute culling, GPU transparency sorting, mesh shaders, aggressive compression, partial remeshing, render-graph aliasing, device-address geometry, async defrag, native indirect-count graphics, alternate visibility hierarchies and optional LOD. Every experiment requires capability checks, diagnostics and safe fallback/disable behavior where feasible.

### Phase 12 — Stabilization, compatibility expansion and public-release readiness — PLANNED

Configuration/UI polish, presets/migration, crash diagnostics, benchmark export, multi-vendor testing, broader model/resource compatibility decisions, selected Fabric compatibility, debug bundles, reproducible releases, licensing decision, regression gates and soak/reload/world-change stress.

---

## 7. Feature inventory

### Terrain core

- [COMPLETE] Immutable section snapshots + halo.
- [COMPLETE] Permanent independent reference oracle.
- [COMPLETE] Production binary/bitmask visibility foundation.
- [COMPLETE foundation] Deterministic topology rectangle extraction.
- [COMPLETE foundation] Canonical render-key classification.
- [COMPLETE foundation] Render-key-aware merge-candidate partition.
- [ACTIVE] Ordinary four-vertex emission-safety classification.
- [PLANNED] Repeat-aware emission representation if dev8 evidence requires it.
- [PLANNED] Key-aware production greedy geometry emission.
- [PLANNED] Full production opaque/cutout terrain replacement.
- [COMPLETE foundation] Supported lighting/AO/tint/material/UV capture truth.
- [PLANNED] Fluids/translucent terrain.
- [COMPLETE foundation] Rebuild/update/unload lifecycle.
- [COMPLETE foundation] Multi-section persistent scene ownership.
- [PLANNED] Large-distance streaming.
- [EXPERIMENTAL] Partial remeshing.
- [EXPERIMENTAL] Optional LOD after full-detail terrain stability.

### GPU scene/draw system

- [COMPLETE foundation] Device-preferred arena/suballocator.
- [COMPLETE foundation] Bounded staging ring.
- [COMPLETE foundation] Frame graph and GPU timestamps.
- [COMPLETE foundation] Indexed indirect rendering.
- [COMPLETE foundation] Compute-generated commands + visibility/compaction primitive.
- [COMPLETE foundation] Persistent multi-section real-scene validation path.
- [PLANNED] Large-scale persistent scene database/culling hierarchy.
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
- [PLANNED] Bounded maintenance/defrag.

Entities, block entities, particles, weather, UI batching and text/glyph caching remain planned in phase order.

---

## 8. Performance measurement plan

Track average/P50/P95/P99/P99.9/max frame time, CPU render-thread time, GPU frame time and observable GC pauses. Renderer metrics include loaded/dirty sections, jobs queued/running/completed/cancelled, mesh percentiles, source faces, greedy rectangles/quads, reduction ratio, vertex/index bytes, staging high-water/backpressure, arena use/fragmentation, visibility counts, compacted commands, draws and stage timings.

Benchmark rules: separate warm-up/steady state, use identical worlds/settings, record render distance/JVM/hardware/driver, identify vanilla/prior/experimental configuration, never hide visual quality reductions and investigate tail-latency regressions even if average FPS improves.

---

## 9. Memory strategy

No validation arena size is automatically a production budget. Production capacity must be explicit/observable, allocation failure bounded/recoverable, growth controlled, memory pressure fed into scheduling, RAM/VRAM reportable against render distance and relocation/defrag frame-budgeted.

---

## 10. Compatibility strategy

Initial promise: Minecraft 26.2 + Fabric + vanilla-first semantics + Vulkan, with no simultaneous full renderer replacement. Unsupported resource/model behavior must be explicit/measurable; never silently approximate complex unsupported geometry as a cube. Broader resource-pack and shader compatibility come later.

---

## 11. Testing ladder

Use the strongest relevant rung: static/invariant review -> exact API inspection -> exact CI -> synthetic GPU validation/readback -> real-section validation -> reference-hardware runtime -> gameplay stress -> benchmark comparison -> soak/stability -> cross-vendor validation.

Compilation alone is never enough when runtime behavior is part of the contract.

---

## 12. Release strategy

Development versions may be direct test JARs/CI artifacts. Internal milestone merges normally use `[no-release]`. Draft PRs remain unmerged until required runtime evidence exists. Public releases are coherent validated checkpoints rather than every dev milestone.

---

## 13. Experimental menu

Experiments must declare capability requirements, expected benefit, failure modes, memory impact, restart/reload requirements and fallback. Where feasible, validation/crash markers should disable only the offending experiment on next launch.

---

## 14. Dependency rules

- correctness before greedy optimization;
- permanent independent Phase 2 oracle throughout Phase 3;
- real terrain before large-scale visibility tuning;
- stable opaque/cutout before complex transparency;
- profiling/evidence before broader native Vulkan expansion;
- full-detail baseline before optional LOD.

---

## 15. Roadmap governance

This file is editable canonical plan state; attempts are immutable.

- **Class A:** status synchronization; update roadmap + `CURRENT_STATE` from existing evidence.
- **Class B:** detail refinement consistent with durable decisions; add an attempt when substantive research produced it.
- **Class C:** phase restructuring requires a new attempt and durable decision when architectural ordering changes.
- **Class D:** product priority/scope changes require explicit superseding decisions.
- **Class E:** major removals become REJECTED/DEFERRED/SUPERSEDED first with durable rationale.

Newer durable decisions override stale roadmap text until synchronized. Always preserve D-0024 greedy/oracle rules and D-0023/D-0025/D-0027 native-scope rules.

---

## 16. Roadmap revision log

### 2026-08-23 — P3.4 dev6/dev7 completion and dev8 activation

- completed dev6 canonical render-key sidecar via PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`;
- completed dev7 render-key-aware merge-candidate sidecar via PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`;
- recorded dev7 real-terrain 95,805 eligible faces -> 85,880 candidates, 7,318 multi-face candidates and 9,925 saved faces (10.3%), exact coverage/determinism and clean lifetime;
- source inspection proved same face key is not sufficient for one ordinary four-vertex rectangle because per-cell color/light interpolation and atlas UV resets can differ;
- activated dev8 ordinary four-vertex emission-safety classification on `phase3/rectangle-emission-safety`, draft PR #40;
- froze exact repeated-field continuity equations and allowed zero ordinary-safe multi-face candidates as a valid measured outcome;
- kept `BakedSectionMesh` authoritative and greedy GPU emission disabled.

### 2026-08-22 — P3.3 completion / P3.4 activation

- completed and merged P3.3 greedy rectangle extraction via PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`;
- recorded successful dev5 runtime with all Phase 3/P3.2/P3.3 gates true;
- recorded 48,261 source faces -> 21,286 rectangles, 55.8% topology reduction, exact mask/reference coverage and clean lifetime;
- retained `BakedSectionMesh` as drawable, so greedy GPU emission remains unclaimed;
- activated P3.4 render-correct merge key;
- froze dev6 correctness-first canonical render-key sidecar in A-0112 and opened draft PR #38;
- preserved arbitrary generalized geometry passthrough and complete visual-key correctness requirements;
- all promotion/synchronization commits use `[no-release]`.

### 2026-08-22 — P3.2 completion / P3.3 activation

- completed P3.2 via PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`;
- recorded dev4 visibility gates, exact six-direction accounting, 3,072 bytes/build, determinism/reference audits and clean lifetime;
- activated P3.3.

### 2026-08-22 — Phase 2 + P3.1 synchronization

Synchronized Phase 2 through P2.7 and P3.1 through dev3 to COMPLETE, including A-0101 fixed-anchor lifecycle proof and validated worker/scheduler merges.

### 2026-08-20 — v1

Created the canonical master roadmap and formal governance model.

---

## 17. Immediate roadmap position

- Phase 0: COMPLETE.
- Phase 1: COMPLETE.
- Phase 2: COMPLETE through P2.7.
- Phase 3: ACTIVE.
- P3.1: COMPLETE through `0.3.0-phase3-dev3`.
- P3.2: COMPLETE through `0.3.0-phase3-dev4`, PR #36.
- P3.3: COMPLETE through `0.3.0-phase3-dev5`, PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.
- P3.4: ACTIVE — dev6 and dev7 complete; dev8 ordinary four-vertex emission-safety classifier is active on draft PR #40; no greedy GPU geometry is claimed yet.
- P3.5-P3.9 remain PLANNED/EXPERIMENTAL as marked.
- Phases 4-12 retain their planned order/scope.

Always verify live details in `ai/CURRENT_STATE.md` before acting because active milestone state changes more frequently than the long-range plan.
