# A-0053 - Phase 2 real-section reference milestone plan

Date: 2026-08-20
Status: IN PROGRESS
Milestone: Phase 2 dev1 - immutable real-section snapshot + reference mesher
Baseline: Phase 1 closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`

## Why Phase 2 begins here

Phase 1 runtime-validated the infrastructure needed to consume terrain meshes: bounded staging, completion-gated device arenas, frame-graph orchestration, GPU compute, scene visibility, atomic indirect-command compaction and public indexed-indirect graphics.

The next correctness risk is no longer the GPU command path. It is converting Minecraft world/block/render state into immutable mesh input without hidden render-thread/world-thread dependencies or visual semantic loss.

## Dev1 goal

Prove that Obsidian can capture one real loaded Minecraft chunk section into immutable data with a one-block neighbor halo, then mesh a deliberately narrow class of real blocks using only that snapshot after capture.

The reference mesher must remain simple and auditable. It is a correctness oracle for the later Phase 3 binary/bitmask greedy mesher, not the final performance implementation.

## Required architecture

1. Capture only on a legal client/render-thread world access point.
2. Choose a deterministic loaded section near the player after world entry.
3. Snapshot 18x18x18 block-state neighborhood for one 16x16x16 target section (one-block halo in every axis).
4. Never read the live world from meshing code after snapshot construction.
5. Record enough identity/state for the supported reference block class to reproduce face visibility deterministically.
6. Reference mesher emits one quad per exposed face; no greedy merging in Phase 2 dev1.
7. Unsupported/complex states must be counted/skipped explicitly rather than silently approximated.
8. Instrument snapshot time, mesh time, sampled cells, supported/unsupported cells, exposed faces, quad/vertex/index counts and output bytes.
9. Keep vanilla terrain active; this milestone must not globally replace terrain.

## Initial support scope

Prefer a deliberately strict first supported class such as full-cube opaque blocks whose collision/render shape and material path can be identified safely through exact Minecraft 26.2 APIs. Air is empty. Complex model blocks, fluids, cutout/translucent layers, tint, AO and dynamic/model-data cases may be counted as unsupported until their exact APIs are inspected and a later Phase 2 milestone handles them correctly.

Do not guess API semantics from older Minecraft versions.

## Validation strategy

The first runtime validation should prove snapshot correctness and mesher determinism before visible replacement:

- a real loaded section is selected;
- snapshot includes 4096 interior cells plus halo storage;
- a stable fingerprint/hash is computed from immutable snapshot data;
- reference mesher runs without any world object reference;
- every emitted face can be validated from snapshot occupancy/neighbor rules;
- mesh accounting is internally consistent (`quads * 4 == vertices`, `quads * 6 == indices`);
- repeated mesh of the same immutable snapshot produces the same counts/hash;
- world entry and normal shutdown remain clean.

If practical within the same milestone, upload the generated mesh to `DeviceGeometryArena` and validate byte-copy/readback ownership. Visible section rendering is allowed only after the snapshot/mesh semantics are proven.

## Greedy meshing relationship

D-0024 remains the production target. Phase 3 will use worker-local binary/bitmask greedy meshing, but its output must be differential-tested against this reference path on randomized/sampled snapshots. The reference mesher therefore favors clarity over speed and should never acquire performance tricks that make it difficult to audit.

## Immediate research

Inspect exact Minecraft 26.2 APIs for:

- `Minecraft` client world/player access;
- `ClientLevel` / loaded chunk lookup without generation/loading side effects;
- `LevelChunk`, section indexing and `LevelChunkSection` state access;
- section/world min/max build-height helpers;
- `BlockState` air/occlusion/full-cube/render-shape predicates;
- block render layer/material/model APIs relevant to classifying a safe first subset;
- light/AO access needed by later Phase 2 work;
- lifecycle hook where the world is guaranteed loaded and render-thread access is legal.

Temporary inspection workflow must be removed before a testable branch head is packaged.