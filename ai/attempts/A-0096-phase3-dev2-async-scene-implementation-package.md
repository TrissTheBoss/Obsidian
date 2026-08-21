# A-0096 — Phase 3 dev2 production async scene implementation + package evidence

Date: 2026-08-21
Status: **IMPLEMENTED / EXACT CI SUCCESS / PACKAGE + BYTECODE VERIFIED / REFERENCE RUNTIME NEXT**

## Starting contract

A-0092 runtime-validated the first P3.1 worker/job boundary but explicitly left `productionSceneInstallStillSynchronous=true`. A-0093 froze the next contract: persistent scene records must source `BakedSectionMesh` results from the bounded `SectionMeshWorkerPool`, while live capture and all GPU ownership stay on the render thread.

This attempt implements that production handoff on branch `phase3/async-scene-integration`, version `0.3.0-phase3-dev2`.

No Phase 3 merge authorization exists.

## Implementation

The validated P2.7 `RealMultiSectionSceneProbe` and P2.6 `RealSectionLifecycleProbe` are intentionally retained unchanged as historical correctness oracles. Dev2 adds a separate active production path:

- `AsyncMultiSectionSceneProbe`
- `WorkerBackedSectionLifecycleProbe`

`FrameCoordinator` now owns the shared `SectionMeshWorkerPool` plus the async scene directly. The dev1 one-shot `WorkerMeshValidationProbe` is no longer part of the active coordinator path.

### Worker-backed record state machine

The active record path is now:

`WAITING_WORLD -> WAITING_MESH -> READY_TO_INSTALL -> LIVE -> RETIRING -> RETIRED`

with `STALE`, `FAILED` and `CLOSED` terminal/control states.

Render-thread responsibilities remain:

1. exact `SectionSnapshot` capture;
2. deterministic `ReferenceFaceMesh` oracle checks;
3. deterministic `SectionBakedQuadSnapshot` capture including vanilla model/material/light/tint data;
4. worker job admission/result acceptance;
5. generation/lifecycle/resource-epoch validation;
6. GPU arena allocation;
7. bounded staging upload;
8. indirect command creation;
9. draw encoding/install;
10. completion-gated retirement.

Workers continue to receive only immutable `SectionSnapshot` + `SectionBakedQuadSnapshot` plus generation/event-sequence/priority metadata and perform pure `BakedSectionMesh.build(...)` work.

### Stale/cancellation contract

Each scene job carries:

- scene generation;
- lifecycle event sequence;
- deterministic priority;
- immutable snapshot identities.

The render-thread record rechecks lifecycle/resource identity:

- before accepting a completed worker result;
- again before any GPU allocation;
- again immediately before staging submission/install.

Invalidation while `WAITING_MESH` requests worker cancellation and moves the record into pre-install retirement until the ticket reaches a terminal state. A completed worker result that becomes stale before installation is counted/discarded without GPU allocation. Safe pre-install stale/cancelled work is tracked separately from unsafe stale GPU installation.

### Scene scheduling

The existing P2.7 correctness-first whole-window invalidation policy remains for this milestone.

Production worker admission is bounded by the nine scene records and the existing fixed worker queue capacity:

- center record: `HIGH` priority;
- all neighboring records: `NORMAL` priority;
- `LOW` remains reserved for later background scheduling work;
- one new record is admitted per render frame;
- multiple mesh jobs may be queued/running concurrently;
- staging/install remains serialized by the existing pending-batch gate.

No unbounded fallback queue was added.

### Metrics/gates

The active path now records:

- scene worker jobs submitted/completed/cancelled;
- scene cancellation requests;
- safe stale worker-result discards;
- worker-backed record installs;
- queue rejections;
- install admission deferrals;
- pre-install invalidations;
- maximum simultaneous scene jobs;
- synchronous scene mesh builds, which is hard-coded/expected to remain zero for this path;
- unsafe stale scene installs separately.

Shutdown logging now includes:

- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- `renderThreadCaptureOwnership=true`;
- `renderThreadGpuOwnership=true`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`.

The dev2 runtime gate requires worker-backed scene READY/rebuild evidence, edit + resource-reload lifecycle evidence, zero unsafe stale installs, zero queue rejection/failure, zero synchronous scene mesh builds, zero queued/running jobs at shutdown and clean renderer resource accounting.

Camera recenter is diagnostic for this gate rather than mandatory because P2.7 already validated recenter separately.

## Exact hosted CI

A temporary draft PR #33 targeted `main` only to trigger the repository's exact pull-request workflow. It is explicitly marked **NEVER MERGE**; canonical dev2 review remains stacked draft PR #32 against `phase3/worker-job-architecture`.

Exact source head under test before this evidence commit:

- `c34f92a968b26ef5cebb16c441d69ae6ba28c337`

GitHub Actions run:

- run `32520955461`
- job `96892802461`
- Java 25 / Gradle 9.5.1
- Build: `success`
- artifact upload: `success`
- release publishing: correctly skipped
- artifact id `9460525228`
- artifact wrapper digest `sha256:5df55ecc717933f9c2d87ed69b52e2c1cc22cca5e03c59022f2011914eb1a3ed`

## Package verification

Artifact contents:

- `Obsidian-0.3.0-phase3-dev2.jar`
  - size `269,298` bytes
  - SHA-256 `bb7d703599e561d2d3cbdf2bd027adcdd968831bc1c3126b8078dca75c2cd812`
- `Obsidian-0.3.0-phase3-dev2-sources.jar`
  - size `140,231` bytes
  - SHA-256 `742c415657dc07657920384ed85f4f2b7823ef0fb5f4497ccc0fbeeefb2a73ab`

`fabric.mod.json` inside the production JAR reports:

- version `0.3.0-phase3-dev2`;
- Minecraft `~26.2`;
- Java `>=25`.

The production JAR contains:

- `FrameCoordinator.class`;
- `SectionMeshWorkerPool` and ticket/worker classes;
- `AsyncMultiSectionSceneProbe` and state/record classes;
- `WorkerBackedSectionLifecycleProbe` and state class.

## Bytecode ownership verification

`javap -p -c` on the exact production JAR proves:

1. `FrameCoordinator` constructs `SectionMeshWorkerPool` and `AsyncMultiSectionSceneProbe` and drives the async scene.
2. `AsyncMultiSectionSceneProbe` constructs and drives `WorkerBackedSectionLifecycleProbe` records and calls their invalidation path.
3. `WorkerBackedSectionLifecycleProbe` calls `SectionMeshWorkerPool.submit(...)` and `SectionMeshWorkerPool.cancel(...)`.
4. The worker-backed render-thread record contains lifecycle-sequence/resource-epoch checks, device-arena allocation, staging submission and indexed-indirect draw calls.
5. The record class has no `BakedSectionMesh.build(...)` invocation; the only grep match for that text was the unrelated `buildTimeNs()` method name.
6. `SectionMeshWorkerPool$Worker` contains the two deterministic `BakedSectionMesh.build(snapshot, bakedSnapshot)` calls and its inputs are the immutable snapshot classes.

This is direct package evidence that pure mesh construction moved to the worker boundary while GPU install ownership stayed on the render thread.

## Result

`SUCCESS` for implementation, exact hosted compile/package and bytecode ownership verification.

This is **not** reference-runtime validation. No claim is made yet that edit/reload-driven async replacement, cancellation/stale-discard behavior, visual continuity or shutdown metrics pass on the reference RX 6800 XT Vulkan runtime.

## Next step

Run `Obsidian-0.3.0-phase3-dev2.jar` on the reference runtime:

1. enter ordinary surface terrain containing supported SOLID + CUTOUT content;
2. allow the async 3x3 scene to become READY;
3. visually inspect for missing/duplicate borders or stale geometry;
4. break/place blocks in the visible scene and allow a worker-backed rebuild;
5. perform F3+T once and allow another rebuild;
6. optionally move/recenter to exercise additional cancellation/relevance churn;
7. exit normally and inspect the full log.

Required machine evidence includes `phase3GateReady=true`, `productionWorkerIntegrationReady=true`, `productionSceneInstallStillSynchronous=false`, `productionWorkerSceneIntegration=true`, at least two READY transitions, at least one rebuild, at least three worker-result installs, zero synchronous scene mesh builds, zero unsafe stale installs, zero queue-full rejection/worker failure, zero queued/running jobs at shutdown, full renderer cleanup and exit code 0.

After runtime success, P3.1 still retains relevance-aware streaming-pressure scheduling, reusable worker-local mesh scratch/allocation reduction and production queue/latency/output-size evidence before P3.2 greedy/bitmask meshing becomes active.
