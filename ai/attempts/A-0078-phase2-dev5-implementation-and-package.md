# A-0078 - Phase 2 dev5 generalized model implementation and package

Status: **IMPLEMENTED / CI PACKAGE SUCCESS / RUNTIME + HUMAN VISUAL VALIDATION PENDING**

Date: 2026-08-21
Branch: `phase2/broader-opaque-cutout-semantics`
Draft PR: #22
Validated package behavior head: `ad417532ef939df8ef0826eac1aab100b2c26faa`

## Objective

Implement the exact Minecraft 26.2 architecture selected by A-0077 for broader opaque/cutout block semantics, activate it in the live Phase 2 validation harness, and produce an exact-dependency dev5 package suitable for the reference RX 6800 XT runtime gate.

## Implemented generalized capture

New `SectionBakedQuadSnapshot` is render-thread-only and calls exact vanilla `ModelBlockRenderer.tesselateBlock(...)` for interior MODEL states. The chosen `BlockQuadOutput` freezes vanilla's finished output after exact seeded model selection, block offsets, shape-based directional culling, directional/general quad iteration, AO/flat light preparation and tint.

The immutable capture freezes per accepted quad:

- exact four section-local positions including vanilla block offset;
- exact four baked UVs;
- exact four final AO/shade/tint ARGB colors;
- exact four packed block/sky-light values including baked material emission;
- baked direction;
- SOLID/CUTOUT layer;
- atlas/sprite/material flags/tint/shade/emission/animation identity;
- source block-local coordinate and state ID.

First-proof block policy is conservative and atomic:

- require `RenderShape.MODEL`;
- reject fluid states;
- reject block entities;
- reject leaves until the live force-opaque leaves option is carried explicitly;
- reject missing models;
- accept only blocks-atlas SOLID/CUTOUT quads;
- reject a whole block if any emitted quad is TRANSLUCENT, wrong-atlas or otherwise unsupported.

All rejection reasons and accepted SOLID/CUTOUT counts remain observable.

`ReferenceFaceMesh` remains unchanged as the permanent independent P2.1 cube oracle.

## Pure generalized mesh

New `BakedSectionMesh` consumes only `SectionSnapshot + SectionBakedQuadSnapshot` and performs no live world/model/light/resource reads.

It deterministically groups all accepted quads into contiguous SOLID then CUTOUT ranges and emits exact `DefaultVertexFormat.BLOCK` layout:

- float3 Position;
- RGBA8 Color;
- float2 UV0;
- signed-short2 packed UV2/light;
- 28 bytes/vertex;
- 4 vertices and 6 int32 indices per quad.

Exact captured color/light is retained in immutable data. Emitted RGB is multiplied uniformly by `3/4` for comparison. A validation-only `1/512` outward offset is applied when a baked direction exists to avoid the already-observed coplanar comparison-depth ambiguity; this is not a production geometry rule.

Bounded capacity:

- max generalized quads: `24,000`;
- max vertex bytes: `2,688,000`;
- max index bytes: `576,000`;
- max geometry upload bytes: `3,264,000`, below the existing 4 MiB validation capacity.

## Live SOLID + CUTOUT probe

New `RealSectionBroadModelProbe` is now the active `FrameCoordinator` validation path.

It requires a sampled section containing at least one accepted SOLID and one accepted CUTOUT quad for the combined gate, otherwise it remains in WAITING_WORLD with a bounded retry delay and asks the runtime tester to move near ordinary terrain plus grass/flowers or another cutout model.

For each accepted capture it proves:

- two deterministic permanent cube-reference builds;
- two deterministic generalized captures;
- two deterministic generalized mesh builds;
- capture/drawable/layer/vertex/index accounting;
- bounded vertex/index arena allocation;
- one bounded upload batch containing geometry plus two indirect commands;
- exact public `SOLID_BLOCK` comparison pipeline;
- exact public `CUTOUT_BLOCK` comparison pipeline with `ALPHA_CUTOUT=0.5`;
- blocks atlas as `Sampler0`;
- live level lightmap as `Sampler2` with clamp-to-edge LINEAR sampler;
- separate public indexed-indirect SOLID and CUTOUT draws;
- resource epoch validation before every live draw;
- completion-gated vertex/index and indirect-command retirement;
- `nativeGraphicsSeam=false`;
- zero profiler-only submissions.

