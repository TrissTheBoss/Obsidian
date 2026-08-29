# Obsidian Master Roadmap and Product Plan

Last materially revised: 2026-08-29  
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

#### P3.3 — Greedy rectangle extraction — COMPLETE

Validated as `0.3.0-phase3-dev5`; PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.

`GreedySectionRectangles` deterministically partitions P3.2 topology into packed 4-byte rectangles with exact no-gap/no-overlap expansion, permanent independent reference checks and bounded reusable scratch. P3.3 does not emit topology rectangles to the GPU.

#### P3.4 — Render-correct merge and emission semantics — COMPLETE

Goal: move from topology-only greedy rectangles to render-correct merge candidates and GPU-emitted greedy geometry without weakening exact material/UV/color/light/model semantics.

**dev6 — canonical render-key sidecar — COMPLETE.** Validated as `0.3.0-phase3-dev6`; PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.

Dev6 maps a canonical face only when exactly one baked SOLID/CUTOUT quad from the same source block is proven to be the exact full unit-cube face for that direction. Exact render equivalence includes direction, layer, full material/sprite/tint/shade/emission/animation identity, corner/winding signature, raw per-corner UV bits, exact ARGB and packed light. Arbitrary generalized geometry remains passthrough.

**dev7 — render-key-aware merge candidates — COMPLETE.** Validated as `0.3.0-phase3-dev7`; PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`.

`RenderMergeCandidates` partitions the complete dev6-eligible canonical face set directly into deterministic same-render-key rectangles; P3.3 topology boundaries are not mandatory candidate boundaries. Retained payload is 6 logical bytes/candidate. Runtime closure proved exact eligible coverage, positive multi-face savings, deterministic audits and clean lifetime.

**dev8 — ordinary four-vertex emission-safety classifier — COMPLETE.** Validated as `0.3.0-phase3-dev8`; PR #40 merge `7a15f857a081fba642fcc28811ce88363b5abb66`.

For repeated corner payload `P[0..3]`, a merged width requires `P0==P1 && P2==P3`; a merged height requires `P0==P2 && P1==P3`. The rule is applied independently to exact ARGB, packed light and raw atlas `(u,v)` bits. Runtime evidence proved ordinary atlas UV reset fails for every observed multi-face candidate while light succeeds for all and color succeeds for nearly all, forcing a repeat-aware UV representation rather than stretched atlas UVs.

**dev9 — repeat-aware UV descriptor / representability — COMPLETE.** Validated as `0.3.0-phase3-dev9`; PR #41 merge `59471127162aaf02c9c87e679e1c4c361f968fac`.

`RepeatAwareUvDescriptors` proves that the representative canonical quad has exactly two raw U values × two raw V values with all four combinations exactly once and an affine square flip/rotation mapping from geometric corners to UV corners. Repeat is defined in candidate-local sprite coordinates before remapping into the same source atlas rectangle; full-atlas wrapping is never the correctness model. Retained descriptor size is 19 logical bytes.

Reference dev9 runtime (A-0131):

- all prior gates plus `repeatAwareUvEvidenceReady=true`;
- 261/261 worker jobs, zero rejection/failure/join failure;
- dev7 multi-face candidates: 5,267;
- repeat-aware UV representable/unrepresentable: **5,267 / 0**;
- repeat-aware four-vertex safe/unsafe after color/light constraints: **5,266 / 1**;
- light safe: 5,267 / 5,267;
- color safe: 5,266 / 5,267;
- ordinary atlas UV safe: 0 / 5,267;
- retained bytes `100,073 = 5,267 * 19`;
- classification audits `261/261`, determinism `6/6`;
- clean worker/staging/arena/resource lifetime and Prism exit code 0.

The observed dev9 result removes UV representation and light interpolation as blockers for the multi-face candidate set. The sole observed four-vertex exclusion is color interpolation.

**dev10 — repeat-aware transport/sampling correctness proof — COMPLETE.** Validated as `0.3.0-phase3-dev10`; PR #42 merge `3f75cf4d7e4a65aa6b12053fd75507d1cd292b34`.

Dev10 froze and production-integrated the no-emission transport representation a later merged-quad path must consume:

- candidate-local half-open repeat coordinates with an explicit positive outer-edge endpoint;
- exact dev9 raw atlas bounds/orientation preservation;
- explicit gradients derived from **unwrapped** repeat coordinates rather than derivatives of wrapped/`fract` coordinates;
- source baked vertex-order/diagonal preservation;
- required use of the same live blocks-atlas texture view and sampler under the same resource epoch;
- compact 4-byte transport proof records and deterministic/accounting audits;
- explicit retention of the internal repeat-line raster/T-junction review obligation.

Reference dev10 runtime (A-0135):

- all prior gates plus `repeatAwareTransportEvidenceReady=true`;
- workers `92/92/92`, steals `69`, zero queue-full rejection/failure/join failure;
- dev7 multi-face candidates `2,229`;
- dev9 repeat-aware representable `2,229/2,229`;
- dev9 four-vertex-safe `2,219/2,229`;
- dev10 transport records **2,219**, exactly equal to the dev9-safe set;
- covered faces `5,460`, faces saved `3,241`;
- explicit-gradient / outer-edge / same-atlas-sampler / raster-review obligations all `2,219`;
- internal S/T/both/union reset counts `900 / 1,409 / 90 / 2,219`;
- retained bytes `8,876 = 2,219 * 4`;
- proof audits `92/92`, determinism `4/4`;
- clean worker/staging/arena/resource lifetime and Prism exit code 0;
- `repeatAwareTransportBoundaryRasterObligationOpen=true` intentionally remains open.

Through dev10, `BakedSectionMesh` remained the authoritative drawable and no new visual verdict was required because emitted geometry did not change.

**dev11 — repeat-aware greedy GPU emission canary — COMPLETE.** Validated as `0.3.0-phase3-dev11`; promotion PR #44 merge `b01ff98c4dbe6e548550f86784547afc37db2b2d`.

Dev11 is the first geometry-changing P3.4 slice. It replaces only dev10-proven transport-safe source-face groups with one repeat-aware large quad per admitted candidate while leaving every unsafe, ambiguous, noncanonical and generalized face on the exact passthrough path.

Implemented invariants:

- eligibility never widens beyond exact dev10 transport records;
- dev6/dev7 material/render equivalence remains authoritative;
- dev8 color/light safety remains authoritative;
- dev9 atlas rectangle/orientation remains exact;
- dev10 repeat/remap, explicit-gradient, same-atlas/sampler, positive outer-edge and source-order/diagonal obligations remain exact;
- one admitted merged candidate replaces its covered source faces exactly once, with exact install-time suppression/replacement validation;
- unsafe/noncanonical/generalized source geometry remains present;
- render-thread live capture/GPU ownership, zero worker live-world reads, bounded staging/arena/resource lifetime and independent oracle behavior remain unchanged;
- a dedicated `repeatAwareGreedyGpuEmission=true` flag identifies the actual emitted path; raw P3.3 topology rectangles are not directly drawn.

The merged public-Blaze3D path uses a 60-byte merged vertex format, namespaced repeat-aware shaders, candidate-local atlas remapping, `textureGrad` from unwrapped repeat coordinates, the same live blocks-atlas view/sampler and four fixed indexed-indirect classes: passthrough/merged × SOLID/CUTOUT. No native Vulkan graphics seam expansion was required.

Reference dev11 runtime (A-0140):

- all prior gates plus `repeatAwareGreedyEmissionEvidenceReady=true`;
- workers `284/284/284`, steals `208`, zero queue-full rejection/failure/join failure;
- transport source multi-face/representable/four-vertex-safe `6,565 / 6,565 / 6,541`;
- transport records `6,541`, covered faces `15,800`, faces saved `9,259`;
- validated installed records `261`;
- draw submissions `43,044`;
- actual/expected indirect calls `172,176 / 172,176 = 43,044 * 4`;
- `repeatAwareGreedyInstallValidationPassed=true` and `repeatAwareGreedyFixedFourClassDrawContract=true`;
- READY transitions `29`, rebuilds `28`, camera recenters `11`, resource reloads `1`;
- workers/staging/arena/resources clean and Prism exit code 0;
- explicit human visual PASS: **“Everything looked visually fine.”**

The dev11 visual PASS closes the concrete P3.4 canary raster obligation on the tested Vulkan reference hardware. It does not falsely complete P3.6's broader T-junction policy. The runtime diagnostic `renderCorrectMergeKeyComplete=false` remains a narrower implementation diagnostic; P3.4 roadmap completion means the frozen dev6-dev11 chain satisfied its required merge/emission correctness and canary validation, not that every future terrain/render class is greedily mergeable.

#### P3.5 — Border/halo correctness — COMPLETE

Validated as corrected `0.3.0-phase3-dev12.1`; promotion PR #46 merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.

A-0146 closed the frozen A-0142 contract on the reference RX 6800 XT Vulkan system: all inherited gates through dev11 remained true, `borderHaloCorrectnessEvidenceReady=true`, 248 installed deterministic proofs produced `380,928 / 380,928 / 380,928` outward/binary/reference matches, shared-border comparison was `167,936 / 167,936`, halo-only and vertical dirty dependencies were exercised, worker world reads remained zero, stale installs remained zero, lifetime closed cleanly and process exit was 0. A-0145's cancellation-aware exact residual accounting remains the durable correction for legitimate stage-boundary cancellation.

#### P3.6 — T-junction policy — ACTIVE

A-0147 freezes `0.3.0-phase3-dev13` as a non-geometry-changing evidence slice. The first task is to prove the actual emitted dev10-safe merged topology rather than preemptively split all greedy quads. Detect strict same-facing/coplanar merged/merged T-junctions in exact section-local integer coordinates, prove bounds/lattice/plane identities and camera-relative section transforms, then deliberately visually inspect a runtime that is proven to contain real junctions.

If real detected T-junctions render without cracks/pinholes/flickering seams on the reference Vulkan path, record no baseline mitigation required for the proven path and retain a cross-vendor/scale revisit hook. If artifacts are observed, prefer targeted raster-safe mitigation or selective splitting before any broader topology change. Any geometry-changing mitigation requires a separately frozen slice and renewed explicit visual/runtime validation. D-0024 remains authoritative.

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
- [COMPLETE foundation] Ordinary four-vertex emission-safety classification.
- [COMPLETE foundation] Repeat-aware UV descriptor / representability proof.
- [COMPLETE foundation] Repeat-aware transport/sampling proof.
- [COMPLETE canary] Repeat-aware render-correct greedy GPU emission.
- [COMPLETE] Border/halo visibility, light/AO and rebuild-invalidation correctness.
- [ACTIVE] Evidence-driven T-junction topology/raster policy.
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
- [COMPLETE canary] Repeat-aware large-quad terrain emission for proven-safe canonical candidates.
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

Candidate experiments include Hi-Z, async compute culling, GPU transparency sorting, mesh shaders, alternate compressed geometry, device-address paths, native indirect-count consumption, partial remeshing, optional LOD and async defrag. None are baseline merely because hardware supports them.

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

### 2026-08-29 — P3.5 completion and P3.6 activation

- A-0146 closed corrected dev12.1 P3.5 runtime with every frozen gate true, exact border/reference/shared-border agreement, halo-only + vertical dependency exercise, clean lifetime and process exit 0;
- exact synchronized P3.5 evidence head passed workflow `33262044878`;
- draft PR #45 was superseded only because the connected ready-for-review mutation still uses invalid `Repository.fullDatabaseId`; non-draft PR #46 promoted the exact same green head as `[no-release]` merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`;
- marked P3.5 COMPLETE;
- A-0147 activated P3.6 and froze dev13 as a non-geometry-changing proof of actual emitted strict T-junction topology plus camera-relative transform evidence before deciding whether mitigation is justified;
- P3.7+ phase order remains unchanged.

