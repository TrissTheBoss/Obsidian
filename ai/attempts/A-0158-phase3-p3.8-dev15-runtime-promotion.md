# A-0158 - P3.8 dev15 reference benchmark runtime promotion

**Date:** 2026-08-29  
**Objective:** Close the frozen A-0154 P3.8 benchmark contract with the exact same canonical dev15 package after A-0157 was partial only because F3+T occurred outside the measured window.  
**Status:** `SUCCESS / PROMOTION-READY`  
**Version:** `0.3.0-phase3-dev15`  
**Branch:** `phase3/meshing-benchmarks`

## Package authority

This run reused the canonical dev15 runtime package from A-0156 without rebuilding or tuning:

- exact package-validation source head: `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`;
- hosted Build workflow: `33270995728` SUCCESS;
- direct JAR: `Obsidian-0.3.0-phase3-dev15.jar`;
- size: `456,609` bytes;
- SHA-256: `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.

A-0157 required no source or package correction. The only missing obligation was a resource reload after the measured benchmark window had armed.

## Reference environment

- Minecraft `26.2`;
- Fabric Loader `0.19.3`;
- Fabric API `0.158.0+26.2`;
- Obsidian `0.3.0-phase3-dev15`;
- Java `25.0.1` 64-bit;
- Windows 11;
- logical processors `12`;
- production mesh workers `4`;
- GPU `AMD Radeon RX 6800 XT`;
- Vulkan backend / AMD proprietary driver `26.8.1`, Vulkan driver identity `1.4.315`;
- Prism Launcher `10.0.5`;
- launcher/server log reports view distance `16` and simulation distance `12` for this run. The dev15 benchmark close line still labels render/simulation distance `not-captured`; no value is fabricated inside benchmark telemetry itself.

## Frozen representative workload - CLOSED

The measured benchmark window armed only after settled async scene READY and inherited P3.7 correctness.

After arm, the run exercised:

1. ordinary section-dirty/block rebuild activity with repeated READY recovery;
2. a real F3+T/resource reload and READY recovery;
3. real scene recenter/traversal and READY recovery;
4. bounded edit/traversal churn with concurrent worker pressure;
5. normal shutdown.

The runtime explicitly armed:

`meshingBenchmarkEvidenceReady=true`

At the first live gate arm:

- samples `247`;
- duration about `29.067 s`;
- reload delta `1`;
- recenter delta `2`;
- READY delta `26`;
- core-dirty delta `1,425`;
- max running/queued worker pressure already satisfied.

The final close retained the successful gate with a larger final window.

## Final benchmark baseline

Measured window:

- duration: `47,121,080,700 ns` (~47.121 s);
- completed samples: `305`;
- retained samples: `305`;
- overflow samples: `0`;
- bounded collector self-test: PASS;
- observed/retained/overflow accounting: exact.

Queue wait distribution:

- P50 `25,700 ns`;
- P95 `50,500 ns`;
- P99 `80,000 ns`;
- max `3,683,900 ns`.

Full production ticket execution distribution, including required correctness sidecars:

- mean `1,312,535 ns`;
- P50 `1,000,600 ns`;
- P95 `2,664,000 ns`;
- P99 `4,432,100 ns`;
- max `14,408,300 ns`.

Per-priority benchmark completions / P99 queue wait:

- HIGH: `37`, P99 `63,100 ns`;
- NORMAL: `145`, P99 `65,700 ns`;
- LOW: `123`, P99 `1,131,600 ns`.

No numerical performance threshold existed before this baseline. These are measured reference values, not a marketing claim and not yet a regression budget.

## Workload identity

The measured window contained real mergeable work:

- source baked quads `178,238`;
- independent reference faces `71,606`;
- topology rectangles `36,057`;
- topology covered faces `71,606`;
- merge candidates `43,239`;
- passthrough identities `170,928`;
- merged identities `3,305`;
- merged covered source faces `7,310`;
- faces saved `4,005`;
- measured reduction `22 permille`;
- output quads `174,233`;
- output vertex bytes `19,937,136`;
- output index bytes `4,181,592`;
- total measured output bytes `24,118,728`.

The run therefore satisfies A-0154's requirement that the first reference baseline contain actual merged candidates and covered faces rather than a passthrough-only workload.

## Worker pressure / utilization / GC

- benchmark max queued jobs `1`;
- benchmark max running jobs `2`;
- stolen completions `240`;
- diagnostic worker busy fraction `2 permille` using the documented worker-count * window-duration denominator;
- benchmark worker completed delta `305`;
- benchmark worker cancelled delta `0`;
- benchmark worker queue rejection delta `0`;
- benchmark stale scene discard delta `14`;
- JVM GC collection delta `24`;
- JVM GC time delta `278 ms`;
- exact allocation bytes remain explicitly `not-portably-measured`.

The 14 measured stale scene discards are legitimate lifecycle invalidations, not unsafe installs. Final scene accounting reports `15` stale discards / `15` preinstall invalidations with `unsafeStaleSceneInstalls=0`.

## Production stage timing / scratch evidence

The final worker close retained the production stage telemetry required by A-0154. Representative totals/maxima include:

- exact baked mesh build total/max `29,258,200 / 2,911,600 ns`;
- paired T-junction proof total/max `23,549,200 / 11,642,300 ns`;
- paired differential proof total/max `102,107,000 / 12,239,700 ns`;
- binary visibility total/max `16,038,600 / 714,400 ns`;
- rectangle extraction total/max `11,421,000 / 695,300 ns`;
- canonical render-key total/max `74,409,300 / 6,632,300 ns`;
- repeat-aware UV total/max `7,242,700 / 244,000 ns`;
- repeat-aware transport total/max `7,693,400 / 428,500 ns`.

Observed reusable scratch high-water remained bounded and non-zero on exercised paths, including:

- main mesh scratch quads `942`;
- visibility rows `324`;
- rectangle scratch rectangles `370`;
- render-key scratch eligible faces `693`;
- repeat-aware UV descriptors `37`;
- repeat-aware transport records `37`.

No benchmark-only duplicate production mesher was introduced.

## Lifecycle closure

Measured lifecycle deltas:

- READY `32`;
- rendered core dirty `1,929`;
- resource reload `1`;
- recenter `2`.

Final scene lifecycle:

- READY transitions `33`;
- scene rebuilds `32`;
- record installs `308`;
- camera recenter events `2`;
- resource reload events `2` total, including startup + measured F3+T;
- dropped lifecycle events `0`;
- observed reasons include `section-dirty|world-change|resource-reload|scene-recenter`.

## Inherited correctness gates

Every inherited gate through P3.7 remained green:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- `renderMergeCandidateEvidenceReady=true`;
- `ordinaryQuadEmissionSafetyEvidenceReady=true`;
- `repeatAwareUvEvidenceReady=true`;
- `repeatAwareTransportEvidenceReady=true`;
- `repeatAwareGreedyEmissionEvidenceReady=true`;
- `borderHaloCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `differentialCorrectnessEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`.

