# Obsidian

[![Build](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml/badge.svg)](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml)

Obsidian is an experimental, Vulkan-only rendering engine replacement project for Minecraft Java 26.2 on Fabric. Its primary performance goal is consistent frame pacing and strong 1%/0.1% lows, followed by smooth chunk streaming and scaling to very large render distances.

> **Current status:** Phase 1 infrastructure. Obsidian does not replace terrain rendering yet and should not be expected to improve FPS at this stage.

## Validated foundation

On the reference Windows 11 / AMD Radeon RX 6800 XT system, Obsidian has validated Fabric/Vulkan bootstrap, controlled GPU submission, a render-frame lifecycle hook, three rotating frame-context slots, and real fence-gated deferred GPU resource destruction. Frame count is never treated as proof that the GPU is finished.

## Next milestone

Phase 1 is now moving into bounded staging/upload infrastructure: fixed-capacity host-visible staging, batched GPU copies, completion-gated space reclamation, controlled backpressure, and upload metrics before terrain ownership begins.

## Roadmap

- **Phase 0:** Vulkan bootstrap/device bridge. **Validated.**
- **Phase 1:** synchronization, resource lifetime, staging/upload allocators, profiling, render graph. **Active.**
- **Phase 2:** initial Obsidian-owned terrain path.
- **Phase 3:** GPU-driven visibility and indirect terrain submission.

## AI continuity

Read `ai/README.md` before substantial work. The repository stores current state, durable decisions, historical attempts, and immutable new attempt records so another model/agent can continue without relying on chat history.

## License status

No public redistribution license has been selected yet. Until one is added, normal copyright rules apply to the source code.
