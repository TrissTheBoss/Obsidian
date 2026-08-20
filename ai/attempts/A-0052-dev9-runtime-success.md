# A-0052 - Phase 1 dev9 runtime success

Date: 2026-08-20
Status: SUCCESS
Milestone: Phase 1 dev9 - GPU visibility and indexed-indirect command compaction
Reference machine: Windows 11, Minecraft 26.2, Fabric Loader 0.19.3, Java 25.0.1, AMD Radeon RX 6800 XT

## Intent

Prove the small-scale final GPU scene shape before allowing Phase 1 to end:

`GPU scene records -> compute visibility -> atomic command compaction -> GPU visible count -> zeroed unused indirect tail -> public Blaze3D fixed-count indexed-indirect draw -> deterministic readback -> completion-gated reclamation`.

The test must retain one useful submission, zero profiler-only submissions, public graphics ownership, and the narrow native compute/storage seam established in dev8.

## Exact tested build

- Mod version: `0.1.0-phase1-dev9`
- PR: #11 `Phase 1: GPU visibility and indirect compaction`
- Exact packaged/final branch head before this evidence commit: `185bffe2e0b98459adb4ca726b16f240949b567b`
- Main JAR SHA-256: `c133229502f4969aa1f894aa09c404d20c06d833ceb11ba26312cf3e6f6ce6de`
- Sources SHA-256: `c44438ba09dfa4161fa6cbe1c0f74df4b7e25bf6927bfa78fa41ded921c0fc55`

## Runtime result

SUCCESS on the reference RX 6800 XT Vulkan runtime.

Startup/capabilities:

- correct `obsidian 0.1.0-phase1-dev9` loaded;
- Minecraft selected Vulkan on AMD Radeon RX 6800 XT;
- `VK_KHR_synchronization2` present;
- Obsidian reported `indirect=true`, `multiDrawIndirect=true`, `persistentMapping=true`;
- `nativeComputeSeam=true`, `nativeGraphicsSeam=false`, `indirectCountConsumed=false` remained the intended ownership boundary.

Submission invariants:

- graphPasses=4;
- usefulSubmissions=1;
- profilerOnlySubmissions=0;
- computeDispatches=1;
- candidates=4;
- expectedVisible=2;
- indirectCalls=1;
- publicIndirectSlots=4;
- pipelineValid=true;
- vertexBytes=144;
- indexBytes=24;
- sceneBytes=64;
- gpuOutputBytes=84;
- stagingPayloadBytes=232;
- arenaUsedBytes=168 at submission.

Visibility/compaction verification:

- executedMask=15;
- visibleCount=2;
- culledCount=2;
- visible-left pixel = RGBA 255/0/255/255;
- visible-right pixel = RGBA 255/0/255/255;
- culled-left pixel = RGBA 0/0/0/255;
- culled-right pixel = RGBA 0/0/0/255;
- corner pixel = RGBA 0/0/0/255;
- pixelsVerified=5;
- compactedCommandsVerified=4, proving the two front visible commands and two fully zero tail commands were correct;
- arenaRetired=2;
- arenaReclaimed=2;
- arenaUsedBytes=0;
- arenaFreeSpans=1;
- arenaFragmentationPermille=0.

Profiler result (validation workload only):

- uploadGpuNs=14800 (~14.80 us);
- visibilityGpuNs=23440 (~23.44 us);
- drawGpuNs=32320 (~32.32 us);
- readbackGpuNs=11680 (~11.68 us);
- totalGpuNs=83680 (~83.68 us);
- queryPolls=1;
- unavailablePolls=0.

The CPU timings include one-shot startup/probe setup and are not renderer performance benchmarks.

World/runtime integration:

- integrated server started normally;
- player joined the world normally;
- vanilla terrain renderer remained active, as intended;
- normal vanilla Chunk Sections UBO growth remained visible during world entry and is not an Obsidian failure.

Shutdown accounting:

- visibilityResult=VERIFIED;
- shutdown after 1913 frames (logged as `1.913` due locale grouping);
- stagingSubmittedBytes=232;
- stagingReclaimedBytes=232;
- stagingHighWater=240;
- stagingBackpressureEvents=0;
- pendingUploadBatches=0;
- arenaHighWater=168;
- arenaAllocations=2;
- arenaAllocationFailures=0;
- arenaRetired=2;
- arenaReclaimed=2;
- arenaRetirementBackpressureEvents=0;
- arenaStaleHandleRejections=0;
- arenaUsedBytes=0;
- arenaFreeSpans=1;
- arenaLargestFree=524288;
- arenaFragmentationPermille=0;
- pendingArenaRetirementBatches=0;
- pendingRetirements=0;
- process exit code 0.

## Interpretation

Dev9 proves the small-scale architecture Obsidian intends to use for terrain: scene metadata can live on the GPU, visibility can be decided on the GPU, surviving work can be compacted into indexed-indirect commands, and public Blaze3D graphics can consume the result without CPU-visible-list construction or native graphics takeover.

The fixed-count zero-tail policy remains a correctness baseline under D-0027. The GPU-visible count is already a first-class output and can later feed `vkCmdDrawIndexedIndirectCount` only if Phase 4 profiling justifies widening the graphics seam.

## Phase transition

This result closes the intended Phase 1 infrastructure work. Phase 2 should begin with one real Minecraft section:

1. capture an immutable section snapshot with a neighbor halo;
2. preserve enough block/render/light information for deterministic meshing without worker-thread world reads;
3. generate geometry with a deliberately simple reference mesher;
4. upload the real section mesh through the validated staging + device arena path;
5. represent it as a GPU scene record and render it through the validated visibility/indirect path;
6. compare against vanilla/reference behavior before disabling vanilla terrain;
7. keep this reference mesher as the differential oracle for Phase 3 binary/bitmask greedy meshing under D-0024.

No failure or workaround remains from dev9 that blocks Phase 2.