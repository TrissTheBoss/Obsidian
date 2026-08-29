# A-0180 — Phase 3 P3.9 dev21 caller-specific tracer implementation/package

Date: 2026-08-30
Status: **SUCCESS for implementation/package / short reference runtime required**
Version: `0.3.0-phase3-dev21`
Parent contract: A-0159
Caller contract: A-0177
Static caller result: A-0178
Tracer contract: A-0179

## Objective

Package the bounded caller-specific diagnostic required by A-0179 without changing the existing missing-provenance fallback, any A-0159 threshold, or any production render path.

## Implementation

Exact implementation commit:

- commit `28d14510991eddd05fb68eed2cb806b63a4ff0eb`;
- tree `31ba14ce0256090a0055236126afe649e5f3bd84`.

Changes are diagnostic/version scope only:

- added `PartialRemeshSingleSectionCallerDiagnostics` with fixed primitive counters, bounded caller scope stack, per-drain deltas and first fallback fixture;
- added `ClientChunkCacheDiagnosticMixin` around exact 26.2 `onLightUpdate(LightLayer, SectionPos)`;
- added `ClientPacketListenerDiagnosticMixin` around exact 26.2 `handleChunksBiomes(ClientboundChunksBiomesPacket)`;
- existing `LevelExtractorMixin` reports caller identity only after lifecycle relevance is accepted and outer origin is already `SINGLE_SECTION`;
- existing provenance diagnostics capture caller deltas alongside the same lifecycle drain and report caller classification after the already-counted provenance fallback;
- version/banner updated to dev21;
- mixin list updated for the two diagnostic scopes.

No changes were made to `AsyncMultiSectionSceneProbe` admission/fallback logic, partial request semantics, meshing, workers, staging, arena, upload/install/draw, shaders/pipelines, P3.7 reference semantics, or any A-0159 threshold. Missing/empty provenance still falls back exactly as dev20.

## Hosted build authority

Normal PR Build:

- workflow run `33278257773`;
- Java 25 / Gradle 9.5.1 job `99168745994`;
- Build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release: SKIPPED;
- artifact id `9722180208`;
- artifact wrapper `obsidian-7df0b6a0fdd47b3acfbe4f59516a8e1cee080515`;
- wrapper size `753000` bytes;
- wrapper digest `sha256:7b7e63908025cc5d8609726aacaa5e70996f4faf728831568941e05287d2bc18`.

The hosted build validates both new Mixin descriptors against the exact Minecraft 26.2 mappings.

## Canonical runtime artifact

`Obsidian-0.3.0-phase3-dev21.jar`

- size: `518959` bytes;
- SHA-256: `9b0e103de085c3f35fac3a3c245f8577c362df1e7c2925bb21c96c107fd7621a`.

Sources:

`Obsidian-0.3.0-phase3-dev21-sources.jar`

- size: `267193` bytes;
- SHA-256: `50bba4884c7c3ff06038be709bfca37c79336710d3d476b5b049230a57b58269`.

Artifact inspection confirms the embedded mod version is `0.3.0-phase3-dev21` and the runtime JAR contains:

- `ClientChunkCacheDiagnosticMixin.class`;
- `ClientPacketListenerDiagnosticMixin.class`;
- `PartialRemeshSingleSectionCallerDiagnostics.class`.

## Required runtime

One short diagnostic run only:

1. wait for settled READY and P3.9 arm;
2. about 6 ordinary safe-interior edits with READY recovery;
3. about 3 safe-interior Y-slice-boundary edits;
4. one quick same-section 3–5 edit burst;
5. F3+T and READY;
6. one real scene recenter and READY;
7. normal exit.

Decisive new closure lines:

- `Phase 3 dev21 P3.9 final single-section caller totals`;
- `Phase 3 dev21 P3.9 first single-section caller fixtures`;
- `Phase 3 dev21 P3.9 final provenance-caller correlation`.

The existing dev20 outer-origin correlation and dev21 provenance diagnostics must also remain coherent.

## Decision after runtime

If every provenance fallback remains outer `SINGLE_SECTION` and every caller correlation is `LIGHT_UPDATE` only, freeze a separate caller-specific correction contract requiring exact pending-section identity and all A-0177 fail-closed conditions before changing behavior.

Any `BIOME_PACKET`, `OTHER_SINGLE_SECTION`, mixed, unavailable, cross-thread-scope, or scope-overflow evidence blocks that correction and requires redesign/cause capture instead.

## Promotion

No promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.
