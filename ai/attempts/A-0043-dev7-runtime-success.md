# A-0043 - Phase 1 dev7 runtime success

**Date:** 2026-08-20  
**Status:** SUCCESS - real Windows 11 / RX 6800 XT Vulkan runtime  
**Version:** `0.1.0-phase1-dev7`

## Objective

Validate the first arena-backed true indexed-indirect graphics path on the reference machine, including multiple indirect commands, deterministic pixels, staging accounting, same-submission arena retirement, world entry and clean shutdown.

## Action

User tested the CI-built dev7 JAR in Prism Launcher with Minecraft 26.2 / Fabric Loader 0.19.3 / Vulkan on AMD Radeon RX 6800 XT and supplied the complete launcher log.

## Result

SUCCESS.

Observed invariants:

- correct `obsidian 0.1.0-phase1-dev7` loaded;
- Vulkan backend, RX 6800 XT, AMD driver 26.7.1;
- device capabilities: indirect=true, multiDrawIndirect=true, persistentMapping=true;
- graphPasses=3;
- usefulSubmissions=1;
- profilerOnlySubmissions=0;
- indirectCalls=1;
- indirectCommands=2;
- triangles=2;
- pipelineValid=true;
- vertexArenaOffset=0, indexArenaOffset=72, firstIndex=36;
- vertexBytes=72, indexBytes=12, indirectBytes=40;
- stagingPayloadBytes=124;
- deterministic pixels: left magenta, right magenta, corner black; pixelsVerified=3;
- executedMask=7;
- timestamp ranges resolved with one nonblocking query poll;
- arenaRetired=2, arenaReclaimed=2, arenaUsedBytes=0;
- final staging submitted/reclaimed=124/124 bytes, high-water=136, backpressure=0, pending=0;
- final arena high-water=84, allocations=2, retired/reclaimed=2/2, freeSpans=1, largestFree=524288, fragmentation=0, pending retirement batches=0;
- entered a single-player world normally;
- shutdown after 2276 frames had no pending retirements;
- process exited with code 0.

## Intended effect

Prove that Obsidian can store geometry in the shared device arena, upload native Vulkan-layout indirect command records, execute multiple indexed indirect draws through public Blaze3D in one useful submission, verify both draws independently, and reclaim the arena only after the same submission completes.

## Actual effect

All target behavior matched. Both indirect command records demonstrably rendered because the left and right independent pixels were magenta while the untouched corner remained black. Staging and arena lifetime accounting were exact and fully reclaimed.

## Evidence

User runtime log from 2026-08-20 21:11 CEST:

- startup/version/backend/capabilities: lines 217-253;
- dev7 submit: lines 254-255;
- dev7 verification: lines 259-260;
- world entry: lines 279-297;
- shutdown accounting and exit code 0: lines 325-328.

## Why it worked

Minecraft 26.2 public Blaze3D maps `RenderPass.drawIndexedIndirect` to the Vulkan indexed-indirect path, while the RX 6800 XT exposes the required indirect and multi-draw-indirect features. Obsidian's 20-byte command records matched the native layout, the shared arena slices matched vertex/index bindings, and staging/arena completion handles referenced the same useful submission timeline.

## Lesson

The public Blaze3D boundary remains sufficient for the current GPU-driven direction. The next useful capability to prove is GPU/compute generation of indirect command data rather than CPU-authored indirect records.

## Next action

Final-CI this runtime-evidence head, promote and squash-merge PR #9 with `[no-release]`, then start Phase 1 dev8 from the resulting `main` commit. Dev8 should inspect exact Minecraft 26.2 compute/storage-buffer APIs and validate compute-written indirect command data consumed by a following graphics pass in the same owned graph/submission if the public API supports the required dependency semantics.