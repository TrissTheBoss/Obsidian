# A-0157 - P3.8 dev15 reference benchmark runtime PARTIAL: measured resource reload missing

**Date:** 2026-08-29  
**Objective:** Evaluate the first canonical dev15 reference benchmark runtime against frozen A-0154.  
**Status:** `PARTIAL`  
**Package:** `Obsidian-0.3.0-phase3-dev15.jar`  
**Canonical SHA-256:** `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`  
**Package-validation head:** `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`  
**Draft PR:** #51

## Result

The first dev15 reference run is a **valid partial benchmark**, not a source/package defect.

The bounded collector, production-worker sampling, workload identity, percentile extraction, worker-pressure accounting, inherited correctness gates and shutdown lifetime all behaved coherently. The final layered P3.8 flag correctly remained false because the measured benchmark window did not include the required resource reload:

- `meshingBenchmarkEvidenceReady=false`;
- `benchmarkWindowArmed=true`;
- `benchmarkCollectorSelfTest=true`;
- `benchmarkResourceReloadDelta=0`;
- global `resourceReloadEvents=1`, attributable to startup before the measured window;
- no later measured F3+T/resource reload was observed.

A-0154 explicitly requires F3+T/resource reload with READY recovery after the benchmark window has been armed. This obligation is not waived.

## Trustworthy measured baseline evidence from this partial run

Measured window:

- duration `81,974,884,800 ns` (~81.975 s);
- completed / retained / overflow samples `291 / 291 / 0`;
- exact bounded collector accounting;
- HIGH/NORMAL/LOW completions `32 / 129 / 130`.

Queue wait distribution:

- P50 `25,700 ns`;
- P95 `55,600 ns`;
- P99 `1,531,700 ns`;
- max `2,079,200 ns`.

Full production-ticket execution distribution, including permanent correctness sidecars:

- mean `1,681,651 ns`;
- P50 `982,500 ns`;
- P95 `5,658,100 ns`;
- P99 `11,804,200 ns`;
- max `22,243,000 ns`.

Per-priority queue P99:

- HIGH `76,100 ns`;
- NORMAL `430,600 ns`;
- LOW `2,008,200 ns`.

Representative workload identity:

- source baked quads `142,244`;
- independent reference faces `61,807`;
- topology rectangles / covered faces `28,345 / 61,807`;
- merge candidates `40,086`;
- final passthrough identities `135,621`;
- final merged identities `2,995`;
- merged covered faces `6,623`;
- faces saved `3,628`;
- reduction `25 permille`;
- output quads `138,616`;
- output vertex bytes `15,908,352`;
- output index bytes `3,326,784`.

Pressure/utilization diagnostics:

- worker count `4`;
- measured max queued jobs `1`;
- measured max running jobs `2`;
- stolen completions `212`;
- worker busy diagnostic `1 permille` over the full observation-duration denominator;
- GC collection/time delta `36 / 343 ms`;
- exact allocation bytes remain explicitly `not-portably-measured`;
- reusable scratch was exercised (`workerScratchBuildUses=308`, `workerMaxScratchQuads=1465` in final worker telemetry).

## Representative lifecycle exercise

The measured window did exercise the other required workload classes:

- ordinary rebuild churn: `benchmarkCoreDirtyDelta=1371`;
- READY recovery: `benchmarkReadyDelta=33`;
- real traversal/recenter: `benchmarkRecenterDelta=7` and final `cameraRecenterEvents=7`;
- observed invalidation reasons include `section-dirty|world-change|resource-reload|scene-recenter`;
- concurrent pressure was real (`benchmarkMaxRunningJobs=2`, `benchmarkMaxQueuedJobs=1`).

The only missing measured lifecycle delta is `benchmarkResourceReloadDelta=0`.

## Inherited correctness / lifetime closure

Every inherited correctness gate through P3.7 remained green:

- `differentialCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `borderHaloCorrectnessEvidenceReady=true`;
- `repeatAwareGreedyEmissionEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`.

P3.7 final proof remained exact:

- records/determinism `300 / 300`;
- source quads `146,825`;
- real merged candidates / expanded faces `3,189 / 7,066`;
- material/direction/geometry `7,066 / 7,066` each;
- UV/color/light `28,264 / 28,264` each;
- missing/duplicate/optimized-without-reference/real-mismatch all `0`;
- fixture self-tests `300 / 300`.

Worker/lifetime closure:

- worker submitted/started/completed `300 / 300 / 300`;
- cancellations/rejections/failures/join failures `0 / 0 / 0 / 0`;
- unsafe stale installs `0`;
- workers/staging/arena/resources clean;
- staging submitted/reclaimed `19,873,120 / 19,873,120` bytes;
- arena allocations/retired/reclaimed `900 / 900 / 900`, used bytes `0`;
- retired/released resources `300 / 300`, pending `0`;
- process exit code `0`.

Dev15 remained non-render-changing; no new visual verdict is required.

## Decision

Do **not** modify source, package, collector logic, workload definitions or thresholds from this result.

The instrumentation produced a coherent baseline and correctly refused promotion because one frozen exercise was absent. Reuse the exact same canonical dev15 JAR.

## Exact rerun requirement

After the log explicitly says the P3.8 measured benchmark window is armed:

1. perform **F3+T**;
2. wait for resource reload completion and async scene READY afterward;
3. perform at least a small ordinary rebuild with READY recovery;
4. cause at least one real `scene-recenter` with READY recovery;
5. create a short bounded burst sufficient for queued/running worker pressure;
6. wait for `meshingBenchmarkEvidenceReady=true` if possible;
7. exit normally and return the complete log/final frame-coordinator line.

Promotion requires `benchmarkResourceReloadDelta > 0` plus all other frozen A-0154 gates. P3.9 remains out of scope.