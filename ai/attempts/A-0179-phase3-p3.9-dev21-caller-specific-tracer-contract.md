# A-0179 — Phase 3 P3.9 dev21 caller-specific single-section tracer contract

Date: 2026-08-30
Status: **PLAN FROZEN**
Version: `0.3.0-phase3-dev21`
Parent contract: A-0159
Caller contract: A-0177
Static result: A-0178

## Purpose

Distinguish the two exact Minecraft 26.2 external callers of public `LevelExtractor.setSectionDirty(III)` during the missing/empty provenance fallback so a later correction can be caller-specific rather than weakening all `SINGLE_SECTION` handling.

## Exact caller classes

A-0178 proves:

- `ClientChunkCache.onLightUpdate(LightLayer, SectionPos)` -> one exact supplied section;
- `ClientPacketListener.handleChunksBiomes(ClientboundChunksBiomesPacket)` -> broad 3x3 chunk-neighborhood across the entire section-Y range.

## Diagnostic classifications

Add bounded primitive caller scope around those exact methods:

- `LIGHT_UPDATE`;
- `BIOME_PACKET`;
- `OTHER_SINGLE_SECTION` for a public single-section event without either caller scope.

The existing dev20 outer origin tracer remains intact and authoritative for `SINGLE_SECTION`; dev21 caller-specific classification is an additional diagnostic dimension only.

## Correlation rule

For each provenance fallback whose dev20 outer origin is `SINGLE_SECTION`, record the immediately preceding drain's caller-specific composition:

- light-update-only;
- biome-packet-only;
- other-only;
- mixed;
- unavailable.

Retain one first primitive fallback fixture containing only:

- caller mask;
- relevant event count;
- section XYZ for the first caller-specific event;
- provenance drain count/flags;
- scene-state ordinal;
- center-known;
- pending-exact-episode-present.

## Explicitly unchanged

- empty/missing provenance still falls back and clears pending exactly as dev20;
- no A-0159 threshold changes;
- no production renderer or lifecycle relevance changes;
- no mesher/worker/upload/GPU changes;
- no P3.7 oracle changes;
- no partial GPU patching;
- no unbounded histories, stack traces, reflection, world/object retention, or per-event heap histories.

## Required self-tests

- nested caller scope precedence is deterministic;
- caller masks accumulate correctly per drain;
- first fixture retention is stable;
- unavailable/other path remains fail-closed;
- existing dev20 origin/provenance self-tests remain green.

## Runtime

One short diagnostic run is sufficient:

1. wait for READY/arm;
2. perform about 6 ordinary safe-interior block edits with READY recovery;
3. perform about 3 Y-slice-boundary edits;
4. one short same-section burst;
5. F3+T and READY;
6. one real scene recenter and READY;
7. normal exit.

No 32-episode evidence run is required because behavior remains unchanged.

## Decision rule

- If all missing/empty `SINGLE_SECTION` fallbacks are `LIGHT_UPDATE` only, a later immutable correction contract may preserve a pending exact episode across a light update **only with exact pending-section identity match and all A-0177 fail-closed conditions**.
- If any fallback involves `BIOME_PACKET`, `OTHER_SINGLE_SECTION`, mixed, or unavailable caller identity, generic preservation is forbidden; redesign/capture the independent cause instead.

## Promotion

No promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.
