# A-0178 — Phase 3 P3.9 dev21 exact public single-section caller result

Date: 2026-08-30
Status: **SUCCESS for A-0177 Stage 1 / caller-specific runtime correlation still required before correction**
Target version: `0.3.0-phase3-dev21`
Parent contract: A-0159
Caller contract: A-0177
Trigger runtime: A-0176

## Purpose

Identify every exact Minecraft 26.2 caller of public `LevelExtractor.setSectionDirty(III)` after A-0176 proved that all `21/21` missing/empty provenance fallbacks were `SINGLE_SECTION` only.

## Hosted inspection authority

Exact dependency/toolchain path:

- Java 25;
- Gradle 9.5.1;
- Fabric Loom 1.17.20;
- exact resolved Minecraft 26.2 client JAR `/home/runner/.gradle/caches/fabric-loom/26.2/minecraft-client-only.jar`;
- `gradle compileClientJava --no-daemon` SUCCESS;
- initial caller run `33277863920`, job `99167679002`, SUCCESS;
- expanded caller-body run `33277944526`, job `99167904069`, SUCCESS.

## Exact caller enumeration

Only four client classes contain the symbol in relevant bytecode:

- `net.minecraft.client.multiplayer.ClientChunkCache`;
- `net.minecraft.client.multiplayer.ClientLevel`;
- `net.minecraft.client.multiplayer.ClientPacketListener`;
- `net.minecraft.client.renderer.extract.LevelExtractor`.

Exactly **two external classes directly invoke public `LevelExtractor.setSectionDirty(III)`**.

### 1. `ClientChunkCache.onLightUpdate(LightLayer, SectionPos)`

The method body is a direct one-section call:

1. `Minecraft.getInstance().levelExtractor`;
2. read supplied `SectionPos.x()`;
3. read supplied `SectionPos.y()`;
4. read supplied `SectionPos.z()`;
5. invoke `LevelExtractor.setSectionDirty(III)`;
6. return.

This is a light-update invalidation with exact section identity and `dirtyFromPlayer=false` through the public sink.

The exact client also shows `ClientPacketListener.handleLevelChunkWithLight(...)` queues light update work through `ClientLevel.queueLightUpdate(...)`, so light invalidations may legitimately execute later than the packet/block operation that initiated lighting work.

### 2. `ClientPacketListener.handleChunksBiomes(ClientboundChunksBiomesPacket)`

The method runs on the client packet processor thread via `PacketUtils.ensureRunningOnSameThread` and:

1. applies biome data;
2. calls `ClientLevel.onChunkLoaded` for each supplied biome chunk;
3. for each biome chunk, loops `dx=-1..1` and `dz=-1..1`;
4. loops every section Y from `ClientLevel.getMinSectionY()` through `getMaxSectionY()`;
5. invokes `Minecraft.levelExtractor.setSectionDirty(chunkX+dx, sectionY, chunkZ+dz)` for every loop element.

This is a broad biome/chunk invalidation, not a localized one-section derivative.

### Internal `LevelExtractor` calls

`LevelExtractor.setBlocksDirty(...)` and `setSectionRangeDirty(...)` call public `setSectionDirty(III)` internally. Dev20's outermost-scope tracer already classifies these as `EXACT_BLOCK`, `BLOCK_RANGE`, `NEIGHBOR_RANGE`, or `SECTION_RANGE`, so they cannot account for A-0176's `SINGLE_SECTION`-only fallback bucket.

Public `setSectionDirty(III)` delegates directly to private `setSectionDirty(IIIZ)` with `dirtyFromPlayer=false`.

## Correlation with A-0176

A-0176's first missing-provenance fixture had:

- exactly one lifecycle-relevant section-dirty event;
- origin `SINGLE_SECTION`;
- section `(58,4,-4)`;
- `dirtyFromPlayer=false`;
- pending exact episode present;
- scene state `SCANNING`;
- exact-provenance drain count/flags `0/0`.

That event shape is structurally consistent with `ClientChunkCache.onLightUpdate` and inconsistent with a full `handleChunksBiomes` sweep for a normally overlapping tracked Y domain. However, dev20 retained only generic `SINGLE_SECTION` origin, so it does not prove that **all 21** fallback drains were light-update calls.

## Safety conclusion

A generic preservation rule for all public `SINGLE_SECTION` events remains forbidden. `handleChunksBiomes` is an independent semantic invalidation and must never be silently folded into a pending localized edit merely because timing/section overlap happens to match.

A-0177 therefore requires one final bounded caller-specific runtime tracer that distinguishes at least:

- `LIGHT_UPDATE` (`ClientChunkCache.onLightUpdate`);
- `BIOME_PACKET` (`ClientPacketListener.handleChunksBiomes`);
- any residual generic/unknown public single-section path.

No admission correction is authorized yet.

## Promotion

No P3.9 promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.
