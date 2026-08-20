# A-0061 - Phase 2 dev2 drawable real-section implementation and exact CI package

**Date:** 2026-08-21  
**Result:** SUCCESS (implementation/package); runtime validation pending  
**Milestone:** Phase 2 dev2 / P2.2 first drawable real section  
**Version:** `0.2.0-phase2-dev2`  
**Branch:** `phase2/drawable-real-section`  
**Draft PR:** #14

## Objective

Implement the first actual drawable geometry derived from a real immutable Minecraft section while preserving the Phase 2 reference oracle, Phase 1 bounded memory/lifetime rules, public-Blaze3D graphics policy, and vanilla terrain as the visual comparison path.

This milestone is intentionally a geometry/placement correctness step. Correct Minecraft sprite/material/UV identity remains P2.3 and light/AO remains P2.4. Greedy meshing remains Phase 3.

## Exact API grounding

A-0060 records the exact Minecraft 26.2 inspection used by this implementation. Key consequences:

- inject immediately after `LevelRenderer.render(...)` inside `GameRenderer.renderLevel`, before HUD projection/depth reset;
- use `CameraRenderState.pos` + `viewRotationMatrix` for camera-relative section placement;
- use the currently active `RenderSystem.getProjectionMatrixBuffer()` for the exact world projection;
- use Minecraft's `DynamicTransforms` + `Projection` / `MATRICES_PROJECTION` uniform contract;
- draw into the existing `GameRenderer.mainRenderTarget()` color/depth views with no clears;
- use reversed-depth `GREATER_THAN_OR_EQUAL`, no depth write and no culling for the comparison overlay;
- public `RenderPass` supports the required vertex/index/indirect draw path;
- `GpuBufferSlice.buffer()/offset()/length()` allow an arena index allocation to be bound with `IndexType.INT` without weakening `DeviceGeometryArena` ownership.

No native Vulkan graphics takeover was required.

## Implementation

### Separate permanent oracle and drawable representation

`ReferenceFaceMesh` remains the independent simple canonical oracle.

New `DrawableSectionMesh` consumes only the immutable `SectionSnapshot` + `ReferenceFaceMesh` and builds a separate deterministic drawable representation:

- one reference face -> one drawable quad;
- four vertices per face;
- six 32-bit indices per face;
- section-local positions;
- section origin retained separately;
- exact face/state identity revalidated against the snapshot;
- duplicate drawable build must be content-identical;
- 32-bit indices are mandatory because the worst-case reference stream can produce 98,304 vertices, beyond 16-bit range.

The dev2 vertex format is `DefaultVertexFormat.POSITION_COLOR`: three float position components plus RGBA8 diagnostic color. Colors encode face orientation only so placement/winding errors are visually obvious. They are explicitly **not** Minecraft material/tint semantics.

### Live world comparison draw

New `GameRendererMixin` calls Obsidian immediately after vanilla `LevelRenderer.render(...)`.

New `RealSectionDrawableProbe` then:

1. waits until a valid real section can be captured;
2. builds the permanent reference oracle twice and requires deterministic equality/nonempty output;
3. builds the drawable mesh twice and requires deterministic equality;
4. allocates generation-safe vertex and index spans in `DeviceGeometryArena`;
5. creates one device-preferred indexed-indirect command buffer;
6. uploads vertex, index and one 20-byte indirect command through bounded persistent staging;
7. computes `viewRotation * translate(sectionOrigin - cameraPosition)` from the exact current camera state;
8. binds Minecraft's current projection, dynamic transform and global uniform state;
9. draws through a public Blaze3D `POSITION_COLOR` / triangle-list pipeline using Minecraft's built-in `core/position_color` shaders;
10. depth-tests against the already-rendered vanilla world with no depth write;
11. briefly repeats the comparison draw for a bounded dev-only window so the orientation-colored overlay can actually be observed;
12. retires both arena allocations and the indirect buffer behind real completion handles;
13. never introduces profiler-only submissions.

Vanilla terrain remains active throughout.

### Bounded validation capacities

Dev1's tiny reference stream fit the 256 KiB/512 KiB validation capacities. Drawable worst-case geometry is intentionally larger, so dev2 raises the validation-only bounded capacities to:

- staging: 4 MiB;
- device geometry arena: 4 MiB.

This is not a production memory-budget decision. The capacities remain explicit/fixed/backpressured and exist only to guarantee that the maximum conservative P2.2 position/color + 32-bit-index stream can be validated without fallback allocation.

### Cleanup

The completed dev1 one-shot `RealSectionReferenceProbe` source was removed from the active branch after dev2 took over the runtime probe role.

The permanent correctness pieces remain:

- `SectionSnapshot`;
- `ReferenceFaceMesh`.

The temporary API-inspection PR #13 was closed without merge after A-0060 preserved its findings.

## Exact clean code/package head

Exact production code head before this documentation record:

`ea106324adfdb9bfdef2757edd36fbfd51bf86a9`

GitHub Actions run:

`32424196221`

Result:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- versioned public release: SKIPPED, as required for a development milestone.

Artifact ID:

`9426803061`

Artifact contents:

- `Obsidian-0.2.0-phase2-dev2.jar`;
- `Obsidian-0.2.0-phase2-dev2-sources.jar`.

Main JAR metadata reports exactly:

`obsidian 0.2.0-phase2-dev2`

Required production classes verified present:

- `GameRendererMixin`;
- `DrawableSectionMesh`;
- `RealSectionDrawableProbe`;
- `IndexedIndirectCommandBuffer`;
- `ReferenceFaceMesh`;
- `FrameCoordinator`.

Completed dev1 one-shot `RealSectionReferenceProbe` verified absent from the clean dev2 JAR.

SHA-256:

- main JAR: `a377ff9b34ae6650efcd1d694c55e08602b1ae3aaa98576440ac34afa4987cce`;
- sources JAR: `3f9eb0c5a0bd56978f27739d8dd7dd9fb6ec86b1c13372cb46d198c8885a2654`.

## Runtime success criteria

P2.2 must remain ACTIVE until the reference RX 6800 XT run proves the actual world-render contract.

Expected runtime evidence:

- correct `obsidian 0.2.0-phase2-dev2` loads on Vulkan;
- exact GameRenderer mixin applies with no injection failure;
- a real 18^3 snapshot is captured and P2.1 invariants still pass;
- `interiorAir + interiorSupported + interiorUnsupported = 4096`;
- reference faces > 0;
- reference build determinism = 2;
- drawable faces = reference faces;
- drawable vertices = faces * 4;
- drawable indices = faces * 6;
- drawable build determinism = 2;
- `worldReadsAfterSnapshot=0` for mesh construction;
- pipeline compiles/validates on the real Vulkan backend;
- `nativeGraphicsSeam=false`;
- indexed-indirect live-world drawing executes;
- orientation-colored Obsidian faces visibly align with the corresponding vanilla full-cube faces during the short comparison window rather than appearing offset/rotated/drifting;
- the overlay remains depth-tested against vanilla terrain;
- profiler-only submissions remain 0;
- staging submitted bytes equal `vertexBytes + indexBytes + 20` and are fully reclaimed;
- two arena allocations retire/reclaim behind completion and return the 4 MiB arena to one free span / zero fragmentation;
- the indirect command resource retires/releases behind completion;
- no upload, arena or generic resource retirement remains pending at clean shutdown;
- vanilla terrain/world entry remains normal;
- process exits code 0.

Exact section coordinates, face counts, fingerprints, camera-relative origin and comparison submission count are runtime/world dependent and must not be hard-coded.

## Next action

1. Synchronize `CURRENT_STATE.md` and `MASTER_ROADMAP.md`: P2.1 COMPLETE, P2.2/dev2 ACTIVE.
2. Update draft PR #14 with exact API, implementation and package evidence.
3. Run final CI on the documentation/status head; source behavior after `ea106324...` must remain unchanged.
4. Runtime-test the exact `0.2.0-phase2-dev2` artifact on the reference RX 6800 XT and record the log plus visual overlay result in A-0062.
5. Only after that evidence passes may P2.2 become COMPLETE and PR #14 be promoted/merged with `[no-release]`.
