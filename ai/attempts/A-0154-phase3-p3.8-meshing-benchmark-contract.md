# A-0154 - P3.8 dev15 meshing benchmark contract freeze

**Date:** 2026-08-29  
**Objective:** Freeze the first P3.8 meshing-benchmark slice from synchronized P3.7-complete `main` before any source change.  
**Status:** `PLAN FROZEN`  
**Planned version:** `0.3.0-phase3-dev15`  
**Branch:** `phase3/meshing-benchmarks`

## Context

P3.7 is complete through `0.3.0-phase3-dev14`. A-0153 closed the permanent differential correctness contract on representative real snapshots and lifecycle transitions; exact synchronized promotion head `a63dce386cbee215007f127e7ba801dc3218eb91` passed hosted Build workflow `33265069030`, PR #50 merged `[no-release]` as `e1e0c583160bd2a36a2fd42a969bf35e5697591b`, and synchronized main head `4c3e69083a7dd35fcf0ae32529439caed5f44c96` passed hosted Build workflow `33265214115`.

P3.8 is therefore active. Its purpose is to establish a trustworthy performance baseline for the actual full-section production mesher before any P3.9 partial-remeshing experiment. P3.8 is a measurement/benchmark milestone, not an optimization-by-assertion milestone.

## Source findings before freeze

Current `SectionMeshWorkerPool` already provides useful low-cost primitives:

1. Every ticket records immutable enqueue/start/end timestamps and exposes `queueWaitNs()` plus `executionNs()`.
2. Pool telemetry already aggregates total/max queue wait and total/max full worker execution time, including per-priority queue wait totals/maxima.
3. Existing build stages expose their own `buildTimeNs()` and the worker pool already accumulates totals/maxima for visibility, greedy rectangles, canonical render keys, merge candidates, ordinary emission safety, repeat-aware UV, repeat-aware transport and other proof/build stages.
4. Existing output telemetry includes source/output quad counts, vertex/index bytes and max output bytes.
5. Existing worker telemetry includes submitted/started/completed/cancelled/failed/stolen jobs, cancellation requests, queue-full rejection, queue depth and per-priority counts.
6. Reusable worker-local scratch already exposes high-water metrics for the main mesh build and sidecars. P3.8 must not replace that bounded scratch model with allocation-heavy benchmark collections.
7. P3.7 differential correctness remains a mandatory permanent gate. Benchmark instrumentation must measure the actual final production path, including correctness proofs, rather than introducing a simplified benchmark-only mesher whose timing is not representative.

The missing P3.8 capability is primarily **bounded distribution/percentile evidence and workload classification**, not basic timestamps.

## Frozen dev15 scope

Dev15 is a **non-render-changing benchmark instrumentation slice** over the existing full-section worker pipeline.

It must not change:

- greedy eligibility or merge policy;
- emitted geometry;
- source suppression/replacement;
- shaders or graphics pipelines;
- atlas/lightmap semantics;
- native Vulkan graphics scope;
- worker count/priority policy merely to improve a benchmark number;
- queue/staging/arena/resource lifetime semantics;
- P3.7 oracle behavior;
- full-section rebuild granularity.

P3.9 partial remeshing is explicitly out of scope.

## Measurement domains

### A. Queue / scheduling latency

Measure the actual production ticket lifecycle from immutable capture submission:

- enqueue-to-worker-start queue wait;
- queue depth at admission when practical without changing scheduling behavior;
- priority lane (HIGH/NORMAL/LOW);
- stolen vs non-stolen execution;
- submitted/started/completed/cancelled/stale/failed/rejected counts.

Report bounded P50/P95/P99/max queue wait for the complete benchmark window and enough per-priority evidence to detect starvation/tail regressions. Existing totals/maxima remain useful but are not sufficient alone for P3.8.

### B. Full worker mesh CPU latency

Measure actual `Ticket.executionNs()` for completed production tickets, including the real dev14 correctness sidecars that execute in production.

Required bounded distribution:

- sample count;
- P50;
- P95;
- P99;
- max;
- mean only as supplementary context, never as the sole performance claim.

Do not subtract correctness work to manufacture a better headline. If a pure-build subdomain is useful, report it separately and label it precisely.

### C. Stage timing composition

Retain current stage totals/maxima and add bounded percentile detail only where it materially helps identify the tail. At minimum preserve/report the existing timing domains for:

