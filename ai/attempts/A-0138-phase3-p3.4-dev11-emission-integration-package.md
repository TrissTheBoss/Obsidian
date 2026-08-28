# A-0138 — Phase 3 P3.4 dev11 repeat-aware greedy GPU-emission integration/package

**Date:** 2026-08-29  
**Result:** SUCCESS — implementation/package CI green; reference runtime and explicit human visual PASS still required.

## Scope
Implement the geometry-changing P3.4 dev11 canary frozen in A-0137. Dev11 replaces only exact dev10 transport-safe canonical source-face groups with one repeat-aware large quad while preserving every unsafe, ambiguous, noncanonical and generalized baked quad on exact passthrough.

Unlike dev10, dev11 **does emit merged GPU geometry**. Promotion therefore requires both the automated runtime gate and a separate explicit human visual PASS.

## Pure hybrid mesh
`RepeatAwareGreedyMesh` is the deterministic worker-owned hybrid output.

Pure-core commit:
- `7a1f136b04299d3e805316c4cdddd1ffbd8907f2`

Workflow `33213121999`:
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

The pure builder:
- suppresses only source baked quads proven to belong to dev10 transport records;
- retains all other baked quads byte-exact on the 28-byte BLOCK passthrough path;
- emits one 60-byte/vertex merged quad per admitted record;
- preserves source baked vertex order/diagonal;
- partitions SOLID/CUTOUT and all six directions deterministically;
- validates exact source suppression/replacement coverage and no duplicate suppression;
- retains one combined index stream with passthrough ranges before merged ranges;
- proves `hybridUploadBytes <= sourceUploadBytes` for every build.

A merged quad costs 264 upload bytes (4 x 60-byte vertices + 6 x 4-byte indices) and replaces at least two 136-byte source quads, so no staging/device-arena capacity increase is required.

## Worker source identity and determinism
To avoid changing scheduler/admission/cancellation behavior, immutable source references are retained through the existing proof chain:
- `CanonicalFaceRenderKeys` retains the exact immutable `SectionSnapshot` source;
- `RepeatAwareUvDescriptors` retains the exact immutable `CanonicalFaceRenderKeys` source;
- `RepeatAwareTransportProof` builds and publishes the dev11 `RepeatAwareGreedyMesh` after the frozen dev10 proof validates.

Source-reference checkpoint:
- commit `91f7cc8632370b7119078bbf9a6a42a8066cf50f`;
- workflow `33213683192` SUCCESS.

Embedded-hybrid checkpoint:
- commit `326db7b25a62151ab6abee43f94352762b135b16`;
- workflow `33215703727` SUCCESS.

`RepeatAwareTransportProof.contentEquals(...)` now also requires hybrid-mesh `contentEquals(...)`, so the existing worker duplicate-build audit validates the dev11 hybrid bytes without modifying worker queueing or priority behavior. The exact `BakedSectionMesh` build remains the independent production-job oracle.

## Public Blaze3D render representation
New merged format:
- `RepeatAwareGreedyRenderFormat.MERGED`;
- exact 60 bytes/vertex;
- Position `RGB32_FLOAT`;
- Color `RGBA8_UNORM`;
- UV0 `RG32_FLOAT` candidate-local repeat coordinate;
- UV2 `RG16_SINT` lightmap coordinate;
- RepeatBasis0 `RGBA32_FLOAT`;
- RepeatBasis1 `RGBA32_FLOAT`.

The first 28 bytes preserve the BLOCK attribute semantics. The appended basis contains the exact dev9 atlas affine basis plus candidate repeat extent.

Namespaced shader resources:
- `assets/obsidian/shaders/core/repeat_aware_block.vsh`;
- `assets/obsidian/shaders/core/repeat_aware_block.fsh`.

The shader preserves the current block path's model/view/projection transform, fog distances, vertex color, live lightmap, alpha cutout and color modulation. Only `Sampler0` addressing changes:
- repeat is computed in candidate-local coordinates;
- full-atlas sampler wrapping is forbidden;
- local repeat is remapped through the exact retained atlas affine basis;
- `textureGrad` uses derivatives from the unwrapped affine atlas coordinate to prevent repeat resets from corrupting implicit LOD derivatives;
- the same live blocks-atlas view/sampler is bound as the existing block path;
- the positive outer-max endpoint rule is retained.

No native Vulkan graphics seam was added.

## Production GPU lifecycle integration
`WorkerBackedSectionLifecycleProbe` now retains:
- exact `BakedSectionMesh` oracle;
- validated `RepeatAwareGreedyMesh` GPU drawable.

Before any install, the render thread validates:
- hybrid source baked fingerprint and transport fingerprint;
- source quad count equals the exact oracle quad count;
- merged quad count equals dev10 transport record count;
- suppressed source quads equal dev10 covered source faces;
- faces saved equal dev10 faces saved;
- hybrid quad count equals source quads minus faces saved;
- hybrid upload bytes do not exceed source upload bytes;
- source upload bytes equal the exact oracle vertex + index payload.

GPU resources use:
- one passthrough vertex allocation;
- one merged vertex allocation;
- one combined index allocation;
- one 4-command indexed-indirect buffer.

Fixed public draw classes:
1. passthrough SOLID;
2. passthrough CUTOUT;
3. merged SOLID;
4. merged CUTOUT.

