# A-0161 - P3.9 dev16 reference runtime FAILED

**Date:** 2026-08-29  
**Milestone:** Phase 3 / P3.9 partial-remeshing experiment  
**Version under test:** `0.3.0-phase3-dev16`  
**Frozen contract:** A-0159  
**Canonical package:** `Obsidian-0.3.0-phase3-dev16.jar`, 490,250 bytes, SHA-256 `b14640ab1a397561371564e6b3c38b93b105e481be6a32b8172b8448de701ffd`  
**Exact package-validation head:** `9b5930a24c8bd1841c474a03f67407231e11bc65`, hosted Build `33273077105` SUCCESS  
**Result:** `FAILED`

## Objective

Run the exact A-0159 reference workload against the shadow-only four-slice dev16 experiment and decide the frozen correctness, evidence-volume, benefit and complexity gates without changing thresholds after seeing results.

## Runtime environment

User-supplied reference runtime on 2026-08-29:

- Windows 11;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- Java 25.0.1;
- Prism Launcher 10.0.5;
- Vulkan backend;
- AMD Radeon RX 6800 XT;
- AMD proprietary Vulkan driver 26.8.1 / Vulkan 1.4.315.

The explicit dev16 shadow window armed after settled READY. The run exercised ordinary edits, post-arm resource reload, real scene recenter activity and normal shutdown.

## Decisive final evidence

The final frame-coordinator closure reported:

- `partialRemeshExperimentEvidenceReady=false`;
- `partialRemeshWindowArmed=true`;
- completed localized episodes `6`;
- fallback episodes `150`;
- one-slice episodes `2`;
- two-slice episodes `4`;
- three-slice episodes `0`;
- coalesced episodes `0`;
- exact episodes `5`;
- **correctness failures `1`**;
- unselected-slice change failures `0`;
- determinism failures `0`;
- selected-cell P50 `500 permille`;
- shadow/full CPU ratio P50 `294 permille`;
- shadow/full CPU ratio P95 `968 permille`;
- projected/full upload ratio P50 `964 permille`;
- projected/full upload ratio P95 `1000 permille`;
- mean/max assembled fixed-slice inflation `0 / 0 permille`;
- metadata `96 bytes/section`;
- fixed slice identities `4`;
- sample accounting `observed=6`, `retained=6`, `overflow=0`;
- `partialRemeshGpuInstallChanged=false`;
- `partialRemeshRenderedGeometryChanged=false`.

Inherited production/correctness evidence remained healthy:

- every inherited automated gate through P3.8 was true, including `differentialCorrectnessEvidenceReady=true` and `meshingBenchmarkEvidenceReady=true`;
- final P3.7 differential proof records `558`, determinism `558/558`, missing `0`, duplicate `0`, optimized-without-reference `0`, real mismatches `0`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`;
- worker queue rejections `0`, worker failures `0`, shutdown join failures `0`;
- one lifecycle-attributable worker cancellation occurred during the long run, with no unsafe stale install and inherited gates still green;
- scene recenter events `10`;
- measured resource reload delta `3` after window arm;
- workers, staging, device arena and deferred resources closed cleanly;
- process exit code `0`.

## Frozen A-0159 gate evaluation

### Correctness - FAILED

A-0159 requires every completed localized episode to be exact and requires zero correctness failures.

Observed: `5/6` exact with `1` correctness failure.

This is independently decisive. The run cannot be classified PARTIAL merely for insufficient workload; a mandatory correctness gate was violated.

The failure was not an unselected-slice stability defect and not a determinism defect: both counters were zero.

### Evidence volume - FAILED

Required / observed:

- completed localized episodes: `>=32` / `6`;
- one-slice episodes: `>=16` / `2`;
- two-slice episodes: `>=8` / `4`;
- coalesced episodes: `>=1` / `0`;
- explicit fallback: `>=1` / `150`.

The user did exercise substantial runtime activity; the experiment classified almost all candidate situations as fallback. The low accepted count is therefore not safely attributable to a simple lack of user interaction.

### Benefit thresholds - NOT CLOSED; observed misses exist

Observed against frozen thresholds:

- selected-cell P50 `500 <= 500`: pass;
- CPU P50 `294 <= 600`: pass;
- CPU P95 `968 <= 800`: fail;
- projected upload P50 `964 <= 600`: fail;
- projected upload P95 `1000 <= 800`: fail.

Only six localized episodes completed, so these ratios are not sufficient to reject the fixed four-slice concept on benefit grounds by themselves. They are still important warning evidence, especially the nearly full-section projected upload ratios. The implementation/evidence run is rejected first on correctness.

The CPU number is also only shadow projection/proof work over already-built production artifacts; it must not be represented as production partial-remesh speedup.

### Complexity - PASSED on observed sample

- metadata `96 <= 1024 bytes/section`;
- exactly `4` retained slice identities;
- mean inflation `0 <= 50 permille`;
- max inflation `0 <= 100 permille`.

## Diagnosis from exact dev16 source

The result exposes two observability defects that prevent a responsible blind rerun:

1. `PartialRemeshShadowResult` already retains a bounded `failureCode` and `failureIndex` with distinct failure classes (unselected change, reference visibility, duplicate source, missing source, merged identity, accounting, exception), but `PartialRemeshExperimentTelemetry` reduces the result to aggregate correctness counters and the final closure does not retain the first failing episode fixture. A-0159 explicitly required a bounded first-failure fixture.
2. `PartialRemeshExperimentTelemetry` records only a combined fallback reason bitmask while the runtime closure does not emit per-reason fallback counts. With 150 fallbacks, the dominant rejection path cannot be established from this log. The admission path can fall back for global lifecycle, missing/ambiguous provenance, multi-section provenance, halo/XZ boundary, all-slice masks, pending-episode replacement or non-LIVE state.

The exact provenance surface is `ClientLevel.setBlocksDirty(BlockPos, oldState, newState)`. Dev16 coalesces block-local provenance by section in a fixed-capacity accumulator and applies conservative section/state/boundary admission. The broad existing section-dirty lifecycle fanout is visible in this run, but the available evidence is insufficient to claim which specific fallback path dominates.

## Decision

**A-0159 dev16 is FAILED as implemented and must not be promoted or merged.**

This does **not** establish a production renderer regression: dev16 shadow geometry was never uploaded or drawn, all inherited production correctness gates remained green, and lifetime closure was clean.

It also does **not yet prove that the four-fixed-slice strategy itself is categorically unviable**, because only six localized samples survived admission. The correct conclusion is narrower: the current dev16 shadow experiment/evidence path cannot justify partial remeshing and contains at least one real shadow-correctness failure that must be identified before further benefit claims.

## Next action

Do not ask the user to rerun the unchanged dev16 package.

Open a new immutable correction/diagnostic attempt while preserving every A-0159 threshold unchanged. Before changing the strategy or correctness rules, add bounded evidence sufficient to localize this run class exactly:

- per-fallback-reason counters for every existing fallback category;
- retained first failing episode fixture containing episode id, selected slice mask, failure code, failure index, deterministic flag and bounded provenance summary;
- bounded first/interesting admission/completion diagnostic records if needed, with no worker hot-path allocation or unbounded logging;
- keep production full-section rendering/GPU install unchanged;
- no partial GPU patching;
- no threshold relaxation.

Only after the exact failure class and dominant fallback reason are known should the provenance/admission or shadow correctness logic be corrected and repackaged for another reference run.