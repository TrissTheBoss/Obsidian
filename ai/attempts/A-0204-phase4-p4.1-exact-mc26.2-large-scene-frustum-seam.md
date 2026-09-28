# A-0204 - Phase 4 P4.1 exact Minecraft 26.2 large-scene / frustum seam

**Date:** 2026-09-02  
**Status:** `SUCCESS` / **EXACT API GROUNDING COMPLETE**  
**Branch:** `phase4/p4.1-persistent-scene-visibility`

## Objective

Close A-0203's mandatory exact-version inspection before any P4.1 renderer-source implementation: establish authoritative Minecraft 26.2 seams for active render distance, loaded chunk/section membership deltas, vertical section range, world camera state and frustum construction/classification.

## Method

Added a temporary GitHub Actions workflow on the Phase 4 branch, resolved the exact Fabric Loom Minecraft 26.2 artifacts with Java 25 / Gradle 9.5.1, and ran `javap -public -c -p` over the relevant client classes. The first run was expanded once to include `Camera`, `ClientChunkCache$Storage`, `LevelExtractor`, `ViewArea` and `SectionRenderDispatcher$RenderSection` so caller semantics rather than only public signatures were captured.

Inspection runs:

- run `33651565582` / #1 — SUCCESS; artifact `9855000802`;
- follow-up run `33651866404` / #3 — SUCCESS; artifact `9855122419`, digest `sha256:73ee439ca3c28ff43d946b8185b69a1d99453df27e2f0df0bee3a4ca18e63e6e`.

Temporary workflow source head for the follow-up: `f8dad8c39205cd9555f0e00c1f2e8f308970573b`.

## Exact findings

### Active render distance

`Options.getEffectiveRenderDistance()` is the authoritative effective client radius used by the renderer. It normally returns `renderDistance().get()`. When the client's level tick-rate manager is not running normally, the bytecode returns `min(configuredRenderDistance, 2)`. `Camera.update(...)` also consumes `getEffectiveRenderDistance()` and multiplies it by 16 for the camera's base far-depth calculation. `LevelRenderer` constructs `ViewArea` using `ClientLevel.getMinSectionY()`, `getMaxSectionY()` and `Options.getEffectiveRenderDistance()`.

P4.1 therefore uses `minecraft.options.getEffectiveRenderDistance()` as the live horizontal scene-radius input, never a hard-coded copy of the configured option.

### Vertical range

`LevelHeightAccessor` exposes exact public helpers:

- `getMinY()` / `getHeight()`;
- `getMinSectionY()` / `getMaxSectionY()`;
- `getSectionsCount()`;
- `getSectionIndex(...)` and `getSectionYFromSectionIndex(...)`.

`LevelRenderer` passes the level's exact min/max section Y into `ViewArea`, so P4.1 must derive vertical slot count from the active `ClientLevel`, not assume an Overworld-specific height.

### Loaded chunk / empty-section delta tracking

`ClientChunkCache` exposes:

- `addedEmptySections()`;
- `removedEmptySections()`;
- `addedLoadedChunks()`;
- `removedLoadedChunks()`;
- `flipUpdateTrackingSets()`;
- `onSectionEmptinessChanged(int sectionX, int sectionY, int sectionZ, boolean hasOnlyAir)`.

`ClientChunkCache$Storage` keeps double-buffered `LongOpenHashSet[]` arrays for all four delta classes plus `updatingSetsIndex`.

Exact storage semantics:

- `onChunkAdded(LevelChunk)` records the packed chunk in `addedLoadedChunks[updatingIndex]`, then scans that chunk's section array once and records every `LevelChunkSection.hasOnlyAir()==true` section in `addedEmptySections[updatingIndex]`;
- `onChunkRemoved(LevelChunk)` records the packed chunk in `removedLoadedChunks[updatingIndex]` and records every vertical section coordinate in `removedEmptySections[updatingIndex]`;
- `onSectionEmptinessChanged(x,y,z,true)` adds the section to `addedEmptySections[updatingIndex]`;
- `onSectionEmptinessChanged(x,y,z,false)` adds it to `removedEmptySections[updatingIndex]`;
- `LevelChunk.setBlockState(...)` invokes the chunk source's emptiness callback with the exact result of `LevelChunkSection.hasOnlyAir()` after an emptiness transition, proving the boolean meaning;
- all these updates are ignored outside the chunk cache's active range.

