# A-0041 - Phase 1 dev7 arena-backed indexed-indirect implementation

**Date:** 2026-08-20  
**Status:** SUCCESS for compile validation; real-machine runtime pending  
**Version:** `0.1.0-phase1-dev7`

## Objective

Connect the previously validated device geometry arena to a real graphics draw and prove the true indexed-indirect path that future GPU visibility/compaction will target.

## Action

Implemented:

- `IndexedIndirectCommandBuffer`: fixed-capacity device-preferred storage with `COPY_DST | INDIRECT_PARAMETERS`, 64-command validation capacity and native 20-byte indexed-indirect records;
- `FrameGraphCommandStream.createCompletionFence()`: exposes an additional lightweight timeline handle for the same useful submission, based on A-0040's exact backend inspection;
- `ArenaIndirectDrawProbe`: allocates vertex/index spans from the real `DeviceGeometryArena`, uploads geometry + two indirect commands through bounded staging, renders two separated triangles in one `drawIndexedIndirect(..., 2)` call, reads back a private 16x16 RGBA8 target and verifies left/right/corner pixels;
- arena retirement uses a second Java timeline handle for the exact same useful submission; no extra queue submission and no native `VkFence` are introduced;
- post-submit retirement registration is retry/diagnostic safe: metadata backpressure or registration errors do not route through pre-submit cleanup or destroy in-flight allocations;
- `FrameCoordinator` now owns the dev7 probe and reports indirect/arena/staging metrics;
- bootstrap wording updated;
- dev6 `FirstDrawProbe` removed;
- temporary indirect API workflow removed;
- version bumped to `0.1.0-phase1-dev7`.

## Validation workload

Geometry arena:

- 6 POSITION vertices = 72 bytes;
- 6 SHORT indices = 12 bytes;
- two allocations, expected combined used/high-water payload = 84 bytes before retirement.

Indirect storage:

- two `VkDrawIndexedIndirectCommand` records;
- 20 bytes/command = 40 bytes;
- command 0 draws first three indices;
- command 1 draws next three indices;
- one public `drawIndexedIndirect(commandSlice, 2)` call.

Staging payload:

- vertex 72 bytes;
- index 12 bytes;
- indirect commands 40 bytes;
- payload total = 124 bytes;
- with the existing 16-byte staging alignment, expected high-water is 136 bytes: vertex `[0,72)`, index `[80,92)`, commands `[96,136)`.

Pixel oracle:

- left interior `(4,8)` must be magenta `255/0/255/255`;
- right interior `(11,8)` must be magenta `255/0/255/255`;
- corner `(0,0)` must remain clear black `0/0/0/255`.

The independent left/right checks make a one-command-only failure observable.

Expected final arena state after the same submission's completion handle retires both spans:

- allocations=2;
- retired=2;
- reclaimed=2;
- used=0;
- freeSpans=1;
- largestFree=524288;
- fragmentation=0;
- retirement backpressure=0;
- pending retirement batches=0.

## Result

The clean implementation compiles against exact Minecraft 26.2. GitHub Actions run `32377000579` passed build and artifact upload on the cleaned source lineage; the hardened post-submit retirement source also compiled successfully in its hosted build lineage. Final documented-head CI remains required before distributing the canonical dev7 test JAR.

## Intended effect

Prove the complete CPU-created terrain-draw precursor: bounded upload -> persistent shared GPU arena -> GPU-resident indirect command records -> one multi-draw indexed-indirect call -> deterministic graphics result -> real completion-gated arena reclamation.

## Actual effect

Source/API/compile validation succeeded. Runtime behavior on the RX 6800 XT remains intentionally unclaimed until the CI-built dev7 artifact is tested.

## Next action

Add durable decision/current-state documentation, run CI on the exact final branch head, inspect the CI artifact, distribute dev7 for the normal Vulkan/world runtime test, and keep PR #9 draft/unmerged until that test passes.