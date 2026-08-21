# A-0093 — Phase 3 dev2 asynchronous scene mesh integration plan

Date: 2026-08-21
Status: PLAN FROZEN / IMPLEMENTATION NEXT

## Context

A-0092 runtime-validates the first P3.1 worker/job concurrency boundary. The worker pool now has proven bounded priority lanes, peer stealing, cancellation, generation/event tagging, deterministic pure mesh builds and clean shutdown on the reference system.

The validated dev1 build deliberately logs `productionSceneInstallStillSynchronous=true`. Dev2 removes that specific boundary without expanding into greedy meshing or worker-thread live-world/model capture.

## Goal

Move persistent multi-section scene **mesh construction** from the render thread to `SectionMeshWorkerPool`, then consume accepted worker results on the render thread for the existing bounded upload/install path.

A dev2 success must prove that a real persistent scene record can survive:

`render-thread immutable capture -> bounded worker job -> generation/event validation -> render-thread GPU allocation/upload/install -> completion-gated replacement`

without stale worker output becoming visible.

## Ownership boundary

### Render thread remains responsible for

- live `ClientLevel`/chunk access;
- `SectionSnapshot` capture;
- `SectionBakedQuadSnapshot.capture(...)` including model/material/light/tint resolution;
- reference-oracle checks used by validation;
- worker job submission/cancellation/result acceptance;
- generation/lifecycle sequence validation;
- GPU arena allocation;
- staging upload;
- indirect command creation;
- graphics draw encoding;
- GPU fence/lifetime/retirement ownership.

### Worker threads receive only

- immutable `SectionSnapshot`;
- immutable `SectionBakedQuadSnapshot`;
- section/scene generation;
- lifecycle validity sequence;
- priority metadata.

Workers perform pure `BakedSectionMesh.build(...)` only.

No live Minecraft world/chunk/model/resource object and no GPU object may cross into a worker job.

## Required state-machine change

The current `RealSectionLifecycleProbe` performs capture + pure mesh build + upload/install in one render-thread method. Dev2 must split this into explicit phases for fixed scene records:

1. `WAITING_WORLD` / capture eligibility;
2. `WAITING_MESH` after immutable capture and worker submission;
3. `READY_TO_INSTALL` when a terminal completed worker result is accepted;
4. existing upload/install -> `LIVE`;
5. `RETIRING` / `RETIRED` as before;
6. `STALE` when generation/event identity no longer matches;
7. `FAILED` only for real invariant/build/GPU failures.

A stale or cancelled ticket must never allocate GPU memory.

## Generation and stale-result contract

For every worker-backed scene record:

- ticket carries the record/scene generation and `SectionLifecycleEvents.latestSequence()` observed after capture;
- before accepting the worker result, both identities must still match the record's expected values;
- before GPU allocation, identities are checked again;
- immediately before batch submission/install, identities are checked again;
- invalidation requests cancel any nonterminal worker ticket;
- a worker result that completes after invalidation is counted and discarded;
- old LIVE geometry follows the existing scene invalidation policy and must not overlap stale replacement ownership.

## Bounded scheduling

- Reuse the existing fixed worker queue capacities; no fallback queue/growth.
- Production scene records use normal least-depth admission rather than validation-only worker pinning.
- Initial priority heuristic for dev2 may be simple and deterministic:
  - center record HIGH;
  - cardinal/diagonal neighbors NORMAL;
  - LOW reserved for later background work.
- Bounded staging/arena behavior remains unchanged.
- Upload/install admission remains render-thread serialized enough to preserve the proven staging contract.

## Validation metrics

Dev2 must add explicit scene-worker metrics separate from the A-0092 one-shot proof, including at minimum:

- scene worker jobs submitted;
- completed;
- cancelled;
- stale/discarded after completion;
- queue rejections;
- install-ready results accepted;
- worker-produced record installs;
- maximum simultaneous scene jobs queued/running;
- queue wait/execution aggregates already exposed by the worker pool;
- synchronous scene mesh builds (must be zero for the dev2 path under validation);
- world reads after immutable generalized capture (must remain zero on workers).

## Runtime gate

A reference run should require:

- several simultaneous worker-produced scene record installs;
- at least one edit-driven scene rebuild;
- at least one resource reload rebuild;
- at least one stale/cancelled scene worker job caused deliberately by invalidation while jobs are outstanding, if the runtime naturally exposes enough overlap; otherwise a deterministic validation self-test may be used without artificial sleeping in production workers;
- zero stale worker result installs;
- zero queue-full rejection and worker failure;
- zero synchronous scene drawable mesh builds in the worker-integrated path;
- `worldReadsAfterGeneralizedCapture=0`;
- full staging/arena/deferred cleanup;
- normal exit 0.

Camera recenter remains useful validation but is not required to re-prove P2.7 unless the new async ownership change touches recenter semantics materially.

## Deliberate non-goals

Not dev2:

- binary/bitmask visibility masks;
- greedy rectangle extraction;
- worker-thread model/material/light capture;
- broad scheduler/adaptive budgets;
- partial remeshing;
- translucent/fluid terrain;
- global vanilla terrain replacement;
- removing the permanent reference oracle;
- widening the native graphics seam.

## Exit

Dev2 closes only when the persistent real scene is demonstrably installing meshes produced by the bounded worker pool with generation-safe stale-result rejection and all existing GPU lifetime guarantees preserved.

After that, remaining P3.1 work is relevance-aware scheduling under streaming pressure plus reusable worker-local scratch/allocation reduction and production latency/queue/output metrics before P3.2 becomes active.