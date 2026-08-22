# A-0100 - Phase 3 dev3 scheduler/backpressure implementation and exact package

**Date:** 2026-08-22  
**Branch:** `phase3/scheduler-backpressure-tuning`  
**Canonical stacked PR:** #34, base `phase3/async-scene-integration`  
**Version:** `0.3.0-phase3-dev3`  
**Result:** `SUCCESS` for implementation / exact CI / package verification; reference runtime still required.

## Objective

Implement the A-0099 P3.1 scheduler/scratch milestone and make the remaining P2.6 fixed-target chunk unload/return evidence obtainable from the current production async-scene validation path without weakening active scene correctness.

## Implementation

### Globally relevance-aware bounded worker selection

`SectionMeshWorkerPool` now selects work in global priority order for each idle worker:

1. own HIGH then peer HIGH stealing;
2. own NORMAL then peer NORMAL stealing;
3. own LOW then peer LOW stealing.

This removes the dev2 case where a worker could consume its own lower-priority job while a peer still held higher-priority work.

Queued cancellation now eagerly removes a still-queued ticket when possible so obsolete work frees bounded queue capacity immediately. The pool remains capped at 1-4 daemon workers and 16 queued jobs per worker with no unbounded fallback.

### Production relevance tiers + bounded multi-admission

`AsyncMultiSectionSceneProbe` now maps the 3x3 scene to:

- center: HIGH;
- cardinal / face-adjacent neighbors: NORMAL;
- diagonals: LOW.

Admission selects the highest-relevance unstarted eligible record first and admits at most two new records in one render frame. Admission defers when outstanding pool work reaches the bounded target `max(2, workerCount * 2)`. Queue-full rejection remains abnormal and gate-failing rather than normal flow control.

Metrics include `maxAdmissionBurst`, scheduler admission deferrals, admissions by relevance tier, and maximum simultaneous scene jobs.

### Worker-local reusable primitive scratch

`BakedSectionMesh.BuildScratch` owns a reusable fixed primitive `orderedSourceQuads` array sized to the already-bounded `SectionBakedQuadSnapshot.MAX_QUADS`. It is owned by a worker and reused across its jobs.

The production build still allocates exact retained output arrays because ownership of those arrays transfers with the completed mesh to the render thread. Dev3 deliberately does not pretend retained output storage can be reused before ownership ends.

The build path also caches `Direction.values()` once rather than allocating the enum array repeatedly inside per-quad processing.

Dev2 performed two full mesh builds for every job. Dev3 performs one production build and retains a second full-build determinism audit for worker warm-up and periodically every 64 local completions. Every accepted mesh still executes `validateAgainst(...)`.

Worker metrics now include:

- submitted/started/completed/cancelled/failed counts by HIGH/NORMAL/LOW priority;
- queue-full and cancellation request counts by priority;
- total/max queue wait, including per-priority wait metrics;
- total/max execution time;
- output quads / vertex bytes / index bytes / max output bytes;
- reusable scratch uses and scratch quad high-water;
- determinism audits and audit matches;
- bounded shutdown join failures.

### Diagnostic-only fixed lifecycle anchor

The old P2.6 merge blocker is specifically fixed-target chunk unload/return coverage. A moving production scene cannot reliably prove that because it intentionally recenters with the player.

`SectionLifecycleEvents` therefore now freezes the **first tracked scene center** as a diagnostic-only fixed lifecycle anchor. Exact `ClientLevel.onChunkLoaded` and `ClientLevel.unload` events inside that first center's 3x3 chunk halo increment separate sticky counters even after the production scene recenters.

Important safety boundary:

- fixed-anchor-only events do **not** advance active scene validity sequence;
- they do **not** invalidate or rebuild the current scene;
- current scene filtering/recentering behavior remains unchanged;
- the anchor exists only to prove the already-grounded P2.6 lifecycle event class in a downstream production run.

### Tightened combined closure flag

Final `FrameCoordinator` exposes three separate outcomes:

- `phase3GateReady` — async production correctness/cleanup gate inherited from dev2;
- `schedulerEvidenceReady` — dev3 scheduler/scratch/output evidence;
- `phase2ChunkLifecycleEvidenceReady` — downstream closure evidence for the old P2.6 fixed-target gap.

`phase2ChunkLifecycleEvidenceReady=true` requires:

- `phase3GateReady=true`;
- fixed-anchor unload count > 0;
- fixed-anchor load count > 0;
- `fixedAnchorReturnSceneReady=true`;
- zero dropped lifecycle events;
- zero unsafe stale installs;
- clean worker/staging/arena/deferred-resource shutdown.

