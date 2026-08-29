# A-0156 - P3.8 dev15 package and runtime handoff

**Date:** 2026-08-29  
**Objective:** Record hosted package authority for the frozen A-0154 / implemented A-0155 P3.8 dev15 benchmark slice and hand the exact package to reference runtime validation.  
**Status:** `PACKAGE CI GREEN / REFERENCE RUNTIME REQUIRED`  
**Version:** `0.3.0-phase3-dev15`  
**Branch:** `phase3/meshing-benchmarks`  
**Draft PR:** #51

## Exact hosted CI authority

Exact connector-authored validation head:

`e6f4b81903ddcdcb859d70a1a01c002a3f550e12`

This commit preserves synchronized implementation/evidence tree `bafe8ff2279f756f3f099bed276b3db430282928` without source-content changes.

Hosted Build workflow **`33270995728`** completed successfully:

- Java 25 setup: SUCCESS;
- Gradle 9.5.1 setup: SUCCESS;
- Build: SUCCESS;
- build artifact upload: SUCCESS;
- versioned release publication: SKIPPED as expected for internal dev milestone.

## Canonical artifact

GitHub Actions artifact:

- artifact id: **`9720099484`**;
- wrapper name: `obsidian-593dda9074bb50d7162f12e0abca62978266a84c`;
- wrapper size: **663,372 bytes**;
- wrapper digest: **`sha256:3cdb197b944bee751d6e6255c347e79a82bb32c8c2e3d153641ac85b871bef9d`**.

Direct runtime JAR extracted from that exact wrapper:

- `Obsidian-0.3.0-phase3-dev15.jar`;
- size: **456,609 bytes**;
- SHA-256: **`eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`**.

Sources JAR:

- `Obsidian-0.3.0-phase3-dev15-sources.jar`;
- size: **234,494 bytes**;
- SHA-256: **`550d488c41c440a5dcaa3de77146557d90d5dd22c142921bbd914fdfb8e51320`**.

The direct versioned JAR, not the Actions ZIP wrapper, is the runtime handoff authority.

## Compile-validated dev15 measurement behavior

Hosted compile verifies the measurement-only source integrates against Minecraft 26.2 / Fabric / Java 25 APIs.

Dev15 retains the complete P3.7 production worker path and adds:

- fixed-capacity primitive queue-wait / full-execution benchmark samples;
- coherent observed/retained/overflow accounting;
- P50/P95/P99/max queue and execution distributions;
- per-priority queue-tail evidence;
- real workload/output identity from completed production tickets;
- worker queue/running pressure, steals and completion-domain evidence;
- reusable scratch high-water reporting;
- JVM GC count/time deltas outside the worker hot path;
- primary baked-mesh timing and wall time around already-required T-junction/differential proof pairs;
- deterministic bounded collector self-test;
- layered `meshingBenchmarkEvidenceReady` over inherited P3.7 correctness/lifetime evidence.

No benchmark-only duplicate mesher/build is introduced. No geometry, merge eligibility, suppression/replacement, shader, pipeline, atlas/lightmap, worker-count/priority policy, rebuild granularity, GPU ownership, native Vulkan graphics scope, staging/arena/resource lifetime, or P3.7 oracle semantics changed.

## Reference runtime contract

Use this exact canonical dev15 JAR on the existing reference Vulkan system. In one coherent run:

1. enter a representative world and wait for inherited P3.7 correctness plus settled scene READY;
2. wait until the log explicitly reports the P3.8 measured benchmark window armed;
3. perform **multiple ordinary block break/place edits**, allowing READY recovery after representative rebuild churn;
4. perform **F3+T/resource reload** and allow READY recovery;
5. traverse far enough for an actual **`scene-recenter`** and allow READY recovery;
6. include a **short bounded edit/traversal burst** sufficient to create concurrent queued/running worker pressure without intentionally changing scheduler policy or creating pathological load;
7. wait for `meshingBenchmarkEvidenceReady=true` if the live gate reports it;
8. exit Minecraft normally and return the **complete log**, including the final FrameCoordinator close line.

No new human visual verdict is required unless an unexpected rendering/visual change is observed.

## Required runtime evidence

Promotion still requires every inherited gate through P3.7 and clean lifetime, including:

- `differentialCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `borderHaloCorrectnessEvidenceReady=true`;
- `repeatAwareGreedyEmissionEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- unsafe stale installs `0`;
- dropped lifecycle evidence `0`;
- no unexpected worker failure / queue rejection / shutdown join failure;
- staging, arena and deferred resources clean;
- process exit code `0`.

P3.8-specific evidence must include:

- `meshingBenchmarkEvidenceReady=true`;
- measured window armed after settled READY;
- completed benchmark samples > 0;
- coherent queue/execution observed-retained-overflow counts;
- ordered nonnegative P50/P95/P99/max queue and execution values;
- real source baked quads / independent reference faces > 0;
- real merge candidates, merged identities and merged covered source faces > 0;
- output vertex/index bytes > 0;
- exercised scratch high-water > 0;
- representative ordinary dirty rebuilds, resource reload and real scene recenter after benchmark arm;
- concurrent pressure evidence (`benchmarkMaxRunningJobs >= 2` or `benchmarkMaxQueuedJobs > 0`);
- bounded collector self-test PASS;
- benchmark worker queue rejection delta `0`;
- exact allocation bytes remain explicitly `not-portably-measured`; use GC deltas and scratch high-water as portable diagnostics.

No numerical CPU-time threshold is a promotion requirement for this first baseline. A coherent but slow baseline remains valid measurement evidence and should be recorded rather than redefined.

## Promotion status

P3.8 remains ACTIVE and PR #51 remains draft. Do not merge from package CI alone. Do not begin P3.9 partial remeshing until the reference runtime closes this benchmark contract and P3.8 is promoted.
