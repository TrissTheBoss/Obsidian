# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev8, merge commit `68e9710ff964a44165122c5d85c0d559e4698b11`
- Active development branch: `phase1/visibility-compaction`
- Active PR: #11, `Phase 1: GPU visibility and indirect compaction`
- Current development version: `0.1.0-phase1-dev9`
- Dev9 status: **runtime validated on the reference RX 6800 XT; final runtime-evidence CI + merge pending**

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
- Vulkan driver observed: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`

## Phase 1 status

Phase 0 and Phase 1 dev1-dev8 are runtime validated and merged with `[no-release]` development semantics.

Phase 1 dev9 is now also runtime validated and is the intended Phase 1 closing milestone.

### Dev9 runtime result - SUCCESS

Exact tested build:

- version `0.1.0-phase1-dev9`;
- packaged/final branch head before runtime evidence `185bffe2e0b98459adb4ca726b16f240949b567b`;
- main JAR SHA-256 `c133229502f4969aa1f894aa09c404d20c06d833ceb11ba26312cf3e6f6ce6de`.

Runtime proved:

- Vulkan on RX 6800 XT with Synchronization2 and indirect/multi-draw support;
- graphPasses=4, executedMask=15;
- usefulSubmissions=1, profilerOnlySubmissions=0;
- computeDispatches=1;
- 4 GPU scene candidates, visibleCount=2, culledCount=2;
- one public fixed-count indexed-indirect call with 4 slots;
- GPU compacted the two visible commands to the front and zeroed both unused tail commands;
- `compactedCommandsVerified=4`;
- visible-left/right pixels magenta, culled-left/right + corner black, `pixelsVerified=5`;
- `nativeComputeSeam=true`, `nativeGraphicsSeam=false`, `indirectCountConsumed=false`;
- staging submitted/reclaimed=232/232 bytes, high-water=240, no backpressure;
- arena high-water=168, allocations=2, retired/reclaimed=2/2, used=0, fully coalesced;
- world entry succeeded;
- shutdown after 1913 frames had no pending GPU work;
- process exited with code 0.

Runtime evidence: `ai/attempts/A-0052-dev9-runtime-success.md`.

## Proven Phase 1 architecture

`Minecraft Vulkan device/queue/presentation -> FrameCoordinator -> bounded persistent staging -> generation-safe device geometry arena -> fixed frame graph -> one Minecraft-owned useful submission -> narrow native Vulkan compute/storage seam -> GPU scene visibility -> atomic indirect-command compaction + GPU visible count -> explicit Sync2 hazards -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

The Phase 1 infrastructure required before real terrain is now proven on the reference hardware.

## Phase 2 direction - first real Minecraft section

Phase 2 should begin only after PR #11 is final-CI gated and merged.

The first Phase 2 milestone is a correctness-first real-section path:

1. identify and capture one real loaded Minecraft section on the client/render thread;
2. copy it into an immutable compact section snapshot;
3. include a one-block neighbor halo/padding needed for face visibility and later AO/light correctness without worker-thread world reads;
4. build a deliberately simple reference mesh from the snapshot;
5. initially constrain supported block/render cases if needed so the output can be proven correct rather than pretending broad compatibility;
6. upload the mesh through the validated staging + `DeviceGeometryArena` path;
7. create a GPU scene record for that section and render it through the validated visibility/indirect path;
8. preserve vanilla terrain during the first milestone as the comparison/reference path; do not globally replace terrain until the Obsidian section output is validated;
9. instrument snapshot time, mesh time, input block count, exposed-face count, vertex/index bytes, staging latency and GPU allocation bytes.

The simple Phase 2 mesher is a permanent differential oracle, not throwaway code.

## Greedy meshing roadmap

D-0024 remains active.

- **Phase 2:** immutable real-section snapshot + halo + simple reference mesher/differential oracle.
- **Phase 3:** production worker-local binary/bitmask greedy mesher with reusable scratch and render-correct merge keys covering material/sprite, render layer, tint, light, AO corners/diagonal, UV behavior and special/fluid state.
- **Phase 4+:** scale the dev9 GPU visibility/compaction architecture over the reduced production meshes; revisit native indirect-count consumption only if profiling justifies it.

Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

## Immediate next action

1. Update PR #11 to record runtime SUCCESS.
2. Run final CI on the exact runtime-evidence head.
3. Promote and squash-merge PR #11 with `[no-release]`.
4. Mark Phase 1 complete on `main`.
5. Create Phase 2 branch from the exact dev9 merge commit.
6. Inspect Minecraft 26.2 client-world/chunk-section/block-state/light/render-model APIs needed for immutable snapshot extraction and a narrow reference mesher.
7. Implement the first Phase 2 snapshot/reference-mesh milestone; keep vanilla terrain active until real-section correctness is proven.

## Relevant durable decisions

D-0014 through D-0027 remain active. D-0024 governs the production greedy mesher; D-0025/D-0026 constrain native compute interop and synchronization; D-0027 preserves public fixed-count graphics as the baseline GPU visibility path.