`fixedAnchorReturnSceneReady` becomes sticky only after both fixed-anchor unload and load have been observed **and the production async scene is LIVE afterward**. This prevents an unload/load pair from falsely closing P2.6 if the renderer never successfully returns to a valid live scene.

`schedulerEvidenceReady=true` requires the Phase 3 correctness gate plus a two-record admission burst, HIGH and lower-priority production work, reusable scratch use, matching determinism audits, and nonzero production mesh output accounting.

## Exact CI evidence

A temporary draft PR #35 targets `main` solely because the canonical workflow triggers exact pull-request CI only for `main`. It is explicitly **NEVER MERGE** and must be closed unmerged after evidence capture.

Implementation compile chain:

- run `32581990177` — implementation head `f54e96ad11b08163ca50d7e5b0b185169ec179ae` — success;
- run `32582026954` — bootstrap-synchronized head `345465680f179bf95ec795cbb27dc939d5d38513` — success;
- final tightened run `32582141208` — canonical tested code head `bd71f9ea1d7bdec02bef656338f0fd270a15dcbf` — success.

Final run facts:

- workflow: `Build`;
- Java 25 / Gradle 9.5.1;
- pull-request event through temporary PR #35;
- build success;
- artifact upload success;
- public release publishing not triggered;
- artifact id `9478074937`;
- artifact wrapper digest `sha256:b07793251fd8b6e7550e2b680df704eeb9cbe7f707221da63fa7c8a217804eeb`.

## Canonical runtime package

Use this final run-385 JAR for reference runtime:

- `Obsidian-0.3.0-phase3-dev3.jar`
  - size `275,994` bytes;
  - SHA-256 `182bac20d44de88705d5549ab5c1dd596aeef1aba53571ee7a121d472c3cc131`.
- `Obsidian-0.3.0-phase3-dev3-sources.jar`
  - size `143,508` bytes;
  - SHA-256 `454ef1373076e327b259312196e1ff1b747198b6d99056132b715e50b34ec48b`.

Packaged `fabric.mod.json` reports:

- version `0.3.0-phase3-dev3`;
- Minecraft `~26.2`;
- Java `>=25`.

Bytecode/package inspection confirmed:

- `BakedSectionMesh$BuildScratch` exists;
- `SectionMeshWorkerPool` exposes outstanding-work, scratch, determinism, output and per-priority metrics;
- `SectionLifecycleEvents` contains independent fixed-anchor load/unload state;
- `FrameCoordinator` contains `schedulerEvidenceReady`, `phase2ChunkLifecycleEvidenceReady`, and `fixedAnchorReturnSceneReady` closure paths;
- runtime instruction text explicitly requires returning to the first tracked area and waiting for the async scene to become LIVE/READY again before exit.

## Reference runtime required

One combined reference run can now close the remaining gap efficiently:

1. start in ordinary surface terrain with supported SOLID + CUTOUT content;
2. wait for initial async scene READY;
3. visually check for missing/duplicate borders or stale geometry;
4. break/place blocks and allow a READY rebuild;
5. perform F3+T once and allow a READY rebuild;
6. travel far enough that the **first** tracked scene neighborhood genuinely unloads;
7. return to that original area so those chunks load again;
8. wait until the async scene is LIVE/READY after returning;
9. visually confirm no stale/duplicate/missing geometry;
10. exit normally and preserve the complete Prism log.

Desired final shutdown evidence:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- fixed-anchor chunk unload/load counts both > 0;
- `productionWorkerIntegrationReady=true`;
- zero synchronous scene mesh builds;
- zero queue-full rejection / worker failure / worker shutdown join failure;
- determinism audit matches equal audits and audits > 0;
- worker scratch uses > 0;
- nonzero output quads/vertex/index bytes;
- `maxAdmissionBurst>=2`;
- zero dropped lifecycle events;
- zero unsafe stale scene installs;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- no staging/arena abandonment;
- exit code 0.

Safe queued/running cancellation or completed-stale preinstall discard may be nonzero during travel and is not itself a failure.

## Merge qualification

The user has standing merge authorization. This package does **not** itself authorize bypassing missing runtime evidence: PR #25 and the stacked Phase 3 chain remain technically dependency-blocked until the combined reference run actually proves `phase2ChunkLifecycleEvidenceReady=true`.

If that run passes, create a new immutable attempt explicitly stating that stronger downstream evidence supersedes A-0084's missing fixed-target observation, synchronize `CURRENT_STATE.md`, and then merge the already-authorized dependency chain in order without asking for another authorization.

## Next action

Run the final dev3 JAR on the reference system with the combined sequence above. Do not start P3.2 greedy/bitmask work before the combined evidence is evaluated and the remaining P3.1 status is synchronized.