Final P3.7 differential proof remained exact:

- proof records/determinism `308 / 308`;
- reference faces `72,652`;
- source baked quads `179,785`;
- passthrough identities `172,333`;
- merged candidates `3,368`;
- merged expanded faces `7,452`;
- material/direction/geometry `7,452 / 7,452` each;
- UV/color/light `29,808 / 29,808` each;
- missing `0`;
- duplicate `0`;
- optimized-without-reference `0`;
- real mismatches `0`;
- fixture self-tests `308 / 308`.

## Worker / lifetime closure

Global production worker close:

- submitted/started/completed `323 / 323 / 323`;
- cancelled `0`;
- cancellation requests `0`;
- queue-full rejections `0`;
- failed jobs `0`;
- shutdown join failures `0`.

Lifetime:

- workers clean `true`;
- staging clean `true`;
- arena clean `true`;
- resources clean `true`;
- staging submitted/reclaimed `24,351,080 / 24,351,080` bytes;
- pending upload batches `0`;
- arena allocations/retired/reclaimed `924 / 924 / 924`;
- arena used bytes `0`;
- arena allocation failures `0`;
- retired/released resources `308 / 308`;
- pending retirements `0`;
- process exit code `0`.

## Decision

**P3.8 dev15 satisfies the complete frozen A-0154 contract and is promotion-ready.**

This closes P3.8 as a measurement baseline milestone. No numerical performance threshold is retrofitted after seeing the data. Future performance work may compare against this recorded reference workload, but must account for workload identity and environment rather than treating one scalar latency as universal.

No new visual verdict is required because dev15 changed no rendering semantics and runtime found no accidental geometry/shader/pipeline change.

## Promotion action

1. synchronize continuity with A-0158 and P3.8 promotion-ready status;
2. require hosted exact-head Java 25 / Gradle 9.5.1 Build success for the synchronized promotion head;
3. promote PR #51 from the exact validated head and merge `[no-release]`;
4. synchronize `main` with P3.8 COMPLETE / P3.9 ACTIVE and require hosted main-head CI;
5. freeze P3.9 partial-remeshing contract in a new immutable attempt **before any P3.9 source change**.

Do not modify dev15 benchmark definitions or implement P3.9 before that freeze.