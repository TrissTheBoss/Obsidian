# A-0175 — Phase 3 P3.9 dev20 section-dirty origin tracer implementation/package

Date: 2026-08-29
Status: **SUCCESS for implementation/package; short reference runtime required**
Version: `0.3.0-phase3-dev20`
Parent contract: A-0159
Investigation contract: A-0172
Call-shape result: A-0173
Tracer contract: A-0174

## Purpose

Package the A-0174 diagnostic-only caller-origin tracer without changing the existing P3.9 fallback/admission decision or any frozen A-0159 threshold.

## Exact implementation

- commit `ffbe60535607a242ef6b2c03c8c44066e69a63ac`
- tree `9131bd8d1edf5440ae8a52817b820b17866fa2e4`
- message `[no-release] Implement P3.9 dev20 section-dirty origin tracer`

Exact A-0174 -> implementation diff contains only:

- `gradle.properties`: dev20 version identity;
- `ObsidianBootstrap.java`: diagnostic banner wording;
- `AsyncMultiSectionSceneProbeDiagnosticMixin.java`: expose the already-drained lifecycle relevant-event count to diagnostics;
- `LevelExtractorMixin.java`: bounded outermost caller-scope classification and observation of only tracked-scene-relevant private section-dirty calls;
- `PartialRemeshProvenanceDiagnostics.java`: dev20 correlation plumbing/logging;
- new `PartialRemeshSectionDirtyOriginDiagnostics.java`: fixed primitive origin counters, per-drain deltas, first fixtures and deterministic self-test.

Not edited:

- `AsyncMultiSectionSceneProbe.java`;
- `PartialRemeshDirtyProvenance.java`;
- `PartialRemeshExperimentTelemetry.java`;
- `FrameCoordinator.java`;
- any worker, mesher, upload, arena, shader, pipeline or native Vulkan implementation;
- any A-0159 threshold expression.

The existing behavior remains: an empty provenance drain still records `FALLBACK_PROVENANCE` and clears the pending episode. Dev20 observes that decision only after it is already made.

## Origin tracer semantics

Tracked-scene-relevant private `LevelExtractor.setSectionDirty(IIIZ)` calls are classified by outermost recognized scope:

- `EXACT_BLOCK`;
- `BLOCK_RANGE`;
- `NEIGHBOR_RANGE`;
- `SECTION_RANGE`;
- `SINGLE_SECTION`;
- `UNCLASSIFIED`.

Nested helper calls inherit the outermost scope, so `setBlockDirty -> setBlocksDirty -> setSectionDirty` remains `EXACT_BLOCK` and `setSectionDirtyWithNeighbors -> setSectionRangeDirty -> setSectionDirty` remains `NEIGHBOR_RANGE`.

The existing `SectionLifecycleEvents.sectionDirty` call runs first. The origin tracer records the event only when the lifecycle sequence advances exactly once, so events outside the active tracked dependency domain are ignored.

All retained state is fixed primitive state. There are no stack traces, unbounded maps/lists/queues, retained Minecraft objects, or per-event heap history.

## Hosted CI authority

PR Build `33277303655` on exact implementation head `ffbe60535607a242ef6b2c03c8c44066e69a63ac`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release: SKIPPED as required.

Artifact:

- id `9721898429`;
- wrapper name `obsidian-8b523eb0e56ab7d802cdea98bbad9dd9570e1f62`;
- wrapper size `740,759` bytes;
- wrapper digest `sha256:7d3c87000818b13f9ebc30265b865583f66864ebc882e19d75a8f2064f5bf647`.

Canonical direct runtime JAR extracted from the artifact:

- `Obsidian-0.3.0-phase3-dev20.jar`
- size `511,074` bytes
- SHA-256 `690b24cd6bb34e47b3b85159eda365da3e1f76f95d41b51f0b1298fc093ed2f3`

Sources JAR:

- `Obsidian-0.3.0-phase3-dev20-sources.jar`
- size `262,188` bytes
- SHA-256 `fcec81638fd5ee20ed03d968fcec0665bac96887a13de3b24778306972cda71a`

The canonical runtime JAR was inspected and contains:

- `PartialRemeshSectionDirtyOriginDiagnostics.class`;
- updated `LevelExtractorMixin.class`;
- updated `PartialRemeshProvenanceDiagnostics.class`;
- updated `AsyncMultiSectionSceneProbeDiagnosticMixin.class`;
- `obsidian.mixins.json`;
- embedded `fabric.mod.json` version `0.3.0-phase3-dev20`.

## Required short runtime

This is a diagnostic runtime, not a full A-0159 closure attempt.

After the P3.9 window arms:

1. perform about 6 ordinary safe-interior edits, letting READY recover;
2. perform about 3 safe-interior Y-slice-boundary edits;
3. perform one quick same-section 3-5 edit burst;
4. F3+T and recover READY;
5. cause one real scene recenter and recover READY;
6. exit normally and return the complete log.

Decisive new lines:

- `Phase 3 dev20 P3.9 final section-dirty origin totals`;
- `Phase 3 dev20 P3.9 first section-dirty origin fixtures`;
- `Phase 3 dev20 P3.9 final provenance-origin correlation`.

The next behavior correction is authorized only if those lines establish a fail-closed causal relationship for the missing/empty `+1` path.

## Promotion

No promotion. PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.