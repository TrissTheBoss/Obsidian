# A-0059 - Phase 2 dev2 first drawable real-section plan

**Date:** 2026-08-21  
**Result:** SUCCESS  
**Milestone:** Phase 2 dev2 / roadmap P2.2 - first drawable real section  
**Baseline:** merged Phase 2 dev1 commit `a714e19ce871bf73136d52f85a1780109aa851dd`  
**Development branch:** `phase2/drawable-real-section`

## Objective

Advance Phase 2 from the runtime-validated immutable real-section/reference oracle to the first actual indexed geometry derived from that real section, while preserving the Phase 2 correctness model and the already-proven Phase 1 GPU ownership/lifetime rules.

P2.1 is complete by A-0058 and PR #12's merge. P2.2 is now the active roadmap item.

## Why this milestone exists

Phase 2 dev1 proved that Obsidian can safely cross the mutable Minecraft-world boundary:

`loaded ClientLevel/chunks -> immutable 18^3 primitive snapshot -> deterministic conservative reference faces -> bounded upload/device arena -> exact GPU readback`.

The next correctness risk is translating that canonical real-section face coverage into drawable vertex/index geometry with correct local/world placement and a real graphics submission, without allowing texture, lighting, model-compatibility, or greedy-meshing complexity to obscure geometry/transform errors.

## Dev2 scope

Dev2 should:

1. keep `SectionSnapshot` as the only live-world capture boundary;
2. keep `ReferenceFaceMesh` simple and independent as the permanent coverage/material-identity oracle;
3. generate a separate drawable mesh from the canonical reference faces;
4. emit four vertices and six indices per canonical face with deterministic winding;
5. carry enough section/world origin information to validate real Minecraft world positioning;
6. use the existing bounded staging + generation-safe device arena rather than standalone upload buffers;
7. use the already-proven public Blaze3D indexed/indirect graphics path unless exact Minecraft 26.2 inspection proves a concrete missing capability;
8. exercise real camera/view/projection semantics at the strongest safe hook/API available in Minecraft 26.2;
9. keep vanilla terrain active as the comparison oracle;
10. preserve completion-gated reclamation and zero routine profiler-only submissions.

## Deliberate feature boundary

P2.2 is a geometry/placement milestone, not the final material pipeline.

Unless exact API inspection proves they are inseparable from a valid first draw, dev2 should use a deliberately obvious debug/orientation material or color for Obsidian geometry and leave these roadmap items to their dedicated milestones:

- P2.3: correct texture/sprite/material identity, UV semantics, tint, render layers, resource reload lifetime;
- P2.4: block/sky light and ambient occlusion correctness;
- P2.5+: broader model/cutout semantics.

Unsupported states remain conservatively omitted exactly as in dev1. Dev2 must not turn an unsupported Minecraft model into a fake cube just to increase rendered coverage.

## Exact API inspection required before implementation

Inspect the exact Loom-resolved Minecraft 26.2 bytecode for:

- camera position/orientation and view/projection data;
- `GameRenderer` / `LevelRenderer` lifecycle points relevant to a world-space draw;
- active color/depth target access if a safe visible comparison draw is possible;
- public Blaze3D pipeline/pass support needed for depth-tested real-section geometry;
- block model/material/sprite APIs to identify what P2.2 can intentionally defer to P2.3;
- packed light APIs to identify what P2.2 can intentionally defer to P2.4.

Do not guess these contracts from older Minecraft versions.

The inspection is being run through a temporary non-production branch/workflow and will be recorded in a new immutable attempt before production implementation. The temporary workflow must not be merged into the dev2 branch.

## Drawable mesh correctness contract

For a reference mesh with `F` faces:

- drawable quads = `F`;
- vertices = `F * 4`;
- indices = `F * 6`;
- every drawable quad maps one-to-one to one canonical reference face;
- all positions are derived from the packed canonical local xyz+direction plus the captured section origin;
- no live-world object/read is permitted during drawable mesh construction;
- winding/order is deterministic;
- duplicate builds from the same immutable reference input must be byte/content identical.

The reference oracle remains the authority for face coverage. The drawable builder is not allowed to silently invent or drop faces.

## GPU/lifetime contract

- vertex and index data live in generation-safe `DeviceGeometryArena` allocations;
- staging is bounded and backpressured;
- upload/draw/readback validation should use one/few deliberate useful submissions, not routine profiler-only work;
- compute/native Vulkan scope must not widen merely for convenience;
- arena/staging reuse and destruction remain tied to real GPU completion;
- all temporary dev2 allocations must return to a fully reclaimable/coalesced state after one-shot validation, unless a later persistent-scene step intentionally retains them and documents that lifetime.

## Runtime success criteria

A reference-machine dev2 run should prove at minimum:

- exact `0.2.0-phase2-dev2` build loads on Vulkan;
- a real section is captured with the dev1 immutable-snapshot invariants intact;
- reference build remains deterministic and `worldReadsAfterSnapshot=0`;
- drawable face/vertex/index counts match the reference oracle exactly;
- drawable build is deterministic;
- real vertex/index bytes pass arena/staging ownership correctly;
- a real indexed/indirect graphics draw executes for the captured section geometry;
- world/section origin and camera transform path are explicitly logged/validated;
- vanilla terrain remains active;
- no routine profiler-only submissions are introduced;
- no pending upload/retirement work remains at clean shutdown;
- process exits code 0.

If the strongest safe first implementation uses a private/offscreen target rather than the live world target, the runtime evidence must explicitly say so and P2.2 must not be marked complete until the roadmap's world-position/camera-transform requirement is genuinely demonstrated.

## Explicit non-goals

- no greedy meshing yet;
- no global vanilla terrain replacement;
- no broad resource-pack/custom-model compatibility claim;
- no cutout/translucent/fluid support claim;
- no final lighting/AO claim;
- no speculative native graphics takeover;
- no performance claim from this first drawable correctness probe.

## Next action

1. Finish the exact Minecraft 26.2 dev2 API inspection and record it immutably.
2. Synchronize `CURRENT_STATE.md` / `MASTER_ROADMAP.md` so P2.1 is COMPLETE and P2.2/dev2 is ACTIVE.
3. Bump the development version to `0.2.0-phase2-dev2`.
4. Implement the separate drawable mesh + runtime probe using the narrowest correct public graphics path supported by the inspection.
5. Let GitHub CI compile/package the exact branch.
6. Runtime-test the resulting dev2 artifact on the reference RX 6800 XT before promoting P2.2 to COMPLETE.
