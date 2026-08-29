# A-0159 - P3.9 dev16 partial-remeshing experiment contract freeze

**Date:** 2026-08-29  
**Objective:** Freeze the first P3.9 partial-remeshing experiment from exact synchronized P3.8-complete `main` before any P3.9 source change.  
**Status:** `PLAN FROZEN`  
**Planned version:** `0.3.0-phase3-dev16`  
**Branch:** `phase3/partial-remeshing`  
**Base main:** `169274b468d2a278d39043938efff19844bec9ba`  
**Base CI:** Build `33272073819` — Java 25 / Gradle 9.5.1 SUCCESS, artifact upload SUCCESS, release SKIPPED.

## Context

P3.8 is complete. A-0158 established the first trustworthy full-section production-mesher reference baseline on the canonical dev15 package. The exact synchronized P3.8 promotion head `144875e71069f7377a97c78947883592e5c88913` passed Build `33271895037`; non-draft same-head PR #52 merged `[no-release]` as `49385aedff74f2382fcd9a9bb44e59cf559e63c4`; synchronized P3.8-complete `main` head `169274b468d2a278d39043938efff19844bec9ba` passed Build `33272073819`.

The P3.9 roadmap is explicitly EXPERIMENTAL: partial slice/subregion rebuilds are permitted only after full-section greedy meshing is stable and measured, and they must prove enough benefit to justify metadata and fragmentation complexity. The proven full-section path therefore remains the control and fallback until a partial path earns promotion.

## Source findings before freeze

Current source makes the experimental boundary concrete:

1. `AsyncMultiSectionSceneProbe` drains scene-level lifecycle reasons. Any relevant event increments one scene generation and `invalidateScene(...)` requests invalidation/retirement for every current scene record before the 3x3 scene is rebuilt. There is no partial-record install path today.
2. `SectionLifecycleEvents.sectionDirty(...)` currently receives exact section coordinates and a player-dirty flag, but not the local block coordinate that changed. It retains aggregate core/halo dependency counters. **Current telemetry is therefore insufficient to select a correct block-local subregion.** Dev16 must not infer a local slice from aggregate section counts.
3. `SectionSnapshot` captures one full `16^3` interior section plus a one-block halo in every direction (`18^3` primitive cells). This full immutable snapshot is the worker truth today.
4. `WorkerBackedSectionLifecycleProbe.captureAndSubmit(...)` captures the full section snapshot, builds the permanent full-section `ReferenceFaceMesh`, captures the full `SectionBakedQuadSnapshot`, and submits one full-section worker ticket.
5. A completed worker publishes the exact full-section oracle mesh, repeat-aware greedy mesh, T-junction proof and P3.7 differential proof. Stale generation/resource-epoch results are rejected before install.
6. GPU install currently allocates whole-record passthrough vertex, merged vertex and index ranges plus four indirect commands, uploads the complete replacement mesh and retires the previous record with completion-gated lifetime. There is no region allocator/range-splice contract yet.

These facts mean jumping directly to fragmented GPU patch installs would mix three independent questions—dirty provenance, partial-mesh correctness/benefit and GPU fragmentation/range lifetime. Dev16 deliberately answers the first two in **shadow mode** before consuming the third.

## Frozen dev16 hypothesis

A fixed small number of deterministic vertical slices may capture most localized edit benefit with substantially less metadata/fragmentation complexity than arbitrary 3D micro-regions.

Dev16 will test exactly four fixed section-local Y slices:

- slice 0: `y=[0,4)`;
- slice 1: `y=[4,8)`;
- slice 2: `y=[8,12)`;
- slice 3: `y=[12,16)`.

Each slice spans the full section X/Z domain. This intentionally sacrifices X/Z locality in exchange for only four stable regions per section, simple ownership, bounded metadata and limited future GPU fragmentation.

Dev16 is **shadow-only and non-render-changing**. The existing dev15 full-section capture, worker mesh, GPU install and draw path remain authoritative and continue to render every frame. Shadow slice output is never uploaded or drawn in dev16.

## A. Exact dirty provenance

Dev16 may select a slice only from exact render-thread-observed block-local dirty provenance.

Requirements:

- add a bounded primitive dirty-provenance bridge that records tracked section identity plus exact section-local block Y for ordinary block changes;
- provenance must come from an exact Minecraft/Fabric lifecycle surface that provides block position/state change identity; do not reverse-engineer local Y from section-dirty counts or timing;
- no mutable world object may cross the render-thread boundary;
- no worker live-world reads are permitted;
- fixed capacity, deterministic coalescing and explicit overflow/fallback accounting;
- multiple edits before consumption coalesce into a four-bit slice mask plus reason/fallback flags, not an unbounded position collection;
- resource reload, world replacement, scene recenter, chunk load/unload, missing/ambiguous provenance, bridge overflow or any unsupported lifecycle cause **must fall back to the proven full-section path** for the experiment episode;
- P3.5 halo/core dependency rules remain authoritative and may force broader fallback.

### Slice dependency expansion

