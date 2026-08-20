# A-0029 - Phase 1 dev4 device arena runtime validation

**Date:** 2026-08-20  
**Status:** SUCCESS  
**Version:** `0.1.0-phase1-dev4`  
**Reference machine:** Windows 11, Minecraft 26.2, Fabric Loader 0.19.3, Java 25.0.1, AMD Radeon RX 6800 XT, AMD Vulkan driver 26.7.1

## Objective

Validate the device-preferred geometry arena/suballocator on the real Vulkan machine, including bounded allocation failure, staged uploads, multi-frame fence-gated retirement, exact span/slot reuse with generation advancement, stale-handle rejection, deterministic readback, free-span coalescing, world entry, and clean shutdown.

## Action

The user launched the CI-built `Obsidian-0.1.0-phase1-dev4.jar` with Minecraft 26.2 using Vulkan, entered a single-player world, moved around, exited the world, and closed Minecraft normally.

## Result

SUCCESS.

Observed startup/runtime values matched the dev4 success criteria:

- staging capacity: `262144` bytes;
- device arena capacity: `524288` bytes;
- initial A/B/C allocations: 3;
- initial arena used/high-water: `212992` bytes;
- deliberate bounded arena allocation failures: 1;
- initial staging payload: 768 bytes;
- B retirement produced one pending free and one pending retirement batch;
- the retirement remained live across a frame boundary;
- D reused B's exact offset `65536` and slot `1` only after completion;
- slot generation advanced `1 -> 2`;
- old B handle was rejected after reuse (`staleHandleRejections=1`);
- after D reuse: used bytes `196608`, free spans 2, fragmentation `50` permille;
- final A/C/D readback/retirement submitted on frame 3;
- final verification completed on frame 4, three frames after the initial upload;
- final allocation totals: successful=4, failures=1, retired=4, reclaimed=4;
- final arena used bytes=0;
- final free spans=1;
- largest free block=`524288`;
- final fragmentation=0;
- staging submitted/reclaimed=`1024/1024` bytes;
- staging high-water=768 bytes;
- staging backpressure events=0;
- pending upload batches=0;
- pending arena retirement batches=0;
- generic pending retirements=0;
- world entry succeeded;
- frame coordinator closed after 2251 frames;
- process exit code=0.

## Intended effect

Prove that future chunk geometry can live in a reusable device-preferred arena without per-mesh GPU-buffer allocation, and that freeing/reusing spans does not rely on CPU frame count or raw offsets.

## Actual effect

The allocator behaved exactly as designed. Most importantly, B was not reusable until its real GPU fence completed on a later frame. Reusing the same physical span did not revive the old handle because ownership identity includes generation state.

## Why this matters

This closes the main GPU-memory ownership chain required before real terrain geometry:

`bounded persistent staging -> batched copy -> device-preferred arena -> aligned suballocation -> generation-safe handle -> completion-gated retirement -> safe reuse -> coalescing`

The fact that the retirement crossed frame boundaries is strong evidence that arena safety is genuinely completion-gated rather than accidentally passing because a same-frame probe finished quickly.

## Evidence

User-provided Prism Launcher log from 2026-08-20 14:35-14:36 local time. Key runtime lines reported:

- device arena foundation armed;
- initial upload values exactly matching expected values;
- B retirement on frame 1;
- safe B-span reuse on frame 2 with generation `1->2`;
- final retirement on frame 3;
- complete verification on frame 4;
- clean shutdown metrics and exit code 0.

## Next action

Promote and merge PR #6 with `[no-release]`. Begin Phase 1 dev5 on a fresh branch from merged `main`, targeting an Obsidian-owned frame graph/command-stream foundation with GPU timestamp ranges embedded in owned submissions rather than profiler-only submissions. Terrain replacement remains inactive until this orchestration layer is validated.