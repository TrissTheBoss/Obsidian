# A-0081 - Phase 2 dev6 exact Minecraft 26.2 lifecycle API inspection

Status: **SUCCESS / LIFECYCLE SEAMS GROUNDED / IMPLEMENTATION MAY PROCEED**

Date: 2026-08-21
Production branch: `phase2/section-lifecycle-rebuild`
Temporary inspection branch: `phase2/section-lifecycle-rebuild-inspect`
Temporary PR: #26, inspection-only; close without merge after this evidence is recorded.

Hosted exact-dependency evidence:

- lifecycle run `32487025079` - SUCCESS - artifact `9448266980`;
- targeted LevelExtractor/storage run `32487514059` - SUCCESS - artifact `9448449721`.

Both used the exact Loom-resolved Minecraft 26.2 client compile classpath under Java 25 / Gradle 9.5.1 and `javap -public/-c -p` bytecode inspection.

## 1. Central render-dirty seam is LevelExtractor

Minecraft 26.2 routes client render-section dirtiness through `net.minecraft.client.renderer.extract.LevelExtractor` and its `SectionUpdateTracker`.

`ClientLevel` public methods delegate directly:

- `sendBlockUpdated(pos, old, new, flags)` -> `LevelExtractor.blockChanged(pos, flags)`;
- `setBlocksDirty(pos, old, new)` -> `LevelExtractor.setBlockDirty(pos, old, new)`;
- `setSectionDirtyWithNeighbors(x,y,z)` -> `LevelExtractor.setSectionDirtyWithNeighbors(x,y,z)`;
- `setSectionRangeDirty(...)` -> `LevelExtractor.setSectionRangeDirty(...)`.

All of LevelExtractor's block/range/neighbor paths converge on private:

`setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean dirtyFromPlayer)`

which delegates to `SectionUpdateTracker.setDirty(...)`.

This private central sink is the narrowest exact P2.6 block/light/border dirty event seam. A mixin injection may observe it without replacing vanilla's dirty logic.

## 2. Exact block-neighbor propagation

`Level.setBlock(...)` calls `setBlocksDirty(pos, oldState, newState)` when the stored state changes, then may call `sendBlockUpdated(...)` depending update flags.

`LevelExtractor.setBlockDirty(pos, old, new)` first calls `ModelManager.requiresRender(old,new)`. When rendering differs it calls `setBlocksDirty(x,y,z,x,y,z)`.

`LevelExtractor.setBlocksDirty(minX,minY,minZ,maxX,maxY,maxZ)` expands the block bounds by one block on every axis before converting each coordinate to section coordinates and calling `setSectionDirty(...)`.

`LevelExtractor.blockChanged(pos, flags)` calls an internal `setBlockDirty(pos, dirtyFromPlayer)` that iterates `pos +/- 1` on X/Y/Z and converts those positions to section coordinates before the same sink.

Therefore vanilla already applies the conservative one-block-halo border rule needed by Obsidian: a boundary block change dirties every neighboring section whose one-block halo can include that changed coordinate. Dev6 must consume the resulting section dirty coordinates rather than reimplementing propagation.

## 3. Exact light dirty propagation

`ClientPacketListener.handleLightUpdatePacket(...)` uses `PacketUtils.ensureRunningOnSameThread(...)`, then queues light application through `ClientLevel.queueLightUpdate(...)`.

During `readSectionList(...)`, changed `DataLayer` values are queued into `LevelLightEngine.queueSectionData(...)`. When the packet requests render dirtiness, the handler calls `ClientLevel.setSectionDirtyWithNeighbors(chunkX, sectionY, chunkZ)`.

`LevelExtractor.setSectionDirtyWithNeighbors(x,y,z)` expands exactly to section range `[x-1..x+1]`, `[y-1..y+1]`, `[z-1..z+1]`, and each reaches the central `setSectionDirty(...)` sink.

`ClientChunkCache.onLightUpdate(LightLayer, SectionPos)` also calls `Minecraft.getInstance().levelExtractor.setSectionDirty(section coordinates)`.

Thus observing the LevelExtractor dirty sink captures both explicit neighbor-expanded light packet dirtiness and direct section light dirtiness.

## 4. Thread affinity

Client packet handlers use `PacketUtils.ensureRunningOnSameThread(...)` before applying world/light packet updates. The relevant ClientLevel/LevelExtractor render extraction dirtiness therefore runs on Minecraft's client main/render thread in this architecture.

Dev6 callbacks must still assert/render-thread-check at the renderer ownership boundary and must not retain mutable world objects.

## 5. Chunk load and unload seams

