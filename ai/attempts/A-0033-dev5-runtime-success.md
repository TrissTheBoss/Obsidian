# A-0033 - Phase 1 dev5 runtime validation success

**Date:** 2026-08-20  
**Status:** SUCCESS  
**Version:** `0.1.0-phase1-dev5`

## Objective

Validate the Phase 1 dev5 fixed frame graph, Obsidian-owned command stream, useful-work-integrated GPU timestamps, nonblocking query collection, deterministic dependent-copy workload, world entry, and clean shutdown on the reference Windows 11 / RX 6800 XT Vulkan machine.

## Action

The user launched the exact CI-built `Obsidian-0.1.0-phase1-dev5.jar` through Prism Launcher 10.0.5 with Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, and Minecraft configured to Vulkan. The run reached a single-player world and then shut down normally.

## Result

SUCCESS.

Observed startup/device state:

- `obsidian 0.1.0-phase1-dev5` loaded;
- Minecraft selected Vulkan;
- GPU: AMD Radeon RX 6800 XT (DISCRETE);
- driver: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`;
- Obsidian attached to Vulkan and armed the frame-graph / integrated-profiler foundation;
- frame coordinator reported 3 context slots, 2048 CPU timing entries, 262144-byte staging capacity, 524288-byte device arena capacity, and 2 graph passes.

Observed graph submission:

- frame = 1;
- passes = 2;
- dependencies = 1;
- useful submissions = 1;
- staging payload = 256 bytes;
- profiler-only submissions = 0;
- timestamp period = 10 ns/tick.

Observed graph verification on frame 1:

- executed mask = `3` (both passes executed);
- pass 0 CPU time log value = `315.500` ns in locale-formatted output;
- pass 1 CPU time log value = `28.000` ns in locale-formatted output;
- pass 0 GPU time log value = `1.640` ns in locale-formatted output;
- pass 1 GPU time log value = `2.600` ns in locale-formatted output;
- total GPU time log value = `4.360` ns in locale-formatted output;
- query polls = 1;
- unavailable query polls = 0;
- useful submissions = 1;
- profiler-only submissions = 0;
- copied bytes verified = 512.

The Java logger uses locale grouping in this run, so values such as `315.500` represent 315500 ns rather than 315.5 ns. These timings are validation evidence for timestamp plumbing, not renderer benchmark results.

World/runtime result:

- recipes/advancements loaded;
- integrated server started;
- user joined the single-player world;
- Minecraft continued normal rendering/chunk-resource behavior;
- vanilla/Mojang Chunk Sections UBO grew reactively through capacities 2 -> 4 -> 8 -> 16 -> 32 -> 64 -> 128 -> 256 -> 512 -> 1024 -> 2048 during world entry. This is observational evidence for future profiling only; it is not attributed to Obsidian dev5 because terrain ownership remains vanilla.

Shutdown accounting after 2649 frames:

- graph result = VERIFIED;
- graph passes = 2;
- useful submissions = 1;
- profiler-only submissions = 0;
- query polls = 1;
- unavailable query polls = 0;
- staging submitted/reclaimed = 256/256 bytes;
- staging high-water = 256 bytes;
- staging backpressure events = 0;
- pending upload batches = 0;
- device arena used/high-water/alloc/failure/retire/reclaim/stale counters = 0;
- device arena free spans = 1;
- largest free span = 524288 bytes;
- fragmentation = 0;
- pending arena retirement batches = 0;
- generic retired/released/pending resources = 0/0/0;
- process exit code = 0.

## Intended effect

Prove that Obsidian can orchestrate multiple dependent passes, timestamp those passes from inside useful owned work, submit the graph once, retrieve GPU timestamp results without routine blocking, verify deterministic GPU data flow, coexist with vanilla terrain rendering, and shut down with no pending GPU work.

## Actual effect

Every dev5 runtime invariant passed. The profiler introduced zero dedicated submissions, both graph passes executed in dependency order, timestamp results were available through one nonblocking poll, both deterministic destination ranges verified, staging fully reclaimed, the otherwise-unused device arena remained pristine, the user entered a world, and the process exited cleanly.

## Evidence

- user Prism Launcher log dated 2026-08-20 15:01:40-15:02:24 +0200;
- exact tested branch head before runtime evidence commit: `33fc90941b4b728195f641060173d74328e2556f`;
- GitHub Actions build run: `32371507539`;
- test JAR SHA-256: `e62044f9556f97c90888ed2bcef36e784cb039126bd3c5cd10e358ed104bfe7e`;
- draft PR #7.

## Why

The exact Minecraft 26.2 query behavior and command-stream design matched the real Vulkan backend. Timestamps were encoded around useful pass work, the useful staging submission also carried the completion fence, and result reads were deferred until completion and used the backend's availability-based non-WAIT query path.

## Side effects / lessons

- Same-frame query availability is valid evidence because completion was still tied to the useful submission and no profiler-only submission was created.
- The public query wrapper allocation remains acceptable for this one-shot validation; D-0022 still forbids turning it into an every-frame default hot-path allocation source.
- Mojang's reactive Chunk Sections UBO growth to 2048 is worth measuring later when Obsidian starts replacing terrain/scene data, but dev5 does not establish that it is a performance problem by itself.
- The orchestration layer is now proven strongly enough to host real graphics work.

## Next action

Promote and merge PR #7 with `[no-release]`. Start Phase 1 dev6 from merged `main`. Inspect exact Minecraft 26.2 shader/pipeline/render-pass/texture/readback APIs and implement the first Obsidian-owned draw as an offscreen/nonvisual validation where possible. The dev6 validation should prove shader/resource loading or compilation, graphics pipeline creation, vertex/index state, render-target ownership, an actual draw command, deterministic output verification, integrated graph timing, clean lifetime handling, world entry, and shutdown before terrain ownership begins.