### 2026-08-29 — P3.4 dev11 completion and P3.5 activation

- completed the geometry-changing dev11 repeat-aware greedy GPU emission canary from exact evidence head `66c38250426cd6d35629fda088ade768420dee0f`;
- A-0140 proved `repeatAwareGreedyEmissionEvidenceReady=true`, exact four-class indirect accounting, clean lifetime and Prism exit code 0;
- received the mandatory explicit human visual PASS on the real RX 6800 XT Vulkan reference system;
- promoted dev11 via PR #44 merge `b01ff98c4dbe6e548550f86784547afc37db2b2d`; PR #43 was superseded only because the connected ready-for-review mutation was broken while it remained draft;
- marked P3.4 render-correct merge/emission semantics COMPLETE through dev11;
- activated P3.5 border/halo correctness;
- kept P3.6 as the broader T-junction policy milestone and kept later phase order unchanged.

### 2026-08-28 — dev10 completion and dev11 geometry-canary activation

- completed dev10 repeat-aware transport/sampling proof via PR #42 merge `3f75cf4d7e4a65aa6b12053fd75507d1cd292b34`;
- A-0135 proved `repeatAwareTransportEvidenceReady=true`, 2,219 exact transport records, exact 4-byte accounting and clean lifetime on the reference Vulkan system;
- preserved `repeatAwareTransportBoundaryRasterObligationOpen=true` as an intentionally unresolved geometry/raster obligation rather than falsely closing it;
- activated dev11 repeat-aware greedy GPU emission canary inside P3.4;
- made renewed explicit human visual validation mandatory for dev11 promotion;
- kept P3.5+ phase order unchanged and kept P3.6 as the broader T-junction policy milestone.