`FrameCoordinator` retains the five-second world-entry arm delay and six fully reclaimed visual passes. Bootstrap messaging now describes the dev5 generalized semantic boundary.

Older dev2/dev3/dev4 probes remain in source as inactive historical diagnostics.

## Exact CI/package evidence

GitHub Actions run `32484610552` on exact behavior head `ad417532ef939df8ef0826eac1aab100b2c26faa`:

- Java 25 / Gradle 9.5.1: **SUCCESS**;
- build: **SUCCESS**;
- build artifact upload: **SUCCESS**;
- public versioned release: **SKIPPED**.

Artifact ID: `9447364592`.

Packaged files:

- `Obsidian-0.2.0-phase2-dev5.jar`;
- JAR SHA-256: `68e393636e0ca216c99b3253033f701ac38aad6ba373538430a910cce238d42e`;
- `Obsidian-0.2.0-phase2-dev5-sources.jar`;
- sources SHA-256: `84b3e4c7846160b771202cd2f09a9453a8077da2dc8417437ad40aa99cf57ad0`.

`fabric.mod.json` metadata is exactly `obsidian 0.2.0-phase2-dev5`.

Packaged bytecode verification confirms:

- active `FrameCoordinator` field and constructor use `RealSectionBroadModelProbe` rather than `RealSectionLightingProbe`;
- the coordinator re-arms `RealSectionBroadModelProbe` for all six passes;
- `RealSectionBroadModelProbe` references `SectionBakedQuadSnapshot` and `BakedSectionMesh`;
- public `RenderPipelines.SOLID_BLOCK` and `RenderPipelines.CUTOUT_BLOCK` are present;
- exact `ALPHA_CUTOUT` shader define is present;
- `Sampler0` and `Sampler2` bindings are present;
- public `RenderPass.drawIndexedIndirect(...)` is present.

Temporary inspection PR #23 was closed without merge after A-0077 was recorded.

## Runtime gate still required

The hosted environment cannot perform the required reference Windows 11 / Prism Launcher / RX 6800 XT Vulkan visual run. The exact dev5 JAR must still be run on the reference machine.

Required runtime evidence:

- exact `obsidian 0.2.0-phase2-dev5` loads under Vulkan;
- at least one accepted SOLID quad and one accepted CUTOUT quad in every completed comparison pass;
- deterministic cube-reference/generalized-capture/drawable duplicates;
- explicit accepted/rejected block counts remain sane and no unsupported block is partially emitted;
- generalizedQuads = solidQuads + cutoutQuads;
- vertices = generalizedQuads * 4;
- indices = generalizedQuads * 6;
- relative geometry, UVs, tint, block/sky light and AO agree visually with vanilla for accepted blocks, ignoring deliberate uniform 3/4 RGB darkening and validation-only 1/512 offset;
- cutout pixels discard correctly rather than appearing as opaque rectangles;
- general/crossed/non-full accepted models remain aligned with vanilla while moving and turning;
- `worldReadsAfterGeneralizedCapture=0`;
- `cubeOraclePreserved=true`;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- `profilerOnlySubmissions=0`;
- all six visual passes complete;
- staging submitted bytes fully reclaim;
- all arena allocations retire/reclaim;
- final arena used bytes = 0 with one full 4 MiB free span and fragmentation 0;
- indirect resources retire/release with none pending;
- process exits 0.

World-dependent counts and fingerprints are evidence, not hard-coded expected values.

## Merge authorization

On 2026-08-21 the user explicitly authorized merging dev5. That authorization is recorded as standing authorization **conditional on the required runtime + human visual gate passing and the final exact-head CI remaining green**. No additional authorization needs to be requested after a successful runtime gate unless the behavior/scope materially changes after that validation.

## Deliberate boundary

Dev5 still does not claim:

- leaves force-opaque option support;
- translucent/fluid terrain;
- event-driven section/block/light/resource invalidation and rebuild lifecycle (P2.6);
- persistent multi-section scene ownership (P2.7);
- production greedy meshing (Phase 3);
- global vanilla terrain replacement;
- production-scale visibility/performance.

## Result

The work previously interrupted by the execution limit is complete through implementation, active-path integration, exact CI/package generation and packaged-bytecode verification. The remaining blocker is the real reference-machine runtime/human gate, not an execution limit.