For a precisely localized block at section-local Y `y`:

- mark the owning four-block slice dirty;
- preserve the proven one-block dependency requirement: if `y` is the first row of a slice, also mark the previous slice when one exists; if `y` is the last row, also mark the next slice when one exists;
- X/Z do not create extra slice identities because every slice spans full X/Z;
- if coalescing marks all four slices, classify the episode as full-section-equivalent rather than pretending it is partial.

The live full-section oracle decides correctness. If runtime evidence shows the one-block Y expansion is insufficient for supported render truth, dev16 fails and the dependency rule must be corrected in a new immutable attempt; never widen it after inspecting only favorable samples.

## B. Shadow slice mesher

Implement a bounded shadow mesher that consumes the same immutable captured truth as the production ticket but emits/proves only the selected slice set.

Dev16 may initially reuse the already-captured full immutable `SectionSnapshot`, `ReferenceFaceMesh` and `SectionBakedQuadSnapshot` so the experiment isolates **meshing/rebuild granularity** from render-thread partial-capture engineering. This is deliberate: dev16 must not claim capture-time savings it does not implement.

The shadow path must:

- use fixed primitive worker-local scratch;
- never mutate or replace the production full-section result;
- preserve exact supported SOLID/CUTOUT material, sprite, UV, ARGB, light/AO, direction and source identity rules;
- preserve dev10/dev11 repeat-aware candidate safety;
- force slice boundaries to be stable ownership boundaries: an experimental merged quad may not span two slice identities;
- retain exact unsupported/generalized passthrough truth within the selected slice;
- record when a production full-section merged candidate would cross a slice boundary and therefore be split by the experimental representation;
- build no benchmark-only duplicate full-section mesh beyond the production build already required for the control.

## C. Permanent control / correctness oracle

The dev15 full-section production result remains the control and drawable. Shadow correctness is evaluated against authoritative source truth, not by demanding identical greedy rectangle topology.

For every admitted shadow episode:

1. selected-slice source baked-quad coverage must be exactly once;
2. selected-slice independent reference canonical faces must match visibility;
3. material/layer/direction/canonical geometry/corner order/raw UV/exact ARGB/packed light checks must be exact;
4. no optimized canonical identity may exist without an independent reference face;
5. no source identity may be missing or duplicated;
6. repeat-aware merged source expansion must equal the selected source coverage it replaces;
7. deterministic double-build audits must match;
8. a bounded first-failure fixture must be retained.

In addition, dev16 must retain a compact per-slice fingerprint from the preceding accepted full-section generation. For a precisely localized edit, every **unselected** slice must prove unchanged source/reference truth between generations. If an unselected slice changes, the localizer/dependency model under-invalidated and the experiment fails rather than silently expanding after install.

A different greedy partition caused solely by fixed slice boundaries is allowed in shadow output only if complete source/render truth remains exact. Candidate/quad inflation from those boundaries is measured explicitly.

## D. Matched benefit measurement

P3.9 exists only if partial work earns its complexity. Dev16 must compare each localized shadow episode to the already-required matched production full-section rebuild for the same generation.

Record at minimum:

- localized episodes admitted;
- fallback/full-section-equivalent episodes and reason counts;
- dirty slice count distribution `1/2/3/4`;
- selected interior cell count and fraction of 4,096 full-section cells;
- selected source baked quads/reference faces;
- shadow topology rectangles/merge candidates/passthrough+merged identities;
- shadow output quads/vertex/index bytes;
- full-section matched source/output identity;
- forced slice-boundary candidate splits;
- assembled/projected quad inflation versus the production optimized section;
- shadow worker CPU time P50/P95/P99/max;
- matched full-section production ticket execution time for the same episodes;
- projected bytes that would need replacement if each selected slice were independently patchable;
- fixed per-section experimental metadata bytes;
- queue/cancellation/stale/fallback counts.

Do not count dev16 shadow work as a production speedup. It adds experimental cost during validation. Benefit claims are ratios of shadow partial work to the matched full-section control, not frame-time claims from a run doing both.

## E. Pre-frozen experiment success thresholds

Unlike P3.8, which established a baseline without a prior threshold, P3.9 is an optional complexity trade. Dev16 therefore freezes decision thresholds **before** seeing results.

The dev16 shadow experiment is worth advancing to a later partial-capture/GPU-install slice only if one coherent reference workload produces all of the following:

### Mandatory correctness / safety

- 100% exact shadow differential checks;
- zero missing/duplicate/optimized-without-reference/real mismatch records;
- zero unselected-slice fingerprint changes for episodes classified partial;
- deterministic shadow audits exact;
- every inherited P3.8/P3.7 correctness/lifetime gate green;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- zero unsafe stale installs, unexpected worker failures, queue rejection or join failure;
- clean worker/staging/arena/resource closure and process exit `0`.

### Minimum evidence volume

- at least `32` precisely localized ordinary-edit episodes in the measured experiment window;
- at least `16` one-slice episodes;
- at least `8` two-slice boundary/dependency episodes;
- at least one coalesced multi-edit episode;
- at least one explicit fallback episode from a non-localizable/global lifecycle cause, proving fallback remains intact.