- binary visibility;
- greedy rectangle extraction;
- canonical render-key classification;
- merge-candidate partition;
- ordinary emission-safety classification;
- repeat-aware UV proof;
- repeat-aware transport proof;
- exact baked mesh construction;
- T-junction/differential sidecar cost if instrumented without duplicating work.

No benchmark-only duplicate build is permitted merely to obtain a timing value. Existing required determinism builds remain part of real cost and may be identified separately where their cadence matters.

### D. Workload / geometry identity

Every benchmark window must carry enough aggregate workload identity to make percentile comparisons meaningful:

- completed production jobs/sample count;
- section/cell domain remains full section, not a partial slice;
- source baked quads;
- exposed independent reference faces;
- topology rectangle count and covered faces;
- render merge candidates;
- final passthrough identities;
- final merged identities;
- merged covered source faces;
- faces saved / reduction ratio;
- output quads;
- vertex bytes;
- index bytes;
- total output bytes.

A benchmark run with no real merged candidates/covered faces cannot be the only P3.8 reference workload.

### E. Scratch / allocation / GC evidence

Preserve existing reusable primitive scratch and report its observed high-water marks.

P3.8 must add no routine per-ticket collections, boxed samples or per-face benchmark objects. Percentile storage must be statically bounded and reused. Acceptable directions include a fixed primitive sample ring/reservoir or fixed histogram with deterministic percentile extraction; the implementation choice must preserve bounded memory and avoid lock-heavy hot-path behavior.

Record JVM GC collection-count/time deltas for the benchmark observation window using standard management surfaces if this can be done outside the worker hot path. This is diagnostic evidence, not a claim that all allocation is attributable to Obsidian.

If exact allocation bytes cannot be obtained portably without intrusive agents/JFR, state that limitation explicitly rather than inventing an allocation number. Use scratch high-water, GC deltas and source review as the baseline evidence.

### F. Worker utilization / pressure

Report enough evidence to characterize whether the benchmark is CPU-saturated, queue-bound or mostly idle:

- worker count;
- completed jobs;
- aggregate worker execution time over observation duration;
- running/queued high-water where available;
- steals;
- queue-full rejection;
- cancellations/stale discards;
- per-priority completion counts.

A simple derived busy fraction may be reported only with an explicit denominator such as `workerCount * observationDuration`; clamp/label it as diagnostic when lifecycle windows overlap startup/shutdown.

## Bounded percentile collector contract

The benchmark collector must be production-safe:

- primitive-only retained samples/buckets;
- fixed capacity known at construction;
- no unbounded growth;
- no sorting/allocation on the worker hot path;
- percentile extraction may copy/sort a bounded primitive snapshot only at final reporting time if needed;
- deterministic reset/window semantics;
- sample overflow/wrap count explicit;
- enough capacity to cover a representative run without silently biasing toward only the newest jobs; if a ring is used, report total observed vs retained sample count.

Prefer a small reusable telemetry component owned by `SectionMeshWorkerPool` or the frame coordinator rather than spreading percentile logic across mesh stages.

## Benchmark windows / representative runtime workload

Dev15 reference runtime must separate startup/warm-up from measured steady-state evidence. The first implementation may arm a benchmark window only after the normal async 3x3 scene reaches READY and all inherited correctness gates are armed.

Within one coherent reference run, exercise:

1. **Initial settled scene / steady state:** reach READY and allow the worker system to settle before the measured window.
2. **Ordinary rebuild churn:** multiple ordinary block break/place edits with READY recovery, producing real full-section rebuild jobs.
3. **Resource reload:** F3+T and READY afterward; record reload/resource epoch but do not mix startup resource work into a claimed steady-state percentile without labeling it.
4. **Traversal/recenter:** move far enough to cause real `scene-recenter` events and READY afterward, exercising fresh section snapshots and worker admission.
5. **Short burst/churn:** a bounded sequence of edits or traversal sufficient to create concurrent queued/running work, without deliberately changing queue policy or forcing pathological load solely to inflate samples.
6. **Normal exit:** close cleanly so worker/staging/arena/resource lifetime remains auditable.

The final report must identify which samples belong to the measured benchmark window and how many completed jobs were observed. Warm-up samples may be reported separately but must not be silently pooled into the steady-state headline.

## Environment identity

Every reference result must retain or log:

- Minecraft version;
- Fabric Loader/API version where available;
- Obsidian version;
- Java version;
- OS;
- CPU logical processor count / worker count;
- GPU/driver/Vulkan backend identity already captured by Obsidian;
- configured render distance and simulation distance when reliably accessible;
- benchmark window duration and completed sample count.

