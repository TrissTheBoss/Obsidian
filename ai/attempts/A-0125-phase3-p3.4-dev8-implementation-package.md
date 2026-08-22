# A-0125 - Phase 3 P3.4 dev8 implementation and runtime package

**Date:** 2026-08-23  
**Branch:** `phase3/rectangle-emission-safety`  
**Canonical PR:** #40 against `main`  
**Version:** `0.3.0-phase3-dev8`  
**Result:** `SUCCESS` for implementation/CI/package; real reference runtime classification evidence remains required before promotion.

## Objective

Integrate the A-0122 ordinary four-vertex emission-safety classifier into production workers and the frozen runtime evidence gate without changing emitted GPU terrain geometry.

## Production pipeline

Every successful worker job now builds, in order:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. `RenderMergeCandidates`;
5. `OrdinaryQuadEmissionSafety`;
6. the existing generalized `BakedSectionMesh` authoritative drawable.

Completed worker tickets retain the dev8 safety sidecar. The classifier runs only over immutable renderer-owned dev7/dev6/baked inputs and performs no live-world or GPU access.

## Classifier semantics

For each dev7 candidate, the representative baked quad is independently reconstructed into canonical geometric corners. Exact repeated-field continuity is classified independently for:

- per-corner ARGB color;
- packed light;
- raw atlas UV `(u,v)` bit pairs.

One flag byte/candidate records color-safe, light-safe, UV-safe and combined ordinary-attribute-safe state.

No assumption is made that an ordinary-safe multi-face candidate must exist. A zero result is valid evidence and would indicate the current ordinary atlas/block vertex representation cannot directly emit useful greedy rectangles without a repeat-aware representation.

## Worker metrics/audits

Production workers now record:

- safety builds and classified candidates;
- singleton and multi-face candidates;
- color safe/unsafe multi-face counts;
- light safe/unsafe multi-face counts;
- UV safe/unsafe multi-face counts;
- combined ordinary-safe/unsafe multi-face counts;
- ordinary-safe covered faces and faces saved;
- per-direction ordinary-safe counts/covered faces;
- exact one-byte-per-candidate retained bytes;
- build time/max candidates;
- reusable scratch uses/high-water;
- one primary classification audit/match per successful build;
- first/every-64-local-completions exact `contentEquals` determinism audits.

All previous P3.2/P3.3/dev6/dev7/reference/baked determinism audits remain active.

## Runtime gate

`FrameCoordinator` now reports `ordinaryQuadEmissionSafetyEvidenceReady=true` only after all prior dev7 gates pass and dev8 proves:

- safety builds are nonzero and cover completed worker jobs;
- classified candidate count equals dev7 candidate count;
- singleton/multi-face counts exactly match dev7;
- color safe+unsafe, light safe+unsafe, UV safe+unsafe and ordinary safe+unsafe each exactly partition multi-face candidates;
- per-direction ordinary-safe count/covered-face sums are exact;
- retained bytes equal classified candidates * 1;
- scratch uses cover builds;
- primary classification audits equal builds and all match;
- determinism audits are nonzero and all match;
- prior worker/lifecycle/lifetime cleanliness remains true.

The gate deliberately has no `ordinarySafe > 0` requirement.

Startup/shutdown diagnostics now expose `ordinaryQuadEmissionSafetySidecarIntegrated=true`, while preserving:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `workerWorldReadsAfterCapture=0`.

## CI evidence

Canonical integrated runtime source/package head:

- `cc7e4d64bdf000635ed765a6e68a6c30cc9c2a8f`.

Exact PR workflow:

- run `32603270509`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release publishing SKIPPED.

Artifact:

- id `9483472246`;
- wrapper name `obsidian-15a62ef6df176bac314f5061c805bfd3d6949466`;
- wrapper size `491,806` bytes;
- wrapper digest `sha256:df2a3dfc37ba75e7db1d4a5650ca54ac0a220155e9bb405dd927941f48df4221`.

## Canonical runtime JAR

- `Obsidian-0.3.0-phase3-dev8.jar`;
- size `337,502` bytes;
- SHA-256 `f7155754683c6f484356cc4e729bd5de262b4acd355df05a49e55122903f9f4e`.

Sources JAR:

- size `175,407` bytes;
- SHA-256 `8cd0abc2d38db49122fce65d0df6230123d369fb86b96012766b65d2fd41fb19`.

Package inspection confirms:

- Fabric mod version `0.3.0-phase3-dev8`;
- Minecraft `~26.2`;
- Fabric Loader `>=0.19.3`;
- Java `>=25`;
- client environment;
- packaged `OrdinaryQuadEmissionSafety`, worker integration, evidence helper, coordinator and bootstrap diagnostics.

## Deliberate boundary

Dev8 remains sidecar-only. It does not emit or replace greedy GPU geometry and does not claim final render-correct merge completion.

No new explicit visual verdict is required for dev8 promotion because the drawable is unchanged. The first later slice that actually changes emitted terrain geometry still requires renewed explicit human visual validation.

## Next action

Run the canonical dev8 JAR on the reference Vulkan machine through ordinary READY/rebuild/resource-reload activity and a complete clean exit. Capture the final shutdown line and Prism exit code. Promotion requires `ordinaryQuadEmissionSafetyEvidenceReady=true` plus all prior gates and clean lifetime closure.