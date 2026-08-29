# Obsidian Current State

Last updated: 2026-08-30

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 — border/halo correctness: COMPLETE through `0.3.0-phase3-dev12.1`.** Promotion merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.
- **P3.6 — T-junction policy: COMPLETE through `0.3.0-phase3-dev13`.** Promotion merge `602c53abb76dff0e27cf314abc308ff5b7ac0cae`.
- **P3.7 — differential correctness: COMPLETE through `0.3.0-phase3-dev14`.** PR #50 merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b`.
- **P3.8 — meshing benchmark: COMPLETE through `0.3.0-phase3-dev15`.** PR #52 merge `49385aedff74f2382fcd9a9bb44e59cf559e63c4`; canonical dev15 SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.
- Synchronized P3.8-complete main: `169274b468d2a278d39043938efff19844bec9ba`, Build `33272073819` SUCCESS.

## Active milestone — P3.9 partial remeshing (EXPERIMENTAL)

- Active branch: `phase3/partial-remeshing`.
- Frozen parent contract: **A-0159** — shadow-only four fixed Y slices, exact block-local dirty provenance, mandatory full fallback, matched production full-section control, permanent P3.7 correctness, and pre-frozen benefit/complexity thresholds. **Every A-0159 threshold remains unchanged.**
- Production full-section invalidation/capture/worker mesh/upload/install/draw remains authoritative. Partial-remesh output is never uploaded or drawn. No partial GPU patch exists.
- Dev16 A-0161 failed one shadow correctness fixture; dev17 corrected the two proven proof defects and A-0164 closed correctness `19/19` but lacked evidence volume.
- Dev18 pending coalescing closed the pending-state loss. A-0167/A-0168 remained PARTIAL; dominant avoidable loss moved to provenance, with no correctness failures.
- A-0169/A-0170 introduced dev19 provenance diagnostics.
- **A-0171 dev19 runtime: diagnostic SUCCESS.** All `40/40` provenance fallbacks were missing/empty; off-render-thread `0`, overflow `0`, other `0`; first fixture was `SCANNING`, center known, pending exact episode present, drain `0/0`. Do not rerun dev19.
- **A-0172 dev20 causal-correlation plan: FROZEN.**
- **A-0173 exact Minecraft 26.2 dirty call shape: SUCCESS.** `ClientLevel.setBlocksDirty` -> `LevelExtractor.setBlockDirty`; one render-relevant exact block dirty synchronously emits `27` section-dirty calls. Common `54` batches are two exact callbacks; the later next-frame `+1` is distinct.
- **A-0174 dev20 outer section-dirty tracer: FROZEN.**
- **A-0175 dev20 package: SUCCESS.** Canonical dev20 SHA-256 `690b24cd6bb34e47b3b85159eda365da3e1f76f95d41b51f0b1298fc093ed2f3`.
- **A-0176 dev20 runtime: diagnostic SUCCESS.** Provenance fallback `21/21` missing/empty. All `21/21` were outer `SINGLE_SECTION` only; exact-block/range/neighbor/mixed/no-relevant/unclassified involvement all `0`; accounting coherent. First event `(58,4,-4)`, `dirtyFromPlayer=false`, one lifecycle-relevant event, `SCANNING`, center known, pending exact episode present. P3.5/P3.6/P3.7/P3.8 and lifetime gates remained green; P3.7 `194/194`, zero real mismatches; clean exit `0`. Do not rerun dev20.
- **A-0177 dev21 exact public-single-section caller contract: FROZEN.** Generic `SINGLE_SECTION` preservation is forbidden until exact caller identity is known.
- **A-0178 exact Minecraft 26.2 caller result: SUCCESS.** There are two external callers of public `LevelExtractor.setSectionDirty(III)`: `ClientChunkCache.onLightUpdate(LightLayer, SectionPos)` emits one supplied section; `ClientPacketListener.handleChunksBiomes(ClientboundChunksBiomesPacket)` performs a broad 3x3 chunk-neighborhood across the full section-Y range. Therefore generic single-section preservation is unsafe. Exact caller inspection runs `33277863920` and `33277944526` succeeded.
- **A-0179 dev21 caller-specific tracer contract: FROZEN.** Add bounded `LIGHT_UPDATE`, `BIOME_PACKET`, and `OTHER_SINGLE_SECTION` correlation only; fallback behavior unchanged.
- **A-0180 dev21 implementation/package: SUCCESS; short reference runtime required.**