All four passes keep render-thread ownership and use public Blaze3D indexed-indirect drawing. Completion-gated retirement was expanded from two to three geometry handles while preserving existing staging/arena/resource cleanup semantics.

The initial renderer integration exposed two dropped compatibility accessors in `AsyncMultiSectionSceneProbe`; hosted CI caught exactly those two missing symbols. They were restored without changing dev11 semantics.

Integrated renderer compatibility checkpoint:
- commit `3678f969a5744e8d262bc8e1c8c2a6234d81d788`;
- workflow `33216216638` SUCCESS.

## Runtime evidence gate
New helper:
- `RepeatAwareGreedyEmissionEvidence`.

Standalone gate checkpoint:
- commit `d8c4ad2f0a52f6e3f4d275ebf411458b5f2b8415`;
- workflow `33216345609` SUCCESS.

The shutdown coordinator now exposes:
- `repeatAwareGreedyEmissionEvidenceReady`;
- `repeatAwareGreedyGpuEmission=true`;
- `repeatAwareGreedyMeshIntegrated=true`;
- installed record count;
- scene worker installs/completions;
- draw submissions;
- actual indirect calls and expected indirect calls;
- fixed `repeatAwareGreedyIndirectClassesPerDraw=4`;
- resource-epoch checks;
- dev10 transport records/covered faces/faces saved feeding the emission path;
- `repeatAwareGreedyInstallValidationPassed`;
- `repeatAwareGreedyFixedFourClassDrawContract`;
- `repeatAwareGreedyVisualValidationRequired=true`;
- `repeatAwareGreedyVisualValidationAutomated=false`.

The automated gate requires the complete prior dev10 chain, production worker integration, local scene readiness, successful validated installs, exactly four indexed-indirect calls per scene draw, resource-epoch checks, positive dev10 transport/savings evidence and completely clean workers/staging/arena/resources.

Exact source suppression/replacement/layer/direction checks run inside every worker hybrid build, and the production render-thread install revalidates source/suppression/replacement/upload identities before an installed record can become LIVE.

## Canonical integrated package
Canonical binary source/package head:
- `e751698ca1c58cbbb59db5c8dd3709dbd2afb69e`.

Workflow:
- `33216666077`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

Artifact:
- id `9703613318`;
- wrapper `obsidian-55b80e1bd68fa6b74c0f043a014ef82f56bc8888`;
- wrapper size `586,870` bytes;
- artifact digest `sha256:54d1b9e659c32e56cb66b136dcacd8a2daf5d0bdd13348d95cca219489c1155d`.

Canonical direct runtime JAR:
- `Obsidian-0.3.0-phase3-dev11.jar`;
- size **405,082 bytes**;
- SHA-256 **`d2ebd11a5fd230d366b27ddaafecb4b0e527183f9aa81c1bda6c4aedca0f4124`**.

Sources JAR:
- `Obsidian-0.3.0-phase3-dev11-sources.jar`;
- size 207,485 bytes;
- SHA-256 `8f83a4f7bcc93afa94cb9d031bbddd5284d26609cfc7433f0b4e51aeab0e0593`.

Package inspection confirms:
- Fabric mod version `0.3.0-phase3-dev11`;
- Minecraft `~26.2`;
- Fabric Loader `>=0.19.3`;
- Java `>=25`;
- client environment;
- packaged `RepeatAwareGreedyMesh`, `RepeatAwareGreedyRenderFormat`, `RepeatAwareGreedyEmissionEvidence`, `RepeatAwareTransportProof`, `WorkerBackedSectionLifecycleProbe`, `FrameCoordinator`, `ObsidianBootstrap`;
- packaged `repeat_aware_block.vsh` and `repeat_aware_block.fsh`.

## Reference runtime and visual validation required
Use the direct canonical dev11 JAR on the reference Vulkan system.

Required exercise:
1. let the initial 3x3 scene reach READY;
2. move/look around merged surfaces and deliberately inspect internal repeat-reset lines and rectangle/section T-junctions;
3. perform a normal block break/place rebuild and wait for READY, then inspect again;
4. perform F3+T and wait for READY, then inspect again;
5. fully exit Minecraft/Prism and retain the complete shutdown tail through launcher exit code 0;
6. provide an explicit human visual verdict: **PASS** or **FAIL**.

Visual PASS means no observed:
- texture stretching;
- atlas bleed;
- repeat-line or mip shimmer artifacts;
- seams/cracks at removed internal boundaries or rectangle/section T-junctions;
- wrong winding/culling;
- color/light mismatch;
- double-draw/z-fighting;
- missing faces/holes.

New required automated final flag:
- `repeatAwareGreedyEmissionEvidenceReady=true`.

All prior flags through `repeatAwareTransportEvidenceReady=true` must remain true, `hardFailure=false`, `repeatAwareGreedyGpuEmission=true`, `synchronousSceneMeshBuilds=0`, and all worker/staging/arena/resource lifetime accounting must close cleanly.

The old fixed-anchor far-travel sequence remains unnecessary because A-0101 permanently closed that lifecycle obligation.

## Promotion boundary
Keep PR #43 draft/unmerged until both:
1. the complete dev11 reference runtime gate passes; and
2. the user explicitly reports visual **PASS**.

Standing Phase 3 authorization permits promotion/merge with `[no-release]` immediately after both frozen gates close. Do not infer or automate the human visual verdict.
