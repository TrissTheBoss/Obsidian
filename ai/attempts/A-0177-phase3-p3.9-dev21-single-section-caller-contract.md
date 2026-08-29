# A-0177 — Phase 3 P3.9 dev21 public single-section caller contract

Date: 2026-08-30
Status: **PLAN FROZEN**
Target version: `0.3.0-phase3-dev21`
Parent contract: A-0159
Trigger runtime: A-0176

## Purpose

A-0176 proves that every dev20 missing/empty provenance fallback (`21/21`) is paired exclusively with one tracked-scene-relevant `SINGLE_SECTION` event from public `LevelExtractor.setSectionDirty(III)`, with no exact-block/range/neighbor/unclassified mixing.

Before changing P3.9 admission, dev21 must identify every exact Minecraft 26.2 caller of public `LevelExtractor.setSectionDirty(III)` and determine whether the observed next-frame single-section event is a deterministic derivative of an already-captured exact block edit or an independent invalidation that requires its own provenance.

## Stage 1 — exact caller inspection only

Use the same hosted dependency/toolchain authority as A-0173:

- Java 25;
- Gradle 9.5.1;
- Fabric Loom / exact Minecraft 26.2 client JAR resolved by `compileClientJava`;
- inspect bytecode, not guessed source/API behavior.

Required evidence:

1. enumerate client classes/methods that invoke public `LevelExtractor.setSectionDirty(III)`;
2. print the bytecode bodies surrounding those invocations;
3. identify whether each caller originates from:
   - chunk/section compile completion;
   - block/model update propagation;
   - light/occlusion update;
   - player-origin dirtying;
   - world/chunk lifecycle;
   - another exact path;
4. identify arguments and available section identity at the call site;
5. determine whether a caller can execute on the render thread on a later frame after the initiating exact block dirty;
6. retain no behavior change in Stage 1.

A temporary self-removing hosted workflow is permitted for exact bytecode inspection. Failed bookkeeping probes produce no behavioral conclusion.

## Stage 2 decision rule

A behavior correction may be frozen only after Stage 1.

### Allowed derivative-preservation direction

Only if exact bytecode proves a specific single-section caller is a deterministic derivative/completion notification for the same section that was already invalidated by an exact block event may a later immutable correction contract consider preserving the pending exact episode across that event.

Any preservation rule must be fail-closed and require all of:

- pending exact episode exists;
- event origin is the proven derivative caller, not generic public single-section dirty;
- event section identity matches the pending episode's section exactly;
- no intervening global lifecycle/recenter/resource reload/world change;
- no provenance ambiguity/overflow/off-thread flag;
- no multi-section or X/Z boundary condition;
- production scene generation/stale-result rules remain unchanged;
- all A-0159 thresholds remain unchanged.

### Required redesign direction

If public single-section callers include independent semantic invalidations that cannot be distinguished safely with existing primitive identity, generic preservation is forbidden. Freeze a new provenance redesign instead, capturing the exact primitive cause at the caller where possible.

If caller identity itself remains ambiguous, add one more bounded caller-specific diagnostic; do not infer causality from timing alone.

## Explicitly forbidden

- changing any A-0159 threshold;
- treating every `SINGLE_SECTION` event as safe merely because a pending episode exists;
- suppressing production invalidation;
- changing production full-section capture/mesh/upload/install/draw;
- changing P3.7 reference semantics;
- changing greedy meshing, worker count/queue/backpressure, staging, arena, lifetime, shaders, pipelines, atlas/lightmap behavior;
- partial GPU patching;
- broad `vkDeviceWaitIdle` or other synchronization shortcuts.

## Runtime requirement

No reference runtime is required for Stage 1 bytecode inspection. If Stage 1 authorizes a correction, that correction must receive its own implementation/package attempt and a subsequent reference runtime before P3.9 can be evaluated again.

## Promotion

No promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.
