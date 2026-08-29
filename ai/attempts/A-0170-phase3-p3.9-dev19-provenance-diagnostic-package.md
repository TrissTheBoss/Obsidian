# A-0170 — Phase 3 P3.9 dev19 provenance diagnostic implementation/package

Date: 2026-08-29
Status: **SUCCESS for implementation/package; short diagnostic runtime required**
Version: `0.3.0-phase3-dev19`
Parent contract: A-0159
Diagnostic contract: A-0169
Trigger runtime: A-0168

## Purpose

Implement the bounded provenance diagnostic frozen in A-0169 without modifying P3.9 admission policy, four-slice semantics, any A-0159 threshold, production full-section rendering, or GPU behavior.

## Implementation

Exact implementation commit:

- head `510ce9b7986c84b5c2a951c681f3f1783b99518c`
- tree `39542c3972772df5984b60d5f91ce8835ffe1b37`
- message `[no-release] Implement P3.9 dev19 provenance diagnostics`

The implementation is intentionally observational:

- `AsyncMultiSectionSceneProbe.java` unchanged;
- `PartialRemeshDirtyProvenance.java` unchanged;
- `PartialRemeshExperimentTelemetry.java` unchanged;
- `FrameCoordinator.java` unchanged;
- therefore A-0159 `thresholdsPassed()` and the actual provenance/admission state machine are byte-for-byte unchanged from dev18.

Dev19 adds:

- `PartialRemeshProvenanceDiagnostics`: fixed primitive counters/first fixture only;
- `AsyncMultiSectionSceneProbeDiagnosticMixin`: observes pre-classification scene state/center and bounded pending state, without modifying target control flow;
- `PartialRemeshDirtyProvenanceDiagnosticMixin`: observes the already-produced drain at `drainInto` TAIL;
- `PartialRemeshExperimentTelemetryDiagnosticMixin`: resets diagnostics when the experiment begins and observes the already-counted `FALLBACK_PROVENANCE` at method TAIL;
- `FrameCoordinatorDiagnosticMixin`: emits the bounded final dev19 provenance line at `FrameCoordinator.close` HEAD;
- mixin registration plus version/bootstrap wording only.

The diagnostic distinguishes:

- `missingOrEmpty`: drain count zero with no fallback flags;
- `offRenderThread`: existing `FLAG_OFF_RENDER_THREAD` present;
- `overflowFlag`: existing `FLAG_OVERFLOW` present;
- summed existing `overflowEvents`;
- `other`: high-level provenance fallback with none of those subreasons (for example a coalesce arithmetic/validation catch).

Subreason counters may overlap while the existing high-level provenance counter remains exactly one fallback. Dev19 does not call the high-level counter itself; it observes it after the existing telemetry method returns.

Exactly one primitive first fixture is retained: fallback index, drain count, fallback flags, overflow events, scene state ordinal/name, center-known, pending-episode-present, and context/pending-probe availability. No mutable world/snapshot/block object is retained.

## Diff safety

Exact A-0169 -> implementation diff contains only:

- `gradle.properties` version identity;
- `ObsidianBootstrap.java` diagnostic wording;
- four new diagnostic mixins;
- one new diagnostic collector;
- `obsidian.mixins.json` registration.

No production renderer, scene admission, worker, mesher, upload, arena, shader, pipeline, or threshold owner is edited.

## Hosted CI authority

PR Build `33276352301` on exact head `510ce9b7986c84b5c2a951c681f3f1783b99518c`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release: SKIPPED as required for the draft `[no-release]` workstream.

Artifact:

- id `9721614518`;
- wrapper name `obsidian-2c0fb9fc57eadbff121257171c6ab827063f6823`;
- wrapper size `728,539` bytes;
- wrapper digest `sha256:f26b3c68cf2637ee88761b7e93a92620232e5cf199673f1df0f5ca8c0b4d04e2`.

Canonical direct runtime JAR extracted from that artifact:

- `Obsidian-0.3.0-phase3-dev19.jar`
- size `503,422` bytes
- SHA-256 `3af4c0773627f1a74bc3c5f25746885b2051f535c68a079157aa9b549d747637`

Sources JAR:

- `Obsidian-0.3.0-phase3-dev19-sources.jar`
- size `257,321` bytes
- SHA-256 `e826833e142c0a10de63a6cd89a118c64ae3dcd88a5ee801d61e98573f0bbe94`

The canonical runtime JAR was additionally inspected and contains all four diagnostic mixin classes, `PartialRemeshProvenanceDiagnostics.class`, and `obsidian.mixins.json`.

## Runtime requirement

This is a diagnostic runtime, not another full 32-episode A-0159 attempt. Use the exact canonical dev19 JAR and:

1. wait for the P3.9 windows to arm;
2. perform about 8 safe-interior ordinary one-slice edits with READY recovery;
3. perform about 4 safe-interior two-slice Y-boundary edits;
4. perform one quick same-section 3-5 edit burst;
5. perform F3+T and recover READY;
6. cause one real scene recenter and recover READY;
7. quit normally and return the complete log.

The decisive new line is `Phase 3 dev19 P3.9 final provenance diagnostics`. It will identify whether A-0168's 80 provenance fallbacks were primarily missing/empty provenance, off-render-thread provenance, bounded overflow, or another existing provenance path.

## Promotion

No promotion is authorized by this package checkpoint. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked. A-0159 thresholds remain unchanged.