P3.8 is a baseline on the reference machine, not a vendor-specific design decision. Later cross-vendor/cross-CPU evidence may extend it.

## Correctness/lifetime gates remain mandatory

Performance evidence is invalid if the run weakens correctness to obtain it. Promotion requires all inherited gates through P3.7 remain true, including:

- `differentialCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `borderHaloCorrectnessEvidenceReady=true`;
- `repeatAwareGreedyEmissionEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- zero unsafe stale installs;
- zero dropped lifecycle evidence;
- zero unexpected worker failures/queue rejection/join failure;
- clean worker/staging/arena/resource closure;
- normal process exit `0`.

Legitimate cancellation/stale-discard activity is not automatically a failure, but its counts must remain attributable and consistent with the existing cancellation-aware accounting. Do not suppress it to make benchmark statistics look cleaner.

## P3.8 evidence gate

Add a final layered benchmark flag, tentatively `meshingBenchmarkEvidenceReady=true`, that requires the inherited P3.7 correctness/lifetime foundation plus:

- measured benchmark window armed only after settled READY;
- completed benchmark execution samples > 0;
- queue-wait and full-execution percentile sample counts coherent;
- P50/P95/P99/max values ordered and nonnegative;
- total observed / retained / overflow accounting exact for bounded collectors;
- real source quads/reference faces > 0;
- real merge candidates and merged covered faces > 0;
- output vertex/index bytes > 0;
- scratch high-water evidence > 0 for exercised paths;
- worker count > 0 and worker utilization window duration > 0;
- representative ordinary rebuild and scene-recenter activity within or adjacent to clearly identified measured windows;
- resource reload exercised and recovered to READY;
- benchmark instrumentation allocation/bounds self-test PASS;
- no benchmark-induced renderer semantic changes.

P3.8 does **not** freeze a numerical performance pass/fail threshold before the first trustworthy baseline exists. Dev15's job is to establish reliable distributions and representative workload identity. Any later regression budget or optimization target must be based on recorded baseline evidence, not retrofitted to guarantee a pass.

## Synthetic telemetry self-test

Add a deterministic bounded collector self-test independent of live timings that proves:

- sample ordering/percentile extraction on a known primitive fixture;
- empty/singleton behavior;
- capacity/wrap or bucket-overflow accounting;
- reset/window semantics;
- P50/P95/P99/max monotonic ordering.

The self-test must not inject fake samples into production benchmark evidence.

## Visual gate

Dev15 changes no rendering semantics. No new human visual verdict is required unless implementation review or runtime reveals an accidental visual/rendering change.

## Explicit non-scope

Do not consume:

- P3.9 partial slice/subregion remeshing;
- new greedy eligibility or merge-key relaxation;
- worker-count/queue-policy tuning intended to improve the benchmark before baseline capture;
- adaptive frame-budget scheduling (Phase 5);
- GPU visibility architecture changes (Phase 4);
- fluids/translucency;
- broader resource/model compatibility;
- native Vulkan graphics expansion;
- public performance marketing claims from a single reference machine.

## Promotion rule

If dev15 package CI and the reference runtime produce coherent bounded percentile evidence over representative real full-section work, while every inherited correctness/lifetime gate remains green, P3.8 may be promoted COMPLETE as a measured baseline milestone.

If timing/accounting is inconsistent, do not massage samples or change workload definitions after seeing results. Preserve the failing evidence in a new immutable attempt, classify collector plumbing vs workload labeling vs real worker-tail behavior, and make the narrow correction.

If the baseline is simply slower than desired but valid, record it truthfully. Performance optimization is driven by evidence; a valid disappointing baseline is still a successful P3.8 measurement result.

## Immediate implementation order

1. Add one bounded primitive percentile/window telemetry component with deterministic self-test.
2. Integrate queue-wait and full-ticket execution sampling at existing ticket boundaries without extra worker builds.
3. Expose/aggregate workload identity, output bytes, scratch high-water and worker-pressure data already present in the pool.
4. Add labeled warm-up vs measured benchmark window control after READY.
5. Add final P3.8 report + `meshingBenchmarkEvidenceReady` layered on P3.7.
6. Bump to `0.3.0-phase3-dev15`.
7. Open a draft P3.8 PR and require exact Java 25 / Gradle 9.5.1 package CI before runtime handoff.

Do not implement P3.9.