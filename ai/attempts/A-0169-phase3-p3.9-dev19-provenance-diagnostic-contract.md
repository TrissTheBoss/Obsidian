# A-0169 — Phase 3 P3.9 dev19 provenance diagnostic contract

Date: 2026-08-29
Status: **PLAN FROZEN**
Version: `0.3.0-phase3-dev19`
Parent contract: A-0159
Trigger: A-0168

## Purpose

Dev19 is a diagnostic-only correction slice. It exists solely to decompose the aggregate P3.9 provenance fallback bucket that dominated A-0168 (`80` provenance fallbacks) after a second controlled dev18 runtime still failed to reach the frozen A-0159 evidence volume.

Dev19 must not relax or modify any A-0159 correctness, evidence-volume, benefit, complexity, lifetime, or inherited thresholds.

## Explicitly unchanged

The following are frozen unchanged from A-0159/A-0165:

- four fixed Y slices;
- one-row vertical dependency expansion;
- `ClientLevel.setBlocksDirty` as the exact block-local provenance surface;
- section-local X/Z boundary conservatism;
- production full-section invalidation/capture/worker mesh/upload/install/draw;
- shadow-only partial-remesh experiment;
- no partial GPU patching;
- pending same-section coalescing semantics from dev18;
- permanent P3.7 differential oracle;
- greedy eligibility/render key/transport/geometry;
- projected upload accounting;
- worker count, queue capacity, priorities, backpressure, staging, arena and deferred release;
- shaders, pipelines, atlas/lightmap semantics;
- every frozen A-0159 threshold.

## Required diagnostic additions

All diagnostic state must be bounded primitive state. No unbounded lists, per-edit object history, boxing/sorting on worker hot paths, or retained mutable Minecraft objects.

### 1. Provenance subreason counters

Within the existing high-level `FALLBACK_PROVENANCE` bucket, retain independent counters for:

- `missingOrEmpty`: drain `count == 0` and no fallback flags;
- `offRenderThread`: `FLAG_OFF_RENDER_THREAD` present;
- `overflowFlag`: `FLAG_OVERFLOW` present;
- `overflowEvents`: sum of the bounded bridge's reported overflow-event count.

If a single fallback contains multiple flags, high-level provenance fallback accounting remains one fallback while individual subreason counters may overlap. The final log must state that relationship explicitly.

### 2. First provenance-fallback fixture

Retain exactly one bounded first fixture containing only primitive fields:

- whether a fixture was retained;
- fallback sequence/index;
- drain count;
- fallback flags;
- overflow-event count;
- scene state ordinal/name if safely available;
- center-known boolean;
- pending-episode-present boolean.

No block position object, world object, section snapshot, collection, or unbounded history may be retained.

### 3. Final closure telemetry

`FrameCoordinator` final P3.9 closure must emit the subreason counters and first fixture alongside existing A-0159 fields.

### 4. Self-test

Add deterministic self-test coverage proving:

- missing/empty classification;
- off-render-thread flag classification;
- overflow flag/event classification;
- overlapping flags do not increment the high-level provenance fallback more than once;
- first fixture is retained once and never replaced.

Synthetic self-test evidence does not replace runtime evidence.

## Runtime decision rule

One short real dev19 diagnostic runtime is sufficient to choose the next implementation action. It does not need to close A-0159 evidence volume.

After the P3.9 window arms:

1. perform several ordinary safe-interior one-slice edits with READY recovery;
2. perform several safe-interior two-slice edits;
3. perform one same-section coalesced burst;
4. perform F3+T and recover READY;
5. cause one scene recenter and recover READY;
6. exit normally and return the complete log.

Interpretation:

- if `missingOrEmpty` dominates, the exact dirty-provenance hook surface/order is incomplete or misaligned with lifecycle invalidation and a new immutable correction attempt must inspect/correct that surface without weakening fallback safety;
- if `offRenderThread` dominates, thread ownership/order must be corrected or a safe primitive handoff designed before admission can expand;
- if `overflowFlag/overflowEvents` dominates, capacity/coalescing behavior may be reconsidered only under a separately frozen bounded-capacity correction; no unbounded structure is allowed;
- if no provenance subreason dominates and valid evidence volume naturally rises, continue A-0159 on the exact same dev19 binary.

## Promotion

Dev19 diagnostic instrumentation itself is not sufficient to promote P3.9. P3.9 remains experimental until a later runtime satisfies every frozen A-0159 gate or the fixed four-slice design is explicitly rejected/redesigned under a new immutable contract.