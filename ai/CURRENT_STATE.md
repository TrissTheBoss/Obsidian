# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range product/feature plan: `ai/MASTER_ROADMAP.md`
- Phase 1 status: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Phase 2 P2.1 status: **COMPLETE / runtime validated and merged**
- P2.1 closing merge: `a714e19ce871bf73136d52f85a1780109aa851dd`
- Active development branch: `phase2/drawable-real-section`
- Active draft PR: #14, `Phase 2 dev2: first drawable real section`
- Current development version: `0.2.0-phase2-dev2`
- Active roadmap item: **P2.2 - first drawable real section**
- Dev2 status: **implementation + exact Minecraft 26.2 API grounding + exact CI package verified; reference RX 6800 XT runtime/visual validation pending**

## Continuity model

The continuity files have explicit non-overlapping roles:

- `ai/CURRENT_STATE.md` = current truth and immediate next action;
- `ai/MASTER_ROADMAP.md` = canonical future roadmap, phases, planned features, experiments, gates, release/compatibility direction, and roadmap-alteration procedure;
- `ai/OPERATING_MANUAL.md` = engineering and handoff procedure;
- `ai/DECISIONS.md` = durable reasoning/policy;
- `ai/ATTEMPT_LOG.md` + `ai/attempts/` = immutable evidence/history.

Roadmap creation/governance evidence: `ai/attempts/A-0057-master-roadmap-and-governance.md`.

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666

## Phase 1 closing result

Runtime evidence: `ai/attempts/A-0052-dev9-runtime-success.md`.

Phase 1 proved the small-scale GPU-driven renderer skeleton end-to-end:

`bounded persistent staging -> generation-safe device arena -> frame graph -> narrow native compute/storage seam -> GPU scene visibility -> atomic indirect compaction + visible count -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`.

This remains the infrastructure foundation for real terrain.

## Phase 2 P2.1 closing result - COMPLETE

Runtime evidence: `ai/attempts/A-0058-phase2-dev1-runtime-success.md`.

Merge: PR #12 -> `a714e19ce871bf73136d52f85a1780109aa851dd`.

P2.1 permanently established:

- real loaded 16^3 section capture;
- one-block halo -> 18^3 / 5832 cells;
- primitive-only immutable snapshot;
- no live world reads during post-capture reference meshing;
- conservative AIR / SUPPORTED_FULL_CUBE / UNSUPPORTED classification;
- deterministic canonical one-face-per-exposed-face reference oracle;
- original BlockState ID retained in each canonical reference face;
- unsupported neighbors suppress rather than approximate output;
- exact duplicate-build determinism;
- bounded staging / generation-safe arena / completion-gated lifetime validation;
- vanilla terrain remains active.

The simple `ReferenceFaceMesh` is permanent and must remain independent of the future production greedy mesher.

## Phase 2 dev2 / P2.2 - ACTIVE

Goal: produce the first actual indexed geometry derived from a real immutable section and validate its placement in the live Minecraft world before adding correct texture/material/light semantics.

Planning evidence: `ai/attempts/A-0059-phase2-dev2-drawable-section-plan.md`.

Exact Minecraft 26.2 API evidence: `ai/attempts/A-0060-phase2-dev2-render-api-inspection.md`.

Implementation/package evidence: `ai/attempts/A-0061-phase2-dev2-implementation-and-package.md`.

### Exact 26.2 render hook and transform path

Bytecode inspection established that the P2.2 hook belongs immediately after `LevelRenderer.render(...)` inside `GameRenderer.renderLevel`, before GameRenderer switches to HUD projection and clears depth.

At that point dev2 uses:

- `GameRenderer.gameRenderState().levelRenderState.cameraRenderState`;
- camera `pos` and `viewRotationMatrix`;
- `RenderSystem.getProjectionMatrixBuffer()` for the exact active world projection;
- `RenderSystem.getDynamicUniforms().writeTransform(...)`;
- `GameRenderer.mainRenderTarget()` color/depth views.

Section-local geometry is placed using:

`viewRotation * translate(sectionOrigin - cameraPosition)`.

### `DrawableSectionMesh`

The drawable mesh is deliberately separate from `ReferenceFaceMesh` so the optimized/drawable representation cannot become its own correctness oracle.

For each canonical reference face dev2 emits:

- one drawable quad;
- 4 vertices;
- 6 indices;
- section-local positions;
- 32-bit indices;
- one RGBA8 orientation/debug color.

The orientation colors exist only to make placement/winding errors obvious during the runtime comparison. They are not Minecraft material, tint or texture semantics.

32-bit indices are required: the maximum 24,576-face reference stream can produce 98,304 drawable vertices, exceeding 16-bit index range.

The drawable builder:

- consumes only `SectionSnapshot` + `ReferenceFaceMesh`;
- revalidates face coverage and state identity;
- has deterministic winding/order;
- is built twice and must be byte/content identical;
- performs no world reads.

### Live comparison graphics path

`GameRendererMixin` invokes `RealSectionDrawableProbe` after vanilla world rendering.

The probe:

1. captures one real immutable snapshot;
2. builds/validates the reference oracle twice;
3. builds/validates the drawable mesh twice;
4. allocates generation-safe vertex/index arena spans;
5. uploads vertex, index and one 20-byte indexed-indirect command through bounded persistent staging;
6. uses public Blaze3D graphics only (`nativeGraphicsSeam=false`);
7. uses Minecraft's built-in `core/position_color` shaders and exact debug bind-group contract;
8. uses triangle-list indexed-indirect drawing with `IndexType.INT`;
9. depth-tests with Minecraft's reversed-depth `GREATER_THAN_OR_EQUAL`, no depth write and no culling;
10. overlays orientation-colored faces on top of the already-rendered vanilla section for a short bounded dev-only comparison window;
11. leaves vanilla terrain active;
12. retires both arena allocations and the indirect command resource behind real GPU completion handles;
13. creates no profiler-only submissions.

### Validation-only memory capacities

P2.2 worst-case drawable bytes are larger than the tiny P2.1 canonical face stream, so dev2 uses explicit bounded validation capacities:

- staging ring: 4 MiB;
- device geometry arena: 4 MiB.

These are validation capacities, not a production renderer memory-budget decision. No unbounded fallback allocation is introduced.

### Dev1 cleanup

The completed one-shot `RealSectionReferenceProbe` runtime class was removed from the active dev2 source after its role was superseded.

The permanent correctness pieces remain:

- `SectionSnapshot`;
- `ReferenceFaceMesh`.

## Exact dev2 code/package verification

Exact behavior/code head before continuity-only records:

`ea106324adfdb9bfdef2757edd36fbfd51bf86a9`

GitHub Actions run:

`32424196221`

Artifact ID:

`9426803061`

Result:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- public release: SKIPPED.

Package:

- `Obsidian-0.2.0-phase2-dev2.jar`;
- main JAR SHA-256: `a377ff9b34ae6650efcd1d694c55e08602b1ae3aaa98576440ac34afa4987cce`;
- sources SHA-256: `3f9eb0c5a0bd56978f27739d8dd7dd9fb6ec86b1c13372cb46d198c8885a2654`.

Verified present:

- `GameRendererMixin`;
- `DrawableSectionMesh`;
- `RealSectionDrawableProbe`;
- `IndexedIndirectCommandBuffer`;
- `ReferenceFaceMesh`;
- `FrameCoordinator`.

Verified absent:

- completed dev1 one-shot `RealSectionReferenceProbe`.

Commits after `ea106324...` are continuity/documentation only unless a later attempt explicitly says otherwise.

## Dev2 runtime success criteria

The reference RX 6800 XT run must prove:

- exact `obsidian 0.2.0-phase2-dev2` loads on Vulkan;
- the exact GameRenderer mixin applies successfully;
- one real 18^3 snapshot is captured;
- `interiorAir + interiorSupported + interiorUnsupported = 4096`;
- reference face count > 0;
- `deterministicReferenceBuilds=2`;
- drawable face count = reference face count;
- drawable vertex count = face count * 4;
- drawable index count = face count * 6;
- `deterministicDrawableBuilds=2`;
- `worldReadsAfterSnapshot=0` for mesh construction;
- public comparison pipeline is valid;
- `nativeGraphicsSeam=false`;
- indexed-indirect live-world drawing executes;
- orientation-colored faces visibly align with the corresponding vanilla supported full-cube faces during the short comparison window;
- the overlay does not appear offset, rotated incorrectly, or drift relative to vanilla while the camera moves;
- depth testing behaves sensibly against vanilla terrain;
- `profilerOnlySubmissions=0`;
- staging submitted bytes = `vertexBytes + indexBytes + 20`, then fully reclaimed;
- exactly two arena allocations retire/reclaim behind completion;
- final arena used bytes = 0;
- final arena free spans = 1;
- final largest free span = 4,194,304 bytes;
- final arena fragmentation = 0;
- indirect command resource retires/releases behind completion;
- no pending upload/arena/generic resource retirements at clean shutdown;
- vanilla world entry/rendering remains normal;
- process exits code 0.

Exact section coordinates, face counts, fingerprints, camera-relative origin and number of comparison draws are world/frame-rate dependent and must not be hard-coded.

## Deliberate P2.2 boundary

Dev2 does **not** claim:

- correct Minecraft textures/sprites/material IDs/UVs/tints/render layers - P2.3;
- correct block/sky light or ambient occlusion - P2.4;
- broad cutout/model semantics - P2.5+;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- real-scale scene visibility tuning - Phase 4;
- production performance from this comparison probe.

## Immediate next action

1. Run final CI on the continuity/status head of draft PR #14.
2. Runtime-test the exact `0.2.0-phase2-dev2` JAR on the reference RX 6800 XT.
3. Save the complete log and explicitly report whether the orientation-colored overlay aligned with vanilla terrain.
4. Record the outcome as A-0062.
5. Keep PR #14 draft/unmerged until runtime validation passes.
6. If dev2 passes, synchronize P2.2 to COMPLETE and merge with `[no-release]` when explicitly authorized; then proceed to P2.3 correct texture/material identity.

## Relevant durable decisions

D-0014 through D-0027 remain active.

Especially relevant:

- D-0016 completion-gated lifetime;
- D-0017 bounded/backpressured staging;
- D-0020 generation-safe arena identity;
- D-0023 public Blaze3D graphics first;
- D-0024 permanent reference oracle before production binary/bitmask greedy meshing;
- D-0025 narrow native Vulkan seam only for missing compute/storage capability;
- D-0027 public indexed-indirect graphics remains the baseline unless profiling later justifies native indirect-count integration.
