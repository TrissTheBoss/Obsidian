# Obsidian

[![Build](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml/badge.svg)](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml)

Obsidian is an experimental, Vulkan-only rendering engine replacement project for Minecraft Java 26.2 on Fabric. Its primary performance goal is consistent frame pacing and strong 1%/0.1% lows, followed by smooth chunk streaming and scaling to very large render distances.

> **Current status:** Phase 1 infrastructure. Obsidian does not replace terrain rendering yet and should not be expected to improve FPS at this stage.

## Validated foundation

On the reference Windows 11 / AMD Radeon RX 6800 XT system, Obsidian has validated:

- Fabric/Vulkan bootstrap and device capability capture;
- a render-frame lifecycle hook;
- controlled GPU command submission through Minecraft's active Vulkan `GpuDevice`;
- nonblocking GPU completion observation;
- a three-slot frame-context ring with monotonically increasing serials;
- real fence-gated deferred GPU resource destruction;
- clean single-player world entry and shutdown with no pending retired test resources.

Frame count is never treated as proof that the GPU is finished. Resource reclamation is completion-gated.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3+
- Java 25
- Gradle 9.5.1 for local development
- Vulkan selected under Minecraft's experimental **Graphics API** setting

If Minecraft starts on OpenGL, Obsidian remains inactive for that session instead of crashing. Set **Video Settings -> Graphics API -> Prefer Vulkan (Experimental)** and restart.

## Next Phase 1 milestone

Before terrain ownership begins, Obsidian is building a bounded staging/upload path with:

- fixed-capacity host-visible staging storage;
- aligned suballocation/ring semantics;
- batched GPU copies;
- real completion-gated staging-space reclamation;
- controlled backpressure instead of unbounded temporary allocation;
- upload capacity/high-water/reclamation/backpressure metrics;
- a small non-visual runtime validation workload.

## Roadmap

- **Phase 0:** Vulkan bootstrap, lifecycle seam, capability reporting, conflicts, configuration. **Validated.**
- **Phase 1:** frame contexts, synchronization, resource lifetime, staging/upload allocators, profiling, render graph. **Active.**
- **Phase 2:** initial Obsidian-owned terrain path.
- **Phase 3:** GPU-driven visibility and indirect terrain submission.
- Later phases: translucency, entities, block entities, particles, UI/text, experimental GPU paths.

## Development priorities

1. 1% and 0.1% lows / frame pacing
2. smooth chunk streaming
3. large render-distance scaling
4. average FPS
5. low avoidable CPU allocation and synchronization
6. minimal visual differences without deliberately preserving vanilla rendering bugs

## AI continuity

Read `ai/README.md` before substantial work. The repository stores current state, durable decisions, historical attempts, and immutable new attempt records so another agent/model can continue without relying on chat history.

## License status

No public redistribution license has been selected yet. Until one is added, normal copyright rules apply to the source code.