`LevelExtractor` copies the four current delta-set references into `ChunkLoadingRenderState` and then calls `ClientChunkCache.flipUpdateTrackingSets()`. Therefore Mojang already maintains a bounded incremental lifecycle stream; P4.1 must not reconstruct membership by scanning every loaded section on camera-only frames.

For ownership safety P4.1 will not mutate or clear Mojang's sets. The first implementation will use existing proven Obsidian chunk-load/unload callbacks plus an exact `ClientChunkCache.onSectionEmptinessChanged` observation hook, with a one-time per-loaded-column section scan on chunk admission. This avoids racing/stealing Mojang's render-state delta ownership while retaining the same exact event semantics.

### Camera/frustum construction

`Camera.update(DeltaTracker)` performs the exact culling setup in this order:

1. obtain/update the camera view rotation matrix via `getViewRotationMatrix(...)`;
2. create the culling projection matrix via `createProjectionMatrixForCulling()`;
3. call `prepareCullFrustum(viewRotationMatrix, cullingProjectionMatrix, cameraPosition)`.

`prepareCullFrustum(...)` constructs `new Frustum(viewRotationMatrix, cullingProjectionMatrix)` unless a deliberately captured debug frustum is active, then calls `Frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z)`.

`Frustum.calculateFrustum(view, projection)` performs `projection.mul(view, internalMatrix)`, then calls `FrustumIntersection.set(internalMatrix)`. Thus the authoritative clip/frustum matrix is **projection * viewRotation** in JOML convention.

`CameraRenderState`, copied from the live `Camera` during render-state extraction, exposes exact world render state including:

- `Vec3 pos`;
- `Matrix4f projectionMatrix`;
- `Matrix4f viewRotationMatrix`;
- `Frustum cullFrustum`;
- camera orientation/rotation flags.

The existing Obsidian Phase 2 seam remains valid: `GameRenderer.gameRenderState().levelRenderState.cameraRenderState` is the authoritative extracted world camera state for shadow validation.

### Frustum/AABB classification semantics

`Frustum.isVisible(AABB)` calls its private cube/AABB test after subtracting stored camera X/Y/Z from the world-space AABB and converting the six relative bounds to float. It treats JOML `FrustumIntersection.intersectAab(...)` results `-2` (inside) and `-1` (intersecting) as visible; only a definite outside-plane result is culled.

Therefore the independent CPU oracle for P4.1 can directly call the exact Minecraft `cullFrustum.isVisible(sectionAabb)` while the GPU implementation mirrors the same conservative camera-relative float AABB/plane test. Border/intersection cases remain visible; no CPU oracle should tighten Minecraft's own conservative rule.

## P4.1 implementation consequences

Frozen implementation direction from these exact findings:

1. active horizontal radius = `Options.getEffectiveRenderDistance()`;
2. vertical range = active level `getMinSectionY()/getMaxSectionY()/getSectionsCount()`;
3. chunk membership is event-driven; existing Obsidian `ClientLevel.onChunkLoaded/unload` hooks seed/remove columns;
4. add an observation-only mixin on `ClientChunkCache.onSectionEmptinessChanged(IIIZ)V` to update a P4.1 section membership queue/database; do not mutate Mojang's double-buffer sets;
5. a chunk-load event may perform one bounded section-array scan for that column using the non-loading `ClientChunkCache.getChunk(... FULL, false)` path already proven by A-0054;
6. camera-only frames update camera/frustum constants and dispatch GPU visibility; they do not scan all chunks/sections in Java;
7. CPU oracle uses `CameraRenderState.cullFrustum.isVisible(AABB)` directly;
8. GPU arithmetic is camera-relative float, matching Minecraft's frustum classifier representation;
9. GPU plane extraction uses the exact `projection * viewRotation` matrix and conservative AABB test; no guessed matrix order;
10. no P3.10 draw/suppression behavior changes in P4.1.

## Next action

Remove the temporary inspection workflow. Then implement the bounded persistent section scene and scalable shadow GPU visibility/readback path under A-0203/A-0204. Any deviation from these exact seams requires a new immutable attempt before runtime measurement.
