# A-0163 - P3.9 dev17 diagnostic/correction implementation and package

**Date:** 2026-08-29  
**Milestone:** Phase 3 / P3.9 partial-remeshing experiment  
**Version:** `0.3.0-phase3-dev17`  
**Frozen correction contract:** A-0162  
**Result:** `SUCCESS` for implementation/package; reference runtime required

## Objective

Correct only the dev16 shadow correctness defects proven by A-0161 source review, add the bounded diagnostics required to localize future failures/fallbacks, keep every A-0159 threshold and admission rule unchanged, and produce an exact hosted-CI runtime package.

## Proven corrections

### 1. Restore permanent P3.7 independent-reference semantics

Dev16 incorrectly required bidirectional equality between `BinarySectionVisibility` and `ReferenceFaceMesh` for every selected cell/direction.

That assertion was stronger than the permanent P3.7 oracle. `ReferenceFaceMesh` deliberately contains only the conservative supported-full-cube subset whose neighbor is definitely air; unsupported neighbors suppress oracle emission. P3.7 instead requires:

- every independent reference face to be visible; and
- every optimized/canonical source mapping to have an independent reference face.

Dev17 applies exactly those two checks on selected slices. A visibility bit outside the conservative reference subset is no longer itself a failure. A new bounded `FAILURE_OPTIMIZED_WITHOUT_REFERENCE` code distinguishes the reverse-direction violation.

This restores, rather than weakens, the already-accepted P3.7 semantics.

### 2. Correct baked/Minecraft direction encoding before merged-identity comparison

During final source audit before packaging, a second dev16 shadow-only defect was proven.

`SectionBakedQuadSnapshot` stores Minecraft `Direction.ordinal()` while `BinarySectionVisibility` uses Obsidian's explicit `WEST/EAST/DOWN/UP/NORTH/SOUTH = 0..5` codes. Dev16 compared those raw integer domains directly in the selected merged-identity proof.

Permanent P3.7 already translates Minecraft direction ordinals to the binary direction codes before comparing them. Dev17 now uses the same translation and self-tests all six directions.

This correction remains shadow-only; production greedy identities and rendered geometry are unchanged.

## Diagnostic additions

`PartialRemeshExperimentTelemetry` now retains bounded primitive evidence for:

- fallback global lifecycle count;
- fallback provenance count;
- fallback multi-section count;
- fallback halo/XZ-boundary count;
- fallback all-slices count;
- fallback pending-episode count;
- fallback not-LIVE count;
- combined fallback reason mask;
- exact fallback accounting coherence (`total == sum(per-reason)`);
- first failed completed episode only: section XYZ, episode id, slice mask, edit count, failure code/name/index and deterministic flag.

`recordFallback` fails closed unless it receives exactly one known fallback reason bit. Existing fixed-capacity percentile sample retention and observed/retained/overflow accounting remain unchanged.

The final `FrameCoordinator` closure emits all of the above fields. No unbounded per-edit history or worker hot-path logging was added.

## Explicitly unchanged from A-0159

- exactly four fixed Y slices;
- one-row vertical dependency expansion;
- `ClientLevel.setBlocksDirty` exact block-local provenance surface;
- all localization/fallback admission rules;
- evidence thresholds and benefit/complexity thresholds;
- production full-section render capture and worker rebuild granularity;
- production greedy eligibility, merge key and transport policy;
- GPU upload/install/draw ownership and rendered geometry;
- shaders/pipelines/atlas/lightmap semantics;
- worker count/priority/backpressure;
- staging, arena and deferred lifetime rules;
- permanent P3.7 oracle and all inherited gates.

No partial GPU patching exists in dev17.

## Implementation/build history

A-0162 was frozen before implementation.

First temporary implementation helper:

- run `33274129416`;
- patch application SUCCESS;
- Java 25 / Gradle 9.5.1 exact project build SUCCESS;
- helper files removed;
- clean commit `6ab549ece4fc3163dad9698ae8860e498c4c7d5f`.

A connector-authored same-tree validation commit `57bd24c1a4b00975dfd733f596eb511b7150e463` was created, but it was deliberately superseded before package authority after the final source audit found the raw direction-encoding mismatch.

Second temporary direction-correction helper:

- run `33274228032`;
- proven direction correction applied SUCCESS;
- Java 25 / Gradle 9.5.1 exact project build SUCCESS;
- helper files removed;
- clean correction commit `e468085366bb4a08043e7c006b466287692dd607`.

Exact connector-authored clean package-validation head:

- commit `bce641ff08353035d6012fb5c5f5d8c06918da41`;
- tree `1d6c9a17f089c25f6d70ad9706ba626b2c98eae4`;
- message `[no-release] Validate corrected P3.9 dev17 package head`;
- hosted Build `33274284466`;
- Java 25 / Gradle 9.5.1 SUCCESS;
- Build SUCCESS;
- artifact upload SUCCESS;
- versioned release SKIPPED.

## Canonical dev17 package

Workflow artifact:

- artifact id `9721025599`;
- wrapper name `obsidian-7fb7eed589e50b3cc912759b93db33ef199b15e5`;
- wrapper size `716,499` bytes;
- wrapper digest `sha256:0005953b5ca1cef9627c033314e26e61399804d2de29ed5c4a9fda94bcfeeecd`;
- workflow head SHA `bce641ff08353035d6012fb5c5f5d8c06918da41`.

Direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev17.jar`;
- size **495,236 bytes**;
- SHA-256 **`4f8d58251f29742afbc67d95e33a884ea72849fe099a225b154af19616ef7904`**.

Sources JAR:

- `Obsidian-0.3.0-phase3-dev17-sources.jar`;
- size `251,825` bytes;
- SHA-256 `9063704e8b44aa0f6fd8a90eac6bcd3d67a64cf7ddc892b5651e7b071fc3f9ab`.

## Runtime requirement

Dev17 is not promoted by compile/package evidence alone. Run one coherent reference session using this exact package and the unchanged A-0159 workload. The final log must determine:

- whether the dev16 correctness failure disappears under the restored permanent P3.7 semantics and corrected direction encoding;
- which exact fallback reasons dominate if localization volume remains low;
- whether a new first-failure fixture exists;
- whether sufficient valid episodes can close the frozen benefit/complexity gates.

If correctness becomes exact but evidence volume remains insufficient, do not change admission policy ad hoc: use dev17's per-reason counters to freeze the next correction under a new immutable attempt. If sufficient valid evidence misses CPU/upload thresholds, the fixed four-slice strategy is rejected/redesigned rather than retuned.

No visual verdict is required unless an unexpected visual change occurs because dev17 shadow output is never uploaded or rendered.