# A-0172 — Phase 3 P3.9 dev20 lifecycle/provenance causal-correlation contract

Date: 2026-08-29
Status: **PLAN FROZEN**
Target version: `0.3.0-phase3-dev20`
Parent contract: A-0159
Trigger runtime: A-0171

## Purpose

A-0171 proved that all 40 dev19 provenance fallbacks were `missingOrEmpty` with zero off-render-thread and zero overflow evidence. The first failing fixture occurred in `SCANNING` while an exact pending episode already existed.

Dev20 must determine and then correct only the causal mismatch between exact `ClientLevel.setBlocksDirty` provenance and derivative `LevelExtractor.setSectionDirty` invalidations. It must not increase evidence volume by treating uncorrelated empty invalidations as localized evidence.

## Frozen unchanged

The following remain unchanged unless a later immutable contract explicitly supersedes this one:

- every A-0159 correctness/evidence/benefit/complexity threshold;
- four fixed Y slices and one-row vertical expansion;
- `ClientLevel.setBlocksDirty` remains the exact block-local provenance source;
- X/Z boundary conservatism;
- production full-section capture/mesh/upload/install/draw;
- shadow-only experiment; no partial GPU patching;
- dev17 corrected reference semantics;
- dev18 same-section pending coalescing;
- permanent P3.7 differential oracle;
- greedy merge eligibility/render key/transport/geometry;
- shaders, pipelines, atlas/lightmap semantics;
- worker count, queue capacity, backpressure, staging, arena and deferred release.

## Stage 1 — exact 26.2 call-shape inspection

Before changing runtime admission logic, inspect the exact Minecraft 26.2 classes used by hosted CI and retain evidence for:

- `ClientLevel.setBlocksDirty(BlockPos, BlockState, BlockState)` bytecode/call shape;
- `LevelExtractor.setSectionDirty(int,int,int,boolean)` bytecode/call shape;
- any direct or indirect path from block-local dirty handling into section-dirty fan-out that is visible from exact dependency bytecode;
- whether multiple section-dirty calls are synchronously emitted from one block-local dirty operation or whether follow-on invalidations are scheduled through another path.

Use the repository's hosted Java 25 / Gradle 9.5.1 environment. Do not infer this from another Minecraft version.

## Stage 2 — correction admissibility

A correction may be implemented only if Stage 1 plus A-0171 evidence gives a fail-closed causal rule.

Allowed correction shapes include:

1. bounded primitive causal tagging/correlation between the exact block dirty event and derivative section-dirty events;
2. preserving an already-exact pending episode across a derivative empty invalidation only when that invalidation is proven correlated to the same exact edit/rebuild sequence;
3. moving or duplicating observation points inside the same exact `setBlocksDirty` surface if bytecode proves the current TAIL placement misses valid returns/orderings;
4. bounded diagnostic counters needed to prove the correction's rule at runtime.

Forbidden:

- unconditional `pending != null && empty => keep pending` behavior;
- treating every empty drain while SCANNING/BUILDING/RETIRING as correlated;
- weakening `FALLBACK_PROVENANCE` for uncorrelated invalidations;
- widening the exact provenance source to guessed world reads;
- retaining Minecraft objects across frames/workers;
- unbounded histories/maps/queues;
- changing A-0159 thresholds;
- changing production geometry or GPU behavior.

## Required correctness properties

Any implemented causal bridge must:

- use bounded primitive state only;
- never create a localized admission without at least one exact block-local provenance record;
- preserve fallback for an empty invalidation that cannot be causally tied to an exact pending episode;
- preserve global/multi-section/XZ/all-slice/not-LIVE safety behavior;
- preserve zero worker world reads after capture;
- preserve zero synchronous scene mesh builds;
- preserve stale-install/queue/lifetime gates;
- retain permanent differential correctness exactly.

## Runtime proof if correction is implemented

The next runtime must expose bounded counters for at least:

- exact localized admissions;
- correlated derivative empty invalidations preserved;
- uncorrelated empty invalidations rejected;
- any correlation overflow/ambiguity fallback;
- first rejected uncorrelated fixture;
- first preserved correlated fixture.

A short diagnostic runtime may precede a full A-0159 closure run if needed. A full closure attempt is authorized only after correlation behavior is unambiguous and fail-closed.

## Promotion

No promotion is authorized by this contract. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.