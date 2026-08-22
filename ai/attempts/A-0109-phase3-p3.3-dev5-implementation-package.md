# A-0109 - Phase 3 P3.3 dev5 implementation and package evidence

**Date:** 2026-08-22  
**Branch:** `phase3/greedy-rectangle-sidecar`  
**Canonical PR:** #37 against `main`  
**Version:** `0.3.0-phase3-dev5`  
**Result:** `PARTIAL` — implementation/package proof passed; reference runtime remains required.

## Frozen contract

A-0108 froze dev5 as a correctness-first topology rectangle sidecar. Dev5 is not allowed to replace the generalized GPU drawable or claim the full P3.4 render-correct merge key.

## Implementation

### `GreedySectionRectangles`

Added a pure topology rectangle representation that consumes only the proven immutable P3.2 `BinarySectionVisibility`:

- one packed `int` per rectangle;
- max records `ReferenceFaceMesh.MAX_FACES = 24,576`;
- max retained record bytes `98,304`;
- fixed worker-local primitive scratch: max record array, 16-row plane workspace, 384 coverage words, six directional area counters;
- deterministic direction/plane/u/v coordinate mapping from A-0108;
- deterministic extraction order direction -> plane -> row -> least-significant set bit;
- seed rectangle takes the full contiguous horizontal run and extends vertically while the entire run remains visible;
- retained output copies only actual packed records;
- deterministic fingerprint/content equality.

Every primary build performs exact coverage validation against its source P3.2 masks before returning:

- every rectangle face must exist in the source mask;
- overlaps are rejected;
- expanded rectangle coverage must equal every source visibility word exactly;
- global/per-direction rectangle area must equal source visible-face counts exactly.

A separate direct audit expands the rectangle set and requires exact count + complete inclusion against the permanent independent `ReferenceFaceMesh` face set.

### Production worker integration

Every real `SectionMeshWorkerPool` job now builds:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. unchanged `BakedSectionMesh`.

The existing first/every-64-local-completions audit cadence additionally performs:

- duplicate visibility determinism;
- independent visibility/reference proof;
- duplicate rectangle determinism;
- direct rectangle/reference proof;
- existing baked-mesh determinism.

New worker metrics cover rectangle builds, record count, covered face area, retained bytes, build time, all six directional rectangle/area totals, scratch reuse/high-water, primary exact mask-coverage audits, deterministic audits and independent-reference audits.

### Runtime coordinator

`FrameCoordinator` now exposes `greedyRectangleEvidenceReady`. Its closure requires the existing Phase 3/P3.2 gates plus:

- production rectangle builds;
- rectangle covered area exactly equals visibility faces globally/by direction;
- rectangle counts sum exactly by direction;
- positive record count and a real topology reduction (`rectangleCount < rectangleCoveredFaces`);
- retained bytes exactly `rectangleCount * 4`;
- rectangle scratch use covers builds;
- primary exact mask-coverage audits match all builds;
- nonzero matching deterministic audits;
- nonzero matching independent-reference audits;
- clean workers/staging/arena/resources.

Final diagnostics explicitly report:

- `greedyRectangleSidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

The existing generalized `BakedSectionMesh` remains the authoritative production GPU output.

## Exact CI

Worker-integrated head:

- `131ea7a97b9805844ad21faad1c8c367cda74d57`;
- run `32599551037`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release SKIPPED.

Canonical runtime source/package head:

- `75a35de6b073ca0d9bce013c43f2043d37f9b79a`;
- run `32599625494`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release SKIPPED.

Artifact:

- artifact id `9482515499`;
- artifact name `obsidian-b486ba8e381b93e40961203edcd296052c25fbd7` (GitHub PR merge-test SHA naming; workflow artifact metadata records branch head `75a35de6b073ca0d9bce013c43f2043d37f9b79a`);
- wrapper size `430,252` bytes;
- wrapper digest `sha256:43da516bc188da254245b36308d7e6ffecd6ae546052e470d61b37b5ae6417c5`.

## Canonical runtime JAR

Direct runtime file extracted from the exact CI artifact:

- `Obsidian-0.3.0-phase3-dev5.jar`;
- size `295,404` bytes;
- SHA-256 `ae87c3b2b1dc0c01c04a61d1282452d653975626f58aeae02ca64fe5cd8b620d`.

Sources JAR:

- size `154,266` bytes;
- SHA-256 `52e42eac5385c5e90d2730140fbded6006dfca5ca2f1de1cbaca337544d37389`.

Packaged `fabric.mod.json` verified:

- version `0.3.0-phase3-dev5`;
- Minecraft `~26.2`;
- Java `>=25`.

Package contents verified to include:

- `GreedySectionRectangles.class`;
- `GreedySectionRectangles$BuildScratch.class`;
- updated `SectionMeshWorkerPool` classes;
- updated `FrameCoordinator.class`;
- updated `ObsidianBootstrap.class`.

## Runtime next

Reference runtime must now prove the frozen A-0108 closure contract. Expected new flag is `greedyRectangleEvidenceReady=true` together with `phase3GateReady=true`, `schedulerEvidenceReady=true`, and `binaryVisibilityEvidenceReady=true`.

The runtime should show exact visibility-face/rectangle-area accounting, real topology reduction, matching mask/determinism/reference audits, zero worker failures/rejections/join failures, clean lifetime shutdown and exit 0.

The old Phase 2 fixed-anchor far-travel proof does not need to be repeated because dev5 does not change those hooks.

## Promotion authorization

The user explicitly authorized P3.3 dev5 merge once the frozen runtime/CI evidence passes. PR #37 remains draft until that evidence exists; promotion must use `[no-release]`.