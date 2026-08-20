# A-0037 - Phase 1 dev6 real Vulkan first-draw success

**Date:** 2026-08-20  
**Status:** SUCCESS  
**Version:** `0.1.0-phase1-dev6`

## Objective

Validate the first actual Obsidian-owned graphics pipeline and indexed draw on the reference Windows 11 / Radeon RX 6800 XT Vulkan machine without modifying the presented Minecraft framebuffer.

## Action

The user ran the exact CI-built `Obsidian-0.1.0-phase1-dev6.jar` with Minecraft 26.2, Fabric Loader 0.19.3, Java 25.0.1 and Minecraft's Vulkan backend. The one-shot dev6 graph uploaded private triangle geometry, rendered it to a 16x16 RGBA8 offscreen target, copied that texture to a MAP_READ buffer, verified deterministic pixels, entered a single-player world, then shut Minecraft down normally.

## Result

SUCCESS.

Observed invariants:

- correct `obsidian 0.1.0-phase1-dev6` loaded;
- Vulkan backend active on AMD Radeon RX 6800 XT, AMD proprietary 26.7.1 driver;
- frame coordinator: contextSlots=3, stagingCapacity=262144, deviceArenaCapacity=524288, graphPasses=3;
- first draw submitted on frame 1;
- usefulSubmissions=1;
- profilerOnlySubmissions=0;
- pipelineValid=true;
- drawCalls=1;
- triangles=1;
- target=16x16 RGBA8;
- vertexBytes=36;
- indexBytes=6;
- stagingPayloadBytes=42;
- graph verified on frame 1 with executedMask=7;
- center pixel `(8,8)` verified RGBA `255/0/255/255`;
- corner pixel `(0,0)` verified RGBA `0/0/0/255`;
- pixelsVerified=2;
- queryPolls=1, unavailablePolls=0;
- staging submitted/reclaimed=42/42 bytes;
- staging high-water=54 bytes, matching the expected 16-byte alignment gap before the 6-byte index reservation;
- staging backpressure=0, pending upload batches=0;
- device geometry arena intentionally remained unused and fully free (524288-byte largest span, fragmentation=0);
- user joined a single-player world normally;
- shutdown after 2543 frames reported `firstDrawResult=VERIFIED`, one useful submission, zero profiler-only submissions and no pending work;
- process exited with code 0.

The observed GPU timings for the tiny validation graph were approximately 10.24 us upload, 21.12 us draw, 6.52 us readback and 38.84 us total. These values prove timestamp plumbing only and are not renderer benchmarks. The one-shot draw CPU setup path was also not treated as steady-state renderer performance.

## Intended effect

Prove that Obsidian can own actual graphics work through Minecraft 26.2's public Blaze3D Vulkan path: custom shaders, graphics pipeline, render attachment, vertex/index binding, indexed draw, readback and integrated timing, all without a separate profiler submission or presented-image modification.

## Actual effect

All intended graphics, correctness, synchronization and lifecycle checks passed on the reference GPU. This validates D-0023's current public-Blaze3D boundary for basic graphics work.

## Evidence

- user Prism Launcher log, 2026-08-20 15:32 local time;
- first-draw submission/verification log lines show `pipelineValid=true`, one draw, one triangle, correct center/corner pixels and embedded GPU timing;
- final coordinator accounting shows staging 42/42, high-water 54, no pending work;
- process exit code 0;
- draft PR #8, branch `phase1/first-draw`.

## Side effects / lessons

- The public Minecraft 26.2 graphics abstraction is sufficient for the first real Obsidian draw; native Vulkan access remains unnecessary at this milestone.
- Same-frame completion is valid because polling is nonblocking and occurs after submission; CPU frame count is still never used as the completion criterion.
- Alignment overhead is visible in staging high-water metrics even when payload accounting remains exact.

## Next action

Promote and merge PR #8 with `[no-release]` after final CI on the runtime-evidence head. Start Phase 1 dev7 from the resulting main commit. Dev7 should connect validated device-arena geometry to an indirect-draw path, inspect exact Minecraft 26.2 support before implementation, retain one/few useful submissions with embedded timestamps, and verify deterministic offscreen pixels before terrain rendering begins.