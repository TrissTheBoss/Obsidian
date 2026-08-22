# A-0086 - Phase 2 dev7 multi-section scene implementation and package verification

Status: **IMPLEMENTED / EXACT CI SUCCESS / PACKAGE + BYTECODE VERIFIED / REFERENCE RUNTIME PENDING**

Date: 2026-08-21
Branch: `phase2/multi-section-scene`
Canonical stacked PR: #27
Temporary exact-CI PR: #28 (never merge)
Version: `0.2.0-phase2-dev7`

## Objective

Implement the A-0085 P2.7 correctness architecture: several neighboring real sections simultaneously under persistent renderer-side scene ownership, with lossless scene-scoped lifecycle validity, bounded upload admission, stable completion-gated ownership and no change to the proven terrain semantic/draw path.

## Implementation

### `SectionLifecycleEvents`

The corrected A-0083 coalescing bridge was generalized from a one-section validity domain to the fixed P2.7 scene domain:

- `SCENE_SECTION_RADIUS=1`;
- `SCENE_RECORD_CAPACITY=9`;
- `SCENE_HALO_CHUNK_RADIUS=2`;
- exact dirty events are relevant for any section in the 3x3 rendered X/Z window at the tracked Y;
- chunk load/unload is relevant within the union 5x5 halo footprint;
- world/resource invalidation remains globally relevant;
- renderer scene recenter has its own explicit internal reason;
- no event ring or payload-overwrite path was reintroduced.

### `RealMultiSectionSceneProbe`

New persistent scene owner:

- preallocated table of nine scene records;
- useful center selected through `SectionSnapshot.tryCaptureNearPlayer()`;
- exact neighboring snapshots use `SectionSnapshot.tryCaptureSection(...)`;
- eligibility captures use `SectionBakedQuadSnapshot.capture(...)` and retain the current validation requirement that a record contain both supported SOLID and CUTOUT quads;
- scene proof requires at least three simultaneous LIVE records and two adjacent horizontal record pairs;
- each admitted record reuses `RealSectionLifecycleProbe`, preserving the already-validated P2.6 capture/build/upload/draw/retirement implementation;
- all records in one scene generation share the same relevant lifecycle sequence;
- relevant invalidation revokes the whole validation scene and completion-retires old ownership;
- player movement outside the current +/-1 section X/Z window recenters the scene;
- record upload admission is serialized behind bounded staging reclamation;
- metrics cover scene readiness/rebuilds, record installs, adjacency, recenter, eligibility, admission deferrals, stale rejection, aggregate draw work and scene mesh bytes.

### `FrameCoordinator`

The active milestone root now owns `RealMultiSectionSceneProbe` instead of a single `RealSectionLifecycleProbe`.

Validation resources:

- staging remains fixed at 4 MiB;
- device geometry arena is fixed at 32 MiB for the nine-record correctness scene;
- no fallback arena growth exists;
- normal polling remains nonblocking;
- shutdown gate requires scene readiness + rebuild + recenter + dirty/resource/chunk unload/load coverage + zero drops/stale installs + full reclamation.

### `ObsidianBootstrap`

Dev7 startup text now advertises the persistent 3x3 renderer-owned scene, 5x5 union halo lifecycle domain, bounded one-record admission, whole-window correctness invalidation and unchanged Phase 3 ownership of production async scheduling/binary greedy meshing.

### Version

`gradle.properties` now declares `mod_version=0.2.0-phase2-dev7`.

## Compile evidence

Because the canonical dev7 PR is intentionally stacked on the unmerged P2.6 branch, the repository's workflow (`pull_request: branches: [main]`) does not run directly on PR #27. Temporary draft PR #28 targets `main` solely to run exact-head CI and must never merge.

Behavior/docs head tested: `0d3bc525fe63a1cd6a7339e24e89613ddbb8d612`.

GitHub Actions run `32501595352`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- Upload build artifacts: SUCCESS;
- Publish versioned release: SKIPPED.

Artifact ID: `9453702048`.

## Package evidence

Exact artifact contents:

- `Obsidian-0.2.0-phase2-dev7.jar`;
- SHA-256 `ca328ffd893ac626500a3610f6e0a8221ff36e4beddf616e5607a5e74e7d800e`;
- `Obsidian-0.2.0-phase2-dev7-sources.jar`;
- SHA-256 `d9d82f6e9594965ff4b2dda21fe5b91471325e8bdf58f76aa363154fd64312d8`;
- `fabric.mod.json` metadata exactly `obsidian` / `0.2.0-phase2-dev7` / Java >=25 / Minecraft ~26.2.

## Packaged-bytecode inspection

The built JAR, not source assumptions, confirms:

- active `FrameCoordinator` has `RealMultiSectionSceneProbe sceneProbe` and constructs/calls that owner from begin/world/end-frame paths;
- `RealMultiSectionSceneProbe` is packaged and references `RealSectionLifecycleProbe`, `SectionSnapshot.tryCaptureSection(...)` and `SectionBakedQuadSnapshot.capture(...)`;
- `SectionLifecycleEvents` packaged constants are exactly scene radius `1`, record capacity `9`, halo chunk radius `2`;
- the old event-ring fields are absent; packaged lifecycle state uses tracked-section identity, relevant sequence and sticky counters;
- `ObsidianBootstrap` contains the dev7 3x3/5x5 scene activation marker;
- the reused packaged `RealSectionLifecycleProbe` still contains public `SOLID_BLOCK`, `CUTOUT_BLOCK`, `ALPHA_CUTOUT`, `Sampler0`, `Sampler2`, and `drawIndexedIndirect`;
- shutdown evidence strings explicitly retain `nativeGraphicsSeam=false` and fixed scene record capacity.

No wider native graphics seam was introduced.

## Required reference runtime

P2.7 remains runtime pending. A reference Vulkan/RX 6800 XT run should prove:

- at least three simultaneous neighboring LIVE records and at least two adjacent pairs;
- correct visual shared borders while moving/turning;
- at least one scene invalidation/rebuild;
- successful F3+T scene rebuild;
- at least one camera scene recenter;
- tracked 5x5-halo chunk unload/load activity;
- `droppedLifecycleEvents=0`;
- zero stale scene/probe installs;
- bounded staging/arena pressure;
- complete geometry/resource reclamation;
- normal process exit `0`;
- shutdown `sceneGateReady=true`.

A temporary whole-window blank interval during correctness rebuild is permitted. Persistent duplicated/missing borders or stale old-window geometry is not.

## Dependency and merge rule

Dev7 is stacked forward work. It may not merge before P2.6 obtains its still-missing chunk unload/load runtime coverage and PR #25 merges. Dev7 also has no standing merge authorization yet.

This attempt is immutable once committed.
