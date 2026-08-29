# A-0162 - P3.9 dev17 diagnostic/correction contract

**Date:** 2026-08-29  
**Milestone:** Phase 3 / P3.9 partial-remeshing experiment  
**Predecessor:** A-0161 FAILED runtime  
**Target version:** `0.3.0-phase3-dev17`  
**Result:** `SUCCESS / PLAN FROZEN`

## Objective

Produce one bounded, shadow-only diagnostic/correction build that makes the A-0161 failure class observable and corrects only defects already proven by source review, while preserving every frozen A-0159 threshold and leaving production full-section capture, worker scheduling, GPU upload/install/draw and rendered geometry unchanged.

## Frozen diagnosis from A-0161

A-0161 proved two experiment defects:

1. `PartialRemeshShadowResult` retains a bounded failure code/index, but aggregate telemetry/final closure discards the first failing episode fixture and fallback telemetry retains only a combined reason mask.
2. Dev16's selected-slice reference check is not semantically identical to the permanent accepted P3.7 oracle. Dev16 required `BinarySectionVisibility` to be exactly equal to `ReferenceFaceMesh` over every selected cell/direction. `ReferenceFaceMesh` intentionally emits only the conservative supported-full-cube subset with definitely-air neighbors, while P3.7 requires (a) every independent reference face to be visible and (b) every optimized canonical source mapping to have an independent reference face. Therefore dev16 could report a false `FAILURE_REFERENCE_VISIBILITY` for visibility bits that are intentionally outside the independent reference subset even when P3.7 is exactly green.

The P3.7 oracle semantics are permanent authority; dev17 must align to them rather than invent a stricter bidirectional equivalence after runtime observation.

## Allowed dev17 changes

### Correctness correction

Replace dev16's bidirectional selected-cell `visibility == reference` assertion with the exact permanent P3.7 semantics, restricted to selected slices:

- every selected `ReferenceFaceMesh` face must be present in `BinarySectionVisibility`;
- every selected canonical source mapping in `CanonicalFaceRenderKeys` must have a corresponding independent reference face;
- selected baked-source coverage must still be exact once-only;
- merged source/material/layer/direction/render-equivalence checks remain mandatory;
- unselected-slice fingerprints remain mandatory;
- deterministic double-build remains mandatory.

Add a distinct bounded failure code for optimized/canonical source without independent reference so the two directions remain distinguishable.

### Diagnostic evidence

`PartialRemeshExperimentTelemetry` must retain, without unbounded allocation:

- exact per-reason fallback counters for global lifecycle, provenance, multi-section, halo/XZ-boundary, all-slices, pending-episode and not-LIVE rejection;
- first failing completed episode only: section XYZ, episode id, selected slice mask, edit count, failure code, failure index and deterministic flag;
- existing observed/retained/overflow accounting unchanged;
- existing fixed-capacity percentile collectors unchanged.

The final frame-coordinator closure must emit these fields explicitly.

### Version/logging

- bump internal test version to `0.3.0-phase3-dev17`;
- startup/runtime wording must identify dev17 diagnostic/correction scope;
- no new visual gate because shadow output remains non-rendered.

## Explicitly forbidden

Dev17 must not:

- relax or retune any A-0159 numerical or correctness threshold;
- change the four fixed Y-slice identities or one-row Y dependency expansion;
- change the `ClientLevel.setBlocksDirty` provenance surface or filter events based on observed runtime guesses;
- change fallback admission policy before per-reason evidence exists;
- add partial capture, partial worker installation, partial GPU uploads, multiple draw records, GPU patching, compaction or defragmentation;
- change production greedy eligibility, render merge keys, shaders, pipelines, atlas/lightmap semantics, worker count/priority/backpressure, staging, arena or lifetime rules.

## Build gate

Before runtime handoff:

- exact Minecraft 26.2 / Java 25 / Gradle 9.5.1 hosted build must pass;
- self-tests must cover the corrected reference semantics, distinct failure code, fallback counter accounting and first-failure retention;
- helper files/workflows used for implementation must remove themselves before the authoritative package head;
- PR #53 remains draft and unmerged.

## Runtime decision rule

A dev17 runtime is still evaluated against the unchanged A-0159 gates. Diagnostic fields explain failures; they do not waive them.

If dev17 reaches zero correctness failures but evidence volume remains low, use the per-reason fallback counts to decide the next admission/provenance correction under a new immutable attempt. If benefit thresholds fail with sufficient valid evidence, reject/redesign the four-slice strategy. If any corrected shadow correctness failure remains, retain the first failure fixture and fix only the proven cause under a new attempt.
