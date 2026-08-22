# A-0099 - Phase 3 dev3 relevance scheduler, worker scratch, and pressure evidence plan

**Date:** 2026-08-22  
**Branch:** `phase3/scheduler-backpressure-tuning`  
**Target version:** `0.3.0-phase3-dev3`  
**Status:** ACTIVE / PLAN FROZEN BEFORE IMPLEMENTATION

## Objective

Close the remaining non-greedy P3.1 gaps left by the successful dev2 production async-scene integration while preserving every Phase 2 correctness and ownership invariant.

This milestone remains P3.1. It does **not** activate binary/bitmask visibility or greedy rectangle extraction.

## Proven foundation retained

Dev3 must preserve the A-0098 runtime-proven ownership flow:

`render-thread immutable capture -> bounded worker mesh job -> render-thread generation/event/resource validation -> render-thread GPU allocation/upload/install -> completion-gated replacement`

Non-negotiable retained properties:

- no worker live-world/chunk/model/material/light access;
- no worker GPU ownership;
- no unbounded queue or staging fallback;
- generation + lifecycle sequence + resource epoch validation before install;
- safe cancellation/stale discard before GPU ownership;
- public Blaze3D SOLID/CUTOUT indexed-indirect drawing;
- completion-gated staging/arena/resource reclamation;
- permanent independent `ReferenceFaceMesh` correctness oracle;
- `synchronousSceneMeshBuilds=0` in the production worker-backed scene.

## Dev3 scope

### 1. Global relevance-aware priority execution

Dev2 assigned center HIGH and all neighbors NORMAL, but each worker consumed its own LOW/NORMAL work before considering higher-priority work queued on peers. Dev3 must make the worker scheduler priority-aware across the entire pool:

1. HIGH work first across own queue and peers;
2. NORMAL only when no HIGH job is available to the selecting worker;
3. LOW only when no HIGH/NORMAL job is available.

Stealing remains bounded and allocation-free.

Scene relevance tiers:

- center section: HIGH;
- face-adjacent/cardinal neighbors: NORMAL;
- diagonal neighbors: LOW.

This gives LOW an actual production relevance meaning rather than reserving it indefinitely.

### 2. Bounded multi-admission with backpressure

Dev2 admitted only one new record per render frame, and the successful runtime observed `maxSimultaneousSceneJobs=1` / maximum queue depth `1`, which did not meaningfully exercise production scheduling pressure.

Dev3 may admit more than one record per frame, but must stay conservative and bounded:

- hard cap at two new scene jobs per render frame;
- choose the most relevant unstarted record first;
- do not admit when pool outstanding work reaches a bounded target derived from worker count;
- record admission/backpressure deferrals separately from queue-full rejection;
- queue-full rejection remains a correctness/scheduler failure, not normal flow control.

The goal is enough concurrency to exercise the pool without turning render-thread immutable capture into an uncontrolled burst.

### 3. Worker-local reusable scratch / validation allocation reduction

`BakedSectionMesh` currently allocates exact output arrays per accepted mesh, which is necessary while the mesh is handed back to the render thread. Dev3 must not pretend those retained output arrays can be reused before ownership ends.

The safe P3.1 reduction is therefore:

- add worker-owned reusable primitive scratch for deterministic source-quad ordering;
- reuse that scratch across jobs on the same worker;
- stop unconditional duplicate full-mesh allocation for every production job;
- retain deterministic full-build audits during worker warm-up / periodic validation with explicit counters;
- retain `mesh.validateAgainst(...)` for every accepted output.

Metrics must expose scratch uses/high-water and determinism audits/matches so allocation reduction is observable rather than implied.

### 4. Production scheduler/output metrics

Add explicit worker-pool metrics for:

- submitted/completed/cancelled jobs by HIGH/NORMAL/LOW priority;
- queue wait totals/maxima by priority;
- total/max execution time;
- output quads;
- output vertex bytes;
- output index bytes;
- maximum single-job output bytes;
- worker-local scratch uses and maximum scratch quad high-water;
- deterministic audit count/matches;
- scheduler admission/backpressure deferrals;
- maximum simultaneous scene jobs and maximum worker queue depth.

No P50/P95/P99 claim is required from one short manual run; that belongs to later benchmark infrastructure. Dev3 needs trustworthy raw production evidence first.

### 5. Fold the old P2.6 chunk-lifecycle evidence gap into the current validation path

The only merge blocker ahead of the stacked Phase 3 work is still the old P2.6 fixed-target chunk unload/return coverage. The exact `ClientLevel.onChunkLoaded` / `ClientLevel.unload` seams and target-scoped event bridge are already implemented, but prior runs never travelled far enough to exercise them.

Dev3 should expose a **separate** final evidence flag, not weaken the Phase 3 correctness gate:

`phase2ChunkLifecycleEvidenceReady=true`

It may become true only when the current reference run contains:

- `chunkUnloadEvents > 0`;
- `chunkLoadEvents > 0`;
- `droppedLifecycleEvents == 0`;
- zero unsafe stale scene installs;
- at least one valid scene rebuild after lifecycle churn;
- clean worker/staging/arena/deferred-resource shutdown;
- normal exit.

This is a later superset runtime exercising the same exact lifecycle hooks/bridge under the production async scene. If it passes, a new immutable attempt may explicitly supersede the A-0084 requirement for a separate old dev6 binary and close P2.6 by stronger downstream evidence. Until such a run exists, do not claim the old gap closed and do not merge the dependency chain.

### Runtime interaction for combined closure

One dev3 reference run should deliberately:

1. wait for initial async scene READY;
2. break/place blocks in the scene and allow rebuild;
3. perform F3+T and allow rebuild;
4. travel far enough that the previously tracked scene-halo chunks actually unload;
5. return to loaded terrain so relevant chunk-load events occur;
6. continue until the async scene becomes READY again;
7. visually inspect for stale/duplicate/missing geometry;
8. exit normally and preserve the complete Prism log.

The final log should distinguish:

- `phase3GateReady` — production async correctness/cleanup;
- `schedulerEvidenceReady` — relevance/backpressure/worker-scratch evidence;
- `phase2ChunkLifecycleEvidenceReady` — old P2.6 unload/return evidence.

### Merge/dependency rule

The user has explicitly authorized merging. That authorization remains standing, but no branch may be promoted past an unmet recorded technical dependency. If the combined dev3 runtime closes `phase2ChunkLifecycleEvidenceReady`, synchronize attempts/current state first, then merge the already-authorized dependency chain in order and retarget stacked Phase 3 PRs as necessary.

## Deliberate exclusions

Not dev3:

- binary/bitmask face masks;
- greedy rectangle extraction;
- partial remeshing;
- worker-thread live Minecraft capture;
- fluid/translucent terrain;
- global vanilla terrain replacement;
- broad FPS or percentile performance claims from the correctness run.

## Next action

Implement the bounded relevance scheduler, worker-local scratch/audit reduction, expanded metrics, combined chunk-lifecycle evidence flag, exact CI/package verification, and direct-JAR runtime handoff.
