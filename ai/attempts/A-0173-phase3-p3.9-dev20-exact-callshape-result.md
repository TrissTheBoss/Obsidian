# A-0173 — Phase 3 P3.9 dev20 exact Minecraft 26.2 dirty call-shape result

Date: 2026-08-29
Status: **SUCCESS for A-0172 Stage 1 / runtime causal tracer still required before correction**
Target version: `0.3.0-phase3-dev20`
Parent contract: A-0159
Investigation contract: A-0172
Trigger runtime: A-0171

## Purpose

Resolve the exact Minecraft 26.2 call shape between Obsidian's exact `ClientLevel.setBlocksDirty` provenance source and the `LevelExtractor.setSectionDirty` production-validity sink before authorizing any change to P3.9 admission behavior.

## Hosted inspection authority

Inspection used the repository's exact hosted dependency/toolchain path:

- Java 25;
- Gradle 9.5.1;
- Fabric Loom 1.17.20;
- `gradle compileClientJava --no-daemon` SUCCESS;
- exact resolved client JAR `/home/runner/.gradle/caches/fabric-loom/26.2/minecraft-client-only.jar`.

Temporary probe history:

- first probe run `33276852973`: FAILED for bookkeeping only because the temporary workflow invoked nonexistent `./gradlew`; no renderer/source conclusion was taken from that run;
- corrected probe head `9c13fc531dd9658eb33e77de822aa9a7da226c1b`, run `33276918411`: SUCCESS;
- expanded exact-bytecode probe head `ad0fafc67652515a48f363fd50941238fd49510d`, run `33276966330`, job `99165265025`: SUCCESS.

## Exact bytecode findings

### 1. `ClientLevel.setBlocksDirty(BlockPos, BlockState, BlockState)`

The method is a synchronous delegate only:

1. load `levelExtractor`;
2. invoke `LevelExtractor.setBlockDirty(pos, oldState, newState)`;
3. return.

Therefore Obsidian's current `ClientLevelMixin` TAIL hook executes only after the complete synchronous `LevelExtractor.setBlockDirty` call returns.

### 2. `LevelExtractor.setBlockDirty(BlockPos, BlockState, BlockState)`

The method calls `ModelManager.requiresRender(oldState, newState)`.

- If render is not required, it returns without dirtying sections.
- If render is required, it calls `setBlocksDirty(x, y, z, x, y, z)`.

### 3. `LevelExtractor.setBlocksDirty(int,int,int,int,int,int)`

For the supplied block range, it expands each axis by one block, converts every expanded block coordinate to section coordinates with `SectionPos.blockToSectionCoord`, and invokes section dirty for each loop element.

For one interior block this is exactly a `3 x 3 x 3 = 27` call fan-out. Multiple block-local dirty callbacks in the same frame add linearly.

This exactly explains the repeated `relevantEvents=54` batches in A-0171 as two render-relevant exact block-dirty callbacks before Obsidian's next frame drain.

### 4. Section dirty sink

`LevelExtractor.setSectionDirty(int,int,int)` delegates to private `setSectionDirty(int,int,int,boolean)` with `dirtyFromPlayer=false`.

The private four-argument method directly calls `SectionUpdateTracker.setDirty(sectionX, sectionY, sectionZ, dirtyFromPlayer)` and returns.

### 5. Neighbor/range section dirty paths

`LevelExtractor.setSectionDirtyWithNeighbors(x,y,z)` calls `setSectionRangeDirty(x-1,y-1,z-1,x+1,y+1,z+1)`.

`setSectionRangeDirty` loops section coordinates and calls `setSectionDirty(int,int,int)` for each section.

## Correlation with A-0171 runtime

A-0171 repeatedly observed a `54`-event section-dirty invalidation followed on the next frame by a small independent invalidation such as `1`.

The exact bytecode proves:

- a render-relevant single-block `setBlocksDirty` synchronous fan-out is 27 section-dirty calls;
- two exact callbacks before the frame drain explain 54 exactly;
- the later next-frame `+1` cannot be part of that already-returned synchronous 27-call fan-out;
- because the ClientLevel TAIL hook runs after the synchronous fan-out but before the frame can advance, TAIL ordering by itself does **not** explain why the later `+1` has no exact block provenance.

The `+1` is therefore a distinct section-dirty call path. Its caller/origin is not identified by the current aggregate lifecycle bridge.

## Safety conclusion

A behavior correction is **not yet authorized**.

It would be unsafe to preserve a pending localized episode across every empty `+1` merely because the preceding frame contained an exact 54-event fan-out. The later event may be related, but A-0173 does not yet prove that relationship.

The next allowed action under A-0172 is a bounded primitive caller-origin tracer that classifies the private `LevelExtractor.setSectionDirty(IIIZ)` calls by their enclosing public/internal dirty path, with no admission, threshold, production-renderer, worker, mesh, upload, or GPU behavior change.

## Required next evidence

The tracer must distinguish at least:

- section dirty calls emitted inside exact `setBlockDirty` / block-range fan-out;
- public single-section dirty calls;
- section-range / neighbor dirty calls;
- any unclassified/ambiguous path;
- first small unprovenanced event fixture, including primitive section coordinates and `dirtyFromPlayer` where available.

If the later `+1` is proven to be a deterministic derivative path that can be tied fail-closed to the existing pending exact episode, a later immutable correction contract may preserve it. Otherwise the provenance source/order must be redesigned without weakening fallback safety.

## Promotion

No P3.9 promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.