### Benefit thresholds for precisely localized episodes

Using matched per-generation control data:

- median selected interior cells must be `<= 50%` of a full section;
- at least `75%` of localized episodes must select no more than two slices;
- shadow meshing CPU P50 must be `<= 60%` of matched full-section production execution;
- shadow meshing CPU P95 must be `<= 80%` of matched full-section production execution;
- projected replacement upload bytes P50 must be `<= 60%` of the matched full-section output bytes;
- projected replacement upload bytes P95 must be `<= 80%` of matched full-section output bytes.

These ratios are evaluated on matched localized episodes, not against unrelated A-0158 aggregate percentiles.

### Complexity / geometry-overhead limits

- fixed experimental metadata must remain `<= 1,024` bytes per section;
- retained slice identities are exactly four; no runtime region-count growth;
- mean assembled shadow quad inflation from forced slice boundaries must be `<= 5%` versus the matched full-section optimized mesh;
- maximum observed assembled quad inflation must be `<= 10%` for the reference workload;
- no unbounded patch history, per-edit allocation list or region free-list is introduced in dev16.

If correctness passes but these benefit/complexity thresholds fail, record P3.9 as evidence against this fixed-slice design. Do not tune thresholds after the run. A materially different partition strategy would require a new immutable experiment contract.

## F. Telemetry / allocation bounds

Use bounded primitive collectors only. Dev16 may reuse the P3.8 percentile component or add a dedicated fixed-capacity primitive matched-pair collector.

Required accounting:

- total observed / retained / overflow exact;
- shadow/control timing pair counts exact;
- no sort/boxing/allocation on the worker hot path;
- percentile extraction only outside the worker hot path;
- scratch high-water reported;
- JVM GC count/time deltas retained as diagnostic evidence;
- exact allocation bytes remain `not-portably-measured` unless a non-intrusive standard surface is proven.

Synthetic self-tests must cover slice-mask coalescing, boundary expansion, fallback classification, matched-pair percentile accounting, fixed metadata accounting and a known shadow differential fixture. Synthetic data never enters runtime evidence.

## G. Representative dev16 runtime

One coherent reference run must include:

1. settle to inherited P3.8/P3.7 READY and arm an explicit dev16 measured experiment window;
2. ordinary edits well inside each of the four slices, accumulating at least 16 one-slice episodes total;
3. edits on/adjacent to Y slice boundaries, accumulating at least 8 two-slice dependency episodes;
4. a short burst of multiple edits before recovery to prove deterministic coalescing;
5. F3+T/resource reload and READY to prove mandatory full fallback;
6. actual scene recenter and READY to prove mandatory full fallback;
7. enough additional localized edits to reach at least 32 localized episodes;
8. normal exit.

No deliberate queue torture or worker-count tuning is permitted.

## H. Visual gate

Dev16 is shadow-only and changes no rendered geometry, shader, pipeline, atlas/lightmap behavior or GPU install path. No new human visual verdict is required unless source review/runtime reveals an accidental rendering change.

Any later P3.9 slice that actually draws fixed-slice geometry or patches GPU ranges is geometry/lifetime changing and must freeze its own visual/lifetime gate before implementation.

## Explicit non-scope for dev16

Do not implement in this first P3.9 slice:

- partial GPU allocation/update/install;
- multiple GPU draw records per section;
- arbitrary 3D microtiles or dynamic dirty AABBs;
- region compaction/defragmentation;
- partial render-thread model/material/light capture savings;
- worker-count or queue-policy tuning;
- greedy merge-key relaxation;
- shader/pipeline changes;
- fluids/translucency;
- Phase 4 visibility architecture;
- Phase 5 adaptive scheduling;
- LOD.

## Promotion / experiment decision

Dev16 can only advance P3.9 to a later partial-capture/GPU-patch contract if the frozen correctness, evidence-volume, benefit and complexity thresholds all pass on the reference workload while the proven full-section renderer remains green.

A successful dev16 result does **not** make partial remeshing the production default. It only proves the fixed four-slice decomposition is worth implementing further.

A failed benefit threshold with exact correctness is a legitimate experimental rejection result. Preserve it and keep the full-section path; do not convert an optional optimization into required architecture merely because code exists.

## Immediate implementation order

1. add bounded exact block-local dirty provenance with mandatory full fallback when identity is unavailable;
2. add deterministic four-slice mask/dependency planner + synthetic self-test;
3. add shadow-only fixed-slice mesh/proof path using existing immutable captured truth;
4. add per-slice previous/current fingerprints to detect under-invalidation outside selected slices;
5. add matched shadow/control timing/work/output telemetry and frozen decision gate;
6. bump to `0.3.0-phase3-dev16`;
7. open a draft P3.9 PR and require exact Java 25 / Gradle 9.5.1 package CI before any runtime handoff.

Do not change production GPU emission/install behavior in dev16.