## Dev21 canonical runtime handoff

Implementation/package authority:

- exact implementation head `28d14510991eddd05fb68eed2cb806b63a4ff0eb`
- tree `31ba14ce0256090a0055236126afe649e5f3bd84`
- hosted Build `33278257773`: Java 25 / Gradle 9.5.1 SUCCESS; Build SUCCESS; artifact upload SUCCESS; release SKIPPED
- artifact id `9722180208`
- wrapper `obsidian-7df0b6a0fdd47b3acfbe4f59516a8e1cee080515`, size `753000` bytes, digest `sha256:7b7e63908025cc5d8609726aacaa5e70996f4faf728831568941e05287d2bc18`
- canonical JAR `Obsidian-0.3.0-phase3-dev21.jar`
- size `518959` bytes
- SHA-256 `9b0e103de085c3f35fac3a3c245f8577c362df1e7c2925bb21c96c107fd7621a`
- sources JAR `267193` bytes; SHA-256 `50bba4884c7c3ff06038be709bfca37c79336710d3d476b5b049230a57b58269`

Dev21 scope/safety:

- `AsyncMultiSectionSceneProbe.java` admission/fallback logic unchanged;
- missing/empty provenance still records `FALLBACK_PROVENANCE` and clears pending exactly as dev20;
- no A-0159 threshold changes;
- no request/slice/dependency/mesher/worker/upload/staging/arena/shader/pipeline/native Vulkan changes;
- no P3.7 oracle changes;
- `LevelExtractorMixin` reports caller identity only after `SectionLifecycleEvents` accepts the event and outer origin is already `SINGLE_SECTION`;
- caller scopes are exact Minecraft 26.2 `ClientChunkCache.onLightUpdate` and `ClientPacketListener.handleChunksBiomes` methods;
- diagnostics retain fixed primitive counters, bounded scope stack, per-drain deltas, and one first fixture only;
- no partial GPU patching.

Draft P3.9 PR: **#53**, keep DRAFT / DO NOT MERGE.

## Exact next action

Run the exact canonical dev21 JAR. This remains a **short diagnostic**, not the 32-episode A-0159 closure run:

1. wait for settled READY and P3.9 arm;
2. perform about 6 safe-interior ordinary edits with READY recovery;
3. perform about 3 safe-interior Y-slice-boundary edits;
4. perform one quick same-section 3-5 edit burst;
5. perform F3+T and recover READY;
6. cause one real scene recenter and recover READY;
7. quit normally and return the complete log.

Decisive lines:

- `Phase 3 dev21 P3.9 final provenance diagnostics`;
- existing `Phase 3 dev20 P3.9 final provenance-origin correlation` (outer tracer retained unchanged);
- `Phase 3 dev21 P3.9 final single-section caller totals`;
- `Phase 3 dev21 P3.9 first single-section caller fixtures`;
- `Phase 3 dev21 P3.9 final provenance-caller correlation`.

Decision rule after runtime:

- if every provenance fallback remains outer `SINGLE_SECTION` and every caller correlation is `LIGHT_UPDATE` only, freeze a separate correction contract requiring exact pending-section identity plus all A-0177 fail-closed conditions before changing behavior;
- any `BIOME_PACKET`, `OTHER_SINGLE_SECTION`, mixed, unavailable, scope cross-thread, or scope-overflow evidence blocks that correction and requires redesign/cause capture instead.

## Public release / handoff policy

- Keep the existing public checkpoint; internal milestone commits remain `[no-release]`.
- Runtime handoff is always the direct versioned `.jar`, never an Actions ZIP wrapper.
