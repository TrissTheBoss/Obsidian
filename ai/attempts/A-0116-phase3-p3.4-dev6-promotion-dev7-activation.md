# A-0116 - Phase 3 P3.4 dev6 promotion and dev7 activation

**Date:** 2026-08-23  
**Result:** `SUCCESS` — P3.4 dev6 was promoted after exact runtime and evidence-head CI closure; P3.4 remains ACTIVE and dev7 merge-candidate sidecar work is activated.

## Objective

Promote the validated `0.3.0-phase3-dev6` render-key sidecar without incorrectly declaring all of P3.4 complete, then activate the next correctness-first P3.4 slice.

## Dev6 promotion evidence

A-0115 records the complete reference shutdown tail and exact dev6 metrics. The final evidence-head CI at `efbaf7d15be5a4472700c861535ee6e4ef8fc038`, run `32601469374`, completed with Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS and release SKIPPED.

PR #38 was promoted from draft and merged with standing user authorization using `[no-release]`.

Merge commit:

- `967c4511cd11cd721886feae6d146f4412790a6d`.

The canonical dev6 package remains:

- `Obsidian-0.3.0-phase3-dev6.jar`;
- size `308,439` bytes;
- SHA-256 `2d2664d1eb6fc844cf70cefabb11400752da20866f4e1f1a79ca3873ea55019a`.

## Status boundary

Dev6 proves the canonical-face render-key classifier and real-terrain equivalence statistics. It deliberately does **not** emit greedy GPU geometry and ends with:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

Therefore P3.4 remains **ACTIVE**. P3.5 is not activated.

## Dev7 activation

Next version: `0.3.0-phase3-dev7`.

Active branch: `phase3/render-merge-candidate-sidecar`.

Dev7 objective: construct a deterministic, bounded, render-key-aware merge-candidate sidecar over the proven P3.2 visibility and dev6 canonical render-key truth while retaining the existing `BakedSectionMesh` as the authoritative GPU drawable.

The dev7 sidecar must partition only dev6-eligible canonical faces into same-render-key rectangles/candidates and leave ambiguous/ineligible canonical faces plus arbitrary generalized baked geometry on passthrough. It must prove exact no-gap/no-overlap coverage and deterministic output on production workers.

## Important correctness boundary before GPU emission

Pairwise face render-key equality is necessary but is not yet sufficient proof that replacing many source quads by one large GPU quad is visually exact. A large quad may interpolate color/light/AO differently from repeated per-cell quads, and atlas UVs that are identical per cell do not automatically define correct texture repetition across a larger rectangle.

Dev7 therefore remains **sidecar-only**. It measures and validates key-aware candidate topology, but does not claim rectangle-level interpolation/UV emission safety and must keep `renderCorrectMergeKeyComplete=false`.

Any later P3.4 slice that begins replacing emitted GPU geometry requires a separately frozen emission contract and renewed explicit human visual validation.

## Next action

Freeze the exact dev7 representation, deterministic extraction order, partition oracle and runtime gate in A-0117, then implement it on `phase3/render-merge-candidate-sidecar` with exact GitHub CI/package authority.