`ClientChunkCache.replaceWithPacketData(...)` updates or creates the `LevelChunk` and then calls `ClientLevel.onChunkLoaded(chunkPos)`.

`ClientChunkCache.Storage.replace(...)` calls `ClientLevel.unload(oldChunk)` before replacing an existing slot.

`ClientChunkCache.Storage.drop(...)` removes the chunk and calls `ClientLevel.unload(chunk)`.

`ClientLevel.unload(LevelChunk)` clears block entities, disables light for the chunk and stops entity ticking.

`ClientLevel.onChunkLoaded(ChunkPos)` invalidates tint-cache entries for the chunk and starts entity ticking.

Therefore narrow injections into public `ClientLevel.onChunkLoaded(ChunkPos)` and `ClientLevel.unload(LevelChunk)` are exact load/unload notification seams for the one-section P2.6 proof.

## 6. World replacement seam

`LevelExtractor.setLevel(ClientLevel)` stores the new level. When non-null it calls `allChanged()`; when null it resets renderer camera/tracker state. In both cases it sets `shouldResetLevelRenderData=true`.

Minecraft's `setLevel(ClientLevel)` also updates the level in client engines. P2.6 can observe `LevelExtractor.setLevel(...)` as the renderer-extraction world replacement/teardown seam without owning Minecraft's level lifecycle.

## 7. Resource/model reload seam

`ModelManager.reload(sharedState, preparationExecutor, barrier, applyExecutor)` asynchronously prepares models/atlases, crosses the preparation barrier, then finishes with `thenAcceptAsync(..., applyExecutor)`.

The final apply function installs the new `BlockStateModelSet`, `BlockModelSet`, `FluidStateModelSet`, item models and model groups.

Minecraft's successful resource-pack reload completion subsequently calls `LevelExtractor.allChanged()`, which rebuilds the `SectionUpdateTracker` and sets `shouldInvalidateCompiledGeometry=true`; the next extraction calls `LevelRenderer.invalidateCompiledGeometry(...)`.

For Obsidian, the safest explicit resource-generation signal is to observe completion of `ModelManager.reload(...)` (or the final model apply completion) and advance the tracked section generation/resource validity. Existing `SectionMaterialSnapshot.currentResourceEpoch()` remains the exact draw/capture resource-epoch guard.

A mixin on public `ModelManager.reload(...)` may attach a completion callback to its returned `CompletableFuture<Void>` using the supplied apply executor, avoiding any dependency on the private reload-state type.

## 8. Fabric API inventory result

The exact resolved Fabric API 0.158.0+26.2 classpath did not expose the older assumed `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents`, `ClientWorldEvents`, or old `ResourceManagerHelper`/`SimpleSynchronousResourceReloadListener` classes under those names.

Do not use older Fabric event-package memory. The vanilla LevelExtractor/ClientLevel/ModelManager seams above are exact, narrow and sufficient for dev6.

## 9. Chosen P2.6 hook architecture

Proceed with narrow version-grounded mixins that only **observe** vanilla lifecycle actions:

1. `LevelExtractorMixin`
   - inject at the central private `setSectionDirty(IIIZ)` sink to emit an Obsidian section-dirty signal after vanilla has already chosen exact affected section coordinates;
   - inject `setLevel(ClientLevel)` to emit world-change/teardown signal.

2. `ClientLevelMixin`
   - inject `onChunkLoaded(ChunkPos)`;
   - inject `unload(LevelChunk)`.

3. `ModelManagerMixin`
   - observe the public `reload(...)` returned future and signal resource/model reload only after it completes successfully on the apply executor.

These hooks must never cancel or alter vanilla behavior.

## 10. Renderer-side generation contract

The persistent one-section dev6 probe/record will:

- choose/install one real section after world entry;
- assign a monotonically increasing generation;
- accept dirty/load/reload/unload signals by coordinates/reason;
- capture/build only for the current generation;
- carry generation through upload/install;
- reject any stale generation before becoming live;
- atomically replace renderer-owned live geometry;
- completion-gate old geometry/indirect retirement;
- stop drawing immediately when invalidated/unloaded until a valid replacement is installed;
- preserve P2.5 immutable generalized capture semantics and pure mesh construction.

P2.7 still owns a persistent multi-section scene database. Phase 3 still owns asynchronous production worker meshing. Dev6's version identity must nevertheless be designed so later async work can reuse it.

## Result

Exact Minecraft 26.2 lifecycle/update/reload behavior is sufficiently grounded to implement the P2.6 one-section event-driven generation-safe proof without parallel dirty heuristics or broader renderer ownership.

This attempt is immutable once committed.
