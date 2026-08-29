# A-0174 — Phase 3 P3.9 dev20 section-dirty origin tracer contract

Date: 2026-08-29
Status: **PLAN FROZEN**
Version: `0.3.0-phase3-dev20`
Parent contract: A-0159
Investigation contract: A-0172
Exact call-shape evidence: A-0173

## Purpose

A-0173 proves that the common `54` lifecycle batches are exactly explainable as two synchronous render-relevant block-dirty callbacks, each expanding to 27 `LevelExtractor.setSectionDirty` calls. It also proves that a later next-frame `+1` invalidation is a distinct section-dirty path and cannot be part of the already-returned exact 27-call fan-out.

Dev20 must identify the caller/origin of those small later section-dirty events before any P3.9 admission behavior changes.

## Explicitly unchanged

Dev20 is diagnostic-only. The following remain byte-for-byte or behaviorally unchanged except for bounded observation calls:

- all A-0159 thresholds;
- four fixed Y slices and one-row vertical expansion;
- `ClientLevel.setBlocksDirty` exact block-local provenance source;
- X/Z boundary conservatism;
- production full-section capture/mesh/upload/install/draw;
- shadow-only P3.9 output and no partial GPU patching;
- dev17 corrected reference semantics;
- dev18 same-section pending coalescing;
- permanent P3.7 oracle;
- greedy merge eligibility/render key/transport/geometry;
- shaders, pipelines, atlas/lightmap semantics;
- worker count, queue, backpressure, staging, arena and deferred lifetime;
- current behavior where an empty exact-provenance drain remains a provenance fallback and clears the pending episode.

No evidence-volume improvement is expected or claimed from this binary.

## Origin classification

Instrument exact Minecraft 26.2 `LevelExtractor` method scopes with bounded primitive state. Every tracked-scene-relevant private `setSectionDirty(IIIZ)` event must be classified by the outermost enclosing dirty operation:

- `EXACT_BLOCK`: inside `setBlockDirty(BlockPos, BlockState, BlockState)`;
- `BLOCK_RANGE`: inside `setBlocksDirty(IIIIII)` with no enclosing exact-block scope;
- `NEIGHBOR_RANGE`: inside `setSectionDirtyWithNeighbors(III)`;
- `SECTION_RANGE`: inside `setSectionRangeDirty(IIIIII)` with no enclosing neighbor scope;
- `SINGLE_SECTION`: inside public `setSectionDirty(III)` with no enclosing outer scope;
- `UNCLASSIFIED`: no recognized enclosing scope.

Nested methods inherit the outermost active scope. Example: exact block -> block range -> private section dirty remains `EXACT_BLOCK`; neighbor -> section range -> public single -> private remains `NEIGHBOR_RANGE`.

## Relevance filter

The tracer must count only private section-dirty calls that actually advance `SectionLifecycleEvents.latestSequence()` for the active tracked dependency domain.

Implementation may compare lifecycle sequence immediately before and after the existing `SectionLifecycleEvents.sectionDirty(...)` call inside `LevelExtractorMixin`. If the sequence does not advance, the diagnostic collector must ignore that event.

## Bounded primitive state

Allowed retained state:

- fixed primitive scope stack with small compile-time capacity;
- monotonic relevant-event counts per origin;
- monotonic `dirtyFromPlayer` counts per origin;
- one bounded first event fixture per origin containing only section XYZ, dirtyFromPlayer, and origin code;
- fixed primitive per-drain delta snapshot;
- provenance-fallback correlation counters that classify the origin mix observed in the immediately preceding lifecycle drain;
- one first provenance-fallback origin fixture.

Forbidden:

- stack traces;
- reflection for caller discovery;
- unbounded maps/lists/queues;
- retained Minecraft/world/block/snapshot objects;
- per-event heap object history;
- changing lifecycle relevance rules or event counters.

## Required diagnostics

At final close, emit:

1. total relevant section-dirty events per origin;
2. `dirtyFromPlayer` totals per origin;
3. first primitive event fixture per origin where observed;
4. provenance-fallback count classified by the immediately preceding drain's origin composition, at minimum:
   - exact-block-only;
   - single-section-only;
   - range/neighbor-only;
   - mixed;
   - no-relevant-section-dirty / unavailable;
   - unclassified involved;
5. first provenance-fallback origin fixture with origin mask, lifecycle relevant-event count, exact-provenance drain count/flags, scene state ordinal, center-known, pending-episode-present;
6. deterministic self-test for scope nesting/origin masks and first-fixture retention;
7. explicit `admissionPolicyChanged=false`, `thresholdsChanged=false`, `productionRendererChanged=false`.

## Runtime sequence

One short reference runtime is sufficient:

1. wait for READY and experiment arm;
2. perform ~6 safe-interior ordinary edits, allowing READY recovery;
3. perform ~3 safe-interior Y-boundary edits;
4. perform one short same-section burst;
5. F3+T and recover READY;
6. one real scene recenter and recover READY;
7. exit normally and return the full log.

This run does not need 32 localized samples and cannot promote P3.9.

## Decision rule

- If missing/empty provenance fallbacks are consistently paired with `SINGLE_SECTION` events, inspect the exact caller(s) of public `LevelExtractor.setSectionDirty(III)` and determine whether they are causally tied to the preceding exact edit before any preservation rule.
- If paired with range/neighbor events, inspect that exact path and require section-identity correlation before preservation.
- If paired with `UNCLASSIFIED`, expand exact bytecode/runtime caller classification under a new immutable diagnostic attempt.
- If paired with no relevant section-dirty event, inspect lifecycle/provenance drain ordering itself.
- If multiple origins mix, do not guess; retain fallback and freeze a narrower follow-up.

## Promotion

No promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.