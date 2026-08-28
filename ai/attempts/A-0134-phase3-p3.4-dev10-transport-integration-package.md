# A-0134 — Phase 3 P3.4 dev10 transport-proof integration/package

**Date:** 2026-08-23  
**Result:** SUCCESS — implementation/package CI green; reference runtime still required.

## Scope
Implement and production-integrate the no-emission P3.4 dev10 repeat-aware transport/sampling proof frozen in A-0133, then produce the direct `0.3.0-phase3-dev10` runtime JAR.

Dev10 does **not** replace terrain geometry. `BakedSectionMesh` remains authoritative and `greedyRectangleGpuEmission=false` / `renderCorrectMergeKeyComplete=false` remain required.

## Pure proof core
New class:
- `src/client/java/dev/obsidian/render/terrain/RepeatAwareTransportProof.java`

Pure-core commit:
- `360726418f009713199686e89dd42b3ea7b6ad1a`

Workflow `32605684776`:
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

The core retains exactly 4 logical bytes per dev9 repeat-aware-four-vertex-safe multi-face candidate:
- unsigned-short dev7 candidate index;
- source geometric-corner order in source baked-vertex order;
- frozen transport/raster obligation flags.

It independently revalidates finite ordered raw atlas bounds, the affine square-symmetry orientation, exact representative raw U/V endpoint bits by geometric corner, source baked corner order, exact source fingerprints/accounting, directional coverage/savings and deterministic retained output.

Frozen obligations carried by every admitted record:
- explicit gradients derived from unwrapped candidate-local repeat coordinates;
- same live blocks-atlas texture view/sampler as the existing block path;
- positive outer-max endpoint policy;
- source baked vertex order/diagonal preservation;
- explicit later raster review at removed internal repeat boundaries.

The internal raster-boundary obligation is deliberately open in dev10 and is **not** a gate failure because no large-quad geometry is emitted yet.

## Production worker integration
Worker integration commit:
- `23f1d360bf2164657d41a55e3c5d985d0643bca7`

Workflow `32605901656`:
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

Every successful worker job now executes:
1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. `RenderMergeCandidates`;
5. `OrdinaryQuadEmissionSafety`;
6. `RepeatAwareUvDescriptors`;
7. `RepeatAwareTransportProof`;
8. unchanged `BakedSectionMesh` drawable.

Completed tickets retain the dev10 proof. Workers expose exact:
- source multi-face / representable / four-vertex-safe counts;
- transport record / unsafe counts;
- covered faces / faces saved;
- explicit-gradient, internal-S/internal-T/internal-both, outer-edge, same-sampler and raster-review obligations;
- per-direction records/covered/saved;
- retained bytes;
- build timing, bounded scratch high-water;
- primary proof audits and determinism audits.

The existing periodic duplicate pipeline now rebuilds and `contentEquals`-checks the dev10 proof before the unchanged baked mesh determinism check.

## Runtime evidence gate
New helper:
- `src/client/java/dev/obsidian/render/frame/RepeatAwareTransportEvidence.java`

`FrameCoordinator` requires `repeatAwareTransportEvidenceReady=true` strictly after `repeatAwareUvEvidenceReady=true`.

Frozen exact gate conditions include:
- builds > 0 and >= completed worker jobs;
- source multi-face == dev9 multi-face;
- source representable == dev9 representable;
- source four-vertex-safe == dev9 four-vertex-safe;
- transport records == source four-vertex-safe;
- unsafe == source multi-face - records;
- explicit-gradient / outer-edge / same-atlas-sampler / raster-review counts == records;
- `internalS + internalT - internalBoth == records`, proving every multi-face record has at least one internal reset boundary;
- direction record/covered/saved sums exact;
- faces saved == covered - records;
- retained bytes == records * 4;
- scratch uses >= builds;
- proof audits == builds and all match;
- determinism audits > 0 and all match;
- all prior worker/lifecycle/staging/arena/resource gates remain clean.

`repeatAwareTransportBoundaryRasterObligationOpen=true` is expected when records exist and does **not** make this no-emission proof gate false.

Coordinator/bootstrap diagnostics state explicitly that dev10 does not emit the large quad and does not widen the native Vulkan seam.

## Canonical integrated package
Canonical source/package head:
- `5dd6f04f3635f8c0436a49bf43396adcbc532bab`

Workflow:
- `32606020092`
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

Artifact:
- id `9484142328`;
- wrapper `obsidian-9b408838882837ec7be8ae39cf56ad765e811f40`;
- wrapper size `546,484` bytes;
- artifact digest `sha256:e907b9652dd0b7d54a21d8a76d96fbfec91d207f038f327dfec3b688eb8bb8cf`.

Canonical direct runtime JAR:
- `Obsidian-0.3.0-phase3-dev10.jar`;
- size **376,137 bytes**;
- SHA-256 **`f37531a48608d6a2e0c0143a7ef72dc6d0c8533f4871d21137ea85a69a8feaf9`**.

Sources JAR:
- `Obsidian-0.3.0-phase3-dev10-sources.jar`;
- size 193,207 bytes;
- SHA-256 `ad589b12fdc388acf5748fbc6b7a98f548df29debcdb369b7c49386cb2e9e308`.

Package inspection confirms:
- Fabric mod version `0.3.0-phase3-dev10`;
- Minecraft `~26.2`;
- Fabric Loader `>=0.19.3`;
- Java `>=25`;
- client environment;
- packaged `RepeatAwareTransportProof`, `RepeatAwareTransportEvidence`, `SectionMeshWorkerPool`, `FrameCoordinator`, `ObsidianBootstrap` and prior dev9 descriptor classes.

## Reference runtime required
Use the direct canonical dev10 JAR on the reference Vulkan system. Required exercise remains:
- initial 3x3 scene reaches READY;
- normal block break/place rebuild reaches READY;
- F3+T reload/rebuild reaches READY;
- ordinary movement/recentering optional;
- fully exit Minecraft/Prism and retain the complete shutdown tail through launcher exit code 0.

New required final flag:
- `repeatAwareTransportEvidenceReady=true`.

All prior gates through `repeatAwareUvEvidenceReady=true` must remain true, `hardFailure=false`, no queue/failure/join/lifecycle/lifetime leak may appear, and `greedyRectangleGpuEmission=false` / `renderCorrectMergeKeyComplete=false` remain expected.

A logged `repeatAwareTransportBoundaryRasterObligationOpen=true` is expected/nonblocking in dev10. It is the named obligation that a later geometry-changing P3.4 slice must exercise visually/on real Vulkan hardware.

The old fixed-anchor far-travel sequence remains unnecessary because its lifecycle proof was closed by A-0101.

## Promotion boundary
Keep PR #42 draft/unmerged until reference runtime closure. Standing Phase 3 authorization permits promotion with `[no-release]` once the frozen dev10 gates pass.

Dev10 itself changes no emitted terrain geometry. The subsequent geometry-changing slice must freeze a separate emission contract and requires renewed explicit human visual validation before promotion.