### 2026-08-23 — dev8/dev9 completion and dev10 activation

- completed dev8 ordinary four-vertex emission-safety classification via PR #40 merge `7a15f857a081fba642fcc28811ce88363b5abb66`;
- dev8 proved ordinary atlas UV reset failed for every observed multi-face candidate while light succeeded for all and color for nearly all;
- completed dev9 repeat-aware UV descriptor / representability via PR #41 merge `59471127162aaf02c9c87e679e1c4c361f968fac`;
- A-0131 proved 5,267/5,267 observed multi-face candidates repeat-aware UV representable and 5,266/5,267 repeat-aware four-vertex safe after color/light constraints;
- activated dev10 repeat-aware transport/sampling correctness proof;
- kept `BakedSectionMesh` authoritative and greedy GPU emission disabled;
- retained explicit future visual-validation requirement for any geometry-changing P3.4 slice.

### 2026-08-23 — P3.4 dev6/dev7 completion and dev8 activation

- completed dev6 canonical render-key sidecar via PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`;
- completed dev7 render-key-aware merge-candidate sidecar via PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`;
- recorded dev7 real-terrain eligible/candidate coverage and deterministic clean lifetime;
- source inspection proved same face key is not sufficient for one ordinary four-vertex rectangle because per-cell color/light interpolation and atlas UV resets can differ;
- activated dev8 ordinary four-vertex emission-safety classification;
- kept `BakedSectionMesh` authoritative and greedy GPU emission disabled.

### 2026-08-22 — P3.3 completion / P3.4 activation

- completed and merged P3.3 greedy rectangle extraction via PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`;
- recorded successful dev5 runtime with all Phase 3/P3.2/P3.3 gates true;
- retained `BakedSectionMesh` as drawable, so greedy GPU emission remained unclaimed;
- activated P3.4 render-correct merge key.

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
- P3.3: COMPLETE through `0.3.0-phase3-dev5`, PR #37.
- **P3.4: COMPLETE through `0.3.0-phase3-dev11`, promotion PR #44.**
- **P3.5: COMPLETE through corrected `0.3.0-phase3-dev12.1`, promotion PR #46.**
- **P3.6: ACTIVE — evidence-driven T-junction policy, dev13 contract frozen in A-0147.**
- P3.7-P3.9 remain PLANNED/EXPERIMENTAL as marked.
- Phases 4-12 retain their planned order/scope.

Always verify live details in `ai/CURRENT_STATE.md` before acting because active milestone state changes more frequently than the long-range plan.