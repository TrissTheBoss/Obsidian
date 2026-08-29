# A-0155 - P3.8 dev15 benchmark implementation review

**Date:** 2026-08-29  
**Objective:** Implement the frozen A-0154 P3.8 measurement-only benchmark slice and freeze the exact source-review checkpoint before hosted package CI.  
**Status:** `IMPLEMENTED / HOSTED PACKAGE CI PENDING`  
**Version:** `0.3.0-phase3-dev15`  
**Branch:** `phase3/meshing-benchmarks`

## Result

Dev15 implements the A-0154 benchmark instrumentation without changing renderer semantics, worker scheduling policy, worker count, full-section rebuild granularity, greedy eligibility, source suppression, shaders/pipelines, atlas/lightmap behavior, native graphics scope, or resource lifetime behavior.

The implementation head before this evidence record is `2a9fa80441b4b5195344a0cae10a47ba2d407d08`.

## Bounded collector

`MeshingBenchmarkTelemetry` is owned by `SectionMeshWorkerPool` and retains a fixed primitive window of 4,096 paired queue-wait/execution samples plus one primitive priority byte per retained sample. Completed production tickets write one paired record only after the ticket reaches `COMPLETED`; no per-face or per-stage benchmark object is created.

The collector reports:

- total observed, retained, and overwrite/overflow sample counts;
- mean plus P50/P95/P99/max for full queue wait and full worker execution;
- per-priority queue-tail distributions;
- HIGH/NORMAL/LOW completion counts;
- stolen completion count;
- maximum queued/running pressure;
- aggregate execution time for diagnostic worker-busy fraction;
- source baked quads and independent reference faces;
- topology rectangles / covered faces;
- render merge candidates;
- final passthrough / merged identities and merged covered source faces;
- faces saved and final output quads/vertex/index bytes;
- JVM GC collection-count/time deltas captured outside the worker hot path.

Percentile extraction copies and sorts only a bounded primitive snapshot on the render/report thread. The worker path performs no sorting and no benchmark collection growth.

A deterministic synthetic collector self-test covers empty, singleton, known percentile ordering, capacity wrap/overflow accounting, reset semantics, and percentile monotonicity. Synthetic values never enter production evidence.

## Real worker integration

The measured window starts only after:

- the async scene is `LIVE`;
- inherited P3.7 differential correctness is armed;
- the worker pool is idle.

Only tickets whose enqueue timestamp is at or after the benchmark-window start are admitted to the percentile/window aggregates. Startup/warm-up jobs are therefore excluded by construction.

The existing production worker sequence is unchanged. Dev15 only observes it. Existing stage totals/maxima remain authoritative; dev15 additionally records the already-produced primary `BakedSectionMesh` build time and wall time around the already-required paired T-junction proof builds and paired differential-proof builds. No benchmark-only duplicate builds are added.

## Runtime gate

The live benchmark gate requires the measured window plus:

- at least three READY transitions after the benchmark baseline;
- at least two rendered-core dirty events after the baseline;
- at least one resource reload after the baseline;
- at least one real scene recenter after the baseline;
- coherent non-empty benchmark samples with real source/reference/merged/output domains;
- all exercised scratch high-water paths nonzero;
- concurrent pressure evidence (`maxRunningJobs >= 2` or `maxQueuedJobs > 0`);
- inherited `differentialCorrectnessEvidenceReady=true`.

The final `meshingBenchmarkEvidenceReady` also requires the complete inherited correctness/lifetime gates and zero queue rejection. No numerical latency threshold is imposed before the first trustworthy baseline exists.

## Environment / allocation labeling

The final report logs Java/OS/logical processors, worker count, Minecraft/Fabric/Obsidian versions, benchmark duration/sample count, and retains the existing GPU/driver/Vulkan bootstrap identity. Exact allocation bytes are explicitly labeled `not-portably-measured`; dev15 does not invent an allocation figure. Scratch high-water and JVM GC deltas are the baseline diagnostics.

Configured render/simulation distance are currently labeled `not-captured` rather than guessed. A-0154 only requires them when reliably accessible; hosted compile/runtime evidence may justify adding a mapped accessor later, but this does not weaken the benchmark distributions.

## Required reference runtime

After package CI succeeds, use the direct dev15 JAR. In one coherent run:

1. wait for inherited P3.7 correctness and settled READY so the P3.8 measured window arms;
2. perform multiple ordinary block break/place edits with READY recovery;
3. perform F3+T and recover to READY;
4. move far enough for an actual `scene-recenter` and recover to READY;
5. include a short bounded edit/traversal burst sufficient to observe concurrent queued/running work;
6. exit normally and return the complete log.

No new visual verdict is required unless an unexpected rendering change is observed.

## Promotion status

Not promotion-ready yet. Hosted exact package CI and the reference runtime remain mandatory. P3.9 partial remeshing remains out of scope.
